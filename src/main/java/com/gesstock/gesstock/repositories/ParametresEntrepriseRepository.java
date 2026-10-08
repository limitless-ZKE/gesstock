package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.ParametresEntreprise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParametresEntrepriseRepository extends JpaRepository<ParametresEntreprise, Integer> {
}
