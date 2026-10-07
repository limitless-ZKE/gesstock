package com.gesstock.gesstock.promotions.controller;

import com.gesstock.gesstock.promotions.dto.PromotionRequest;
import com.gesstock.gesstock.promotions.dto.PromotionResponse;
import com.gesstock.gesstock.promotions.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/promotions")
public class PromotionRestController {

    private final PromotionService promotionService;

    public PromotionRestController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    public List<PromotionResponse> lister() {
        return promotionService.lister();
    }

    @GetMapping("/{id}")
    public PromotionResponse obtenir(@PathVariable Long id) {
        return promotionService.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<PromotionResponse> creer(@Valid @RequestBody PromotionRequest request) {
        PromotionResponse response = promotionService.creer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public PromotionResponse modifier(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        return promotionService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        promotionService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
