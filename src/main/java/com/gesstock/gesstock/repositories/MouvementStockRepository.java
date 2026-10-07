package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.MouvementStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long> {
    List<MouvementStock> findByArticleIdAndDateMouvementBetweenOrderByDateMouvementDesc(
        Long articleId, LocalDateTime debut, LocalDateTime fin);
}
