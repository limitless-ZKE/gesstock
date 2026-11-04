package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Approvisionnement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ApprovisionnementRepository extends JpaRepository<Approvisionnement, Long> {
    Optional<Approvisionnement> findByNumeroIgnoreCase(String numero);
    List<Approvisionnement> findAllByOrderByDateApprovisionnementDescIdDesc();

    /** Les bons réceptionnés pendant [debut, fin[, avec leurs lignes : ce sont les achats de la période. */
    @org.springframework.data.jpa.repository.Query("select distinct a from Approvisionnement a join fetch a.lignes left join fetch a.fournisseur "
        + "where a.statut = com.gesstock.gesstock.entities.StatutApprovisionnement.RECU "
        + "and a.recuLe >= :debut and a.recuLe < :fin order by a.recuLe asc, a.id asc")
    List<Approvisionnement> recusEntre(@org.springframework.data.repository.query.Param("debut") java.time.LocalDateTime debut,
                                       @org.springframework.data.repository.query.Param("fin") java.time.LocalDateTime fin);
}