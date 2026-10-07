package com.gesstock.gesstock.services;

import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + username));

        Set<SimpleGrantedAuthority> authorities = utilisateur.getRoles().isEmpty()
            ? Set.of(new SimpleGrantedAuthority(toAuthority(utilisateur.getRole())))
            : utilisateur.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(toAuthority(role.getName())))
                .collect(Collectors.toSet());

        return User.withUsername(utilisateur.getUsername())
            .password(utilisateur.getPassword())
            .authorities(authorities)
            .disabled(!utilisateur.isActif())
            .build();
    }

    private String toAuthority(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalStateException("Aucun rôle n'est attribué à l'utilisateur");
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }
}