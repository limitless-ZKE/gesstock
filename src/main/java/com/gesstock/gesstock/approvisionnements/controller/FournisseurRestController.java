package com.gesstock.gesstock.approvisionnements.controller;

import com.gesstock.gesstock.approvisionnements.dto.FournisseurRequest;
import com.gesstock.gesstock.approvisionnements.dto.FournisseurResponse;
import com.gesstock.gesstock.approvisionnements.service.FournisseurCatalogueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/fournisseurs")
public class FournisseurRestController {

    private final FournisseurCatalogueService fournisseurService;

    public FournisseurRestController(FournisseurCatalogueService fournisseurService) {
        this.fournisseurService = fournisseurService;
    }

    @GetMapping
    public List<FournisseurResponse> lister() {
        return fournisseurService.lister();
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
