package com.gesstock.gesstock.common.exception;

/** L'accord présenté n'est pas valable (identifiants refusés, pas un responsable, même personne, trop d'essais). 409, code {@code APPROBATION_REFUSEE}. */
public class ApprobationRefuseeException extends ConflitRessourceException {
    public ApprobationRefuseeException(String message) {
        super(message);
    }
}
