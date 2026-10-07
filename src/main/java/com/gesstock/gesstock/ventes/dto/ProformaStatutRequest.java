package com.gesstock.gesstock.ventes.dto;

import com.gesstock.gesstock.entities.StatutProforma;
import jakarta.validation.constraints.NotNull;

public record ProformaStatutRequest(@NotNull StatutProforma statut) {}
