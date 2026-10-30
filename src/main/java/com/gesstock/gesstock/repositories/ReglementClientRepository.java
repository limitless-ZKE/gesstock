package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.ReglementClient;
import com.gesstock.gesstock.entities.ModeReglement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * Les règlements de dettes d'une période (historique), encaissés par un compte ou par tous ({@code recuPar} nul), les plus
     * récents d'abord, avec leur client, leur dette et la vente d'origine.
     */
    @EntityGraph(attributePaths = {"client", "dette", "dette.vente", "recuPar"})
    @Query("select r from ReglementClient r left join r.recuPar compte where r.dateReglement >= :debut and r.dateReglement < :fin "
        + "and (:recuPar is null or lower(compte.username) = lower(:recuPar)) order by r.dateReglement desc, r.id desc")
    List<ReglementClient> reglementsDeLaPeriode(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin,
        @Param("recuPar") String recuPar);

    @Query("select count(r) from ReglementClient r left join r.recuPar compte where r.dateReglement >= :debut and r.dateReglement < :fin "
        + "and (:recuPar is null or lower(compte.username) = lower(:recuPar))")
    long compterReglementsDeLaPeriode(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin,
        @Param("recuPar") String recuPar);

    /** Tous les règlements de ces dettes, dans l'ordre où ils ont été reçus : pour savoir ce qui restait dû après chacun. */
    List<ReglementClient> findByDetteIdInOrderByDateReglementAscIdAsc(Collection<Long> dettes);
}
