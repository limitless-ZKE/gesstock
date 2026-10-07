package com.gesstock.gesstock.articles.controller;

import com.gesstock.gesstock.articles.dto.UniteVenteRequest;
import com.gesstock.gesstock.articles.dto.UniteVenteResponse;
import com.gesstock.gesstock.articles.service.UniteVenteArticleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/articles/{articleId}/unites-vente")
public class UniteVenteArticleController {

    private final UniteVenteArticleService uniteVenteService;

    public UniteVenteArticleController(UniteVenteArticleService uniteVenteService) {
        this.uniteVenteService = uniteVenteService;
    }

    @GetMapping
    public List<UniteVenteResponse> lister(@PathVariable Long articleId) {
        return uniteVenteService.lister(articleId);
    }

    @PostMapping
    public ResponseEntity<UniteVenteResponse> associer(
        @PathVariable Long articleId,
        @Valid @RequestBody UniteVenteRequest request
    ) {
        UniteVenteResponse response = uniteVenteService.associer(articleId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{uniteVenteId}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{uniteVenteId}")
    public UniteVenteResponse modifier(
        @PathVariable Long articleId,
        @PathVariable Long uniteVenteId,
        @Valid @RequestBody UniteVenteRequest request
    ) {
        return uniteVenteService.modifier(articleId, uniteVenteId, request);
    }

    @DeleteMapping("/{uniteVenteId}")
    public ResponseEntity<Void> supprimer(
        @PathVariable Long articleId,
        @PathVariable Long uniteVenteId
    ) {
        uniteVenteService.supprimer(articleId, uniteVenteId);
        return ResponseEntity.noContent().build();
    }
}
