package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Approvisionnement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ApprovisionnementRepository extends JpaRepository<Approvisionnement, Long> {
    Optional<Approvisionnement> findByNumeroIgnoreCase(String numero);
    List<Approvisionnement> findAllByOrderByDateApprovisionnementDescIdDesc();
}