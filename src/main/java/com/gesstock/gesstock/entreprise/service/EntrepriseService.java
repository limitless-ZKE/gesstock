package com.gesstock.gesstock.entreprise.service;

import com.gesstock.gesstock.entities.LogoEntreprise;
import com.gesstock.gesstock.entities.ParametresEntreprise;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.entreprise.dto.EntrepriseRequest;
import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.dto.LogoResponse;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.LogoEntrepriseRepository;
import com.gesstock.gesstock.repositories.ParametresEntrepriseRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Informations de l'entreprise et logo, lues au moment de l'impression par les écrans (tickets, reçus, rapports) et par les
 * exports serveur (PDF, Excel). Une seule entreprise : une seule ligne, créée par la migration V13.
 *
 * <p>Le logo est conservé une seule fois (PNG contrôlé, au plus {@value TraitementLogo#COTE_MAX_STOCKE} px) ; les formats
 * réduits (ticket, en-tête, miniature) en sont dérivés à la demande et gardés en mémoire. La clé de ce cache contient
 * l'empreinte du logo : un logo remplacé ne peut jamais être servi sous son ancienne version, même sur un autre serveur.
 */
@Service
@Transactional
public class EntrepriseService {

    /** Le logo servi : octets de l'image, type MIME et empreinte (utilisée comme ETag). */
    public record LogoServi(byte[] contenu, String typeMime, String empreinte) {}

    private final ParametresEntrepriseRepository parametresRepository;
    private final LogoEntrepriseRepository logoRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalActiviteService journalActiviteService;
    private final long logoOctetsMax;
    private final Map<String, byte[]> variantes = new ConcurrentHashMap<>();

    public EntrepriseService(
        ParametresEntrepriseRepository parametresRepository,
        LogoEntrepriseRepository logoRepository,
        UtilisateurRepository utilisateurRepository,
        JournalActiviteService journalActiviteService,
        @Value("${app.entreprise.logo-octets-max:2097152}") long logoOctetsMax
    ) {
        this.parametresRepository = parametresRepository;
        this.logoRepository = logoRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.journalActiviteService = journalActiviteService;
        this.logoOctetsMax = logoOctetsMax;
    }

    public EntrepriseResponse obtenir() {
        return reponse(lireOuCreer());
    }

    public EntrepriseResponse modifier(EntrepriseRequest demande, String username) {
        ParametresEntreprise parametres = lireOuCreer();
        parametres.setNom(demande.nom().trim());
        parametres.setAdresse(nettoyer(demande.adresse()));
        parametres.setTelephone(nettoyer(demande.telephone()));
        parametres.setEmail(nettoyer(demande.email()));
        parametres.setIdentifiantLegal(nettoyer(demande.identifiantLegal()));
        parametres.setSiteWeb(nettoyer(demande.siteWeb()));
        parametres.setPiedDePage(nettoyer(demande.piedDePage()));
        parametres.setLogoSurTickets(demande.logoSurTickets());
        horodater(parametres, username);
        parametresRepository.save(parametres);
        journalActiviteService.enregistrer("MODIFICATION", "ENTREPRISE", (long) ParametresEntreprise.ID_UNIQUE,
            "Informations de l'entreprise modifiées : " + parametres.getNom(), username);
        return reponse(parametres);
    }

    /** Remplace le logo (ou le pose). Le fichier est contrôlé et ré-encodé : voir {@link TraitementLogo}. */
    public EntrepriseResponse remplacerLogo(byte[] fichier, String username) {
        TraitementLogo.LogoTraite traite = TraitementLogo.traiter(fichier, logoOctetsMax);
        ParametresEntreprise parametres = lireOuCreer();

        LogoEntreprise logo = logoRepository.findById(ParametresEntreprise.ID_UNIQUE).orElseGet(LogoEntreprise::new);
        logo.setContenu(traite.png());
        logo.setTypeMime("image/png");
        logo.setLargeur(traite.largeur());
        logo.setHauteur(traite.hauteur());
        logo.setEmpreinte(traite.empreinte());
        logo.setMisAJourLe(LocalDateTime.now());
        logoRepository.save(logo);

        parametres.setLogoEmpreinte(traite.empreinte());
        parametres.setLogoLargeur(traite.largeur());
        parametres.setLogoHauteur(traite.hauteur());
        horodater(parametres, username);
        parametresRepository.save(parametres);
        variantes.clear();
        journalActiviteService.enregistrer("MODIFICATION", "ENTREPRISE", (long) ParametresEntreprise.ID_UNIQUE,
            "Logo de l'entreprise remplacé (" + traite.largeur() + " × " + traite.hauteur() + " px)", username);
        return reponse(parametres);
    }

    public EntrepriseResponse supprimerLogo(String username) {
        ParametresEntreprise parametres = lireOuCreer();
        if (!parametres.aUnLogo()) {
            return reponse(parametres);
        }
        logoRepository.deleteById(ParametresEntreprise.ID_UNIQUE);
        parametres.setLogoEmpreinte(null);
        parametres.setLogoLargeur(null);
        parametres.setLogoHauteur(null);
        horodater(parametres, username);
        parametresRepository.save(parametres);
        variantes.clear();
        journalActiviteService.enregistrer("SUPPRESSION", "ENTREPRISE", (long) ParametresEntreprise.ID_UNIQUE,
            "Logo de l'entreprise retiré", username);
        return reponse(parametres);
    }

    /**
     * Le logo au format demandé, ou vide s'il n'y en a pas. Le fichier n'est lu en base qu'au premier appel d'un format : les
     * suivants sortent du cache, identifiés par l'empreinte.
     */
    public Optional<LogoServi> logo(TailleLogo taille) {
        ParametresEntreprise parametres = lireOuCreer();
        if (!parametres.aUnLogo()) {
            return Optional.empty();
        }
        String empreinte = parametres.getLogoEmpreinte();
        byte[] contenu = variantes.get(empreinte + ":" + taille);
        if (contenu == null) {
            Optional<LogoEntreprise> stocke = logoRepository.findById(ParametresEntreprise.ID_UNIQUE);
            if (stocke.isEmpty() || !empreinte.equals(stocke.get().getEmpreinte())) {
                return Optional.empty();   // parametres et logo remplacés en même temps par un autre serveur : on réessaiera
            }
            contenu = TraitementLogo.redimensionner(stocke.get().getContenu(), taille);
            if (variantes.size() > 16) {
                variantes.clear();         // borne mémoire : 4 formats par logo, les anciens logos n'ont plus lieu d'être
            }
            variantes.put(empreinte + ":" + taille, contenu);
        }
        return Optional.of(new LogoServi(contenu, "image/png", empreinte));
    }

    // ------------------------------------------------------------------ outils

    /** La ligne existe grâce à la migration ; elle est recréée si elle manque (base de test, base restaurée à moitié). */
    private ParametresEntreprise lireOuCreer() {
        return parametresRepository.findById(ParametresEntreprise.ID_UNIQUE).orElseGet(() -> {
            ParametresEntreprise parametres = new ParametresEntreprise();
            parametres.setNom("Mon entreprise");
            parametres.setPiedDePage("Merci de votre visite !");
            return parametresRepository.saveAndFlush(parametres);
        });
    }

    private void horodater(ParametresEntreprise parametres, String username) {
        parametres.setMisAJourLe(LocalDateTime.now());
        parametres.setMisAJourPar(utilisateurRepository.findByUsername(username).orElse(null));
    }

    private static String nettoyer(String texte) {
        if (texte == null) {
            return null;
        }
        String propre = texte.trim();
        return propre.isEmpty() ? null : propre;
    }

    private static EntrepriseResponse reponse(ParametresEntreprise parametres) {
        Utilisateur auteur = parametres.getMisAJourPar();
        return new EntrepriseResponse(
            parametres.getNom(),
            parametres.getAdresse(),
            parametres.getTelephone(),
            parametres.getEmail(),
            parametres.getIdentifiantLegal(),
            parametres.getSiteWeb(),
            parametres.getPiedDePage(),
            parametres.isLogoSurTickets(),
            parametres.aUnLogo()
                ? new LogoResponse(parametres.getLogoEmpreinte(), parametres.getLogoLargeur(), parametres.getLogoHauteur())
                : null,
            parametres.getMisAJourLe(),
            auteur == null ? null : auteur.getUsername());
    }
}
