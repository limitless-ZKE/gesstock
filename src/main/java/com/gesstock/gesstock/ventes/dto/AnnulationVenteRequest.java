package com.gesstock.gesstock.ventes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param articlesRetournesEnEtat à confirmer lorsque le client a déjà versé de l'argent : la vente n'est
 *                                annulée et le client remboursé que s'il rapporte les articles en l'état.
 *                                Facultatif : absent, il vaut {@code false}.
 */
public record AnnulationVenteRequest(
    @NotBlank @Size(max = 500) String motif,
    Boolean articlesRetournesEnEtat
) {
    public boolean articlesRapportes() {
        return Boolean.TRUE.equals(articlesRetournesEnEtat);
    }
}
