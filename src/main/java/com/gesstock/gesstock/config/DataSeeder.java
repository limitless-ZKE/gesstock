package com.gesstock.gesstock.config;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.entities.Client;
import com.gesstock.gesstock.entities.Fournisseur;
import com.gesstock.gesstock.entities.Role;
import com.gesstock.gesstock.entities.UniteMesure;
import com.gesstock.gesstock.entities.UniteVente;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import com.gesstock.gesstock.repositories.ClientRepository;
import com.gesstock.gesstock.repositories.FournisseurRepository;
import com.gesstock.gesstock.repositories.RoleRepository;
import com.gesstock.gesstock.repositories.UniteMesureRepository;
import com.gesstock.gesstock.repositories.UniteVenteRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Component
@Profile({"dev", "test"})
public class DataSeeder implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepository roleRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CategorieArticleRepository categorieRepository;
    private final UniteMesureRepository uniteMesureRepository;
    private final UniteVenteRepository uniteVenteRepository;
    private final ArticleRepository articleRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
        RoleRepository roleRepository,
        UtilisateurRepository utilisateurRepository,
        CategorieArticleRepository categorieRepository,
        UniteMesureRepository uniteMesureRepository,
        UniteVenteRepository uniteVenteRepository,
        ArticleRepository articleRepository,
        FournisseurRepository fournisseurRepository,
        ClientRepository clientRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.roleRepository = roleRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.categorieRepository = categorieRepository;
        this.uniteMesureRepository = uniteMesureRepository;
        this.uniteVenteRepository = uniteVenteRepository;
        this.articleRepository = articleRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!jeuDeDemoVide()) {
            LOGGER.info("Initialisation de démonstration ignorée : des données existent déjà.");
            return;
        }

        Role adminRole = creerRole("ADMIN");
        Role caissierRole = creerRole("CAISSIER");
        Role gestionnaireRole = creerRole("GESTIONNAIRE");

        creerUtilisateur("admin", "admin123", adminRole);
        creerUtilisateur("caissier", "caissier123", caissierRole);
        creerUtilisateur("gestionnaire", "gestionnaire123", gestionnaireRole);

        CategorieArticle electronique = creerCategorie("Électronique", "Appareils et accessoires électroniques");
        CategorieArticle alimentation = creerCategorie("Alimentation", "Produits alimentaires et boissons");
        CategorieArticle mobilier = creerCategorie("Mobilier", "Meubles et équipements");

        UniteMesure piece = creerUnite("PCE", "Pièce");
        UniteMesure kilogramme = creerUnite("KG", "Kilogramme");
        UniteMesure litre = creerUnite("L", "Litre");

        Article telephone = creerArticle("ELEC-001", "Téléphone Android 128 Go", "189000", "245000", 8,
            12, electronique, piece);
        Article ordinateur = creerArticle("ELEC-002", "Ordinateur portable 15 pouces", "420000", "535000", 4,
            5, electronique, piece);
        Article riz = creerArticle("ALIM-001", "Riz local", "900", "1300", 20,
            180, alimentation, kilogramme);
        Article huile = creerArticle("ALIM-002", "Huile végétale", "1400", "1950", 12,
            75, alimentation, litre);
        Article chaise = creerArticle("MOB-001", "Chaise de bureau", "28000", "39000", 5,
            18, mobilier, piece);
        Article bureau = creerArticle("MOB-002", "Bureau de travail", "65000", "89000", 3,
            7, mobilier, piece);

        creerUniteVente(telephone, piece, "UNITE", "1", "245000");
        creerUniteVente(ordinateur, piece, "UNITE", "1", "535000");
        creerUniteVente(riz, kilogramme, "KG", "1", "1300");
        creerUniteVente(huile, litre, "LITRE", "1", "1950");
        creerUniteVente(chaise, piece, "UNITE", "1", "39000");
        creerUniteVente(bureau, piece, "UNITE", "1", "89000");
        creerUniteVente(riz, kilogramme, "SAC-25KG", "25", "31000");
        creerUniteVente(huile, litre, "BIDON-5L", "5", "9500");

        fournisseurRepository.saveAll(List.of(
            new Fournisseur("Tech Distribution", "+221 33 800 10 10",
                "ventes@tech-distribution.example", "Dakar, Sénégal"),
            new Fournisseur("Agro Marché", "+221 33 800 20 20",
                "contact@agromarche.example", "Thiès, Sénégal"),
            new Fournisseur("Mobilier Pro", "+221 33 800 30 30",
                "commandes@mobilierpro.example", "Dakar, Sénégal")
        ));
        clientRepository.saveAll(List.of(
            new Client("Aminata Diop", "+221 77 111 22 33", "Dakar, Sénégal"),
            new Client("Mamadou Fall", "+221 76 444 55 66", "Thiès, Sénégal"),
            new Client("Entreprise Baobab", "+221 33 700 80 90", "Dakar, Sénégal")
        ));

        LOGGER.info("Jeu de démonstration GesStock 3.0 chargé : 3 utilisateurs, 3 catégories, 6 articles.");
    }

    private boolean jeuDeDemoVide() {
        return roleRepository.count() == 0
            && utilisateurRepository.count() == 0
            && categorieRepository.count() == 0
            && uniteMesureRepository.count() == 0
            && uniteVenteRepository.count() == 0
            && articleRepository.count() == 0
            && fournisseurRepository.count() == 0
            && clientRepository.count() == 0;
    }

    private Role creerRole(String name) {
        Role role = new Role();
        role.setName(name);
        return roleRepository.save(role);
    }

    private void creerUtilisateur(String username, String rawPassword, Role role) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setUsername(username);
        utilisateur.setPassword(passwordEncoder.encode(rawPassword));
        utilisateur.setRole(role.getName());
        utilisateur.setActif(true);
        utilisateur.setRoles(Set.of(role));
        utilisateurRepository.save(utilisateur);
    }

    private CategorieArticle creerCategorie(String nom, String description) {
        CategorieArticle categorie = new CategorieArticle();
        categorie.setNom(nom);
        categorie.setDescription(description);
        categorie.setActive(true);
        return categorieRepository.save(categorie);
    }

    private UniteMesure creerUnite(String code, String libelle) {
        UniteMesure unite = new UniteMesure();
        unite.setCode(code);
        unite.setLibelle(libelle);
        return uniteMesureRepository.save(unite);
    }

    private Article creerArticle(
        String reference,
        String nom,
        String prixAchat,
        String prixVente,
        int seuilAlerte,
        int quantite,
        CategorieArticle categorie,
        UniteMesure unite
    ) {
        Article article = new Article();
        article.setReference(reference);
        article.setNom(nom);
        article.setPrixAchat(new BigDecimal(prixAchat));
        article.setPrix(new BigDecimal(prixVente));
        article.setSeuilAlerte(seuilAlerte);
        article.setQuantite(quantite);
        article.setCategorie(categorie);
        article.setUniteMesure(unite);
        return articleRepository.save(article);
    }

    private void creerUniteVente(
        Article article,
        UniteMesure unite,
        String code,
        String facteurConversion,
        String prixVente
    ) {
        UniteVente uniteVente = new UniteVente();
        uniteVente.setArticle(article);
        uniteVente.setUniteMesure(unite);
        uniteVente.setCode(code);
        uniteVente.setFacteurConversion(new BigDecimal(facteurConversion));
        uniteVente.setPrixVente(new BigDecimal(prixVente));
        uniteVente.setActive(true);
        uniteVenteRepository.save(uniteVente);
    }
}
