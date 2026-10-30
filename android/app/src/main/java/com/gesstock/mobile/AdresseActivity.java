package com.gesstock.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Réglage de l'adresse du serveur, au premier lancement puis à la demande. L'adresse n'est retenue qu'après avoir vérifié
 * qu'elle mène à GesStock, sauf si l'on insiste (le PC serveur est éteint au moment du réglage, par exemple).
 */
public class AdresseActivity extends Activity {

    private static final int DELAI_VERIFICATION_MS = 6000;

    private EditText champ;
    private Button connecter;
    private Button enregistrerQuandMeme;
    private ProgressBar attente;
    private TextView message;
    /** L'adresse qui vient d'échouer : « Enregistrer quand même » la retient telle quelle. */
    private String adresseEnEchec;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        setContentView(R.layout.activity_adresse);
        Bords.appliquer(this, findViewById(R.id.racine));
        champ = findViewById(R.id.adresse);
        connecter = findViewById(R.id.connecter);
        enregistrerQuandMeme = findViewById(R.id.enregistrer_quand_meme);
        attente = findViewById(R.id.attente);
        message = findViewById(R.id.message);

        String actuelle = Reglages.adresse(this);
        if (actuelle != null) {
            champ.setText(AdresseServeur.pourSaisie(actuelle));
            champ.setSelection(champ.length());
        }
        connecter.setOnClickListener(bouton -> verifier());
        champ.setOnEditorActionListener((vue, action, evenement) -> {
            if (action != EditorInfo.IME_ACTION_GO) {
                return false;
            }
            verifier();
            return true;
        });
        enregistrerQuandMeme.setOnClickListener(bouton -> {
            if (adresseEnEchec != null) {
                retenir(adresseEnEchec);
            }
        });
    }

    private void verifier() {
        String adresse = AdresseServeur.normaliser(champ.getText().toString());
        enregistrerQuandMeme.setVisibility(View.GONE);
        adresseEnEchec = null;
        if (adresse == null) {
            afficher(getString(R.string.adresse_invalide));
            return;
        }
        connecter.setEnabled(false);
        attente.setVisibility(View.VISIBLE);
        afficher(getString(R.string.adresse_verification, AdresseServeur.pourSaisie(adresse)));
        new Thread(() -> {
            VerificationServeur.Resultat resultat = VerificationServeur.verifier(adresse, DELAI_VERIFICATION_MS);
            runOnUiThread(() -> conclure(adresse, resultat));
        }, "gesstock-verification").start();
    }

    private void conclure(String adresse, VerificationServeur.Resultat resultat) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        connecter.setEnabled(true);
        attente.setVisibility(View.GONE);
        if (resultat == VerificationServeur.Resultat.GESSTOCK) {
            retenir(adresse);
            return;
        }
        int texte = resultat == VerificationServeur.Resultat.AUTRE_SERVEUR ? R.string.adresse_autre_serveur : R.string.adresse_injoignable;
        afficher(getString(texte, AdresseServeur.pourSaisie(adresse)));
        adresseEnEchec = adresse;
        enregistrerQuandMeme.setVisibility(View.VISIBLE);
    }

    private void afficher(String texte) {
        message.setText(texte);
        message.setVisibility(View.VISIBLE);
    }

    /** Retient l'adresse et rouvre GesStock dessus, à la place des écrans précédents. */
    private void retenir(String adresse) {
        Reglages.enregistrerAdresse(this, adresse);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
