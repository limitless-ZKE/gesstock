package com.gesstock.gesstock.clients.dto;

/**
 * @param nombreAchats nombre de ventes validées du client (calculé par le serveur : l'écran n'a plus besoin de charger
 *                     tout l'historique des ventes pour l'afficher)
 */
public record ClientResponse(Long id, String nom, String email, String telephone, String adresse, long nombreAchats) {}
