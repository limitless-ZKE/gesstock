package com.gesstock.gesstock.referentiels.service;

import com.gesstock.gesstock.entities.Role;
import com.gesstock.gesstock.repositories.RoleRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.referentiels.dto.RoleRequest;
import com.gesstock.gesstock.referentiels.dto.RoleResponse;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.referentiels.mapper.RoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final UtilisateurRepository utilisateurRepository;

    public RoleService(RoleRepository roleRepository, UtilisateurRepository utilisateurRepository) {
        this.roleRepository = roleRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> lister() {
        return roleRepository.findAll().stream().map(RoleMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse obtenir(Long id) {
        return RoleMapper.toResponse(trouver(id));
    }

    public RoleResponse creer(RoleRequest request) {
        String name = normaliser(request.name());
        verifierNomDisponible(name, null);
        Role role = new Role();
        role.setName(name);
        return RoleMapper.toResponse(roleRepository.save(role));
    }

    public RoleResponse modifier(Long id, RoleRequest request) {
        Role role = trouver(id);
        String ancienNom = role.getName();
        String nouveauNom = normaliser(request.name());
        verifierNomDisponible(nouveauNom, id);
        role.setName(nouveauNom);
        role.getUtilisateurs().forEach(utilisateur -> {
            if (utilisateur.getRole() != null && normaliser(utilisateur.getRole()).equals(normaliser(ancienNom))) {
                utilisateur.setRole(nomLegacy(nouveauNom));
            }
        });
        return RoleMapper.toResponse(roleRepository.save(role));
    }

    public void supprimer(Long id) {
        Role role = trouver(id);
        if (utilisateurRepository.existsByRoles_Id(id)) {
            throw new ConflitRessourceException("Le rôle est attribué à au moins un utilisateur et ne peut pas être supprimé");
        }
        roleRepository.delete(role);
    }

    private Role trouver(Long id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Rôle introuvable: " + id));
    }

    private void verifierNomDisponible(String name, Long idIgnore) {
        roleRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getId().equals(idIgnore)) {
                throw new ConflitRessourceException("Un rôle portant ce nom existe déjà");
            }
        });
    }

    private String normaliser(String name) {
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }

    private String nomLegacy(String name) {
        return normaliser(name);
    }
}
