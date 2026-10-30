package com.gesstock.gesstock.journal.controller;

import com.gesstock.gesstock.journal.dto.JournalActiviteResponse;
import com.gesstock.gesstock.journal.dto.JournalOperationsResponse;
import com.gesstock.gesstock.journal.dto.RecapitulatifClotureResponse;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.journal.service.JournalOperationsExportService;
import com.gesstock.gesstock.journal.service.JournalOperationsService;
import com.gesstock.gesstock.journal.service.OperationsConnues;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Journal des opérations (qui a fait quoi, quand). Réservé aux responsables : administrateur, gérant, gestionnaire (SecurityConfig,
 * décision de l'utilisateur) ; un caissier n'y a pas accès. Le journal d'une période se lit pour tous les utilisateurs ou pour un
 * seul, toutes les opérations ou une famille, et s'imprime (PDF) ou s'exporte (Excel) tel quel.
 */
@RestController
@RequestMapping("/api/journal-activites")
public class JournalActiviteRestController {

    private static final MediaType EXCEL = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final JournalActiviteService journalService;
    private final JournalOperationsService operationsService;
    private final JournalOperationsExportService exportService;

    public JournalActiviteRestController(JournalActiviteService journalService, JournalOperationsService operationsService,
                                         JournalOperationsExportService exportService) {
        this.journalService = journalService;
        this.operationsService = operationsService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<JournalActiviteResponse> lister(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journee
    ) {
        return journalService.listerJournee(journee);
    }

    @GetMapping("/cloture/{sessionId}")
    public RecapitulatifClotureResponse recapitulatif(@PathVariable Long sessionId) {
        return journalService.recapitulatif(sessionId);
    }

    @GetMapping("/operations")
    public JournalOperationsResponse operations(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String utilisateur,
        @RequestParam(required = false) OperationsConnues.Famille famille
    ) {
        return operationsService.journal(debut, fin, compte(utilisateur), famille);
    }

    @GetMapping("/operations/excel")
    public ResponseEntity<byte[]> excel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String utilisateur,
        @RequestParam(required = false) OperationsConnues.Famille famille
    ) throws IOException {
        String nom = compte(utilisateur);
        return fichier(exportService.exporterExcel(debut, fin, nom, famille), nomDeFichier(nom, debut, fin) + ".xlsx", EXCEL);
    }

    @GetMapping("/operations/pdf")
    public ResponseEntity<byte[]> pdf(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String utilisateur,
        @RequestParam(required = false) OperationsConnues.Famille famille
    ) throws IOException {
        String nom = compte(utilisateur);
        return fichier(exportService.exporterPdf(debut, fin, nom, famille), nomDeFichier(nom, debut, fin) + ".pdf", MediaType.APPLICATION_PDF);
    }

    private static String compte(String utilisateur) {
        return utilisateur == null || utilisateur.isBlank() ? null : utilisateur.trim();
    }

    private static ResponseEntity<byte[]> fichier(byte[] contenu, String nom, MediaType type) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nom)
            .contentType(type)
            .body(contenu);
    }

    /** « gesstock-journal-operations-2026-10-01-2026-10-31 », avec l'utilisateur quand il n'y en a qu'un. */
    private static String nomDeFichier(String utilisateur, LocalDate debut, LocalDate fin) {
        String qui = utilisateur == null ? "" : "-" + utilisateur.replaceAll("[^A-Za-z0-9._-]", "_");
        return "gesstock-journal-operations" + qui + "-" + debut + "-" + fin;
    }
}
