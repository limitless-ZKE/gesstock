package com.gesstock.gesstock.clients.service;

import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.DetteAvecClientResponse;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.common.export.ExportSupport;
import com.gesstock.gesstock.common.export.ListeImprimable;
import com.gesstock.gesstock.common.export.ListeImprimable.Colonne;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * La liste des clients à imprimer ou à retravailler, filtrée par dette : tous, avec une dette en cours, sans dette, ou dont la dette
 * la plus ancienne date d'au moins N jours (les clients à relancer). Comme pour l'écran, une dette compte tant qu'elle n'est pas
 * réglée jusqu'au dernier franc.
 */
@Service
@Transactional(readOnly = true)
public class ClientExportService {

    public enum FiltreClients {
        TOUS("Tous les clients", "tous"),
        AVEC_DETTE("Clients avec une dette en cours", "avec-dette"),
        SANS_DETTE("Clients sans dette", "sans-dette"),
        DETTE_ANCIENNE("Clients dont la dette a plus de {jours} jours", "dette-ancienne");

        private final String libelle;
        private final String nomDeFichier;

        FiltreClients(String libelle, String nomDeFichier) {
            this.libelle = libelle;
            this.nomDeFichier = nomDeFichier;
        }

        public String libelle(int jours) { return libelle.replace("{jours}", Integer.toString(jours)); }

        public String nomDeFichier() { return nomDeFichier; }

        /** Lit le paramètre de l'adresse ; vide = tous. */
        public static FiltreClients de(String texte) {
            if (texte == null || texte.isBlank()) return TOUS;
            String propre = texte.trim().toLowerCase(Locale.ROOT);
            for (FiltreClients filtre : values()) {
                if (filtre.name().toLowerCase(Locale.ROOT).equals(propre) || filtre.nomDeFichier.equals(propre)) return filtre;
            }
            throw new RequeteInvalideException("Filtre de clients inconnu : « " + texte + " » (tous, avec-dette, sans-dette ou dette-ancienne)");
        }
    }

    /** Un client retenu : ce qu'il doit, depuis quand, et combien de dettes ouvertes. */
    public record Ligne(ClientResponse client, BigDecimal dette, LocalDate detteLaPlusAncienne, int nombreDeDettes) {
        public long anciennete(LocalDate aujourdhui) {
            return detteLaPlusAncienne == null ? 0 : ChronoUnit.DAYS.between(detteLaPlusAncienne, aujourdhui);
        }
    }

    public static final int JOURS_PAR_DEFAUT = 30;
    private static final int JOURS_MAX = 3650;

    private final ClientCatalogueService clientService;
    private final GestionDetteClientService detteService;
    private final EntrepriseService entrepriseService;

    public ClientExportService(ClientCatalogueService clientService, GestionDetteClientService detteService,
                               EntrepriseService entrepriseService) {
        this.clientService = clientService;
        this.detteService = detteService;
        this.entrepriseService = entrepriseService;
    }

    /** Le nombre de jours demandé, 30 par défaut ; de 1 à 3 650. */
    public static int jours(Integer demande) {
        int jours = demande == null ? JOURS_PAR_DEFAUT : demande;
        if (jours < 1 || jours > JOURS_MAX) {
            throw new RequeteInvalideException("Le nombre de jours doit être compris entre 1 et " + JOURS_MAX);
        }
        return jours;
    }

