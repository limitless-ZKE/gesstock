package com.gesstock.gesstock.approvisionnements.controller;

import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import com.gesstock.gesstock.fournisseurs.service.FournisseurExportService;
import com.gesstock.gesstock.fournisseurs.service.FournisseurExportService.FiltreFournisseurs;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/fournisseurs")
public class FournisseurRestController {

    private final FournisseurCatalogueService fournisseurService;
    private final FournisseurExportService exportService;

    public FournisseurRestController(FournisseurCatalogueService fournisseurService, FournisseurExportService exportService) {
        this.fournisseurService = fournisseurService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<FournisseurResponse> lister() {
        return fournisseurService.lister();
    }

    /** La liste des fournisseurs à imprimer. {@code filtre} : tous, avec-dette ou sans-dette ; {@code recherche} : comme l'écran. */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exporterPdf(@RequestParam(required = false) String filtre, @RequestParam(required = false) String recherche)
        throws IOException {
        FiltreFournisseurs choix = FiltreFournisseurs.de(filtre);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gesstock-fournisseurs-" + choix.nomDeFichier() + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(exportService.exporterPdf(choix, recherche));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exporterExcel(@RequestParam(required = false) String filtre, @RequestParam(required = false) String recherche)
        throws IOException {
        FiltreFournisseurs choix = FiltreFournisseurs.de(filtre);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gesstock-fournisseurs-" + choix.nomDeFichier() + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(exportService.exporterExcel(choix, recherche));
    }

    @GetMapping("/{id}")
    public FournisseurResponse obtenir(@PathVariable Long id) {
        return fournisseurService.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<FournisseurResponse> creer(@Valid @RequestBody FournisseurRequest request) {
        FournisseurResponse response = fournisseurService.creer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public FournisseurResponse modifier(@PathVariable Long id, @Valid @RequestBody FournisseurRequest request) {
        return fournisseurService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        fournisseurService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
