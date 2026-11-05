package com.gesstock.gesstock.fournisseurs.service;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.export.ExportSupport;
import com.gesstock.gesstock.common.export.ListeImprimable;
import com.gesstock.gesstock.common.export.ListeImprimable.Colonne;
import com.gesstock.gesstock.entities.Fournisseur;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.fournisseurs.dto.CompteFournisseurResume;
import com.gesstock.gesstock.repositories.FournisseurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** La liste des fournisseurs à imprimer ou à retravailler, avec ce que nous leur devons, filtrée comme l'écran. */
@Service
@Transactional(readOnly = true)
public class FournisseurExportService {

    /** Quels fournisseurs : tous, ceux à qui nous devons de l'argent, ou ceux que nous ne devons plus rien. */
    public enum FiltreFournisseurs {
        TOUS("Tous les fournisseurs", "tous"),
        AVEC_DETTE("Fournisseurs à qui nous devons de l'argent", "avec-dette"),
        SANS_DETTE("Fournisseurs sans dette", "sans-dette");

        private final String libelle;
        private final String nomDeFichier;

        FiltreFournisseurs(String libelle, String nomDeFichier) {
            this.libelle = libelle;
            this.nomDeFichier = nomDeFichier;
        }

        public String libelle() { return libelle; }

        public String nomDeFichier() { return nomDeFichier; }

        boolean retient(BigDecimal soldeDu) {
            return switch (this) {
                case TOUS -> true;
                case AVEC_DETTE -> soldeDu.signum() > 0;
                case SANS_DETTE -> soldeDu.signum() <= 0;
            };
        }

        /** Lit le paramètre de l'adresse ; vide = tous. */
        public static FiltreFournisseurs de(String texte) {
            if (texte == null || texte.isBlank()) return TOUS;
            String propre = texte.trim().toLowerCase(Locale.ROOT);
            for (FiltreFournisseurs filtre : values()) {
                if (filtre.name().toLowerCase(Locale.ROOT).equals(propre) || filtre.nomDeFichier.equals(propre)) return filtre;
            }
            throw new RequeteInvalideException("Filtre de fournisseurs inconnu : « " + texte + " » (tous, avec-dette ou sans-dette)");
        }
    }

    /** Un fournisseur retenu, avec ce que nous lui devons. */
    public record Ligne(Fournisseur fournisseur, BigDecimal soldeDu) {}

    private final FournisseurRepository fournisseurRepository;
    private final CompteFournisseurService compteService;
    private final EntrepriseService entrepriseService;

    public FournisseurExportService(FournisseurRepository fournisseurRepository, CompteFournisseurService compteService,
                                    EntrepriseService entrepriseService) {
        this.fournisseurRepository = fournisseurRepository;
        this.compteService = compteService;
        this.entrepriseService = entrepriseService;
    }

    /** Les fournisseurs retenus par le filtre et la recherche, par nom ; ceux qui nous sont le plus dus d'abord pour « avec dette ». */
    public List<Ligne> retenir(FiltreFournisseurs filtre, String recherche) {
        Map<Long, BigDecimal> soldes = compteService.lister().stream()
            .collect(Collectors.toMap(CompteFournisseurResume::fournisseurId, CompteFournisseurResume::soldeDu, (a, b) -> a));
        String requete = recherche == null ? "" : recherche.trim().toLowerCase(Locale.ROOT);
        var lignes = fournisseurRepository.findAllByOrderByNomAsc().stream()
            .map(fournisseur -> new Ligne(fournisseur, soldes.getOrDefault(fournisseur.getId(), BigDecimal.ZERO)))
            .filter(ligne -> filtre.retient(ligne.soldeDu()))
            .filter(ligne -> requete.isEmpty() || correspond(ligne.fournisseur(), requete))
            .collect(Collectors.toList());
        if (filtre == FiltreFournisseurs.AVEC_DETTE) {
            lignes.sort(java.util.Comparator.comparing(Ligne::soldeDu).reversed());
        }
        return lignes;
    }

    private static boolean correspond(Fournisseur fournisseur, String requete) {
        return java.util.stream.Stream.of(fournisseur.getNom(), fournisseur.getTelephone(), fournisseur.getEmail(), fournisseur.getAdresse())
            .anyMatch(valeur -> valeur != null && valeur.toLowerCase(Locale.ROOT).contains(requete));
    }

    private ListeImprimable liste(FiltreFournisseurs filtre, String recherche) {
        String sousTitre = filtre.libelle()
            + (recherche == null || recherche.isBlank() ? "" : " · recherche « " + recherche.trim() + " »")
            + " · édité le " + ExportSupport.jourHeure(LocalDateTime.now());
        ListeImprimable liste = new ListeImprimable("Liste des fournisseurs", sousTitre, "Aucun fournisseur ne correspond à ce choix.",
            List.of(new Colonne("Fournisseur", 2.4f, false), new Colonne("Téléphone", 1.4f, false), new Colonne("E-mail", 2f, false),
                new Colonne("Adresse", 2.4f, false), new Colonne("Nous leur devons", 1.5f, true)));
        BigDecimal total = BigDecimal.ZERO;
        List<Ligne> lignes = retenir(filtre, recherche);
        for (Ligne ligne : lignes) {
            Fournisseur fournisseur = ligne.fournisseur();
            liste.ligne(fournisseur.getNom(), fournisseur.getTelephone(), fournisseur.getEmail(), fournisseur.getAdresse(), ligne.soldeDu());
            total = total.add(ligne.soldeDu());
        }
        liste.total("Total", lignes.size() + (lignes.size() > 1 ? " fournisseurs" : " fournisseur"), "", "", total);
        return liste;
    }

    public byte[] exporterPdf(FiltreFournisseurs filtre, String recherche) throws IOException {
        return liste(filtre, recherche).pdf(entrepriseService);
    }

    public byte[] exporterExcel(FiltreFournisseurs filtre, String recherche) throws IOException {
        return liste(filtre, recherche).excel(entrepriseService);
    }
}
