package com.gesstock.gesstock.common.exception;

/** Opération incompatible avec l'état courant ou doublon : répondue en 409. */
public class ConflitRessourceException extends RuntimeException {
    public ConflitRessourceException(String message) {
        super(message);
    }
}
