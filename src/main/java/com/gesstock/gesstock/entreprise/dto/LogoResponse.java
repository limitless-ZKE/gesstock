package com.gesstock.gesstock.entreprise.dto;

/** Le logo actuel. {@code empreinte} change à chaque remplacement : à joindre à l'adresse de l'image pour contourner un ancien cache. */
public record LogoResponse(String empreinte, int largeur, int hauteur) {}
