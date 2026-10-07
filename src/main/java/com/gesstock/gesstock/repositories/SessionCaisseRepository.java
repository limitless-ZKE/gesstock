package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SessionCaisseRepository extends JpaRepository<SessionCaisse, Long> {
    List<SessionCaisse> findByJourneeOrderByOuverteLeDesc(LocalDate journee);
    List<SessionCaisse> findByJourneeBetweenOrderByJourneeDescOuverteLeDesc(LocalDate debut, LocalDate fin);
    Optional<SessionCaisse> findByJourneeAndStatut(LocalDate journee, StatutSessionCaisse statut);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionCaisse s where s.journee = :journee and s.statut = :statut")
    Optional<SessionCaisse> findByJourneeAndStatutForUpdate(
        @Param("journee") LocalDate journee,
        @Param("statut") StatutSessionCaisse statut);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionCaisse s where s.id = :id")
    Optional<SessionCaisse> findByIdForUpdate(@Param("id") Long id);
    Optional<SessionCaisse> findFirstByStatutOrderByOuverteLeDesc(StatutSessionCaisse statut);
    boolean existsByJournee(LocalDate journee);
}
