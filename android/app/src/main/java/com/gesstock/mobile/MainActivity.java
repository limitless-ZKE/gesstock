package com.gesstock.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/**
 * L'écran de l'application : GesStock tel que le sert le serveur du magasin, dans une vue web plein écran. L'application
 * n'embarque aucune page : une mise à jour de GesStock sur le serveur arrive sur le téléphone sans réinstaller l'application.
 * Elle ajoute ce qu'une vue web ne fait pas seule : imprimer, enregistrer un PDF ou un Excel, choisir un fichier (le logo de
 * l'entreprise), des fenêtres de confirmation au nom de GesStock, et un écran clair quand le serveur ne répond pas.
 */
public class MainActivity extends Activity {

    /** Raccourci « Serveur » de l'icône (appui long) : ouvre le réglage de l'adresse. */
    static final String ACTION_CHANGER_ADRESSE = "com.gesstock.mobile.CHANGER_ADRESSE";

    private static final int DEMANDE_FICHIER = 1;
    private static final int DEMANDE_STOCKAGE = 2;

    private String adresse;
    private WebView vue;
    private ProgressBar chargement;
    private View ecranErreur;
    private TextView messageErreur;
    /** Vrai entre l'échec du chargement d'une page et le début du chargement suivant. */
    private boolean pageEnErreur;
    private ValueCallback<Uri[]> choixFichier;
    /** Android 8 et 9 : le fichier attend la permission d'écrire dans Téléchargements. */
    private FichierEnAttente fichierEnAttente;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        adresse = Reglages.adresse(this);
        if (adresse == null) {
            // Premier lancement : rien à afficher tant que l'adresse du serveur n'est pas connue.
            startActivity(new Intent(this, AdresseActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        Bords.appliquer(this, findViewById(R.id.racine));
        vue = findViewById(R.id.vue);
        chargement = findViewById(R.id.chargement);
        ecranErreur = findViewById(R.id.erreur);
        messageErreur = findViewById(R.id.erreur_message);
        findViewById(R.id.reessayer).setOnClickListener(bouton -> recharger());
        findViewById(R.id.changer_adresse).setOnClickListener(bouton -> changerAdresse());
        configurer();
        vue.loadUrl(adresse);
        if (ACTION_CHANGER_ADRESSE.equals(getIntent().getAction())) {
            changerAdresse();
        }
    }

    @Override
    protected void onNewIntent(Intent intention) {
        super.onNewIntent(intention);
        if (ACTION_CHANGER_ADRESSE.equals(intention.getAction())) {
            changerAdresse();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")   // l'interface de GesStock est une application JavaScript
    private void configurer() {
        WebSettings reglages = vue.getSettings();
        reglages.setJavaScriptEnabled(true);
        reglages.setDomStorageEnabled(true);
        reglages.setAllowFileAccess(false);
        reglages.setUserAgentString(reglages.getUserAgentString() + " GesStockAndroid/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        vue.addJavascriptInterface(new PontAndroid(this), "GesStockAndroid");
        vue.setWebViewClient(new Navigation());
        vue.setWebChromeClient(new Fenetres());
    }

    /** Appelé par la page (bouton « Imprimer ») : la fenêtre d'impression d'Android, sur la page telle qu'elle s'imprime. */
    void imprimer(String titre) {
        if (!pageDuServeur()) {
            return;
        }
        String nom = titre == null || titre.trim().isEmpty() ? getString(R.string.app_name) : titre.trim();
        PrintManager impression = (PrintManager) getSystemService(PRINT_SERVICE);
        impression.print(nom, vue.createPrintDocumentAdapter(nom), new PrintAttributes.Builder().build());
    }

    /** Appelé par la page (exports PDF et Excel) : le fichier va dans Téléchargements/GesStock, puis s'ouvre. */
    void enregistrerFichier(String nom, String type, byte[] contenu) {
        if (!pageDuServeur()) {
            return;
        }
        String nomSur = NomsDeFichiers.nomSur(nom);
        String typeSur = type == null || type.trim().isEmpty() ? "application/octet-stream" : type.trim();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            fichierEnAttente = new FichierEnAttente(nomSur, typeSur, contenu);
            requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, DEMANDE_STOCKAGE);
            return;
        }
        enregistrerEtOuvrir(nomSur, typeSur, contenu);
    }

    @Override
    public void onRequestPermissionsResult(int demande, String[] permissions, int[] reponses) {
        if (demande != DEMANDE_STOCKAGE) {
            super.onRequestPermissionsResult(demande, permissions, reponses);
            return;
        }
        FichierEnAttente fichier = fichierEnAttente;
        fichierEnAttente = null;
        if (fichier == null) {
            return;
        }
        if (reponses.length > 0 && reponses[0] == PackageManager.PERMISSION_GRANTED) {
            enregistrerEtOuvrir(fichier.nom, fichier.type, fichier.contenu);
        } else {
            Toast.makeText(this, R.string.stockage_refuse, Toast.LENGTH_LONG).show();
        }
    }

    private void enregistrerEtOuvrir(String nom, String type, byte[] contenu) {
        Uri fichier;
        try {
            fichier = Fichiers.enregistrer(this, nom, type, contenu);
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, getString(R.string.fichier_non_enregistre, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, getString(R.string.fichier_enregistre, nom), Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(fichier, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.fichier_sans_application, Toast.LENGTH_LONG).show();
        }
    }

    /** Le pont ne sert qu'aux pages du serveur GesStock : la vue n'en affiche pas d'autres, mais on le vérifie. */
    private boolean pageDuServeur() {
        String url = vue.getUrl();
        return url != null && AdresseServeur.memeServeur(url, adresse);
    }

    private void recharger() {
        ecranErreur.setVisibility(View.GONE);
        String url = vue.getUrl();
        vue.loadUrl(url != null && AdresseServeur.memeServeur(url, adresse) ? url : adresse);
    }

    private void changerAdresse() {
        startActivity(new Intent(this, AdresseActivity.class));
    }

    @Override
    protected void onActivityResult(int demande, int resultat, Intent donnees) {
        if (demande == DEMANDE_FICHIER && choixFichier != null) {
            choixFichier.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultat, donnees));
            choixFichier = null;
            return;
        }
        super.onActivityResult(demande, resultat, donnees);
    }

    @Override
    @SuppressWarnings("deprecation")   // toujours appelé pour une application qui cible Android 15 (targetSdk 35)
    public void onBackPressed() {
        if (vue != null && ecranErreur.getVisibility() != View.VISIBLE && vue.canGoBack()) {
            vue.goBack();
        } else {
            // Comme le bouton d'accueil : l'application reste ouverte, avec la session et la vente en cours.
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onPause() {
        // La session (cookie) survit ainsi à un arrêt de l'application par le téléphone.
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (vue != null) {
            vue.destroy();
        }
        super.onDestroy();
    }

    /** La vue ne quitte jamais le serveur GesStock : un lien vers un autre site s'ouvre dans le navigateur du téléphone. */
    private final class Navigation extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView vue, WebResourceRequest demande) {
            Uri cible = demande.getUrl();
            if (AdresseServeur.memeServeur(cible.toString(), adresse)) {
                return false;
            }
            String schema = cible.getScheme() == null ? "" : cible.getScheme();
            if (schema.equals("http") || schema.equals("https") || schema.equals("tel") || schema.equals("mailto")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, cible));
                } catch (ActivityNotFoundException e) {
                    // Aucune application pour ce lien : on reste sur la page.
                }
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView vue, String url, Bitmap icone) {
            pageEnErreur = false;
        }

        @Override
        public void onPageFinished(WebView vue, String url) {
            if (!pageEnErreur) {
                ecranErreur.setVisibility(View.GONE);
            }
        }

        @Override
        public void onReceivedError(WebView vue, WebResourceRequest demande, WebResourceError erreur) {
            if (demande.isForMainFrame()) {
                pageEnErreur = true;
                messageErreur.setText(getString(R.string.erreur_message, AdresseServeur.pourSaisie(adresse), erreur.getDescription()));
                ecranErreur.setVisibility(View.VISIBLE);
            }
        }
    }

