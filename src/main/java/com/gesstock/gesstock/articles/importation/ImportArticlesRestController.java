package com.gesstock.gesstock.articles.importation;

import com.gesstock.gesstock.articles.importation.ImportArticlesService.ResultatImport;
import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Import d'articles depuis Excel ou CSV : ADMIN, MANAGER et GESTIONNAIRE (voir SecurityConfig). */
@RestController
@RequestMapping("/api/articles/import")
public class ImportArticlesRestController {

    private static final MediaType EXCEL = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportArticlesService importService;

    public ImportArticlesRestController(ImportArticlesService importService) {
        this.importService = importService;
    }

    /** Le fichier Excel de départ ; avec {@code avecArticles=true}, le catalogue actuel dans le même format, à modifier puis réimporter. */
    @GetMapping("/modele")
    public ResponseEntity<byte[]> modele(@RequestParam(defaultValue = "false") boolean avecArticles) throws IOException {
        String nom = avecArticles ? "gesstock-articles-modifiables.xlsx" : "gesstock-modele-import-articles.xlsx";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nom)
            .contentType(EXCEL)
            .body(importService.modele(avecArticles));
    }

    /** Ce que l'import ferait, ligne par ligne, sans rien enregistrer. */
    @PostMapping("/apercu")
    public ApercuImport apercu(@RequestParam("fichier") MultipartFile fichier) {
        return importService.apercu(fichier.getOriginalFilename(), octets(fichier));
    }

    /** L'import, tout ou rien : une seule ligne en erreur et rien n'est enregistré. */
    @PostMapping
    public ResultatImport importer(@RequestParam("fichier") MultipartFile fichier, @AuthenticationPrincipal UserDetails principal) {
        return importService.importer(fichier.getOriginalFilename(), octets(fichier), principal.getUsername());
    }

    private static byte[] octets(MultipartFile fichier) {
        try {
            return fichier.getBytes();
        } catch (IOException exception) {
            throw new RequeteInvalideException("Le fichier n'a pas pu être lu : réessayez.");
        }
    }
}
