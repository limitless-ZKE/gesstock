package com.gesstock.gesstock.ventes.fiscal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Régime de TVA de l'application. Tant que {@code app.tva.taux} vaut 0 (réglage par défaut), aucune TVA n'est facturée.
 * Pour l'activer : {@code APP_TVA_TAUX=18} et {@code APP_TVA_PRIX_TTC=true} si les prix du catalogue sont TTC
 * (la taxe est alors extraite du prix), {@code false} s'ils sont HT (la taxe s'ajoute). Aucune migration n'est nécessaire.
 */
@Configuration
public class FiscaliteConfig {

    @Bean
    public CalculTva calculTva(
        @Value("${app.tva.taux:0}") BigDecimal taux,
        @Value("${app.tva.prix-ttc:false}") boolean prixTtc
    ) {
        return new CalculTva(taux, prixTtc);
    }
}
