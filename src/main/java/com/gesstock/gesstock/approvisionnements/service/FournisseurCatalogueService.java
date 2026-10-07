package com.gesstock.gesstock.approvisionnements.service;

import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.approvisionnements.mapper.ApprovisionnementMapper;
import com.gesstock.gesstock.entities.Fournisseur;
import com.gesstock.gesstock.repositories.FournisseurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class FournisseurCatalogueService {

    private final FournisseurRepository fournisseurRepository;

    public FournisseurCatalogueService(FournisseurRepository fournisseurRepository) {
        this.fournisseurRepository = fournisseurRepository;
    }

    @Transactional(readOnly = true)
    public List<FournisseurResponse> lister() {
        return fournisseurRepository.findAllByOrderByNomAsc().stream()
            .map(ApprovisionnementMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FournisseurResponse obtenir(Long id) {
        return ApprovisionnementMapper.toResponse(trouver(id));
    }

    public FournisseurResponse creer(FournisseurRequest request) {
        verifierNomDisponible(request.nom(), null);
        Fournisseur fournisseur = new Fournisseur();
        ApprovisionnementMapper.mettreAJourFournisseur(fournisseur, request);
        return ApprovisionnementMapper.toResponse(fournisseurRepository.save(fournisseur));
    }

    public FournisseurResponse modifier(Long id, FournisseurRequest request) {
        Fournisseur fournisseur = trouver(id);
        verifierNomDisponible(request.nom(), id);
        ApprovisionnementMapper.mettreAJourFournisseur(fournisseur, request);
        return ApprovisionnementMapper.toResponse(fournisseurRepository.save(fournisseur));
    }

    public void supprimer(Long id) {
        Fournisseur fournisseur = trouver(id);
        if (fournisseur.getApprovisionnements() != null && !fournisseur.getApprovisionnements().isEmpty()) {
            throw new ConflitRessourceException(
                "Ce fournisseur est référencé par des approvisionnements et ne peut pas être supprimé");
        }
        fournisseurRepository.delete(fournisseur);
    }

    private void verifierNomDisponible(String nom, Long idIgnore) {
        fournisseurRepository.findByNomIgnoreCase(nom.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(idIgnore)) {
                throw new ConflitRessourceException("Un fournisseur portant ce nom existe déjà");
            }
        });
    }

    private Fournisseur trouver(Long id) {
        return fournisseurRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Fournisseur introuvable: " + id));
    }
}