    /** Barre de progression, choix d'un fichier, et fenêtres alert() / confirm() de la page au nom de GesStock. */
    private final class Fenetres extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView vue, int progression) {
            chargement.setProgress(progression);
            chargement.setVisibility(progression < 100 ? View.VISIBLE : View.GONE);
        }

        @Override
        public boolean onShowFileChooser(WebView vue, ValueCallback<Uri[]> rappel, FileChooserParams parametres) {
            if (choixFichier != null) {
                choixFichier.onReceiveValue(null);
            }
            choixFichier = rappel;
            try {
                startActivityForResult(parametres.createIntent(), DEMANDE_FICHIER);
                return true;
            } catch (ActivityNotFoundException e) {
                choixFichier = null;
                Toast.makeText(MainActivity.this, R.string.aucun_choix_de_fichier, Toast.LENGTH_LONG).show();
                return false;
            }
        }

        @Override
        public boolean onJsAlert(WebView vue, String url, String message, JsResult resultat) {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (fenetre, bouton) -> resultat.confirm())
                .setOnCancelListener(fenetre -> resultat.cancel())
                .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView vue, String url, String message, JsResult resultat) {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (fenetre, bouton) -> resultat.confirm())
                .setNegativeButton(R.string.annuler, (fenetre, bouton) -> resultat.cancel())
                .setOnCancelListener(fenetre -> resultat.cancel())
                .show();
            return true;
        }
    }

    private static final class FichierEnAttente {
        final String nom;
        final String type;
        final byte[] contenu;

        FichierEnAttente(String nom, String type, byte[] contenu) {
            this.nom = nom;
            this.type = type;
            this.contenu = contenu;
        }
    }
}
