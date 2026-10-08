package com.gesstock.gesstock.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/** Répond 429 à une tentative de connexion pendant un blocage, AVANT même de vérifier le mot de passe. */
public class LimiteConnexionsFilter extends OncePerRequestFilter {

    private final LimiteurConnexions limiteur;

    public LimiteConnexionsFilter(LimiteurConnexions limiteur) {
        this.limiteur = limiteur;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // URI moins le contexte : getServletPath() est vide hors conteneur (tests MockMvc) et ne conviendrait pas partout.
        String chemin = request.getRequestURI().substring(request.getContextPath().length());
        return !HttpMethod.POST.matches(request.getMethod()) || !"/api/auth/login".equals(chemin);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Duration restant = limiteur.blocageRestant(LimiteurConnexions.cle(request.getParameter("username"), request.getRemoteAddr()));
        if (restant.isZero()) {
            chain.doFilter(request, response);
            return;
        }
        long minutes = Math.max(1, (restant.toSeconds() + 59) / 60);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(restant.toSeconds()));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
            + "\"detail\":\"Trop de tentatives de connexion. Réessayez dans " + minutes + " minute(s).\","
            + "\"code\":\"TROP_DE_TENTATIVES\"}");
    }
}
