package com.gesstock.gesstock.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HorlogeConfigTest {

    private final TimeZone fuseauInitial = TimeZone.getDefault();

    @AfterEach
    void restaurerLeFuseauDeLaJvm() {
        TimeZone.setDefault(fuseauInitial);
    }

    @Test
    void unFuseauExpliciteS_appliqueALHorlogeEtAuxNowDuCodeMetier() {
        HorlogeConfig configuration = new HorlogeConfig("Africa/Lagos");

        assertThat(configuration.horloge().getZone()).isEqualTo(ZoneId.of("Africa/Lagos"));
        assertThat(TimeZone.getDefault().getID()).as("LocalDate.now() de la caisse").isEqualTo("Africa/Lagos");
    }

    @Test
    void sansFuseauConfigureLaMachineFaitFoi() {
        HorlogeConfig configuration = new HorlogeConfig("  ");

        assertThat(configuration.horloge().getZone()).isEqualTo(ZoneId.systemDefault());
        assertThat(TimeZone.getDefault()).isEqualTo(fuseauInitial);
    }

    @Test
    void unFuseauInconnuFaitEchouerLeDemarrageAuLieuDeFausserLesJournees() {
        assertThatThrownBy(() -> new HorlogeConfig("Mars/Olympus")).isInstanceOf(DateTimeException.class);
    }
}
