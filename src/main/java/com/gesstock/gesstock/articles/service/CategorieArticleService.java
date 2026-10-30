package com.gesstock.gesstock.articles.service;

import com.gesstock.gesstock.articles.dto.CategorieRequest;
import com.gesstock.gesstock.articles.dto.CategorieResponse;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.articles.mapper.CatalogueMapper;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class CategorieArticleService {

    private final CategorieArticleRepository categorieRepository;
    private final JournalActiviteService journalService;

    public CategorieArticleService(CategorieArticleRepository categorieRepository, JournalActiviteService journalService) {
        this.categorieRepository = categorieRepository;
        this.journalService = journalService;
    }

    @Transactional(readOnly = true)
    public List<CategorieResponse> lister(Long parentId) {
        List<CategorieArticle> categories = parentId == null
            ? categorieRepository.findAll()
            : categorieRepository.findByParentIdOrderByNomAsc(parentId);
        if (parentId != null && !categorieRepository.existsById(parentId)) {
            throw new RessourceIntrouvableException("Catégorie parente introuvable: " + parentId);
        }
        return categories.stream().map(CatalogueMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CategorieResponse obtenir(Long id) {
        return CatalogueMapper.toResponse(trouver(id));
    }

    public CategorieResponse creer(CategorieRequest request) {
        String nom = request.nom().trim();
        verifierNomDisponible(nom, null);
        CategorieArticle categorie = new CategorieArticle();
        remplir(categorie, request, nom);
        CategorieArticle enregistree = categorieRepository.save(categorie);
        journalService.enregistrer("CREATION", "CATEGORIE", enregistree.getId(), "Catégorie « " + nom + " » créée", null);
        return CatalogueMapper.toResponse(enregistree);
    }

    public CategorieResponse modifier(Long id, CategorieRequest request) {
        CategorieArticle categorie = trouver(id);
        String nom = request.nom().trim();
        verifierNomDisponible(nom, id);
        String ancienNom = categorie.getNom();
        boolean etaitActive = categorie.isActive();
        remplir(categorie, request, nom);
        CategorieArticle enregistree = categorieRepository.save(categorie);
        List<String> changements = new ArrayList<>();
        if (!ancienNom.equals(nom)) changements.add("renommée « " + nom + " »");
        if (etaitActive != enregistree.isActive()) changements.add(enregistree.isActive() ? "réactivée" : "désactivée");
        if (!changements.isEmpty()) {
            journalService.enregistrer("MODIFICATION", "CATEGORIE", id, "Catégorie « " + ancienNom + " » " + String.join(", ", changements), null);
        }
        return CatalogueMapper.toResponse(enregistree);
    }

    public void supprimer(Long id) {
        CategorieArticle categorie = trouver(id);
        if (!categorie.getArticles().isEmpty() || !categorie.getSousCategories().isEmpty()
            || !categorie.getPromotions().isEmpty()) {
            throw new ConflitRessourceException("Cette catégorie est utilisée par des articles, sous-catégories ou promotions");
        }
        categorieRepository.delete(categorie);
        journalService.enregistrer("SUPPRESSION", "CATEGORIE", id, "Catégorie « " + categorie.getNom() + " » supprimée", null);
    }

    private void remplir(CategorieArticle categorie, CategorieRequest request, String nom) {
        CategorieArticle parent = request.parentId() == null ? null : trouver(request.parentId());
        if (parent != null && parent.getId().equals(categorie.getId())) {
            throw new ConflitRessourceException("Une catégorie ne peut pas être sa propre sous-catégorie");
        }
        CategorieArticle parentCourant = parent;
        while (parentCourant != null) {
            if (parentCourant.getId().equals(categorie.getId())) {
                throw new ConflitRessourceException("Cette modification créerait une hiérarchie de catégories cyclique");
            }
            parentCourant = parentCourant.getParent();
        }
        categorie.setNom(nom);
        categorie.setDescription(request.description());
        categorie.setActive(request.active() == null || request.active());
        categorie.setParent(parent);
    }

    private void verifierNomDisponible(String nom, Long idIgnore) {
        categorieRepository.findByNomIgnoreCase(nom).ifPresent(existing -> {
            if (!existing.getId().equals(idIgnore)) {
                throw new ConflitRessourceException("Une catégorie portant ce nom existe déjà");
            }
        });
    }

    private CategorieArticle trouver(Long id) {
        return categorieRepository.findById(id)
            .orElseThrow(() -> new RessourceIntrouvableException("Catégorie introuvable: " + id));
    }
}
