package com.gesstock.gesstock.ventes.politique;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Qui peut accorder quelle remise sans l'accord d'un supérieur.
 *
 * <ul>
 *   <li>chaque rôle a un plafond de remise négociée (en % du prix catalogue) ; un utilisateur cumulant des rôles
 *       bénéficie du plus favorable ;</li>
 *   <li>on ne vend jamais sous le coût (CMP) augmenté de la marge plancher sans accord, même sous le plafond ;</li>
 *   <li>l'accord doit venir d'une AUTRE personne ayant un rôle d'approbation (jamais la même, jamais un caissier).</li>
 * </ul>
 * Les promotions ne comptent pas dans le plafond : elles sont décidées d'avance par la direction.
 */
public final class PolitiqueRemise {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final Set<String> ROLES_APPROBATION = Set.of("MANAGER", "ADMIN");

    private final Map<String, BigDecimal> plafondParRole;
    private final BigDecimal margePlancherPourcent;

    public PolitiqueRemise(Map<String, BigDecimal> plafondParRole, BigDecimal margePlancherPourcent) {
        this.plafondParRole = Map.copyOf(plafondParRole);
        this.margePlancherPourcent = margePlancherPourcent;
    }

    public record Decision(boolean approbationRequise, String motif) {
        static Decision autorisee() {
            return new Decision(false, null);
        }

        static Decision approbation(String motif) {
            return new Decision(true, motif);
        }
    }

    /**
     * @param prixNegocie        prix convenu avec le client (égal au prix catalogue s'il n'y a pas de négociation)
     * @param prixNetApresPromo  prix réellement facturé à l'unité, après la promotion éventuelle
     * @param coutUnitaire       CMP de l'article (nul si inconnu : seul le plafond s'applique)
     */
    public Decision evaluer(
        Collection<String> roles,
        BigDecimal prixCatalogue,
        BigDecimal prixNegocie,
        BigDecimal prixNetApresPromo,
        BigDecimal coutUnitaire
    ) {
        BigDecimal plafond = plafondDe(roles);
        // Comparaison EXACTE par produit en croix : arrondir le pourcentage ferait passer un dépassement d'un centime.
        boolean depasse = prixCatalogue.subtract(prixNegocie).multiply(CENT).compareTo(prixCatalogue.multiply(plafond)) > 0;
        if (depasse) {
            BigDecimal remise = remisePourcent(prixCatalogue, prixNegocie);
            return Decision.approbation("Remise de " + remise.stripTrailingZeros().toPlainString()
                + " % au-delà du plafond autorisé de " + plafond.stripTrailingZeros().toPlainString() + " %");
        }
        if (coutUnitaire != null) {
            BigDecimal planchier = coutUnitaire.multiply(BigDecimal.ONE.add(margePlancherPourcent.divide(CENT, 6, RoundingMode.HALF_UP)));
            if (prixNetApresPromo.compareTo(planchier) < 0) {
                return Decision.approbation("Prix inférieur au coût de revient majoré de la marge minimale");
            }
        }
        return Decision.autorisee();
    }

    /** Le plus fort plafond parmi les rôles de l'utilisateur ; 0 si aucun de ses rôles n'a de plafond. */
    BigDecimal plafondDe(Collection<String> roles) {
        return roles.stream()
            .map(PolitiqueRemise::normaliser)
            .map(role -> plafondParRole.getOrDefault(role, BigDecimal.ZERO))
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    }

    /** Un accord n'est valable que s'il vient d'une autre personne, avec un rôle d'approbation. */
    public boolean accordValable(String demandeur, String approbateur, Collection<String> rolesApprobateur) {
        return !demandeur.equalsIgnoreCase(approbateur)
            && rolesApprobateur.stream().map(PolitiqueRemise::normaliser).anyMatch(ROLES_APPROBATION::contains);
    }

    static BigDecimal remisePourcent(BigDecimal prixCatalogue, BigDecimal prixNegocie) {
        if (prixCatalogue.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return prixCatalogue.subtract(prixNegocie).multiply(CENT).divide(prixCatalogue, 4, RoundingMode.HALF_UP);
    }

    private static String normaliser(String role) {
        String valeur = role.trim().toUpperCase(Locale.ROOT);
        return valeur.startsWith("ROLE_") ? valeur.substring(5) : valeur;
    }
}
