package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Vente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.ModeReglement;

@Repository
public interface VenteRepository extends JpaRepository<Vente, Long> {
    List<Vente> findAllByOrderByIdDesc();

    List<Vente> findByStatutInAndDateVenteGreaterThanEqualAndDateVenteLessThanOrderByDateVenteDesc(
        java.util.Collection<StatutVente> statuts, java.time.LocalDateTime debut, java.time.LocalDateTime fin);
    List<Vente> findByDateVenteBetween(LocalDateTime startDate, LocalDateTime endDate);
    List<Vente> findByStatutAndDateVenteGreaterThanEqualAndDateVenteLessThan(
        StatutVente statut, LocalDateTime debut, LocalDateTime fin);

    @Query("SELECT v.article, SUM(v.quantite) FROM Vente v GROUP BY v.article ORDER BY SUM(v.quantite) DESC")
    List<Object[]> findTopVendus(Pageable pageable);

    // NOUVEAU : Récupère l'historique avec les lignes de vente et les articles pour éviter les listes vides
    @Query("SELECT DISTINCT v FROM Vente v LEFT JOIN FETCH v.lignesVente lv LEFT JOIN FETCH lv.article ORDER BY v.id DESC")
    List<Vente> findAllWithLignesVente();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vente v where v.id = :id")
    java.util.Optional<Vente> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(v) from Vente v where v.statut = :statut and v.dateVente >= :debut and v.dateVente < :fin")
    long compterVentesEntre(@Param("statut") StatutVente statut,
                            @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("select coalesce(sum(v.montantTotal), 0) from Vente v " +
        "where v.statut = :statut and v.dateVente >= :debut and v.dateVente < :fin")
    java.math.BigDecimal chiffreAffairesEntre(@Param("statut") StatutVente statut,
                                @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("select count(v) from Vente v where v.sessionCaisse.id = :sessionId and v.statut = :statut")
    long compterVentesSession(@Param("sessionId") Long sessionId, @Param("statut") StatutVente statut);

    @Query("select coalesce(sum(v.montantTotal), 0) from Vente v " +
        "where v.sessionCaisse.id = :sessionId and v.statut = :statut")
    java.math.BigDecimal chiffreAffairesSession(@Param("sessionId") Long sessionId, @Param("statut") StatutVente statut);

    @Query("select coalesce(sum(coalesce(v.montantRecu, " +
        "case when v.venteACredit = true then 0 else v.montantTotal end)), 0) from Vente v " +
        "where v.sessionCaisse.id = :sessionId and v.statut = :statut")
    java.math.BigDecimal encaissementsSession(@Param("sessionId") Long sessionId, @Param("statut") StatutVente statut);

    @Query("select coalesce(sum(v.montantTotal), 0) from Vente v " +
        "where v.sessionCaisse.id = :sessionId and v.statut = :statut " +
        "and v.modeReglement = :mode and v.venteACredit = false")
    java.math.BigDecimal encaissementsEspecesSession(@Param("sessionId") Long sessionId,
                                       @Param("statut") StatutVente statut,
                                       @Param("mode") ModeReglement mode);

    @Query("select a.id, a.reference, a.nom, sum(l.quantite) from LigneVente l " +
        "join l.vente v join l.article a where v.statut = :statut " +
        "and v.dateVente >= :debut and v.dateVente < :fin " +
        "group by a.id, a.reference, a.nom order by sum(l.quantite) desc")
    List<Object[]> articlesLesPlusVendus(@Param("statut") StatutVente statut,
                                         @Param("debut") LocalDateTime debut,
                                         @Param("fin") LocalDateTime fin,
                                         org.springframework.data.domain.Pageable pageable);

    @Query("select a.id, a.reference, a.nom, sum(l.quantite), " +
        "sum((l.prixUnitaire - coalesce(l.remiseUnitaire, 0)) * l.quantite), " +
        "sum(a.prixAchat * l.quantite * coalesce(u.facteurConversion, 1)) " +
        "from LigneVente l join l.vente v join l.article a left join l.uniteVente u " +
        "where v.statut = :statut and v.dateVente >= :debut and v.dateVente < :fin " +
        "group by a.id, a.reference, a.nom order by sum((l.prixUnitaire - coalesce(l.remiseUnitaire, 0)) * l.quantite) desc")
    List<Object[]> margeParArticle(@Param("statut") StatutVente statut,
                                   @Param("debut") LocalDateTime debut,
                                   @Param("fin") LocalDateTime fin);

    @Query("select c.id, c.nom, " +
        "sum((l.prixUnitaire - coalesce(l.remiseUnitaire, 0)) * l.quantite), " +
    "sum(a.prixAchat * l.quantite * coalesce(u.facteurConversion, 1)) " +
    "from LigneVente l join l.vente v join l.article a left join l.uniteVente u left join a.categorie c " +
        "where v.statut = :statut and v.dateVente >= :debut and v.dateVente < :fin " +
        "group by c.id, c.nom")
    List<Object[]> margeParCategorie(@Param("statut") StatutVente statut,
                                     @Param("debut") LocalDateTime debut,
                                     @Param("fin") LocalDateTime fin);
}