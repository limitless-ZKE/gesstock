package com.gesstock.gesstock.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * L'heure de l'application, avec un fuseau EXPLICITE ({@code app.zone-horaire}, Africa/Dakar par défaut) : la
 * « journée de caisse » est la date du serveur, elle ne doit pas dépendre du fuseau de la machine ou du conteneur
 * (un conteneur est en UTC : pour un commerce à UTC+1 la journée changerait à 1 h du matin).
 *
 * <p>Le fuseau par défaut de la JVM est aligné sur celui-ci : les {@code LocalDate.now()} et {@code LocalDateTime.now()}
 * du code métier et des entités suivent alors le fuseau du commerce. Un fuseau inconnu fait échouer le démarrage.
 */
@Configuration
public class HorlogeConfig {

    private final ZoneId zone;

    public HorlogeConfig(@Value("${app.zone-horaire:}") String zoneHoraire) {
        if (zoneHoraire.isBlank()) {
            this.zone = ZoneId.systemDefault();
        } else {
            this.zone = ZoneId.of(zoneHoraire.trim());
            TimeZone.setDefault(TimeZone.getTimeZone(this.zone));
        }
    }

    @Bean
    public Clock horloge() {
        return Clock.system(zone);
    }
}
