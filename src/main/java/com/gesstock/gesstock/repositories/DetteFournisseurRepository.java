package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.DetteFournisseur;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DetteFournisseurRepository extends JpaRepository<DetteFournisseur, Long> {

    @Query("select d from DetteFournisseur d join fetch d.approvisionnement "
        + "where d.fournisseur.id = :fournisseurId order by d.dateCreation desc, d.id desc")
    List<DetteFournisseur> findDuFournisseur(@Param("fournisseurId") Long fournisseurId);

    /**
     * Dettes encore à régler, les plus anciennes d'abord : c'est l'ordre d'imputation des avoirs. Lecture verrouillée donc à jour
     * (sous REPEATABLE READ, une lecture simple pourrait voir l'état d'avant une imputation validée entre-temps).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DetteFournisseur d where d.fournisseur.id = :fournisseurId "
        + "and d.statut in (com.gesstock.gesstock.entities.StatutDette.EN_COURS, "
        + "com.gesstock.gesstock.entities.StatutDette.PARTIELLEMENT_REGLEE) order by d.dateCreation asc, d.id asc")
    List<DetteFournisseur> findOuvertes(@Param("fournisseurId") Long fournisseurId);

    /** Lu sans charger la dette : le verrou du compte doit être pris AVANT de lire la dette à modifier. */
    @Query("select d.fournisseur.id from DetteFournisseur d where d.id = :id")
    Optional<Long> findFournisseurId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DetteFournisseur d where d.id = :id")
    Optional<DetteFournisseur> findByIdForUpdate(@Param("id") Long id);

    /** Solde dû par fournisseur : lignes {fournisseurId, solde}. */
    @Query("select d.fournisseur.id, sum(d.montantInitial - d.montantRegle) from DetteFournisseur d "
        + "where d.statut in (com.gesstock.gesstock.entities.StatutDette.EN_COURS, "
        + "com.gesstock.gesstock.entities.StatutDette.PARTIELLEMENT_REGLEE) group by d.fournisseur.id")
    List<Object[]> soldesParFournisseur();
}
