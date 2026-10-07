package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.MouvementCaisse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface MouvementCaisseRepository extends JpaRepository<MouvementCaisse, Long> {
    List<MouvementCaisse> findBySessionCaisseIdOrderByDateMouvementDesc(Long sessionId);

    @Query("select coalesce(sum(m.montant), 0) from MouvementCaisse m where m.sessionCaisse.id = :sessionId")
    BigDecimal totalSorties(@Param("sessionId") Long sessionId);
}
