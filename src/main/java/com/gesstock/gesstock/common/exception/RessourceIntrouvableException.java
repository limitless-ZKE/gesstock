package com.gesstock.gesstock.common.exception;

/** Ressource demandée absente : répondue en 404 par {@link GlobalApiExceptionHandler}. */
public class RessourceIntrouvableException extends RuntimeException {
    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
