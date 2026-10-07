package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.JournalActivite;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
