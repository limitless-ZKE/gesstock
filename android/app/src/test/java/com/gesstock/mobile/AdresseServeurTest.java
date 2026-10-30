package com.gesstock.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdresseServeurTest {

    @Test
    public void uneAdresseSeuleRecoitLePortDeGesStock() {
        assertEquals("http://192.168.1.75:8080/", AdresseServeur.normaliser("192.168.1.75"));
    }

    @Test
    public void lePortTapeEstGarde() {
        assertEquals("http://192.168.1.75:9090/", AdresseServeur.normaliser("192.168.1.75:9090"));
    }

    @Test
    public void uneAdresseCopieeDuNavigateurEstRameneeALaRacine() {
        assertEquals("http://192.168.1.75:8080/", AdresseServeur.normaliser("  http://192.168.1.75:8080/caisse?jour=1 "));
    }

    @Test
    public void lesVirgulesEtLesEspacesTapesParErreurSontCorriges() {
        assertEquals("http://192.168.1.75:8080/", AdresseServeur.normaliser("192,168,1,75 : 8080"));
    }

    @Test
    public void unNomDeServeurEtHttpsSontAcceptes() {
        assertEquals("http://serveur-caisse:8080/", AdresseServeur.normaliser("Serveur-Caisse"));
        assertEquals("https://gesstock.exemple.com/", AdresseServeur.normaliser("https://gesstock.exemple.com"));
    }

    @Test
    public void ceQuiNEstPasUneAdresseDeServeurEstRefuse() {
        assertNull(AdresseServeur.normaliser(null));
        assertNull(AdresseServeur.normaliser("   "));
        assertNull(AdresseServeur.normaliser("ftp://192.168.1.75"));
        assertNull(AdresseServeur.normaliser("http://"));
        assertNull(AdresseServeur.normaliser("192.168.1.75:99999"));
        assertNull(AdresseServeur.normaliser("serveur_caisse"));
    }

    @Test
    public void laVueResteSurLeServeurDeGesStock() {
        String base = "http://192.168.1.75:8080/";
        assertTrue(AdresseServeur.memeServeur("http://192.168.1.75:8080/caisse", base));
        assertTrue(AdresseServeur.memeServeur("HTTP://192.168.1.75:8080/api/auth/me", base));
        assertFalse(AdresseServeur.memeServeur("http://192.168.1.75/", base));
        assertFalse(AdresseServeur.memeServeur("https://192.168.1.75:8080/", base));
        assertFalse(AdresseServeur.memeServeur("https://www.exemple.com/", base));
        assertFalse(AdresseServeur.memeServeur("pas une adresse", base));
        assertTrue(AdresseServeur.memeServeur("https://gesstock.exemple.com:443/caisse", "https://gesstock.exemple.com/"));
    }

    @Test
    public void lAdresseSAfficheCommeOnLaTape() {
        assertEquals("192.168.1.75:8080", AdresseServeur.pourSaisie("http://192.168.1.75:8080/"));
        assertEquals("https://gesstock.exemple.com", AdresseServeur.pourSaisie("https://gesstock.exemple.com/"));
    }
}
