package com.gesstock.gesstock.articles.controller;

import com.gesstock.gesstock.articles.dto.AjustementStockRequest;
import com.gesstock.gesstock.articles.dto.ArticleRequest;
import com.gesstock.gesstock.articles.dto.ArticleResponse;
import com.gesstock.gesstock.articles.service.AjustementStockService;
import com.gesstock.gesstock.articles.service.ArticleCatalogueService;
import com.gesstock.gesstock.articles.service.ArticleExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleRestController {

    private final ArticleCatalogueService articleService;
    private final AjustementStockService ajustementStockService;
    private final ArticleExportService exportService;

    public ArticleRestController(ArticleCatalogueService articleService, AjustementStockService ajustementStockService,
                                 ArticleExportService exportService) {
        this.articleService = articleService;
        this.ajustementStockService = ajustementStockService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<ArticleResponse> lister(@RequestParam(required = false) String recherche) {
        return articleService.lister(recherche);
    }

    /**
     * Liste des articles à imprimer, classés par catégorie. {@code stock} : tous, disponible, bas ou rupture ;
     * {@code avecStock=false} donne la liste simple (sans stock ni valeur).
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exporterPdf(
        @RequestParam(required = false) String stock,
        @RequestParam(required = false) Long categorieId,
        @RequestParam(defaultValue = "true") boolean avecStock
    ) throws IOException {
        ArticleExportService.FiltreStock filtre = ArticleExportService.FiltreStock.de(stock);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nomDeFichier(filtre, avecStock) + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(exportService.exporterPdf(filtre, categorieId, avecStock));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exporterExcel(
        @RequestParam(required = false) String stock,
        @RequestParam(required = false) Long categorieId,
        @RequestParam(defaultValue = "true") boolean avecStock
    ) throws IOException {
        ArticleExportService.FiltreStock filtre = ArticleExportService.FiltreStock.de(stock);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nomDeFichier(filtre, avecStock) + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(exportService.exporterExcel(filtre, categorieId, avecStock));
    }

    private static String nomDeFichier(ArticleExportService.FiltreStock filtre, boolean avecStock) {
        return "gesstock-articles-" + filtre.nomDeFichier() + (avecStock ? "" : "-simple");
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
    public ResponseEntity<ArticleResponse> creer(
        @Valid @RequestBody ArticleRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        ArticleResponse response = articleService.creer(request, principal.getUsername());
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
