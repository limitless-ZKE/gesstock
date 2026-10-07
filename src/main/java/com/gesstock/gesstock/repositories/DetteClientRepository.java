package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.DetteClient;
import com.gesstock.gesstock.entities.StatutDette;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface DetteClientRepository extends JpaRepository<DetteClient, Long> {
    List<DetteClient> findByClientIdOrderByDateCreationDesc(Long clientId);
    List<DetteClient> findByStatutOrderByDateCreationAsc(StatutDette statut);

    @Query("select d from DetteClient d join fetch d.client left join fetch d.vente "
        + "where d.statut <> com.gesstock.gesstock.entities.StatutDette.ANNULEE "
        + "order by d.dateCreation desc, d.id desc")
    List<DetteClient> findToutesAvecClient();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DetteClient d where d.id = :id")
    Optional<DetteClient> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(d.montantInitial - d.montantRegle), 0) from DetteClient d " +
        "where d.client.id = :clientId and d.statut <> com.gesstock.gesstock.entities.StatutDette.ANNULEE")
    java.math.BigDecimal calculerEncoursClient(@Param("clientId") Long clientId);

    @Query("select coalesce(sum(d.montantInitial - d.montantRegle), 0) from DetteClient d " +
        "where d.statut <> com.gesstock.gesstock.entities.StatutDette.ANNULEE " +
        "and d.statut <> com.gesstock.gesstock.entities.StatutDette.REGLEE")
    java.math.BigDecimal calculerEncoursGlobal();
}
