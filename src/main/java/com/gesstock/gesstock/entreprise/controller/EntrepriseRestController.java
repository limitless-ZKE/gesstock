package com.gesstock.gesstock.entreprise.controller;

import com.gesstock.gesstock.common.exception.RessourceIntrouvableException;
import com.gesstock.gesstock.entreprise.dto.EntrepriseRequest;
import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entreprise.service.EntrepriseService.LogoServi;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Informations de l'entreprise et logo. Les lire (et charger le logo pour l'imprimer) : tous les rôles connectés, car un
 * caissier imprime des tickets. Les modifier : ADMIN et MANAGER, voir SecurityConfig.
 */
@RestController
@RequestMapping("/api/entreprise")
public class EntrepriseRestController {

    private final EntrepriseService entrepriseService;

    public EntrepriseRestController(EntrepriseService entrepriseService) {
        this.entrepriseService = entrepriseService;
    }

    @GetMapping
    public EntrepriseResponse obtenir() {
        return entrepriseService.obtenir();
    }

    @PutMapping
    public EntrepriseResponse modifier(@Valid @RequestBody EntrepriseRequest demande, @AuthenticationPrincipal UserDetails principal) {
        return entrepriseService.modifier(demande, principal.getUsername());
    }

    /** Envoi (ou remplacement) du logo : partie « fichier » d'une requête multipart. */
    @PostMapping("/logo")
    public EntrepriseResponse remplacerLogo(
        @RequestPart("fichier") MultipartFile fichier,
        @AuthenticationPrincipal UserDetails principal
    ) throws IOException {
        return entrepriseService.remplacerLogo(fichier.getBytes(), principal.getUsername());
    }

    @DeleteMapping("/logo")
    public EntrepriseResponse supprimerLogo(@AuthenticationPrincipal UserDetails principal) {
        return entrepriseService.supprimerLogo(principal.getUsername());
    }

    /**
     * L'image du logo au format demandé ({@code taille} : miniature, ticket, entete ou original). L'empreinte sert d'ETag :
     * le navigateur revalide à chaque impression (réponse 304, sans image) et récupère le nouveau logo dès qu'il change.
     */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(
        @RequestParam(defaultValue = "entete") String taille,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String siEtagDifferent
    ) {
        TailleLogo format = TailleLogo.depuisParametre(taille);
        LogoServi logo = entrepriseService.logo(format)
            .orElseThrow(() -> new RessourceIntrouvableException("Aucun logo n'est enregistré"));
        String etag = "\"" + logo.empreinte() + "-" + format.name().toLowerCase() + "\"";
        ResponseEntity.BodyBuilder reponse = ResponseEntity.status(etag.equals(siEtagDifferent) ? HttpStatus.NOT_MODIFIED : HttpStatus.OK)
            .eTag(etag)
            .cacheControl(CacheControl.noCache().cachePrivate());
        if (etag.equals(siEtagDifferent)) {
            return reponse.build();
        }
        return reponse
            .contentType(MediaType.parseMediaType(logo.typeMime()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"logo.png\"")
            .body(logo.contenu());
    }
}
