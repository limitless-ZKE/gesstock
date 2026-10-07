package com.gesstock.gesstock.referentiels.service;

import com.gesstock.gesstock.entities.Role;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.repositories.RoleRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.referentiels.dto.CreateUtilisateurRequest;
import com.gesstock.gesstock.referentiels.dto.UpdateUtilisateurRequest;
import com.gesstock.gesstock.referentiels.dto.UtilisateurResponse;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.referentiels.mapper.UtilisateurMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.Locale;

@Service
@Transactional
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UtilisateurService(
        UtilisateurRepository utilisateurRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UtilisateurResponse> lister() {
        return utilisateurRepository.findAll().stream().map(UtilisateurMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UtilisateurResponse obtenir(Long id) {
        return UtilisateurMapper.toResponse(trouver(id));
    }

    public UtilisateurResponse creer(CreateUtilisateurRequest request) {
        verifierNomDisponible(request.username(), null);
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setUsername(request.username().trim());
        utilisateur.setPassword(passwordEncoder.encode(request.password()));
        utilisateur.setActif(request.actif() == null || request.actif());
        attribuerRoles(utilisateur, request.roleIds());
        return UtilisateurMapper.toResponse(utilisateurRepository.save(utilisateur));
    }

    public UtilisateurResponse modifier(Long id, UpdateUtilisateurRequest request, String utilisateurConnecte) {
        Utilisateur utilisateur = trouver(id);
        verifierNomDisponible(request.username(), id);
        verifierModificationPropreCompte(utilisateur, request.actif(), utilisateurConnecte);
        if (utilisateur.getUsername().equalsIgnoreCase(utilisateurConnecte)) {
            utilisateur.getRoles().stream()
                .filter(role -> role.getName().trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", "").equals("ADMIN"))
                .findFirst()
                .ifPresent(adminRole -> {
                    if (!request.roleIds().contains(adminRole.getId())) {
                        throw new ConflitRessourceException("Vous ne pouvez pas retirer votre propre rôle ADMIN");
                    }
                });
        }

        utilisateur.setUsername(request.username().trim());
        if (request.password() != null) {
            utilisateur.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.actif() != null) {
            utilisateur.setActif(request.actif());
        }
        attribuerRoles(utilisateur, request.roleIds());
        return UtilisateurMapper.toResponse(utilisateurRepository.save(utilisateur));
    }

    public void supprimer(Long id, String utilisateurConnecte) {
        Utilisateur utilisateur = trouver(id);
        if (utilisateur.getUsername().equalsIgnoreCase(utilisateurConnecte)) {
            throw new ConflitRessourceException("Vous ne pouvez pas supprimer votre propre compte");
        }
        utilisateurRepository.delete(utilisateur);
    }

    private void verifierModificationPropreCompte(Utilisateur utilisateur, Boolean actif, String utilisateurConnecte) {
        if (utilisateur.getUsername().equalsIgnoreCase(utilisateurConnecte) && Boolean.FALSE.equals(actif)) {
            throw new ConflitRessourceException("Vous ne pouvez pas désactiver votre propre compte");
        }
    }

    private void attribuerRoles(Utilisateur utilisateur, Set<Long> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            Set<Long> trouves = new HashSet<>();
            roles.forEach(role -> trouves.add(role.getId()));
            Set<Long> inconnus = new HashSet<>(roleIds);
            inconnus.removeAll(trouves);
            throw new RessourceIntrouvableException("Rôles introuvables: " + inconnus);
        }
        roles.sort(Comparator.comparing(Role::getName));
        utilisateur.setRoles(new HashSet<>(roles));
        utilisateur.setRole(nomLegacy(roles.get(0).getName()));
    }

    private void verifierNomDisponible(String username, Long idIgnore) {
        utilisateurRepository.findByUsernameIgnoreCase(username.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(idIgnore)) {
                throw new ConflitRessourceException("Un utilisateur portant ce nom existe déjà");
            }
        });
    }

    private Utilisateur trouver(Long id) {
        return utilisateurRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable: " + id));
    }

    private String nomLegacy(String roleName) {
        String normalized = roleName.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }
}
