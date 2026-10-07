package com.gesstock.gesstock.journal.controller;

import com.gesstock.gesstock.journal.dto.JournalActiviteResponse;
import com.gesstock.gesstock.journal.dto.RecapitulatifClotureResponse;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/journal-activites")
public class JournalActiviteRestController {

    private final JournalActiviteService journalService;

    public JournalActiviteRestController(JournalActiviteService journalService) {
        this.journalService = journalService;
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
}
