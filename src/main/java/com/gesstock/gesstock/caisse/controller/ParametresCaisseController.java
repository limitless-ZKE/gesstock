package com.gesstock.gesstock.caisse.controller;

import com.gesstock.gesstock.ventes.fiscal.CalculTva;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Règles que la caisse applique mais ne décide pas : elle les lit ici pour afficher les mêmes totaux que le serveur.
 *
 * @param tauxTva  taux de TVA imposé par le serveur, en % (0 tant que la TVA n'est pas activée)
 * @param prixTtc  vrai si les prix du catalogue sont TTC (la taxe en est extraite), faux s'ils sont HT (elle s'ajoute)
 */
@RestController
@RequestMapping("/api/caisse/parametres")
public class ParametresCaisseController {

    public record ParametresCaisseResponse(BigDecimal tauxTva, boolean prixTtc) {}

    private final CalculTva calculTva;

    public ParametresCaisseController(CalculTva calculTva) {
        this.calculTva = calculTva;
    }

    @GetMapping
    public ParametresCaisseResponse lire() {
        return new ParametresCaisseResponse(calculTva.taux(), calculTva.prixTtc());
    }
}
