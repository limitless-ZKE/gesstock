package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByQuantiteLessThanEqual(int seuil);

    List<Article> findByNomContainingIgnoreCase(String nom);

    @Query("select a from Article a where a.quantite <= a.seuilAlerte")
    List<Article> findArticlesSousSeuilAlerte();

    Optional<Article> findByReferenceIgnoreCase(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Article a where a.id = :id")
    Optional<Article> findByIdForUpdate(@Param("id") Long id);

    /** Valeur du stock : quantité × coût moyen pondéré (et non le dernier prix d'achat, qui la fausserait à chaque lot). */
    @Query("select coalesce(sum(a.cmp * a.quantite), 0) from Article a")
    java.math.BigDecimal valeurTotaleStock();

    @Query("select count(a) from Article a where a.quantite <= a.seuilAlerte")
    long compterArticlesSousSeuilAlerte();
}