package com.gesstock.gesstock.referentiels.service;

import com.gesstock.gesstock.entities.UniteMesure;
import com.gesstock.gesstock.repositories.UniteMesureRepository;
import com.gesstock.gesstock.referentiels.dto.UniteMesureRequest;
import com.gesstock.gesstock.referentiels.dto.UniteMesureResponse;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.referentiels.mapper.UniteMesureMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UniteMesureService {

    private final UniteMesureRepository uniteMesureRepository;

    public UniteMesureService(UniteMesureRepository uniteMesureRepository) {
        this.uniteMesureRepository = uniteMesureRepository;
    }

    @Transactional(readOnly = true)
    public List<UniteMesureResponse> lister() {
        return uniteMesureRepository.findAll().stream().map(UniteMesureMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UniteMesureResponse obtenir(Long id) {
        return UniteMesureMapper.toResponse(trouver(id));
    }

    public UniteMesureResponse creer(UniteMesureRequest request) {
        verifierCodeDisponible(request.code(), null);
        UniteMesure uniteMesure = new UniteMesure();
        UniteMesureMapper.updateEntity(request, uniteMesure);
        return UniteMesureMapper.toResponse(uniteMesureRepository.save(uniteMesure));
    }

    public UniteMesureResponse modifier(Long id, UniteMesureRequest request) {
        UniteMesure uniteMesure = trouver(id);
        verifierCodeDisponible(request.code(), id);
        UniteMesureMapper.updateEntity(request, uniteMesure);
        return UniteMesureMapper.toResponse(uniteMesureRepository.save(uniteMesure));
    }

    public void supprimer(Long id) {
        uniteMesureRepository.delete(trouver(id));
    }

    private UniteMesure trouver(Long id) {
        return uniteMesureRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Unité de mesure introuvable: " + id));
    }

    private void verifierCodeDisponible(String code, Long idIgnore) {
        uniteMesureRepository.findByCodeIgnoreCase(code.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(idIgnore)) {
                throw new ConflitRessourceException("Une unité de mesure portant ce code existe déjà");
            }
        });
    }
}
