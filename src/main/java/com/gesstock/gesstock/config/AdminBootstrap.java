package com.gesstock.gesstock.config;

import com.gesstock.gesstock.entities.Role;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.repositories.RoleRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Crée le premier administrateur en production, où le {@link DataSeeder} est désactivé.
 * N'agit que si aucun utilisateur n'existe et si les identifiants sont fournis par l'environnement
 * (APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD) : aucun mot de passe par défaut n'est jamais créé.
 */
@Component
@Profile("!dev & !test")
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int LONGUEUR_MINIMALE_MOT_DE_PASSE = 12;

    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminBootstrap(
        UtilisateurRepository utilisateurRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.bootstrap.admin.username:}") String username,
        @Value("${app.bootstrap.admin.password:}") String password
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (utilisateurRepository.count() > 0) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            LOGGER.warn("Aucun utilisateur n'existe et APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD ne sont pas définis : "
                + "personne ne pourra se connecter.");
            return;
        }
        if (password.length() < LONGUEUR_MINIMALE_MOT_DE_PASSE) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD doit contenir au moins "
                + LONGUEUR_MINIMALE_MOT_DE_PASSE + " caractères");
        }

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role role = new Role();
            role.setName("ADMIN");
            return roleRepository.save(role);
        });

        Utilisateur admin = new Utilisateur();
        admin.setUsername(username.trim());
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(adminRole.getName());
        admin.setActif(true);
        admin.setRoles(Set.of(adminRole));
        utilisateurRepository.save(admin);
        LOGGER.info("Administrateur initial '{}' créé.", admin.getUsername());
    }
}
