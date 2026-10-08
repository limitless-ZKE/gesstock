package com.gesstock.gesstock.interfaceweb;

/**
 * L'interface (frontend) embarquée dans le programme : une seule adresse pour tous les postes du réseau.
 *
 * <p>Elle n'existe que si l'application a été construite avec le profil Maven « interface » (le dossier {@code frontend/dist}
 * est alors copié dans {@code static/}). Sans elle — développement, tests — rien de ce qui touche à l'interface n'est actif
 * et tout ce qui n'est pas l'API reste refusé, comme avant.
 */
public final class InterfaceWeb {

    /**
     * Condition d'activation : le fichier d'entrée de l'interface existe. La valeur peut être remplacée par la propriété
     * {@code app.interface.index} (utilisé par les tests pour pointer vers un dossier de fichiers factices).
     */
    public static final String CONDITION_INDEX = "${app.interface.index:classpath:/static/index.html}";

    /**
     * Les pages de l'interface. Un rechargement (F5) ou un lien direct sur l'une d'elles doit ouvrir l'application, dont le
     * routeur affiche ensuite le bon écran. Liste VOLONTAIREMENT explicite plutôt qu'un « tout sauf /api » : un chemin
     * inconnu reste refusé. Un test compare cette liste aux routes de {@code frontend/src/App.tsx}.
     */
    public static final String[] PAGES = {
        "/", "/login", "/dashboard", "/caisse", "/caisse/mouvements", "/historique-ventes", "/articles", "/inventaires",
        "/approvisionnements", "/fournisseurs", "/retours-fournisseurs", "/clients-dettes", "/statistiques", "/entreprise",
        "/admin/utilisateurs",
    };

    /** Fichiers produits par la construction de l'interface (Vite) : page d'entrée, icône et fichiers à empreinte. */
    public static final String[] FICHIERS = {"/index.html", "/favicon.svg", "/assets/**"};

    private InterfaceWeb() {}
}
