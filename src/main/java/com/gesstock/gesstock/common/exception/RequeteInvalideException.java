package com.gesstock.gesstock.common.exception;

/** Données métier invalides (hors validation de bean) : répondue en 400. */
public class RequeteInvalideException extends RuntimeException {
    public RequeteInvalideException(String message) {
        super(message);
    }
}
