package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.LigneApprovisionnement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LigneApprovisionnementRepository extends JpaRepository<LigneApprovisionnement, Long> {
    List<LigneApprovisionnement> findByApprovisionnementId(Long approvisionnementId);
}