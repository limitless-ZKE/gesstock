package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Proforma;
import com.gesstock.gesstock.entities.StatutProforma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProformaRepository extends JpaRepository<Proforma, Long> {
    Optional<Proforma> findByNumero(String numero);

    Optional<Proforma> findByNumeroIgnoreCase(String numero);
    List<Proforma> findByClientIdOrderByDateEmissionDesc(Long clientId);
    List<Proforma> findByStatutOrderByDateEmissionDesc(StatutProforma statut);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proforma p where p.id = :id")
    Optional<Proforma> findByIdForUpdate(@Param("id") Long id);
}
