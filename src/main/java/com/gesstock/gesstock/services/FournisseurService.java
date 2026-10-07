package com.gesstock.gesstock.services;

import com.gesstock.gesstock.entities.Fournisseur;
import com.gesstock.gesstock.repositories.FournisseurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FournisseurService {

    @Autowired
    private FournisseurRepository fournisseurRepository;

    public List<Fournisseur> getAllFournisseurs() {
        return fournisseurRepository.findAll();
    }

    // Ajouté pour correspondre au contrôleur
    public List<Fournisseur> listerTous() {
        return fournisseurRepository.findAll();
    }

    public Optional<Fournisseur> getFournisseurById(Long id) {
        return fournisseurRepository.findById(id);
    }

    public Fournisseur saveFournisseur(Fournisseur fournisseur) {
        return fournisseurRepository.save(fournisseur);
    }

    public void deleteFournisseur(Long id) {
        fournisseurRepository.deleteById(id);
    }
}