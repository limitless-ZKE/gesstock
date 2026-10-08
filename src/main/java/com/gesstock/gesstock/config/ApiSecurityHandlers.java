package com.gesstock.gesstock.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import com.gesstock.gesstock.security.LimiteurConnexions;

import java.io.IOException;

/**
 * Réponses de la couche sécurité au même format que le reste de l'API (RFC 7807, {@code application/problem+json}),
 * avec un {@code code} stable que le front peut tester. Les textes sont fixes : aucune donnée de la requête n'y entre.
 */
@Component
public class ApiSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final LimiteurConnexions limiteur;

    public ApiSecurityHandlers(LimiteurConnexions limiteur) {
        this.limiteur = limiteur;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        ecrire(response, HttpStatus.UNAUTHORIZED, "Authentification requise", "NON_AUTHENTIFIE");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        if (exception instanceof CsrfException) {
            ecrire(response, HttpStatus.FORBIDDEN,
                "Jeton de sécurité absent ou expiré : rechargez la page puis réessayez", "CSRF_INVALIDE");
            return;
        }
        ecrire(response, HttpStatus.FORBIDDEN, "Vous n'avez pas les droits nécessaires pour cette action", "ACCES_REFUSE");
    }

    /** Connexion réussie : pas de redirection, le front relit /api/auth/me. */
    public void loginReussi(HttpServletRequest request, HttpServletResponse response,
                            Authentication authentication) {
        limiteur.reinitialiser(LimiteurConnexions.cle(request.getParameter("username"), request.getRemoteAddr()));
        // Spring Security renouvelle le jeton CSRF à la connexion mais ne l'écrit qu'à la réponse SUIVANTE : on le charge
        // ici pour que cette réponse porte déjà le nouveau cookie XSRF-TOKEN. Sans cela, la première écriture après la
        // connexion part avec un jeton supprimé et reçoit un 403 (le front y échappait en relisant /api/auth/me).
        CsrfToken jeton = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (jeton != null) {
            jeton.getToken();
        }
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    /** Même message pour un mot de passe faux, un compte inconnu ou désactivé : on ne révèle pas lequel. */
    public void loginEchoue(HttpServletRequest request, HttpServletResponse response,
                            AuthenticationException exception) throws IOException {
        limiteur.enregistrerEchec(LimiteurConnexions.cle(request.getParameter("username"), request.getRemoteAddr()));
        ecrire(response, HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect", "IDENTIFIANTS_INVALIDES");
    }

    private void ecrire(HttpServletResponse response, HttpStatus status, String detail, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
            + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }
}
