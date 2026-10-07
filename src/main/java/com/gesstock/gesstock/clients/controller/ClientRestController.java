package com.gesstock.gesstock.clients.controller;

import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.EncoursClientResponse;
import com.gesstock.gesstock.clients.dto.ReglementResponse;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.clients.service.GestionDetteClientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientRestController {

    private final ClientCatalogueService clientService;
    private final GestionDetteClientService detteService;

    public ClientRestController(ClientCatalogueService clientService, GestionDetteClientService detteService) {
        this.clientService = clientService;
        this.detteService = detteService;
    }

    @GetMapping
    public List<ClientResponse> lister() {
        return clientService.lister();
    }

    @GetMapping("/{id}")
    public ClientResponse obtenir(@PathVariable Long id) {
        return clientService.obtenir(id);
    }

    @PostMapping
    public ResponseEntity<ClientResponse> creer(@Valid @RequestBody ClientRequest request) {
        ClientResponse response = clientService.creer(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ClientResponse modifier(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.modifier(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        clientService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/encours")
    public EncoursClientResponse encours(@PathVariable Long id) {
        return clientService.obtenirEncours(id);
    }

    @GetMapping("/{id}/reglements")
    public List<ReglementResponse> historiqueReglements(@PathVariable Long id) {
        return detteService.listerReglementsClient(id);
    }
}
