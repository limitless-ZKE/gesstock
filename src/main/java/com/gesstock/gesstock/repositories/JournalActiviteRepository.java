package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.JournalActivite;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JournalActiviteRepository extends JpaRepository<JournalActivite, Long> {
    List<JournalActivite> findByDateActiviteBetweenOrderByDateActiviteAsc(
        LocalDateTime debut, LocalDateTime fin);
    List<JournalActivite> findByDateActiviteBetweenAndUtilisateurIdOrderByDateActiviteAsc(
        LocalDateTime debut, LocalDateTime fin, Long utilisateurId);
    List<JournalActivite> findByUtilisateurIdAndDateActiviteBetweenOrderByDateActiviteDesc(
        Long utilisateurId, LocalDateTime debut, LocalDateTime fin);
    List<JournalActivite> findByEntiteAndEntiteIdOrderByDateActiviteDesc(String entite, Long entiteId);

    /**
     * Le journal des opérations d'une période, d'un compte ou de tous ({@code utilisateur} nul), les plus récentes d'abord. Jointure
     * externe : une opération sans compte (connexion refusée d'un identifiant inconnu) reste dans « tous les utilisateurs ».
     */
    @EntityGraph(attributePaths = "utilisateur")
    @Query("select j from JournalActivite j left join j.utilisateur compte where j.dateActivite >= :debut and j.dateActivite < :fin "
        + "and (:utilisateur is null or lower(compte.username) = lower(:utilisateur)) order by j.dateActivite desc, j.id desc")
    List<JournalActivite> operationsDeLaPeriode(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin,
        @Param("utilisateur") String utilisateur);

    @Query("select count(j) from JournalActivite j left join j.utilisateur compte where j.dateActivite >= :debut and j.dateActivite < :fin "
        + "and (:utilisateur is null or lower(compte.username) = lower(:utilisateur))")
    long compterOperationsDeLaPeriode(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin,
        @Param("utilisateur") String utilisateur);
}
