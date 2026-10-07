package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Promotion;
import com.gesstock.gesstock.entities.TypePromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    @Query("""
        select distinct p from Promotion p
        left join p.articles a
        left join p.categories c
        where p.active = true and p.debut <= :instant and p.fin >= :instant
          and (a.id = :articleId or c.id = :categorieId)
        """)
    List<Promotion> findPromotionsApplicables(
        @Param("articleId") Long articleId,
        @Param("categorieId") Long categorieId,
        @Param("instant") LocalDateTime instant);

    List<Promotion> findByActiveTrueAndTypeAndDebutLessThanEqualAndFinGreaterThanEqualOrderByValeurDesc(
        TypePromotion type, LocalDateTime debut, LocalDateTime fin);
}
