package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByUsername(String username);
    Optional<Utilisateur> findByUsernameIgnoreCase(String username);
    boolean existsByRoles_Id(Long roleId);
}