package com.gesstock.gesstock.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduction unique des erreurs de l'API REST en {@link ProblemDetail}.
 * Limité aux {@link RestController} pour ne pas modifier le comportement des contrôleurs Thymeleaf.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalApiExceptionHandler {

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ProblemDetail traiterIntrouvable(RessourceIntrouvableException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflitRessourceException.class)
    public ProblemDetail traiterConflit(ConflitRessourceException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(RequeteInvalideException.class)
    public ProblemDetail traiterRequeteInvalide(RequeteInvalideException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail traiterValidation(MethodArgumentNotValidException exception) {
        Map<String, String> erreurs = exception.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                error -> error.getField(),
                error -> error.getDefaultMessage() == null ? "Valeur invalide" : error.getDefaultMessage(),
                (first, ignored) -> first
            ));
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Les données envoyées sont invalides");
        detail.setProperty("erreurs", erreurs);
        return detail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail traiterViolation(ConstraintViolationException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Les paramètres sont invalides");
        detail.setProperty("erreurs", exception.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                violation -> violation.getMessage(),
                (first, ignored) -> first
            )));
        return detail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail traiterTypeInvalide(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Le paramètre '" + exception.getName() + "' a un format invalide");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail traiterCorpsInvalide(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Le corps de la requête est absent ou mal formé");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail traiterIntegrite(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT,
            "L'opération est impossible : la donnée existe déjà ou est encore référencée");
    }

    private ProblemDetail problem(HttpStatus status, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setType(URI.create("about:blank"));
        return detail;
    }
}
