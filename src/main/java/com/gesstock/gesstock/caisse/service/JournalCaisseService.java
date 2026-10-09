package com.gesstock.gesstock.caisse.service;

import com.gesstock.gesstock.caisse.dto.EcritureCaisse;
import com.gesstock.gesstock.caisse.dto.EncaissementHorsEspeces;
import com.gesstock.gesstock.caisse.dto.JournalCaisseResponse;
import com.gesstock.gesstock.caisse.dto.JournalSessionCaisse;
import com.gesstock.gesstock.caisse.dto.TotauxJournalCaisse;
import com.gesstock.gesstock.caisse.dto.TypeEcritureCaisse;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.MouvementCaisse;
import com.gesstock.gesstock.entities.ReglementClient;
import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.MouvementCaisseRepository;
import com.gesstock.gesstock.repositories.ReglementClientRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Journal de caisse : pour chaque journée de la période, les écritures d'espèces dans l'ordre du temps (fond de caisse, ventes
 * encaissées en espèces, règlements de dettes en espèces, sorties) et le solde après chacune. Les règles sont celles du solde
 * théorique de la clôture ({@code SessionCaisseService}) : une vente annulée n'y figure plus, une vente à crédit n'a rien
 * rapporté, et ce qui est payé autrement qu'en espèces (Mobile Money, carte…) ne passe pas par le tiroir-caisse : il est résumé
 * à part. Le dernier solde de chaque journée est donc toujours égal au solde théorique affiché à la clôture.
 */
@Service
@Transactional(readOnly = true)
public class JournalCaisseService {

    private static final List<StatutVente> STATUTS = List.of(StatutVente.VALIDEE, StatutVente.ANNULEE);

    private final SessionCaisseRepository sessionRepository;
    private final VenteRepository venteRepository;
    private final ReglementClientRepository reglementRepository;
    private final MouvementCaisseRepository mouvementRepository;

    public JournalCaisseService(
        SessionCaisseRepository sessionRepository,
        VenteRepository venteRepository,
        ReglementClientRepository reglementRepository,
        MouvementCaisseRepository mouvementRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.venteRepository = venteRepository;
        this.reglementRepository = reglementRepository;
        this.mouvementRepository = mouvementRepository;
    }

    public JournalCaisseResponse journal(LocalDate debut, LocalDate fin) {
        HistoriqueVentesService.verifierPeriode(debut, fin);
        List<SessionCaisse> sessions = new ArrayList<>(sessionRepository.findByJourneeBetweenOrderByJourneeDescOuverteLeDesc(debut, fin));
        sessions.sort(Comparator.comparing(SessionCaisse::getJournee));   // dans l'ordre des journées, comme un livre de caisse
        List<JournalSessionCaisse> journees = sessions.stream().map(this::journee).toList();
        return new JournalCaisseResponse(debut, fin, totaux(journees), journees);
    }

