package com.gesstock.gesstock.caisse.service;

import com.gesstock.gesstock.caisse.dto.CaissierDuJournal;
import com.gesstock.gesstock.caisse.dto.EcritureCaisse;
import com.gesstock.gesstock.caisse.dto.EcritureCaissier;
import com.gesstock.gesstock.caisse.dto.EncaissementHorsEspeces;
import com.gesstock.gesstock.caisse.dto.JournalCaisseResponse;
import com.gesstock.gesstock.caisse.dto.JournalCaissierJour;
import com.gesstock.gesstock.caisse.dto.JournalCaissierResponse;
import com.gesstock.gesstock.caisse.dto.JournalSessionCaisse;
import com.gesstock.gesstock.caisse.dto.TotauxJournalCaisse;
import com.gesstock.gesstock.caisse.dto.TotauxJournalCaissier;
import com.gesstock.gesstock.caisse.dto.TypeEcritureCaisse;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.MouvementCaisse;
import com.gesstock.gesstock.entities.ReglementClient;
import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.MouvementCaisseRepository;
import com.gesstock.gesstock.repositories.ReglementClientRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
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
    private final UtilisateurRepository utilisateurRepository;

    public JournalCaisseService(
        SessionCaisseRepository sessionRepository,
        VenteRepository venteRepository,
        ReglementClientRepository reglementRepository,
        MouvementCaisseRepository mouvementRepository,
        UtilisateurRepository utilisateurRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.venteRepository = venteRepository;
        this.reglementRepository = reglementRepository;
        this.mouvementRepository = mouvementRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    public JournalCaisseResponse journal(LocalDate debut, LocalDate fin) {
        HistoriqueVentesService.verifierPeriode(debut, fin);
        List<SessionCaisse> sessions = new ArrayList<>(sessionRepository.findByJourneeBetweenOrderByJourneeDescOuverteLeDesc(debut, fin));
        sessions.sort(Comparator.comparing(SessionCaisse::getJournee));   // dans l'ordre des journées, comme un livre de caisse
        List<JournalSessionCaisse> journees = sessions.stream().map(this::journee).toList();
        return new JournalCaisseResponse(debut, fin, totaux(journees), journees);
    }

    /**
     * Les écritures d'espèces de la session dans l'ordre du temps, ce qui est entré hors espèces et les ventes à crédit. Avec un
     * {@code caissier}, seulement ce que ce compte a fait : ses ventes (vendeur), les règlements qu'il a reçus, ses sorties.
     */
    private Collecte collecter(SessionCaisse session, String caissier) {
        List<Ecriture> ecritures = new ArrayList<>();
        Map<ModeReglement, BigDecimal> horsEspeces = new EnumMap<>(ModeReglement.class);
        BigDecimal ventesACredit = BigDecimal.ZERO;
        long validees = 0;
        long annulees = 0;

        for (Vente vente : venteRepository.findBySessionCaisseIdAndStatutInOrderByDateVenteAscIdAsc(session.getId(), STATUTS)) {
            if (!concerne(caissier, vente.getVendeur())) continue;
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
            if (!concerne(caissier, reglement.getRecuPar())) continue;
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
            if (!concerne(caissier, sortie.getEffectuePar())) continue;
            ecritures.add(new Ecriture(sortie.getDateMouvement(), TypeEcritureCaisse.SORTIE,
                "Sortie — " + sortie.getMotif() + " (" + sortie.getBeneficiaire() + ")", null,
                sortie.getEffectuePar() == null ? null : sortie.getEffectuePar().getUsername(), null, sortie.getMontant()));
        }
        ecritures.sort(Comparator.comparing(Ecriture::date));   // tri stable : à l'heure près, l'ordre de saisie est conservé
        return new Collecte(ecritures, horsEspeces, ventesACredit, validees, annulees);
    }

    /** Sans filtre ({@code caissier} nul), tout est retenu ; sinon seulement ce qui porte le nom de ce compte. */
    private static boolean concerne(String caissier, Utilisateur auteur) {
        return caissier == null || (auteur != null && caissier.equals(auteur.getUsername()));
    }

    private JournalSessionCaisse journee(SessionCaisse session) {
        Collecte collecte = collecter(session, null);
        List<Ecriture> ecritures = collecte.ecritures();
        Map<ModeReglement, BigDecimal> horsEspeces = collecte.horsEspeces();
        BigDecimal ventesACredit = collecte.ventesACredit();
        long validees = collecte.validees();
        long annulees = collecte.annulees();

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

    /**
     * Le journal d'un caissier : seulement ses lignes, journée par journée (les journées où il n'a rien fait n'y figurent pas),
     * avec ses espèces à remettre. Le compte doit exister ; un compte désactivé reste consultable (audit d'un ancien employé).
     */
    public JournalCaissierResponse journalDuCaissier(String caissier, LocalDate debut, LocalDate fin) {
        HistoriqueVentesService.verifierPeriode(debut, fin);
        Utilisateur compte = utilisateurRepository.findByUsernameIgnoreCase(caissier == null ? "" : caissier.trim())
            .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable : " + caissier));
        List<SessionCaisse> sessions = new ArrayList<>(sessionRepository.findByJourneeBetweenOrderByJourneeDescOuverteLeDesc(debut, fin));
        sessions.sort(Comparator.comparing(SessionCaisse::getJournee));
        List<JournalCaissierJour> jours = new ArrayList<>();
        for (SessionCaisse session : sessions) {
            Collecte collecte = collecter(session, compte.getUsername());
            if (collecte.ecritures().isEmpty() && collecte.validees() == 0 && collecte.annulees() == 0) continue;
            jours.add(jourDuCaissier(session, collecte));
        }
        return new JournalCaissierResponse(compte.getUsername(), debut, fin, totauxDuCaissier(jours), jours);
    }

    /** Les comptes dont un responsable peut demander le journal, par ordre alphabétique. */
    public List<CaissierDuJournal> caissiersDuJournal() {
        return utilisateurRepository.findAll().stream()
            .map(compte -> new CaissierDuJournal(compte.getUsername(), compte.isActif()))
            .sorted(Comparator.comparing(CaissierDuJournal::username, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static JournalCaissierJour jourDuCaissier(SessionCaisse session, Collecte collecte) {
        List<EcritureCaissier> lignes = new ArrayList<>();
        BigDecimal ventes = BigDecimal.ZERO;
        BigDecimal reglements = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        BigDecimal cumul = BigDecimal.ZERO;
        for (Ecriture ecriture : collecte.ecritures()) {
            if (ecriture.entree() != null) {
                cumul = cumul.add(ecriture.entree());
                if (ecriture.type() == TypeEcritureCaisse.VENTE) ventes = ventes.add(ecriture.entree());
                else reglements = reglements.add(ecriture.entree());
            } else {
                cumul = cumul.subtract(ecriture.sortie());
                sorties = sorties.add(ecriture.sortie());
            }
            lignes.add(new EcritureCaissier(ecriture.date(), ecriture.type(), ecriture.libelle(), ecriture.reference(),
                ecriture.entree(), ecriture.sortie(), cumul));
        }
        List<EncaissementHorsEspeces> autres = collecte.horsEspeces().entrySet().stream()
            .map(entree -> new EncaissementHorsEspeces(entree.getKey(), entree.getValue())).toList();
        return new JournalCaissierJour(session.getId(), session.getCode(), session.getJournee(), session.getStatut(),
            ventes, reglements, sorties, ventes.add(reglements).subtract(sorties), collecte.validees(), collecte.annulees(),
            collecte.ventesACredit(), autres, lignes);
    }

    private static TotauxJournalCaissier totauxDuCaissier(List<JournalCaissierJour> jours) {
        BigDecimal ventes = BigDecimal.ZERO;
        BigDecimal reglements = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        BigDecimal aRemettre = BigDecimal.ZERO;
        BigDecimal autres = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        long nombreVentes = 0;
        long annulees = 0;
        for (JournalCaissierJour jour : jours) {
            ventes = ventes.add(jour.ventesEspeces());
            reglements = reglements.add(jour.reglementsEspeces());
            sorties = sorties.add(jour.sorties());
            aRemettre = aRemettre.add(jour.especesARemettre());
            for (EncaissementHorsEspeces autre : jour.autresEncaissements()) autres = autres.add(autre.montant());
            credit = credit.add(jour.ventesACredit());
            nombreVentes += jour.nombreVentes();
            annulees += jour.nombreVentesAnnulees();
        }
        return new TotauxJournalCaissier(jours.size(), nombreVentes, annulees, ventes, reglements, sorties, aRemettre, autres, credit);
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

    /** Ce qu'une session contient, avant calcul des soldes. */
    private record Collecte(List<Ecriture> ecritures, Map<ModeReglement, BigDecimal> horsEspeces, BigDecimal ventesACredit,
                            long validees, long annulees) {}

    /** Une écriture avant calcul du solde : exactement une des deux colonnes, entrée ou sortie, est renseignée. */
    private record Ecriture(LocalDateTime date, TypeEcritureCaisse type, String libelle, String reference, String utilisateur,
                            BigDecimal entree, BigDecimal sortie) {}
}
