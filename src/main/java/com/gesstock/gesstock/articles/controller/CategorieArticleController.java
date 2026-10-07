package com.gesstock.gesstock.articles.controller;

import com.gesstock.gesstock.articles.dto.CategorieRequest;
import com.gesstock.gesstock.articles.dto.CategorieResponse;
import com.gesstock.gesstock.articles.service.CategorieArticleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/articles/categories")
public class CategorieArticleController {

    private final CategorieArticleService categorieService;

    public CategorieArticleController(CategorieArticleService categorieService) {
        this.categorieService = categorieService;
    }

    @GetMapping
    public List<CategorieResponse> lister(@RequestParam(required = false) Long parentId) {
        return categorieService.lister(parentId);
    }

    @GetMapping("/{id}")
    public CategorieResponse obtenir(@PathVariable Long id) {
        return categorieService.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<CategorieResponse> creer(@Valid @RequestBody CategorieRequest request) {
        CategorieResponse response = categorieService.creer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public CategorieResponse modifier(@PathVariable Long id, @Valid @RequestBody CategorieRequest request) {
        return categorieService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        categorieService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
