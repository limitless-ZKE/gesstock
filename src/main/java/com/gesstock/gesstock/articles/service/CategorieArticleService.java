package com.gesstock.gesstock.articles.service;

import com.gesstock.gesstock.articles.dto.CategorieRequest;
import com.gesstock.gesstock.articles.dto.CategorieResponse;
import com.gesstock.gesstock.common.exception.ConflitRessourceException;
import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.articles.mapper.CatalogueMapper;
import com.gesstock.gesstock.entities.CategorieArticle;
import com.gesstock.gesstock.repositories.CategorieArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategorieArticleService {

    private final CategorieArticleRepository categorieRepository;

    public CategorieArticleService(CategorieArticleRepository categorieRepository) {
        this.categorieRepository = categorieRepository;
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
        return CatalogueMapper.toResponse(categorieRepository.save(categorie));
    }

    public CategorieResponse modifier(Long id, CategorieRequest request) {
        CategorieArticle categorie = trouver(id);
        String nom = request.nom().trim();
        verifierNomDisponible(nom, id);
        remplir(categorie, request, nom);
        return CatalogueMapper.toResponse(categorieRepository.save(categorie));
    }

    public void supprimer(Long id) {
        CategorieArticle categorie = trouver(id);
        if (!categorie.getArticles().isEmpty() || !categorie.getSousCategories().isEmpty()
            || !categorie.getPromotions().isEmpty()) {
            throw new ConflitRessourceException("Cette catégorie est utilisée par des articles, sous-catégories ou promotions");
        }
        categorieRepository.delete(categorie);
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
