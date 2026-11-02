package com.gesstock.gesstock.common.exception;

import com.gesstock.gesstock.common.web.RequestIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Point UNIQUE de traduction des erreurs en {@link ProblemDetail} (RFC 7807, {@code application/problem+json}).
 * Toute réponse d'erreur de l'API porte : {@code status}, {@code title}, {@code detail} (en français, sans détail
 * technique), {@code code} (stable, que le front peut tester) et {@code requestId} (pour retrouver la trace dans
 * les logs). Une erreur imprévue donne un 500 générique : la cause n'est écrite que dans les logs.
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ProblemDetail traiterIntrouvable(RessourceIntrouvableException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), "RESSOURCE_INTROUVABLE");
    }

    @ExceptionHandler(ConflitRessourceException.class)
    public ProblemDetail traiterConflit(ConflitRessourceException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "CONFLIT");
    }

    @ExceptionHandler(RequeteInvalideException.class)
    public ProblemDetail traiterRequeteInvalide(RequeteInvalideException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), "REQUETE_INVALIDE");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail traiterViolation(ConstraintViolationException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Les paramètres sont invalides", "VALIDATION");
        detail.setProperty("erreurs", exception.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                violation -> violation.getMessage(),
                (first, ignored) -> first)));
        return detail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail traiterTypeInvalide(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST,
            "Le paramètre '" + exception.getName() + "' a un format invalide", "PARAMETRE_INVALIDE");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail traiterIntegrite(DataIntegrityViolationException exception) {
        LOGGER.warn("Contrainte d'intégrité violée", exception);
        return problem(HttpStatus.CONFLICT,
            "L'opération est impossible : la donnée existe déjà ou est encore référencée", "CONTRAINTE_VIOLEE");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ProblemDetail traiterConcurrence(Exception exception) {
        LOGGER.warn("Conflit d'accès concurrent", exception);
        return problem(HttpStatus.CONFLICT,
            "Ces données sont en cours de modification par une autre opération : réessayez dans un instant",
            "CONFLIT_CONCURRENCE");
    }

    /** Sans ce handler, un refus de {@code @PreAuthorize} serait avalé par le handler générique et répondu en 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail traiterAccesRefuse(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Vous n'avez pas les droits nécessaires pour cette action", "ACCES_REFUSE");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail traiterImprevu(Exception exception) {
        LOGGER.error("Erreur imprévue [requestId={}]", MDC.get(RequestIdFilter.MDC_KEY), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
            "Une erreur interne est survenue. Communiquez l'identifiant de requête au support.", "ERREUR_INTERNE");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        Map<String, String> erreurs = exception.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                error -> error.getField(),
                error -> error.getDefaultMessage() == null ? "Valeur invalide" : error.getDefaultMessage(),
                (first, ignored) -> first));
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Les données envoyées sont invalides", "VALIDATION");
        detail.setProperty("erreurs", erreurs);
        return ResponseEntity.badRequest().body(detail);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST,
            "Le corps de la requête est absent ou mal formé", "CORPS_INVALIDE"));
    }

    /** Envoi d'un fichier sans la partie attendue (le formulaire n'a pas joint de fichier). */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
        MissingServletRequestPartException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST,
            "Aucun fichier n'a été envoyé (partie « " + exception.getRequestPartName() + " » absente)", "FICHIER_ABSENT"));
    }

    /**
     * Erreurs MVC standard (404, 405, 413, 415, paramètre manquant…) : mêmes champs et textes en français. Le corps y est
     * construit par Spring juste avant cette méthode, d'où le point d'accroche {@code createResponseEntity}.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
        Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request
    ) {
        if (body instanceof ProblemDetail detail) {
            detail.setType(URI.create("about:blank"));
            detail.setDetail(switch (statusCode.value()) {
                case 404 -> "Ressource introuvable";
                case 405 -> "Méthode non autorisée sur cette route";
                case 413 -> "Le fichier envoyé est trop volumineux";
                case 415 -> "Type de contenu non supporté";
                default -> detail.getDetail() == null ? "Requête invalide" : detail.getDetail();
            });
            detail.setProperty("code", switch (statusCode.value()) {
                case 404 -> "RESSOURCE_INTROUVABLE";
                case 405 -> "METHODE_NON_AUTORISEE";
                case 413 -> "FICHIER_TROP_VOLUMINEUX";
                case 415 -> "TYPE_NON_SUPPORTE";
                default -> "REQUETE_INVALIDE";
            });
            detail.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ProblemDetail problem(HttpStatus status, String message, String code) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setType(URI.create("about:blank"));
        detail.setProperty("code", code);
        detail.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return detail;
    }
}
