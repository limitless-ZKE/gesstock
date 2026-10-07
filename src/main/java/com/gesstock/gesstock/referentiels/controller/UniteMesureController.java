package com.gesstock.gesstock.referentiels.controller;

import com.gesstock.gesstock.referentiels.dto.UniteMesureRequest;
import com.gesstock.gesstock.referentiels.dto.UniteMesureResponse;
import com.gesstock.gesstock.referentiels.service.UniteMesureService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/referentiels/unites-mesure")
public class UniteMesureController {

    private final UniteMesureService uniteMesureService;

    public UniteMesureController(UniteMesureService uniteMesureService) {
        this.uniteMesureService = uniteMesureService;
    }

    @GetMapping
    public List<UniteMesureResponse> lister() {
        return uniteMesureService.lister();
    }

    @GetMapping("/{id}")
    public UniteMesureResponse obtenir(@PathVariable Long id) {
        return uniteMesureService.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<UniteMesureResponse> creer(@Valid @RequestBody UniteMesureRequest request) {
        UniteMesureResponse response = uniteMesureService.creer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public UniteMesureResponse modifier(@PathVariable Long id, @Valid @RequestBody UniteMesureRequest request) {
        return uniteMesureService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        uniteMesureService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
