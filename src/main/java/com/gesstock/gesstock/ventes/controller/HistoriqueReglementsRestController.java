package com.gesstock.gesstock.ventes.controller;

import com.gesstock.gesstock.security.PerimetreVendeur;
import com.gesstock.gesstock.ventes.dto.ReglementsDettesResponse;
import com.gesstock.gesstock.ventes.service.HistoriqueReglementsService;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Historique des règlements de dettes d'une période, à l'écran puis en Excel ou en PDF. « Vendeur » désigne ici le compte qui a
 * encaissé le paiement : un caissier ne voit que ce qu'il a encaissé, un responsable tout ou le compte qu'il nomme.
 */
@RestController
@RequestMapping("/api/ventes/historique/reglements")
public class HistoriqueReglementsRestController {

    private final HistoriqueReglementsService reglementsService;
    private final HistoriqueVentesExportService exportService;

    public HistoriqueReglementsRestController(HistoriqueReglementsService reglementsService, HistoriqueVentesExportService exportService) {
        this.reglementsService = reglementsService;
        this.exportService = exportService;
    }

    @GetMapping
    public ReglementsDettesResponse reglements(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String vendeur,
        Authentication authentication
    ) {
        return reglementsService.historique(debut, fin, PerimetreVendeur.vendeurAutorise(authentication, vendeur));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String vendeur,
        Authentication authentication
    ) throws IOException {
        String nom = PerimetreVendeur.vendeurAutorise(authentication, vendeur);
        return HistoriqueVentesRestController.fichier(exportService.exporterReglementsExcel(debut, fin, nom),
            HistoriqueVentesRestController.nomDeFichier("gesstock-reglements-dettes", nom, debut, fin) + ".xlsx", HistoriqueVentesRestController.EXCEL);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String vendeur,
        Authentication authentication
    ) throws IOException {
        String nom = PerimetreVendeur.vendeurAutorise(authentication, vendeur);
        return HistoriqueVentesRestController.fichier(exportService.exporterReglementsPdf(debut, fin, nom),
            HistoriqueVentesRestController.nomDeFichier("gesstock-reglements-dettes", nom, debut, fin) + ".pdf", MediaType.APPLICATION_PDF);
    }
}
