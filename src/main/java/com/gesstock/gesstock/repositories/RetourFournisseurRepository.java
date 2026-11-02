package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.RetourFournisseur;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RetourFournisseurRepository extends JpaRepository<RetourFournisseur, Long> {

    @EntityGraph(attributePaths = {"lignes", "lignes.article", "fournisseur", "approvisionnement", "creePar", "validePar"})
    List<RetourFournisseur> findAllByOrderByIdDesc();

    /** Numéro d'ordre dans la journée : RF-AAAAMMJJ-001, 002… (un retour annulé garde son numéro). */
    long countByNumeroStartingWith(String prefixe);

    /** Un retour (même annulé : il garde ses lignes) porte-t-il sur ce bon ? Alors le bon ne se corrige plus. */
    boolean existsByApprovisionnementId(Long approvisionnementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RetourFournisseur r where r.id = :id")
    Optional<RetourFournisseur> findByIdForUpdate(@Param("id") Long id);
}
