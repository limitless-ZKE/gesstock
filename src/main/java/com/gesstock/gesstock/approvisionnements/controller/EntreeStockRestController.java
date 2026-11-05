package com.gesstock.gesstock.approvisionnements.controller;

import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementRequest;
import com.gesstock.gesstock.approvisionnements.dto.ApprovisionnementResponse;
import com.gesstock.gesstock.approvisionnements.dto.ReceptionRequest;
import com.gesstock.gesstock.approvisionnements.service.ApprovisionnementExportService;
import com.gesstock.gesstock.approvisionnements.service.EntreeStockService;
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
@RequestMapping("/api/approvisionnements")
public class EntreeStockRestController {

    private final EntreeStockService entreeStockService;
    private final ApprovisionnementExportService exportService;

    public EntreeStockRestController(EntreeStockService entreeStockService, ApprovisionnementExportService exportService) {
        this.entreeStockService = entreeStockService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<ApprovisionnementResponse> lister() {
        return entreeStockService.lister();
    }

    @GetMapping("/{id}")
    public ApprovisionnementResponse obtenir(@PathVariable Long id) {
        return entreeStockService.obtenir(id);
    }

    /** Le bon à imprimer, en PDF (fournisseur, articles, prix d'achat, total, cases de signature). */
    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exporterPdf(@PathVariable Long id) throws IOException {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + exportService.nomDeFichier(id) + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(exportService.exporterPdf(id));
    }

    @PostMapping
    public ResponseEntity<ApprovisionnementResponse> creer(
        @Valid @RequestBody ApprovisionnementRequest request
    ) {
        ApprovisionnementResponse response = entreeStockService.creer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ApprovisionnementResponse modifier(
        @PathVariable Long id,
        @Valid @RequestBody ApprovisionnementRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return entreeStockService.modifier(id, request, principal.getUsername());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        entreeStockService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/validation")
    public ApprovisionnementResponse valider(
        @PathVariable Long id,
        @RequestBody(required = false) ReceptionRequest reception,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return entreeStockService.valider(id, principal.getUsername(), reception != null && reception.payerEnEspeces());
    }
}
