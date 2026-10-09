package com.gesstock.gesstock.ventes.service;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.LigneVente;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.ventes.dto.HistoriqueVentesResponse;
import com.gesstock.gesstock.ventes.dto.LigneHistoriqueVente;
import com.gesstock.gesstock.ventes.dto.ResumeHistoriqueVentes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Historique des ventes sur une période, pour l'écran et pour les documents imprimés (Excel, PDF) : les trois lisent les mêmes
 * chiffres, calculés ici une seule fois. Les ventes en brouillon n'en font pas partie : elles n'ont encore rien changé.
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

    private final VenteRepository venteRepository;

    public HistoriqueVentesService(VenteRepository venteRepository) {
        this.venteRepository = venteRepository;
    }

    public HistoriqueVentesResponse historique(LocalDate debut, LocalDate fin) {
        return construire(debut, fin, charger(debut, fin, VENTES_ECRAN_MAX, "l'afficher"));
    }

    /** Les ventes de la période, les plus récentes d'abord (avec leurs lignes, leur client et leur vendeur). */
    public List<Vente> charger(LocalDate debut, LocalDate fin, int limite, String usage) {
        verifierPeriode(debut, fin);
        var du = debut.atStartOfDay();
        var au = fin.plusDays(1).atStartOfDay();
        long total = STATUTS.stream().mapToLong(statut -> venteRepository.compterVentesEntre(statut, du, au)).sum();
        if (total > limite) {
            throw new RequeteInvalideException("Cette période compte " + total + " ventes : c'est trop pour " + usage
                + " (" + limite + " au plus). Choisissez une période plus courte.");
        }
        return venteRepository.findByStatutInAndDateVenteGreaterThanEqualAndDateVenteLessThanOrderByDateVenteDesc(STATUTS, du, au);
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

    /** Remise négociée (prix catalogue − prix appliqué, jamais négative) + remise de promotion, sur toutes les lignes. */
    static BigDecimal remisesDe(Vente vente) {
        BigDecimal total = BigDecimal.ZERO;
        for (LigneVente ligne : vente.getLignesVente()) {
            BigDecimal catalogue = ligne.getPrixCatalogue() == null ? ligne.getPrixUnitaire() : ligne.getPrixCatalogue();
            BigDecimal negociee = catalogue.subtract(ligne.getPrixUnitaire()).max(BigDecimal.ZERO);
            BigDecimal promotion = ligne.getRemiseUnitaire() == null ? BigDecimal.ZERO : ligne.getRemiseUnitaire();
            total = total.add(negociee.add(promotion).multiply(BigDecimal.valueOf(ligne.getQuantite())));
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
            vente.getLignesVente().stream().mapToInt(LigneVente::getQuantite).sum(),
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
