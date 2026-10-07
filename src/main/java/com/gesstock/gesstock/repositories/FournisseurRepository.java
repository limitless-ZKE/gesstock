package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface FournisseurRepository extends JpaRepository<Fournisseur, Long> {
    Optional<Fournisseur> findByNomIgnoreCase(String nom);
    List<Fournisseur> findAllByOrderByNomAsc();
}