    private JournalSessionCaisse journee(SessionCaisse session) {
        List<Ecriture> ecritures = new ArrayList<>();
        Map<ModeReglement, BigDecimal> horsEspeces = new EnumMap<>(ModeReglement.class);
        BigDecimal ventesACredit = BigDecimal.ZERO;
        long validees = 0;
        long annulees = 0;

        for (Vente vente : venteRepository.findBySessionCaisseIdAndStatutInOrderByDateVenteAscIdAsc(session.getId(), STATUTS)) {
            if (vente.getStatut() == StatutVente.ANNULEE) {
                annulees++;
                continue;
            }
            validees++;
            if (vente.isVenteACredit()) {
                ventesACredit = ventesACredit.add(vente.getMontantTotal());
            } else if (vente.getModeReglement() == ModeReglement.ESPECES) {
                String client = vente.getClient() == null ? "client de passage" : vente.getClient().getNom();
                ecritures.add(new Ecriture(vente.getDateVente(), TypeEcritureCaisse.VENTE, "Vente — " + client, vente.getCode(),
                    vente.getVendeur() == null ? null : vente.getVendeur().getUsername(), vente.getMontantTotal(), null));
            } else {
                ModeReglement mode = vente.getModeReglement() == null ? ModeReglement.AUTRE : vente.getModeReglement();
                horsEspeces.merge(mode, vente.getMontantTotal(), BigDecimal::add);
            }
        }
        for (ReglementClient reglement : reglementRepository.findBySessionCaisseIdOrderByDateReglementDesc(session.getId())) {
            if (reglement.getModeReglement() == ModeReglement.ESPECES) {
                String client = reglement.getClient() == null ? "client" : reglement.getClient().getNom();
                ecritures.add(new Ecriture(reglement.getDateReglement(), TypeEcritureCaisse.REGLEMENT, "Règlement de dette — " + client,
                    reglement.getReference(), reglement.getRecuPar() == null ? null : reglement.getRecuPar().getUsername(),
                    reglement.getMontant(), null));
            } else {
                ModeReglement mode = reglement.getModeReglement() == null ? ModeReglement.AUTRE : reglement.getModeReglement();
                horsEspeces.merge(mode, reglement.getMontant(), BigDecimal::add);
            }
        }
        for (MouvementCaisse sortie : mouvementRepository.findBySessionCaisseIdOrderByDateMouvementDesc(session.getId())) {
            ecritures.add(new Ecriture(sortie.getDateMouvement(), TypeEcritureCaisse.SORTIE,
                "Sortie — " + sortie.getMotif() + " (" + sortie.getBeneficiaire() + ")", null,
                sortie.getEffectuePar() == null ? null : sortie.getEffectuePar().getUsername(), null, sortie.getMontant()));
        }
        ecritures.sort(Comparator.comparing(Ecriture::date));   // tri stable : à l'heure près, l'ordre de saisie est conservé

        // L'ouverture ouvre toujours le journal ; le solde se calcule ensuite écriture par écriture.
        List<EcritureCaisse> lignes = new ArrayList<>();
        BigDecimal solde = session.getFondDeCaisse();
        lignes.add(new EcritureCaisse(session.getOuverteLe(), TypeEcritureCaisse.OUVERTURE, "Fond de caisse à l'ouverture", null,
            session.getOuvertePar() == null ? null : session.getOuvertePar().getUsername(), session.getFondDeCaisse(), null, solde));
        BigDecimal encaissements = BigDecimal.ZERO;
        BigDecimal reglements = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        for (Ecriture ecriture : ecritures) {
            if (ecriture.entree() != null) {
                solde = solde.add(ecriture.entree());
                if (ecriture.type() == TypeEcritureCaisse.VENTE) encaissements = encaissements.add(ecriture.entree());
                else reglements = reglements.add(ecriture.entree());
            } else {
                solde = solde.subtract(ecriture.sortie());
                sorties = sorties.add(ecriture.sortie());
            }
            lignes.add(new EcritureCaisse(ecriture.date(), ecriture.type(), ecriture.libelle(), ecriture.reference(),
                ecriture.utilisateur(), ecriture.entree(), ecriture.sortie(), solde));
        }

        BigDecimal ecart = session.getMontantCloture() == null ? null : session.getMontantCloture().subtract(solde);
        List<EncaissementHorsEspeces> autres = horsEspeces.entrySet().stream()
            .map(entree -> new EncaissementHorsEspeces(entree.getKey(), entree.getValue())).toList();
        return new JournalSessionCaisse(
            session.getId(), session.getCode(), session.getJournee(), session.getStatut(),
            session.getOuvertePar() == null ? null : session.getOuvertePar().getUsername(),
            session.getFermeePar() == null ? null : session.getFermeePar().getUsername(),
            session.getOuverteLe(), session.getFermeeLe(), session.getFondDeCaisse(),
            encaissements, reglements, sorties, solde, session.getMontantCloture(), ecart,
            validees, annulees, ventesACredit, autres,
            session.getStatut() == StatutSessionCaisse.OUVERTE && session.getJournee().isBefore(LocalDate.now()),
            lignes);
    }

    private static TotauxJournalCaisse totaux(List<JournalSessionCaisse> journees) {
        BigDecimal fonds = BigDecimal.ZERO;
        BigDecimal encaissements = BigDecimal.ZERO;
        BigDecimal reglements = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        BigDecimal solde = BigDecimal.ZERO;
        BigDecimal comptees = BigDecimal.ZERO;
        BigDecimal ecarts = BigDecimal.ZERO;
        BigDecimal autres = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        long ventes = 0;
        long annulees = 0;
        int nonCloturees = 0;
        for (JournalSessionCaisse journee : journees) {
            fonds = fonds.add(journee.fondDeCaisse());
            encaissements = encaissements.add(journee.encaissementsEspeces());
            reglements = reglements.add(journee.reglementsClientsEspeces());
            sorties = sorties.add(journee.sorties());
            solde = solde.add(journee.soldeTheorique());
            if (journee.montantCloture() == null) {
                nonCloturees++;
            } else {
                comptees = comptees.add(journee.montantCloture());
                ecarts = ecarts.add(journee.ecart());
            }
            for (EncaissementHorsEspeces autre : journee.autresEncaissements()) autres = autres.add(autre.montant());
            credit = credit.add(journee.ventesACredit());
            ventes += journee.nombreVentes();
            annulees += journee.nombreVentesAnnulees();
        }
        return new TotauxJournalCaisse(journees.size(), nonCloturees, ventes, annulees, fonds, encaissements, reglements, sorties,
            solde, comptees, ecarts, autres, credit);
    }

    /** Une écriture avant calcul du solde : exactement une des deux colonnes, entrée ou sortie, est renseignée. */
    private record Ecriture(LocalDateTime date, TypeEcritureCaisse type, String libelle, String reference, String utilisateur,
                            BigDecimal entree, BigDecimal sortie) {}
}
