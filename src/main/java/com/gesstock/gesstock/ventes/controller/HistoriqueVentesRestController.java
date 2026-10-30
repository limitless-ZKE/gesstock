package com.gesstock.gesstock.ventes.controller;

import com.gesstock.gesstock.security.PerimetreVendeur;
import com.gesstock.gesstock.ventes.dto.HistoriqueVentesResponse;
import com.gesstock.gesstock.ventes.dto.VentesSoldeesResponse;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesExportService;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
 * Historique des ventes d'une période (toutes les ventes, ou seulement les ventes soldées) : les chiffres de l'écran, puis les mêmes
 * en Excel ou en PDF à imprimer. Un caissier ne voit que SES ventes (son nom est imposé, un autre nom est refusé : 403) ; un
 * responsable voit celles de tous, ou celles du vendeur qu'il nomme (voir {@link PerimetreVendeur}).
 */
@RestController
@RequestMapping("/api/ventes/historique")
public class HistoriqueVentesRestController {

    static final MediaType EXCEL = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final HistoriqueVentesService historiqueService;
    private final HistoriqueVentesExportService exportService;

    public HistoriqueVentesRestController(HistoriqueVentesService historiqueService, HistoriqueVentesExportService exportService) {
        this.historiqueService = historiqueService;
        this.exportService = exportService;
    }

    @GetMapping
    public HistoriqueVentesResponse historique(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String vendeur,
        Authentication authentication
    ) {
        return historiqueService.historique(debut, fin, PerimetreVendeur.vendeurAutorise(authentication, vendeur));
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String vendeur,
        Authentication authentication
    ) throws IOException {
        String nom = PerimetreVendeur.vendeurAutorise(authentication, vendeur);
        return fichier(exportService.exporterExcel(debut, fin, nom), nomDeFichier("gesstock-historique-ventes", nom, debut, fin) + ".xlsx", EXCEL);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String vendeur,
        Authentication authentication
    ) throws IOException {
        String nom = PerimetreVendeur.vendeurAutorise(authentication, vendeur);
        return fichier(exportService.exporterPdf(debut, fin, nom), nomDeFichier("gesstock-historique-ventes", nom, debut, fin) + ".pdf",
            MediaType.APPLICATION_PDF);
    }

    @GetMapping("/soldees")
    public VentesSoldeesResponse soldees(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String vendeur,
        Authentication authentication
    ) {
        return historiqueService.ventesSoldees(debut, fin, PerimetreVendeur.vendeurAutorise(authentication, vendeur));
    }

    @GetMapping("/soldees/excel")
    public ResponseEntity<byte[]> soldeesExcel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String vendeur,
        Authentication authentication
    ) throws IOException {
        String nom = PerimetreVendeur.vendeurAutorise(authentication, vendeur);
        return fichier(exportService.exporterSoldeesExcel(debut, fin, nom), nomDeFichier("gesstock-ventes-soldees", nom, debut, fin) + ".xlsx", EXCEL);
    }

    @GetMapping("/soldees/pdf")
    public ResponseEntity<byte[]> soldeesPdf(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String vendeur,
        Authentication authentication
    ) throws IOException {
        String nom = PerimetreVendeur.vendeurAutorise(authentication, vendeur);
        return fichier(exportService.exporterSoldeesPdf(debut, fin, nom), nomDeFichier("gesstock-ventes-soldees", nom, debut, fin) + ".pdf",
            MediaType.APPLICATION_PDF);
    }

    static ResponseEntity<byte[]> fichier(byte[] contenu, String nom, MediaType type) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nom)
            .contentType(type)
            .body(contenu);
    }

    /** « gesstock-historique-ventes-2026-10-01-2026-10-31 », avec le vendeur quand il n'y en a qu'un : « …-ventes-marie-2026-… ». */
    static String nomDeFichier(String debutDuNom, String vendeur, LocalDate debut, LocalDate fin) {
        String qui = vendeur == null ? "" : "-" + vendeur.replaceAll("[^A-Za-z0-9._-]", "_");
        return debutDuNom + qui + "-" + debut + "-" + fin;
    }
}
