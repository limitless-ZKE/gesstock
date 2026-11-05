package com.gesstock.mobile;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Test;

/** Un petit serveur web local joue le PC serveur : GesStock, un autre serveur, ou personne. */
public class VerificationServeurTest {

    private ServerSocket serveur;

    @After
    public void arreter() throws IOException {
        if (serveur != null) {
            serveur.close();
        }
    }

    /** Répond {@code code} et {@code page} à chaque requête (les tests Android n'ont pas le serveur web du JDK). */
    private String serveurQuiRepond(int code, String page) throws IOException {
        serveur = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        ServerSocket ecoute = serveur;
        Thread fil = new Thread(() -> {
            while (!ecoute.isClosed()) {
                try (Socket client = ecoute.accept()) {
                    BufferedReader requete = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                    String ligne = requete.readLine();
                    while (ligne != null && !ligne.isEmpty()) {
                        ligne = requete.readLine();
                    }
                    byte[] corps = page.getBytes(StandardCharsets.UTF_8);
                    String entete = "HTTP/1.1 " + code + " Essai\r\n"
                        + "Content-Type: text/html; charset=utf-8\r\n"
                        + "Content-Length: " + corps.length + "\r\n"
                        + "Connection: close\r\n\r\n";
                    OutputStream sortie = client.getOutputStream();
                    sortie.write(entete.getBytes(StandardCharsets.US_ASCII));
                    sortie.write(corps);
                    sortie.flush();
                } catch (IOException e) {
                    return;
                }
            }
        }, "serveur-essai");
        fil.setDaemon(true);
        fil.start();
        return "http://127.0.0.1:" + serveur.getLocalPort() + "/";
    }

    @Test
    public void reconnaitLaPageDAccueilDeGesStock() throws IOException {
        String adresse = serveurQuiRepond(200, "<!doctype html><html lang=\"fr\"><head><title>GesStock 3.0</title></head></html>");
        assertEquals(VerificationServeur.Resultat.GESSTOCK, VerificationServeur.verifier(adresse, 5000));
    }

    @Test
    public void unAutreServeurNEstPasGesStock() throws IOException {
        String adresse = serveurQuiRepond(200, "<html><title>WampServer</title></html>");
        assertEquals(VerificationServeur.Resultat.AUTRE_SERVEUR, VerificationServeur.verifier(adresse, 5000));
    }

    @Test
    public void unePageIntrouvableNEstPasGesStock() throws IOException {
        String adresse = serveurQuiRepond(404, "GesStock : page introuvable");
        assertEquals(VerificationServeur.Resultat.AUTRE_SERVEUR, VerificationServeur.verifier(adresse, 5000));
    }

    @Test
    public void personneNeRepond() throws IOException {
        // Un serveur qui accepte la connexion mais ne répond jamais : le délai est dépassé, l'adresse est « injoignable ».
        // Fermer un port puis s'y connecter serait une course contre le système, qui peut le réattribuer à autre chose entre-temps
        // (échec isolé constaté en CI le 2026-11-05) : ici le port reste occupé par ce test jusqu'à la fin.
        serveur = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        String adresse = "http://127.0.0.1:" + serveur.getLocalPort() + "/";
        assertEquals(VerificationServeur.Resultat.INJOIGNABLE, VerificationServeur.verifier(adresse, 1000));
    }
}
