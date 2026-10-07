package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.UniteVente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UniteVenteRepository extends JpaRepository<UniteVente, Long> {
    List<UniteVente> findByArticleIdAndActiveTrue(Long articleId);
    List<UniteVente> findByArticleIdOrderByIdAsc(Long articleId);
    boolean existsByUniteMesureId(Long uniteMesureId);
}
