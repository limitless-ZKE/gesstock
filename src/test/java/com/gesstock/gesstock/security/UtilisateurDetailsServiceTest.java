package com.gesstock.gesstock.security;

import com.gesstock.gesstock.entities.Role;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UtilisateurDetailsServiceTest {

    private final UtilisateurRepository repository = mock(UtilisateurRepository.class);
    private final UtilisateurDetailsService service = new UtilisateurDetailsService(repository);

    private static Role role(String nom) {
        Role role = new Role();
        role.setName(nom);
        return role;
    }

    private UserDetails charger(boolean actif, String... roles) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setUsername("u");
        utilisateur.setPassword("hash");
        utilisateur.setActif(actif);
        for (String nom : roles) {
            utilisateur.getRoles().add(role(nom));
        }
        when(repository.findByUsername("u")).thenReturn(Optional.of(utilisateur));
        return service.loadUserByUsername("u");
    }

    private static Set<String> droits(UserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void lesDroitsViennentDesRolesAttribuesEtSontNormalises() {
        UserDetails details = charger(true, "manager", "ROLE_GESTIONNAIRE");

        assertThat(droits(details)).containsExactlyInAnyOrder("ROLE_MANAGER", "ROLE_GESTIONNAIRE");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void unCompteSansAucunRoleOuDesactiveNePeutPasSeConnecter() {
        assertThat(charger(true).isEnabled()).as("aucun rôle attribué").isFalse();
        assertThat(charger(false, "CAISSIER").isEnabled()).as("compte désactivé").isFalse();
    }

    @Test
    void unCompteInconnuEstRefuse() {
        when(repository.findByUsername("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("x")).isInstanceOf(UsernameNotFoundException.class);
    }
}
