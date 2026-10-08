package com.gesstock.gesstock.ventes.politique;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Plafonds de remise négociée par rôle, en % du prix catalogue (variables {@code APP_REMISES_PLAFOND_<RÔLE>}) :
 * CAISSIER 5 %, GESTIONNAIRE 10 %, MANAGER 25 %, ADMIN 100 % par défaut. {@code APP_REMISES_MARGE_PLANCHER} (0 % par défaut)
 * est la marge minimale au-dessus du coût de revient : en dessous, l'accord d'un responsable est exigé même sous le plafond.
 */
@Configuration
public class PolitiqueRemiseConfig {

    @Bean
    public PolitiqueRemise politiqueRemise(
        @Value("${app.remises.plafond.admin:100}") BigDecimal admin,
        @Value("${app.remises.plafond.manager:25}") BigDecimal manager,
        @Value("${app.remises.plafond.gestionnaire:10}") BigDecimal gestionnaire,
        @Value("${app.remises.plafond.caissier:5}") BigDecimal caissier,
        @Value("${app.remises.marge-plancher:0}") BigDecimal margePlancher
    ) {
        return new PolitiqueRemise(
            Map.of("ADMIN", admin, "MANAGER", manager, "GESTIONNAIRE", gestionnaire, "CAISSIER", caissier), margePlancher);
    }
}
