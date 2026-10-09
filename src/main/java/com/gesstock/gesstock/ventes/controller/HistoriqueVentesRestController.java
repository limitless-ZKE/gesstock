package com.gesstock.gesstock.ventes.controller;

import com.gesstock.gesstock.ventes.dto.HistoriqueVentesResponse;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesExportService;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Historique des ventes d'une période : les chiffres de l'écran, puis les mêmes en Excel ou en PDF à imprimer. Mêmes droits que
 * le reste des ventes (SecurityConfig) : un caissier consulte déjà les ventes de n'importe quelle journée.
 */
@RestController
@RequestMapping("/api/ventes/historique")
public class HistoriqueVentesRestController {

    private final HistoriqueVentesService historiqueService;
    private final HistoriqueVentesExportService exportService;

    public HistoriqueVentesRestController(HistoriqueVentesService historiqueService, HistoriqueVentesExportService exportService) {
        this.historiqueService = historiqueService;
        this.exportService = exportService;
    }

    @GetMapping
    public HistoriqueVentesResponse historique(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) {
        return historiqueService.historique(debut, fin);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) throws IOException {
        byte[] fichier = exportService.exporterExcel(debut, fin);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gesstock-historique-ventes-" + debut + "-" + fin + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(fichier);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) throws IOException {
        byte[] fichier = exportService.exporterPdf(debut, fin);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gesstock-historique-ventes-" + debut + "-" + fin + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(fichier);
    }
}
