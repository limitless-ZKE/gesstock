package com.gesstock.gesstock.interfaceweb;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.Arrays;

/**
 * Sert l'interface depuis le programme lui-même. Inactif tant que {@code static/index.html} n'existe pas.
 */
@Configuration
@ConditionalOnResource(resources = InterfaceWeb.CONDITION_INDEX)
public class InterfaceWebConfig implements WebMvcConfigurer {

    private final WebProperties.Resources ressources;

    public InterfaceWebConfig(WebProperties webProperties) {
        this.ressources = webProperties.getResources();
    }

    /** Chaque page de l'interface renvoie la page d'entrée : le routeur du navigateur prend ensuite le relais. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String page : InterfaceWeb.PAGES) {
            registry.addViewController(page).setViewName("forward:/index.html");
        }
    }

    /**
     * Les fichiers de {@code /assets} portent une empreinte dans leur nom (index-AbC123.js) : un fichier modifié change de nom,
     * donc le navigateur peut les garder un an. La page d'entrée, elle, n'est jamais mise en cache (valeur par défaut de la
     * sécurité) : c'est elle qui désigne les fichiers à jour.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String[] emplacements = Arrays.stream(ressources.getStaticLocations())
            .map(emplacement -> emplacement.endsWith("/") ? emplacement + "assets/" : emplacement + "/assets/")
            .toArray(String[]::new);
        registry.addResourceHandler("/assets/**")
            .addResourceLocations(emplacements)
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
