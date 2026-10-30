package com.gesstock.gesstock.journal.dto;

import java.time.LocalDateTime;

/**
 * Une opération du journal, telle qu'on la lit : qui, quand, quoi (en clair) et le détail enregistré au moment même.
 *
 * @param utilisateur le compte qui a fait l'opération ; nul pour une connexion refusée d'un identifiant inconnu
 * @param famille     « Ventes », « Caisse », « Connexions et comptes »…
 * @param operation   « Vente annulée », « Clôture de la caisse »…
 * @param element     l'élément concerné, avec ses mots d'origine (« VENTE #42 ») : pour retrouver la fiche si besoin
 * @param poste       l'adresse du poste d'où vient une connexion (vide pour les autres opérations)
 */
public record OperationJournal(
    Long id,
    LocalDateTime date,
    String utilisateur,
    String famille,
    String operation,
    String details,
    String element,
    String poste
) {}
