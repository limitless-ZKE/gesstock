package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.LigneInventaire;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LigneInventaireRepository extends JpaRepository<LigneInventaire, Long> {

    Optional<LigneInventaire> findByInventaireIdAndArticleId(Long inventaireId, Long articleId);

    @EntityGraph(attributePaths = {"article", "article.categorie", "article.uniteMesure"})
    @Query(value = "select l from LigneInventaire l where l.inventaire.id = :id order by l.article.nom",
        countQuery = "select count(l) from LigneInventaire l where l.inventaire.id = :id")
    Page<LigneInventaire> lignes(@Param("id") Long inventaireId, Pageable pageable);

    @EntityGraph(attributePaths = {"article", "article.categorie", "article.uniteMesure"})
    @Query(value = "select l from LigneInventaire l where l.inventaire.id = :id and l.stockCompte is not null "
        + "and l.stockCompte <> l.stockTheorique order by l.article.nom",
        countQuery = "select count(l) from LigneInventaire l where l.inventaire.id = :id and l.stockCompte is not null "
            + "and l.stockCompte <> l.stockTheorique")
    Page<LigneInventaire> ecarts(@Param("id") Long inventaireId, Pageable pageable);

    /** Toutes les lignes avec leur article, triées par nom d'article (l'export de la feuille de comptage, la validation). */
    @EntityGraph(attributePaths = {"article", "article.categorie", "article.uniteMesure"})
    @Query("select l from LigneInventaire l where l.inventaire.id = :id order by l.article.nom")
    List<LigneInventaire> toutes(@Param("id") Long inventaireId);

    /** Totaux d'un inventaire : lignes, lignes comptées, lignes en écart, valeur algébrique des écarts au coût moyen. */
    @Query("select count(l), "
        + "coalesce(sum(case when l.stockCompte is not null then 1 else 0 end), 0), "
        + "coalesce(sum(case when l.stockCompte is not null and l.stockCompte <> l.stockTheorique then 1 else 0 end), 0), "
        + "coalesce(sum(case when l.stockCompte is not null then (l.stockCompte - l.stockTheorique) * l.coutUnitaire else 0 end), 0) "
        + "from LigneInventaire l where l.inventaire.id = :id")
    List<Object[]> totaux(@Param("id") Long inventaireId);
}
