package com.gesstock.gesstock.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * L'heure de l'application, injectable (donc réglable dans les tests) et avec un fuseau EXPLICITE : la « journée de
 * caisse » est la date du serveur, elle ne doit pas dépendre du fuseau par défaut de la machine ou du conteneur.
 */
@Configuration
public class HorlogeConfig {

    @Bean
    public Clock horloge(@Value("${app.zone-horaire:UTC}") String zoneHoraire) {
        return Clock.system(ZoneId.of(zoneHoraire));
    }
}
