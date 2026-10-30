package com.gesstock.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.junit.Test;

public class NomsDeFichiersTest {

    @Test
    public void unNomOrdinaireResteTelQuel() {
        String nom = "gesstock-journal-caisse-2026-10-09-2026-10-09.pdf";
        assertEquals(nom, NomsDeFichiers.nomSur(nom));
    }

    @Test
    public void unNomNePeutPasDesignerUnAutreDossier() {
        assertEquals("_.._etc_passwd", NomsDeFichiers.nomSur("../../etc/passwd"));
        assertEquals("a_b_c.pdf", NomsDeFichiers.nomSur("a\\b:c.pdf"));
    }

    @Test
    public void unNomVideDevientGesstock() {
        assertEquals("gesstock", NomsDeFichiers.nomSur(null));
        assertEquals("gesstock", NomsDeFichiers.nomSur(" .. "));
    }

    @Test
    public void unNomTropLongGardeSaFinEtSonExtension() {
        String nom = NomsDeFichiers.nomSur("x".repeat(200) + ".pdf");
        assertEquals(120, nom.length());
        assertTrue(nom.endsWith(".pdf"));
    }

    @Test
    public void unNomDejaPrisRecoitUnNumero() {
        Set<String> pris = Set.of("journal.pdf", "journal (1).pdf");
        assertEquals("journal (2).pdf", NomsDeFichiers.nomLibre("journal.pdf", pris::contains));
        assertEquals("stock.xlsx", NomsDeFichiers.nomLibre("stock.xlsx", pris::contains));
        assertEquals("releve (1)", NomsDeFichiers.nomLibre("releve", Set.of("releve")::contains));
    }
}
