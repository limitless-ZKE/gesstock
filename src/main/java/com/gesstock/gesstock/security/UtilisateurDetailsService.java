package com.gesstock.gesstock.security;

import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Charge un compte depuis la base : ses droits viennent uniquement de ses rôles ({@code utilisateur_roles}). */
@Service
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurDetailsService(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + username));

        Set<SimpleGrantedAuthority> authorities = utilisateur.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority(toAuthority(role.getName())))
            .collect(Collectors.toSet());

        return User.withUsername(utilisateur.getUsername())
            .password(utilisateur.getPassword())
            .authorities(authorities)
            // un compte sans aucun rôle ne peut rien faire : on refuse la connexion (401) plutôt qu'une erreur 500
            .disabled(!utilisateur.isActif() || authorities.isEmpty())
            .build();
    }

    private String toAuthority(String role) {
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}
