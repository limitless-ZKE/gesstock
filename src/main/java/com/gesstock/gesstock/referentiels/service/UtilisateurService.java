package com.gesstock.gesstock.referentiels.service;

import com.gesstock.gesstock.entities.Role;
import com.gesstock.gesstock.entities.Utilisateur;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
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

import java.util.ArrayList;
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
    private final JournalActiviteService journalService;

    public UtilisateurService(
        UtilisateurRepository utilisateurRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        JournalActiviteService journalService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.journalService = journalService;
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
        Utilisateur enregistre = utilisateurRepository.save(utilisateur);
        journalService.enregistrer("CREATION", "UTILISATEUR", enregistre.getId(), "Compte « " + enregistre.getUsername() + " » créé : "
            + roles(enregistre) + (enregistre.isActif() ? "" : ", désactivé"), null);
        return UtilisateurMapper.toResponse(enregistre);
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

        String nom = utilisateur.getUsername();
        String rolesAvant = roles(utilisateur);
        boolean etaitActif = utilisateur.isActif();
        utilisateur.setUsername(request.username().trim());
        if (request.password() != null) {
            utilisateur.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.actif() != null) {
            utilisateur.setActif(request.actif());
        }
        attribuerRoles(utilisateur, request.roleIds());
        Utilisateur enregistre = utilisateurRepository.save(utilisateur);
        List<String> changements = new ArrayList<>();
        if (!nom.equals(enregistre.getUsername())) changements.add("renommé « " + enregistre.getUsername() + " »");
        if (!rolesAvant.equals(roles(enregistre))) changements.add("rôles " + rolesAvant + " → " + roles(enregistre));
        if (etaitActif != enregistre.isActif()) changements.add(enregistre.isActif() ? "réactivé" : "désactivé");
        if (request.password() != null) changements.add("mot de passe changé");
        if (!changements.isEmpty()) {
            journalService.enregistrer("MODIFICATION", "UTILISATEUR", id, "Compte « " + nom + " » : " + String.join(", ", changements),
                utilisateurConnecte);
        }
        return UtilisateurMapper.toResponse(enregistre);
    }

    /**
     * Un compte qui a laissé des traces (ventes, journal des opérations, connexions comprises) ne se supprime pas : la base le refuse
     * et l'historique reste lisible. Il se désactive.
     */
    public void supprimer(Long id, String utilisateurConnecte) {
        Utilisateur utilisateur = trouver(id);
        if (utilisateur.getUsername().equalsIgnoreCase(utilisateurConnecte)) {
            throw new ConflitRessourceException("Vous ne pouvez pas supprimer votre propre compte");
        }
        utilisateurRepository.delete(utilisateur);
        journalService.enregistrer("SUPPRESSION", "UTILISATEUR", id, "Compte « " + utilisateur.getUsername() + " » supprimé", utilisateurConnecte);
    }

    /** « ADMIN, CAISSIER » : les rôles d'un compte, sans le préfixe technique, dans l'ordre alphabétique. */
    private static String roles(Utilisateur utilisateur) {
        List<String> noms = utilisateur.getRoles().stream()
            .map(role -> role.getName().trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", ""))
            .sorted()
            .toList();
        return noms.isEmpty() ? "aucun rôle" : String.join(", ", noms);
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
}
