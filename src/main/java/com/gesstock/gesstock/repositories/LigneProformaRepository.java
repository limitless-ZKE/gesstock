package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.LigneProforma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneProformaRepository extends JpaRepository<LigneProforma, Long> {
    List<LigneProforma> findByProformaId(Long proformaId);
}
