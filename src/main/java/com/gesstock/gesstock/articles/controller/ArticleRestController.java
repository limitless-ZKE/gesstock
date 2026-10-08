package com.gesstock.gesstock.articles.controller;

import com.gesstock.gesstock.articles.dto.AjustementStockRequest;
import com.gesstock.gesstock.articles.dto.ArticleRequest;
import com.gesstock.gesstock.articles.dto.ArticleResponse;
import com.gesstock.gesstock.articles.service.AjustementStockService;
import com.gesstock.gesstock.articles.service.ArticleCatalogueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleRestController {

    private final ArticleCatalogueService articleService;
    private final AjustementStockService ajustementStockService;

    public ArticleRestController(ArticleCatalogueService articleService, AjustementStockService ajustementStockService) {
        this.articleService = articleService;
        this.ajustementStockService = ajustementStockService;
    }

    @GetMapping
    public List<ArticleResponse> lister(@RequestParam(required = false) String recherche) {
        return articleService.lister(recherche);
    }

    @GetMapping("/alertes-stock")
    public List<ArticleResponse> listerEnAlerte() {
        return articleService.listerEnAlerte();
    }

    @GetMapping("/{id}")
    public ArticleResponse obtenir(@PathVariable Long id) {
        return articleService.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<ArticleResponse> creer(@Valid @RequestBody ArticleRequest request) {
        ArticleResponse response = articleService.creer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ArticleResponse modifier(@PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
        return articleService.modifier(id, request);
    }

    /** Seul moyen de corriger le stock d'un article : motif obligatoire, mouvement et journal. */
    @PostMapping("/{id}/ajustement-stock")
    public ArticleResponse ajusterStock(
        @PathVariable Long id,
        @Valid @RequestBody AjustementStockRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return ajustementStockService.ajuster(id, request, principal.getUsername());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        articleService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
