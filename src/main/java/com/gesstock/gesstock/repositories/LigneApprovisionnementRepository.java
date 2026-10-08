package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.LigneApprovisionnement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LigneApprovisionnementRepository extends JpaRepository<LigneApprovisionnement, Long> {
    List<LigneApprovisionnement> findByApprovisionnementId(Long approvisionnementId);

    /**
     * Compte {@code quantite} unités de plus comme rendues au fournisseur, SEULEMENT si la ligne n'est pas dépassée. Un UPDATE
     * lit la ligne à jour et la verrouille : deux retours simultanés ne peuvent pas se partager la même unité. Retourne 0 si la
     * quantité n'est plus disponible. L'entité chargée n'est pas mise à jour : la rafraîchir si on en relit le compteur.
     */
    @Modifying(flushAutomatically = true)
    @Query("update LigneApprovisionnement l set l.quantiteRetournee = l.quantiteRetournee + :quantite "
        + "where l.id = :id and l.quantiteRetournee + :quantite <= l.quantite")
    int retournerQuantite(@Param("id") Long id, @Param("quantite") int quantite);
}
