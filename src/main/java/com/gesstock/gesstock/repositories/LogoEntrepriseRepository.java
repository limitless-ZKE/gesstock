package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.LogoEntreprise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogoEntrepriseRepository extends JpaRepository<LogoEntreprise, Integer> {
}
