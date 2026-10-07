package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.LigneVente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneVenteRepository extends JpaRepository<LigneVente, Long> {
    List<LigneVente> findByVenteId(Long venteId);
}
