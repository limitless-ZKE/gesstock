package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.ReglementClient;
import com.gesstock.gesstock.entities.ModeReglement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

public interface ReglementClientRepository extends JpaRepository<ReglementClient, Long> {
    List<ReglementClient> findByClientIdOrderByDateReglementDesc(Long clientId);
    List<ReglementClient> findByClientIdAndDateReglementBetweenOrderByDateReglementDesc(
        Long clientId, LocalDateTime debut, LocalDateTime fin);
    List<ReglementClient> findByDetteIdOrderByDateReglementDesc(Long detteId);
    List<ReglementClient> findBySessionCaisseIdOrderByDateReglementDesc(Long sessionId);

    @Query("select coalesce(sum(r.montant), 0) from ReglementClient r " +
        "where r.sessionCaisse.id = :sessionId and r.modeReglement = :mode")
    BigDecimal totalParSessionEtMode(@Param("sessionId") Long sessionId, @Param("mode") ModeReglement mode);
}
