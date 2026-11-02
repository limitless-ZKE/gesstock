package com.gesstock.gesstock.ventes.service;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.DetteClient;
import com.gesstock.gesstock.entities.LigneVente;
import com.gesstock.gesstock.entities.ReglementClient;
import com.gesstock.gesstock.entities.StatutDette;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.ReglementClientRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.stock.Quantites;
import com.gesstock.gesstock.ventes.dto.HistoriqueVentesResponse;
import com.gesstock.gesstock.ventes.dto.LigneHistoriqueVente;
import com.gesstock.gesstock.ventes.dto.LigneVenteSoldee;
import com.gesstock.gesstock.ventes.dto.ResumeHistoriqueVentes;
import com.gesstock.gesstock.ventes.dto.ResumeVentesSoldees;
import com.gesstock.gesstock.ventes.dto.VentesSoldeesResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Historique des ventes sur une période, pour l'écran et pour les documents imprimés (Excel, PDF) : les trois lisent les mêmes
 * chiffres, calculés ici une seule fois. Les ventes en brouillon n'en font pas partie : elles n'ont encore rien changé. Toutes les
 * ventes, ou seulement les ventes soldées ; celles de tous les vendeurs, ou d'un seul (le contrôleur décide qui voit quoi).
 */
@Service
@Transactional(readOnly = true)
public class HistoriqueVentesService {

    /** Une période plus longue est refusée : au-delà d'un an, un état de gestion se découpe par exercice. */
    public static final int JOURS_MAX = 366;
    /** Au plus ce nombre de ventes à l'écran : plus, le navigateur ralentit ; l'impression, elle, va plus loin. */
    public static final int VENTES_ECRAN_MAX = 5_000;
    public static final int VENTES_DOCUMENT_MAX = 50_000;

    private static final List<StatutVente> STATUTS = List.of(StatutVente.VALIDEE, StatutVente.ANNULEE);
    /** Taille des listes d'identifiants envoyées en une requête (une clause « in » trop longue ralentit la base). */
    private static final int PAQUET = 1_000;

    private final VenteRepository venteRepository;
    private final ReglementClientRepository reglementRepository;

    public HistoriqueVentesService(VenteRepository venteRepository, ReglementClientRepository reglementRepository) {
        this.venteRepository = venteRepository;
        this.reglementRepository = reglementRepository;
    }

    public HistoriqueVentesResponse historique(LocalDate debut, LocalDate fin) {
        return historique(debut, fin, null);
    }

    /** @param vendeur le compte dont on veut les ventes, ou {@code null} pour tous les vendeurs */
    public HistoriqueVentesResponse historique(LocalDate debut, LocalDate fin, String vendeur) {
        return construire(debut, fin, charger(debut, fin, vendeur, VENTES_ECRAN_MAX, "l'afficher"));
    }

    public List<Vente> charger(LocalDate debut, LocalDate fin, int limite, String usage) {
        return charger(debut, fin, null, limite, usage);
    }

    /** Les ventes de la période (d'un vendeur ou de tous), les plus récentes d'abord, avec leurs lignes, leur client et leur vendeur. */
    public List<Vente> charger(LocalDate debut, LocalDate fin, String vendeur, int limite, String usage) {
        return chargerAvecStatuts(STATUTS, debut, fin, vendeur, limite, usage);
    }

    private List<Vente> chargerAvecStatuts(List<StatutVente> statuts, LocalDate debut, LocalDate fin, String vendeur, int limite, String usage) {
        verifierPeriode(debut, fin);
        var du = debut.atStartOfDay();
        var au = fin.plusDays(1).atStartOfDay();
        long total = venteRepository.compterVentesDeLaPeriode(statuts, du, au, vendeur);
        if (total > limite) {
            throw new RequeteInvalideException("Cette période compte " + total + " ventes : c'est trop pour " + usage
                + " (" + limite + " au plus). Choisissez une période plus courte.");
        }
        return venteRepository.ventesDeLaPeriode(statuts, du, au, vendeur);
    }

    /** Les ventes de la période entièrement payées (au comptant, ou à crédit dont la dette est réglée), d'un vendeur ou de tous. */
    public VentesSoldeesResponse ventesSoldees(LocalDate debut, LocalDate fin, String vendeur) {
        return construireSoldees(debut, fin, chargerSoldees(debut, fin, vendeur, VENTES_ECRAN_MAX, "l'afficher"));
    }

    public List<Vente> chargerSoldees(LocalDate debut, LocalDate fin, String vendeur, int limite, String usage) {
        return chargerAvecStatuts(List.of(StatutVente.VALIDEE), debut, fin, vendeur, limite, usage).stream()
            .filter(HistoriqueVentesService::soldee)
            .toList();
    }

    /** Une vente validée est soldée si elle a été payée au comptant, ou si sa dette est réglée jusqu'au dernier franc. */
    static boolean soldee(Vente vente) {
        if (!vente.isVenteACredit()) {
            return true;
        }
        DetteClient dette = vente.getDetteClient();
        return dette != null && dette.getStatut() == StatutDette.REGLEE;
    }

