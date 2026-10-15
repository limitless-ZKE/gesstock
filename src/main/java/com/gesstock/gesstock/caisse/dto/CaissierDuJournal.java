package com.gesstock.gesstock.caisse.dto;

/** Un compte dont un responsable peut demander le journal de caisse (un ancien employé désactivé reste consultable). */
public record CaissierDuJournal(String username, boolean actif) {}
