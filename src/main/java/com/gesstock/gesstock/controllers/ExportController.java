package com.gesstock.gesstock.controllers;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.services.ExcelExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/export")
public class ExportController {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private VenteRepository venteRepository;

    @Autowired
    private ExcelExportService excelExportService;

    // Télécharger le stock en fichier Excel
    @GetMapping("/articles/excel")
    public ResponseEntity<InputStreamResource> exportArticlesExcel() throws IOException {
        List<Article> articles = articleRepository.findAll();
        ByteArrayInputStream in = excelExportService.exporterArticlesToExcel(articles);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=inventaire_stock.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    // Télécharger les ventes en fichier Excel
    @GetMapping("/ventes/excel")
    public ResponseEntity<InputStreamResource> exportVentesExcel() throws IOException {
        List<Vente> ventes = venteRepository.findAll();
        ByteArrayInputStream in = excelExportService.exporterVentesToExcel(ventes);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=historique_ventes.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }
}