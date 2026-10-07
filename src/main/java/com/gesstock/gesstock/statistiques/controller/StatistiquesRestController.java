package com.gesstock.gesstock.statistiques.controller;

import com.gesstock.gesstock.statistiques.dto.SyntheseStatistiquesResponse;
import com.gesstock.gesstock.statistiques.dto.DashboardStatistiquesResponse;
import com.gesstock.gesstock.statistiques.dto.RapportStatistiquesResponse;
import com.gesstock.gesstock.statistiques.service.StatistiquesExportService;
import com.gesstock.gesstock.statistiques.service.StatistiquesService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/statistiques")
public class StatistiquesRestController {

    private final StatistiquesService statistiquesService;
    private final StatistiquesExportService exportService;

    public StatistiquesRestController(
        StatistiquesService statistiquesService,
        StatistiquesExportService exportService
    ) {
        this.statistiquesService = statistiquesService;
        this.exportService = exportService;
    }

    @GetMapping("/dashboard")
    public DashboardStatistiquesResponse dashboard() {
        return statistiquesService.dashboard();
    }

    @GetMapping("/synthese")
    public SyntheseStatistiquesResponse synthese(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journee
    ) {
        return statistiquesService.synthese(journee);
    }

    @GetMapping("/rapport")
    public RapportStatistiquesResponse rapport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) {
        verifierPeriode(debut, fin);
        return statistiquesService.rapport(debut, fin);
    }

    @GetMapping("/rapport/excel")
    public ResponseEntity<byte[]> exporterExcel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) throws IOException {
        verifierPeriode(debut, fin);
        byte[] fichier = exportService.exporterExcel(statistiquesService.rapport(debut, fin));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gesstock-rapport-" + debut + "-" + fin + ".xlsx")
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(fichier);
    }

    @GetMapping("/rapport/pdf")
    public ResponseEntity<byte[]> exporterPdf(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin
    ) throws IOException {
        verifierPeriode(debut, fin);
        byte[] fichier = exportService.exporterPdf(statistiquesService.rapport(debut, fin));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gesstock-rapport-" + debut + "-" + fin + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(fichier);
    }

    private void verifierPeriode(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La période doit contenir une date de début antérieure ou égale à la date de fin");
        }
    }
}
