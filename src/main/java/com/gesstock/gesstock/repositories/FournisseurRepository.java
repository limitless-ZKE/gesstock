package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Fournisseur;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface FournisseurRepository extends JpaRepository<Fournisseur, Long> {
    Optional<Fournisseur> findByNomIgnoreCase(String nom);
    List<Fournisseur> findAllByOrderByNomAsc();

    /** Verrou de compte : toute opération qui touche aux dettes ou aux avoirs d'un fournisseur commence par ici. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fournisseur f where f.id = :id")
    Optional<Fournisseur> findByIdForUpdate(@Param("id") Long id);
}
