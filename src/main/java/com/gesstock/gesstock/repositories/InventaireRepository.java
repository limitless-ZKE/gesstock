package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Inventaire;
import com.gesstock.gesstock.entities.StatutInventaire;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventaireRepository extends JpaRepository<Inventaire, Long> {

    List<Inventaire> findAllByOrderByIdDesc();

    Optional<Inventaire> findFirstByStatutIn(Collection<StatutInventaire> statuts);

    /** Numéro d'ordre dans la journée : INV-AAAAMMJJ-001, 002… (un inventaire annulé garde son numéro). */
    long countByCodeStartingWith(String prefixe);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventaire i where i.id = :id")
    Optional<Inventaire> findByIdForUpdate(@Param("id") Long id);
}
