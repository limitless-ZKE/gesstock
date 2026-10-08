package com.gesstock.gesstock.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Horloge de test : l'heure ne bouge que si le test l'avance. */
public class HorlogeReglable extends Clock {

    private Instant instant = Instant.parse("2026-10-08T09:00:00Z");

    public void avancer(Duration duree) {
        instant = instant.plus(duree);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
