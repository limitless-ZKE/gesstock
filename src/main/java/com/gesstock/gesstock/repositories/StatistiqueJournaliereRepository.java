package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.StatistiqueJournaliere;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StatistiqueJournaliereRepository extends JpaRepository<StatistiqueJournaliere, Long> {
    Optional<StatistiqueJournaliere> findByJournee(LocalDate journee);
    List<StatistiqueJournaliere> findByJourneeBetweenOrderByJourneeAsc(LocalDate debut, LocalDate fin);
}
