package com.gesstock.gesstock.caisse.controller;

import com.gesstock.gesstock.caisse.dto.CaissierDuJournal;
import com.gesstock.gesstock.caisse.dto.JournalCaissierResponse;
import com.gesstock.gesstock.caisse.service.JournalCaisseService;
import com.gesstock.gesstock.caisse.service.JournalCaissierExportService;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.security.PerimetreVendeur;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Journal de caisse d'UN caissier, à l'écran puis en Excel ou en PDF à imprimer et signer. Un caissier ne lit que le sien : son nom
 * est imposé par le serveur, un autre nom est refusé (403) quoi que l'écran envoie. Un responsable (administrateur, gérant,
 * gestionnaire) doit nommer le caissier. Le journal complet de la journée, qui montre les lignes de tous, leur est réservé
 * (SecurityConfig).
 */
@RestController
@RequestMapping("/api/caisse/journal")
public class JournalCaissierRestController {

    private final JournalCaisseService journalService;
    private final JournalCaissierExportService exportService;

    public JournalCaissierRestController(JournalCaisseService journalService, JournalCaissierExportService exportService) {
        this.journalService = journalService;
        this.exportService = exportService;
    }

    /** Les comptes dont on peut demander le journal : réservé aux responsables (SecurityConfig). */
    @GetMapping("/caissiers")
    public List<CaissierDuJournal> caissiers() {
        return journalService.caissiersDuJournal();
    }

    @GetMapping("/caissier")
    public JournalCaissierResponse journal(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String caissier,
        Authentication authentication
    ) {
        return journalService.journalDuCaissier(caissierAutorise(authentication, caissier), debut, fin);
    }

    @GetMapping("/caissier/excel")
    public ResponseEntity<byte[]> excel(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String caissier,
        Authentication authentication
    ) throws IOException {
        String nom = caissierAutorise(authentication, caissier);
        byte[] fichier = exportService.exporterExcel(nom, debut, fin);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nomDeFichier(nom, debut, fin) + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(fichier);
    }

    @GetMapping("/caissier/pdf")
    public ResponseEntity<byte[]> pdf(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
        @RequestParam(required = false) String caissier,
        Authentication authentication
    ) throws IOException {
        String nom = caissierAutorise(authentication, caissier);
        byte[] fichier = exportService.exporterPdf(nom, debut, fin);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nomDeFichier(nom, debut, fin) + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(fichier);
    }

    /** Le caissier dont le journal est servi : le sien pour un caissier, celui qui est nommé pour un responsable. */
    private static String caissierAutorise(Authentication authentication, String demande) {
        boolean nomme = demande != null && !demande.isBlank();
        if (PerimetreVendeur.responsable(authentication)) {
            if (!nomme) throw new RequeteInvalideException("Choisissez le caissier dont vous voulez le journal.");
            return demande.trim();
        }
        if (nomme && !demande.trim().equalsIgnoreCase(authentication.getName())) {
            throw new AccessDeniedException("Un caissier ne peut lire que son propre journal de caisse.");
        }
        return authentication.getName();
    }

    private static String nomDeFichier(String caissier, LocalDate debut, LocalDate fin) {
        return "gesstock-journal-caissier-" + caissier.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + debut + "-" + fin;
    }
}