    /**
     * Les clients retenus par le filtre et la recherche. Par nom ; pour les filtres de dette, du plus gros débiteur au plus petit.
     */
    public List<Ligne> retenir(FiltreClients filtre, int jours, String recherche, LocalDate aujourdhui) {
        Map<Long, BigDecimal> dettes = new HashMap<>();
        Map<Long, LocalDate> plusAnciennes = new HashMap<>();
        Map<Long, Integer> nombres = new HashMap<>();
        for (DetteAvecClientResponse dette : detteService.listerToutesLesDettes()) {
            if (dette.solde() == null || dette.solde().signum() <= 0) continue;
            dettes.merge(dette.clientId(), dette.solde(), BigDecimal::add);
            plusAnciennes.merge(dette.clientId(), dette.dateCreation(), (a, b) -> a.isBefore(b) ? a : b);
            nombres.merge(dette.clientId(), 1, Integer::sum);
        }
        String requete = recherche == null ? "" : recherche.trim().toLowerCase(Locale.ROOT);
        List<Ligne> lignes = clientService.lister().stream()
            .map(client -> new Ligne(client, dettes.getOrDefault(client.id(), BigDecimal.ZERO), plusAnciennes.get(client.id()),
                nombres.getOrDefault(client.id(), 0)))
            .filter(ligne -> switch (filtre) {
                case TOUS -> true;
                case AVEC_DETTE -> ligne.dette().signum() > 0;
                case SANS_DETTE -> ligne.dette().signum() <= 0;
                case DETTE_ANCIENNE -> ligne.dette().signum() > 0 && ligne.anciennete(aujourdhui) >= jours;
            })
            .filter(ligne -> requete.isEmpty() || correspond(ligne.client(), requete))
            .collect(Collectors.toList());
        Comparator<Ligne> parNom = Comparator.comparing(ligne -> ligne.client().nom(), String.CASE_INSENSITIVE_ORDER);
        if (filtre == FiltreClients.AVEC_DETTE || filtre == FiltreClients.DETTE_ANCIENNE) {
            lignes.sort(Comparator.comparing(Ligne::dette).reversed().thenComparing(parNom));
        } else {
            lignes.sort(parNom);
        }
        return lignes;
    }

    private static boolean correspond(ClientResponse client, String requete) {
        return Stream.of(client.nom(), client.telephone(), client.email(), client.adresse())
            .anyMatch(valeur -> valeur != null && valeur.toLowerCase(Locale.ROOT).contains(requete));
    }

    private ListeImprimable liste(FiltreClients filtre, int jours, String recherche) {
        LocalDate aujourdhui = LocalDate.now();
        String sousTitre = filtre.libelle(jours)
            + (recherche == null || recherche.isBlank() ? "" : " · recherche « " + recherche.trim() + " »")
            + " · édité le " + ExportSupport.jourHeure(LocalDateTime.now());
        ListeImprimable liste = new ListeImprimable("Liste des clients", sousTitre, "Aucun client ne correspond à ce choix.",
            List.of(new Colonne("Client", 2.4f, false), new Colonne("Téléphone", 1.4f, false), new Colonne("Adresse", 2.2f, false),
                new Colonne("Achats", 0.8f, true), new Colonne("Dette en cours", 1.5f, true), new Colonne("Dette la plus ancienne", 1.9f, false)));
        BigDecimal total = BigDecimal.ZERO;
        List<Ligne> lignes = retenir(filtre, jours, recherche, aujourdhui);
        for (Ligne ligne : lignes) {
            ClientResponse client = ligne.client();
            String depuis = ligne.detteLaPlusAncienne() == null ? ""
                : ExportSupport.jour(ligne.detteLaPlusAncienne()) + " (" + ligne.anciennete(aujourdhui) + " j)";
            liste.ligne(client.nom(), client.telephone(), client.adresse(), client.nombreAchats(),
                ligne.dette().signum() > 0 ? ligne.dette() : null, depuis);
            total = total.add(ligne.dette());
        }
        liste.total("Total", lignes.size() + (lignes.size() > 1 ? " clients" : " client"), "", "", total, "");
        return liste;
    }

    public byte[] exporterPdf(FiltreClients filtre, int jours, String recherche) throws IOException {
        return liste(filtre, jours, recherche).pdf(entrepriseService);
    }

    public byte[] exporterExcel(FiltreClients filtre, int jours, String recherche) throws IOException {
        return liste(filtre, jours, recherche).excel(entrepriseService);
    }
}
