package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.ReglementFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReglementFournisseurRepository extends JpaRepository<ReglementFournisseur, Long> {

    List<ReglementFournisseur> findByFournisseurIdOrderByDateReglementAscIdAsc(Long fournisseurId);
}
