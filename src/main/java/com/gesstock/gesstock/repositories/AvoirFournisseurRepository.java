package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.AvoirFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvoirFournisseurRepository extends JpaRepository<AvoirFournisseur, Long> {

    List<AvoirFournisseur> findByFournisseurIdOrderByDateCreationDescIdDesc(Long fournisseurId);

    boolean existsByFournisseurId(Long fournisseurId);

    /** Avoirs dont il reste du crédit, les plus anciens d'abord. */
    @Query("select a from AvoirFournisseur a where a.fournisseur.id = :fournisseurId and a.montant > a.montantUtilise "
        + "order by a.dateCreation asc, a.id asc")
    List<AvoirFournisseur> findDisponibles(@Param("fournisseurId") Long fournisseurId);

    /** Crédit disponible par fournisseur : lignes {fournisseurId, crédit}. */
    @Query("select a.fournisseur.id, sum(a.montant - a.montantUtilise) from AvoirFournisseur a "
        + "where a.montant > a.montantUtilise group by a.fournisseur.id")
    List<Object[]> creditsParFournisseur();
}
