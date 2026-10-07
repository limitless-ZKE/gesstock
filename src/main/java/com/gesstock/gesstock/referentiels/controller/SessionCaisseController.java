package com.gesstock.gesstock.referentiels.controller;

import com.gesstock.gesstock.referentiels.dto.ClotureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/referentiels/sessions-caisse")
public class SessionCaisseController {

    private final SessionCaisseService sessionCaisseService;

    public SessionCaisseController(SessionCaisseService sessionCaisseService) {
        this.sessionCaisseService = sessionCaisseService;
    }

    @GetMapping
    public List<SessionCaisseResponse> lister(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journee
    ) {
        return sessionCaisseService.lister(journee == null ? LocalDate.now() : journee);
    }

    @GetMapping("/{id}")
    public SessionCaisseResponse obtenir(@PathVariable Long id) {
        return sessionCaisseService.obtenir(id);
    }

    @GetMapping("/ouverte")
    public SessionCaisseResponse sessionOuverte(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journee
    ) {
        return journee == null
            ? sessionCaisseService.sessionOuverteCourante()
            : sessionCaisseService.sessionOuverte(journee);
    }

    @PostMapping("/ouverture")
    public ResponseEntity<SessionCaisseResponse> ouvrir(
        @Valid @RequestBody OuvertureSessionRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        SessionCaisseResponse response = sessionCaisseService.ouvrir(request, principal.getUsername());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/referentiels/sessions-caisse/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/{id}/cloture")
    public SessionCaisseResponse cloturer(
        @PathVariable Long id,
        @Valid @RequestBody ClotureSessionRequest request,
        @AuthenticationPrincipal UserDetails principal
    ) {
        return sessionCaisseService.cloturer(id, request, principal.getUsername());
    }
}
