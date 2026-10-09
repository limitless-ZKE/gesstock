package com.gesstock.gesstock.caisse.controller;

import com.gesstock.gesstock.caisse.dto.JournalCaisseResponse;
import com.gesstock.gesstock.caisse.service.JournalCaisseExportService;
import com.gesstock.gesstock.caisse.service.JournalCaisseService;
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
 * Journal de caisse d'une période : les écritures d'espèces journée par journée, à l'écran puis en Excel ou en PDF à imprimer et
 * signer. Mêmes droits que le reste de la caisse (SecurityConfig) : un caissier lit déjà les sessions et leurs sorties.
 */
@RestController
@RequestMapping("/api/caisse/journal")
public class JournalCaisseRestController {

    private final JournalCaisseService journalService;
    private final JournalCaisseExportService exportService;

    public JournalCaisseRestController(JournalCaisseService journalService, JournalCaisseExportService exportService) {
        this.journalService = journalService;
        this.exportService = exportService;
    }

    @GetMapping
    public JournalCaisseResponse journal(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) {
        return journalService.journal(debut, fin);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) throws IOException {
        byte[] fichier = exportService.exporterExcel(debut, fin);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gesstock-journal-caisse-" + debut + "-" + fin + ".xlsx")
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
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gesstock-journal-caisse-" + debut + "-" + fin + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(fichier);
    }
}
