package com.gesstock.gesstock.fournisseurs.dto;

import java.math.BigDecimal;

/**
 * @param soldeDu          ce que nous devons encore (dettes non réglées)
 * @param creditDisponible avoirs non encore consommés ; les avoirs s'imputent d'eux-mêmes sur les dettes, donc un
 *                         fournisseur n'a jamais à la fois un solde dû et un crédit disponible
 */
public record CompteFournisseurResume(Long fournisseurId, String nom, BigDecimal soldeDu, BigDecimal creditDisponible) {}