    public VentesSoldeesResponse construireSoldees(LocalDate debut, LocalDate fin, List<Vente> ventes) {
        Map<Long, LocalDateTime> dernierReglement = dernierReglementParDette(ventes.stream()
            .filter(Vente::isVenteACredit)
            .map(vente -> vente.getDetteClient().getId())
            .toList());
        long nombreComptant = 0;
        long nombreCredits = 0;
        BigDecimal comptant = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        List<LigneVenteSoldee> lignes = new ArrayList<>();
        for (Vente vente : ventes) {
            if (vente.isVenteACredit()) {
                nombreCredits++;
                credits = credits.add(vente.getMontantTotal());
            } else {
                nombreComptant++;
                comptant = comptant.add(vente.getMontantTotal());
            }
            lignes.add(new LigneVenteSoldee(
                vente.getId(),
                vente.getCode(),
                vente.getDateVente(),
                vente.getClient() == null ? null : vente.getClient().getNom(),
                vente.getVendeur() == null ? null : vente.getVendeur().getUsername(),
                vente.isVenteACredit(),
                vente.isVenteACredit() ? null : vente.getModeReglement(),
                vente.getMontantTotal(),
                vente.isVenteACredit() ? dernierReglement.get(vente.getDetteClient().getId()) : vente.getDateVente()));
        }
        return new VentesSoldeesResponse(debut, fin, new ResumeVentesSoldees(lignes.size(), comptant.add(credits),
            nombreComptant, comptant, nombreCredits, credits), lignes);
    }

    /** Pour chaque dette, la date de son dernier règlement : celui qui l'a soldée. */
    private Map<Long, LocalDateTime> dernierReglementParDette(List<Long> dettes) {
        Map<Long, LocalDateTime> derniers = new HashMap<>();
        for (List<Long> paquet : paquets(dettes)) {
            for (ReglementClient reglement : reglementRepository.findByDetteIdInOrderByDateReglementAscIdAsc(paquet)) {
                derniers.put(reglement.getDette().getId(), reglement.getDateReglement());
            }
        }
        return derniers;
    }

    /** Une longue liste d'identifiants, découpée pour des requêtes de taille raisonnable. */
    static <T> List<List<T>> paquets(Collection<T> elements) {
        List<T> liste = new ArrayList<>(elements);
        List<List<T>> paquets = new ArrayList<>();
        for (int debut = 0; debut < liste.size(); debut += PAQUET) {
            paquets.add(liste.subList(debut, Math.min(liste.size(), debut + PAQUET)));
        }
        return paquets;
    }

    public HistoriqueVentesResponse construire(LocalDate debut, LocalDate fin, List<Vente> ventes) {
        List<LigneHistoriqueVente> lignes = ventes.stream().map(HistoriqueVentesService::versLigne).toList();
        long validees = 0;
        long annulees = 0;
        BigDecimal chiffre = BigDecimal.ZERO;
        BigDecimal remises = BigDecimal.ZERO;
        BigDecimal annule = BigDecimal.ZERO;
        for (LigneHistoriqueVente ligne : lignes) {
            if (ligne.statut() == StatutVente.VALIDEE) {
                validees++;
                chiffre = chiffre.add(ligne.montantTotal());
                remises = remises.add(ligne.remises());
            } else {
                annulees++;
                annule = annule.add(ligne.montantTotal());
            }
        }
        return new HistoriqueVentesResponse(debut, fin, new ResumeHistoriqueVentes(validees, chiffre, remises, annulees, annule), lignes);
    }

    /** Remises de toutes les lignes : total au prix catalogue − montant facturé (négociée par unité ou sur la ligne, et promotion), jamais négatif. */
    static BigDecimal remisesDe(Vente vente) {
        BigDecimal total = BigDecimal.ZERO;
        for (LigneVente ligne : vente.getLignesVente()) {
            BigDecimal catalogue = ligne.getPrixCatalogue() == null ? ligne.getPrixUnitaire() : ligne.getPrixCatalogue();
            total = total.add(Quantites.montant(catalogue, ligne.getQuantite()).subtract(ligne.getMontantLigne()).max(BigDecimal.ZERO));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static LigneHistoriqueVente versLigne(Vente vente) {
        return new LigneHistoriqueVente(
            vente.getId(),
            vente.getCode(),
            vente.getDateVente(),
            vente.getStatut(),
            vente.getClient() == null ? null : vente.getClient().getNom(),
            vente.getVendeur() == null ? null : vente.getVendeur().getUsername(),
            vente.isVenteACredit(),
            vente.getModeReglement(),
            vente.getLignesVente().stream().map(LigneVente::getQuantite).reduce(BigDecimal.ZERO, BigDecimal::add),
            remisesDe(vente),
            vente.getMontantTotal());
    }

    public static void verifierPeriode(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) {
            throw new RequeteInvalideException("Indiquez la date de début et la date de fin de la période.");
        }
        if (fin.isBefore(debut)) {
            throw new RequeteInvalideException("La date de fin doit être égale ou postérieure à la date de début.");
        }
        if (ChronoUnit.DAYS.between(debut, fin) >= JOURS_MAX) {
            throw new RequeteInvalideException("La période ne peut pas dépasser " + JOURS_MAX + " jours.");
        }
    }
}
