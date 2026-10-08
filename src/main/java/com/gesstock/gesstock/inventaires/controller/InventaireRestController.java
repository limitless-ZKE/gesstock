package com.gesstock.gesstock.inventaires.controller;

import com.gesstock.gesstock.inventaires.dto.InventaireRequest;
import com.gesstock.gesstock.inventaires.dto.InventaireResponse;
import com.gesstock.gesstock.inventaires.dto.LigneInventaireRequest;
import com.gesstock.gesstock.inventaires.dto.LigneInventaireResponse;
import com.gesstock.gesstock.inventaires.service.InventaireExportService;
import com.gesstock.gesstock.inventaires.service.InventaireService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Inventaire physique. Ouvrir, compter et soumettre : ADMIN, MANAGER, GESTIONNAIRE. Valider, reprendre et annuler : ADMIN et
 * MANAGER seulement (voir SecurityConfig) ; la validation exige en plus une personne différente de celle qui a ouvert.
 */
@RestController
@RequestMapping("/api/inventaires")
public class InventaireRestController {

    public static final String EN_TETE_TOTAL = "X-Total-Count";

    private final InventaireService inventaireService;
    private final InventaireExportService exportService;

    public InventaireRestController(InventaireService inventaireService, InventaireExportService exportService) {
        this.inventaireService = inventaireService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<InventaireResponse> lister() {
        return inventaireService.lister();
    }

    @GetMapping("/{id}")
    public InventaireResponse obtenir(@PathVariable Long id) {
        return inventaireService.obtenir(id);
    }

    /** Lignes paginées (50 par défaut, 500 au plus) ; {@code ecartsSeulement} ne garde que les articles comptés en écart. */
    @GetMapping("/{id}/lignes")
    public ResponseEntity<List<LigneInventaireResponse>> lignes(
        @PathVariable Long id,
        @RequestParam(defaultValue = "false") boolean ecartsSeulement,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        InventaireService.PageLignes resultat = inventaireService.lignes(id, ecartsSeulement, page, size);
        return ResponseEntity.ok().header(EN_TETE_TOTAL, String.valueOf(resultat.total())).body(resultat.lignes());
    }

    @PostMapping
    public ResponseEntity<InventaireResponse> ouvrir(
        @Valid @RequestBody InventaireRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        InventaireResponse reponse = inventaireService.ouvrir(request, principal.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(reponse.id()).toUri();
        return ResponseEntity.created(location).body(reponse);
    }

    @PutMapping("/{id}/lignes/{articleId}")
    public LigneInventaireResponse saisir(
        @PathVariable Long id,
        @PathVariable Long articleId,
        @Valid @RequestBody LigneInventaireRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return inventaireService.saisir(id, articleId, request, principal.getUsername());
    }

    @PostMapping("/{id}/soumission")
    public InventaireResponse soumettre(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return inventaireService.soumettre(id, principal.getUsername());
    }

    @PostMapping("/{id}/reprise")
    public InventaireResponse reprendre(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return inventaireService.reprendre(id, principal.getUsername());
    }

    @PostMapping("/{id}/validation")
    public InventaireResponse valider(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return inventaireService.valider(id, principal.getUsername());
    }

    @PostMapping("/{id}/annulation")
    public InventaireResponse annuler(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        return inventaireService.annuler(id, principal.getUsername());
    }

    @GetMapping("/{id}/export.xlsx")
    public ResponseEntity<byte[]> exporter(@PathVariable Long id) throws IOException {
        InventaireResponse inventaire = inventaireService.obtenir(id);
        byte[] fichier = exportService.exporterExcel(inventaire, inventaireService.toutesLesLignes(id));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + inventaire.code() + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(fichier);
    }
}
