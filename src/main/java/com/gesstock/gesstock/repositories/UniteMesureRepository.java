package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.UniteMesure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UniteMesureRepository extends JpaRepository<UniteMesure, Long> {
    Optional<UniteMesure> findByCodeIgnoreCase(String code);
}
