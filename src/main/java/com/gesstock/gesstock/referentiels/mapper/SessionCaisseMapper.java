package com.gesstock.gesstock.referentiels.mapper;

import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.referentiels.dto.SessionCaisseResponse;

public final class SessionCaisseMapper {

    private SessionCaisseMapper() {}

    public static SessionCaisseResponse toResponse(SessionCaisse session) {
        return new SessionCaisseResponse(
            session.getId(),
            session.getCode(),
            session.getJournee(),
            session.getOuverteLe(),
            session.getFermeeLe(),
            session.getFondDeCaisse(),
            session.getMontantCloture(),
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            session.getFondDeCaisse(),
            session.getMontantCloture() == null ? null : session.getMontantCloture().subtract(session.getFondDeCaisse()),
            session.getStatut(),
            session.getOuvertePar().getId(),
            session.getOuvertePar().getUsername(),
            session.getFermeePar() == null ? null : session.getFermeePar().getId(),
            session.getFermeePar() == null ? null : session.getFermeePar().getUsername(),
            session.getStatut() == com.gesstock.gesstock.entities.StatutSessionCaisse.OUVERTE
                && session.getJournee().isBefore(java.time.LocalDate.now())
        );
    }
}
