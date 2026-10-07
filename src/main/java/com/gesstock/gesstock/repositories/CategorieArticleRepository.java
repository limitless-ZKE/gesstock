package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.CategorieArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategorieArticleRepository extends JpaRepository<CategorieArticle, Long> {
    Optional<CategorieArticle> findByNomIgnoreCase(String nom);
    List<CategorieArticle> findByActiveTrueOrderByNomAsc();
    List<CategorieArticle> findByParentIdOrderByNomAsc(Long parentId);
    boolean existsByParentId(Long parentId);
    boolean existsByArticles_Id(Long articleId);
}
