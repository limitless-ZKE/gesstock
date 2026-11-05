package com.gesstock.gesstock.clients.controller;

import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.EncoursClientResponse;
import com.gesstock.gesstock.clients.dto.ReglementResponse;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.clients.service.ClientExportService;
import com.gesstock.gesstock.clients.service.ClientExportService.FiltreClients;
import com.gesstock.gesstock.clients.service.GestionDetteClientService;
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
@RequestMapping("/api/clients")
public class ClientRestController {

    private final ClientCatalogueService clientService;
    private final GestionDetteClientService detteService;
    private final ClientExportService exportService;

    public ClientRestController(ClientCatalogueService clientService, GestionDetteClientService detteService,
                                ClientExportService exportService) {
        this.clientService = clientService;
        this.detteService = detteService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<ClientResponse> lister() {
        return clientService.lister();
    }

    /**
     * La liste des clients à imprimer. {@code filtre} : tous, avec-dette, sans-dette ou dette-ancienne (avec {@code jours},
     * 30 par défaut : la dette la plus ancienne date d'au moins ce nombre de jours) ; {@code recherche} : comme l'écran.
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exporterPdf(
        @RequestParam(required = false) String filtre,
        @RequestParam(required = false) Integer jours,
        @RequestParam(required = false) String recherche
    ) throws IOException {
        FiltreClients choix = FiltreClients.de(filtre);
        int nombreDeJours = ClientExportService.jours(jours);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nomDeFichier(choix, nombreDeJours) + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(exportService.exporterPdf(choix, nombreDeJours, recherche));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exporterExcel(
        @RequestParam(required = false) String filtre,
        @RequestParam(required = false) Integer jours,
        @RequestParam(required = false) String recherche
    ) throws IOException {
        FiltreClients choix = FiltreClients.de(filtre);
        int nombreDeJours = ClientExportService.jours(jours);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nomDeFichier(choix, nombreDeJours) + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(exportService.exporterExcel(choix, nombreDeJours, recherche));
    }

    private static String nomDeFichier(FiltreClients filtre, int jours) {
        return "gesstock-clients-" + filtre.nomDeFichier() + (filtre == FiltreClients.DETTE_ANCIENNE ? "-" + jours + "j" : "");
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
