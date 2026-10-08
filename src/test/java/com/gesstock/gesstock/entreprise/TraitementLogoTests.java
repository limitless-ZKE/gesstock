package com.gesstock.gesstock.entreprise;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
import com.gesstock.gesstock.entreprise.service.TraitementLogo;
import com.gesstock.gesstock.entreprise.service.TraitementLogo.LogoTraite;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ce que le serveur accepte, refuse et fabrique à partir d'un fichier envoyé comme logo. Sans Spring : rapide. */
class TraitementLogoTests {

    private static final long DEUX_MO = 2L * 1024 * 1024;

    // --- ce qui est accepté ---

    @Test
    void unPngEstAccepteConserveSesDimensionsEtReçoitUneEmpreinte() {
        LogoTraite logo = TraitementLogo.traiter(ImagesDeTest.png(400, 150), DEUX_MO);

        assertThat(TraitementLogo.estPng(logo.png())).isTrue();
        assertThat(logo.largeur()).isEqualTo(400);
        assertThat(logo.hauteur()).isEqualTo(150);
        assertThat(logo.empreinte()).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void unJpegEstConvertiEnPng() {
        LogoTraite logo = TraitementLogo.traiter(ImagesDeTest.jpeg(300, 120), DEUX_MO);

        assertThat(TraitementLogo.estPng(logo.png())).as("le serveur stocke toujours du PNG").isTrue();
        assertThat(ImagesDeTest.dimensions(logo.png())).containsExactly(300, 120);
    }

    @Test
    void laTransparenceEstConservee() throws Exception {
        LogoTraite logo = TraitementLogo.traiter(ImagesDeTest.pngTransparent(200, 100), DEUX_MO);

        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(logo.png()));
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(image.getRGB(0, 0) >>> 24).as("le coin du logo reste transparent").isZero();
    }

    @Test
    void deuxEnvoisIdentiquesDonnentLaMemeEmpreinteDeuxLogosDifferentsDeuxEmpreintes() {
        LogoTraite premier = TraitementLogo.traiter(ImagesDeTest.png(200, 100), DEUX_MO);
        LogoTraite identique = TraitementLogo.traiter(ImagesDeTest.png(200, 100), DEUX_MO);
        LogoTraite autre = TraitementLogo.traiter(ImagesDeTest.png(210, 100), DEUX_MO);

        assertThat(identique.empreinte()).isEqualTo(premier.empreinte());
        assertThat(autre.empreinte()).isNotEqualTo(premier.empreinte());
    }

    // --- la taille s'adapte à l'endroit où le logo s'affiche ---

    @Test
    void unGrandLogoEstReduitAuMaximumStocke() {
        LogoTraite logo = TraitementLogo.traiter(ImagesDeTest.png(3000, 1500), DEUX_MO);

        assertThat(logo.largeur()).isEqualTo(TraitementLogo.COTE_MAX_STOCKE);
        assertThat(logo.hauteur()).as("proportions 2:1 conservées").isEqualTo(500);
    }

    @Test
    void chaqueEmplacementRecoitUnFormatAdapteAvecLesProportionsDuLogo() {
        byte[] stocke = TraitementLogo.traiter(ImagesDeTest.png(1000, 400), DEUX_MO).png();

        assertThat(ImagesDeTest.dimensions(TraitementLogo.redimensionner(stocke, TailleLogo.MINIATURE))).as("160 × 80 au plus").containsExactly(160, 64);
        assertThat(ImagesDeTest.dimensions(TraitementLogo.redimensionner(stocke, TailleLogo.TICKET))).as("320 × 160 au plus").containsExactly(320, 128);
        assertThat(ImagesDeTest.dimensions(TraitementLogo.redimensionner(stocke, TailleLogo.ENTETE))).as("720 × 300 au plus").containsExactly(720, 288);
        assertThat(ImagesDeTest.dimensions(TraitementLogo.redimensionner(stocke, TailleLogo.ORIGINAL))).containsExactly(1000, 400);
    }

    @Test
    void unLogoHautEstLimiteParSaHauteur() {
        byte[] stocke = TraitementLogo.traiter(ImagesDeTest.png(300, 600), DEUX_MO).png();

        assertThat(ImagesDeTest.dimensions(TraitementLogo.redimensionner(stocke, TailleLogo.TICKET))).containsExactly(80, 160);
    }

    @Test
    void unPetitLogoNEstJamaisAgrandi() {
        byte[] stocke = TraitementLogo.traiter(ImagesDeTest.png(100, 40), DEUX_MO).png();

        for (TailleLogo taille : TailleLogo.values()) {
            assertThat(ImagesDeTest.dimensions(TraitementLogo.redimensionner(stocke, taille))).as(taille.name()).containsExactly(100, 40);
        }
        assertThat(TraitementLogo.redimensionner(stocke, TailleLogo.TICKET)).as("rendu tel quel, sans ré-encodage").isSameAs(stocke);
    }

    @Test
    void lesNomsDeTailleSontReconnusSansTenirCompteDeLaCasseEtDefautSurLEnTete() {
        assertThat(TailleLogo.depuisParametre("Ticket")).isEqualTo(TailleLogo.TICKET);
        assertThat(TailleLogo.depuisParametre(" miniature ")).isEqualTo(TailleLogo.MINIATURE);
        assertThat(TailleLogo.depuisParametre(null)).isEqualTo(TailleLogo.ENTETE);
        assertThat(TailleLogo.depuisParametre("")).isEqualTo(TailleLogo.ENTETE);
        assertThatThrownBy(() -> TailleLogo.depuisParametre("gigantesque")).isInstanceOf(RequeteInvalideException.class);
    }

    // --- ce qui est refusé ---

    @Test
    void unSvgEstRefuseCarIlPeutContenirDuScript() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> TraitementLogo.traiter(svg, DEUX_MO))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("PNG ou JPEG");
    }

    @Test
    void gifEtWebpNeSontPasAcceptes() {
        byte[] gif = "GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1);
        byte[] webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> TraitementLogo.traiter(gif, DEUX_MO)).isInstanceOf(RequeteInvalideException.class);
        assertThatThrownBy(() -> TraitementLogo.traiter(webp, DEUX_MO)).isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void leFormatSeReconnaitALaSignatureNonAuTypeAnnonce() {
        // du texte « habillé » en .png : la signature manque, le contenu est refusé
        byte[] faux = "ceci n'est pas une image".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> TraitementLogo.traiter(faux, DEUX_MO)).isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void unPngCorrompuEstRefuseSansErreurInterne() {
        byte[] valide = ImagesDeTest.png(200, 100);
        byte[] tronque = java.util.Arrays.copyOf(valide, valide.length / 3);

        assertThatThrownBy(() -> TraitementLogo.traiter(tronque, DEUX_MO))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("illisible");
    }

    @Test
    void unFichierVideEstRefuse() {
        assertThatThrownBy(() -> TraitementLogo.traiter(new byte[0], DEUX_MO)).isInstanceOf(RequeteInvalideException.class).hasMessageContaining("vide");
        assertThatThrownBy(() -> TraitementLogo.traiter(null, DEUX_MO)).isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void unFichierTropLourdEstRefuseAvantToutDecodage() {
        byte[] png = ImagesDeTest.png(400, 150);

        assertThatThrownBy(() -> TraitementLogo.traiter(png, png.length - 1L))
            .isInstanceOf(RequeteInvalideException.class).hasMessageContaining("dépasse la taille autorisée");
    }

    @Test
    void uneImageAuxDimensionsExcessivesEstRefuseeSansEtreDecodee() {
        // 4001 px de large : quelques Ko de fichier, mais refusée sur sa seule en-tête
        byte[] large = ImagesDeTest.png(4001, 10);
        // 3500 × 3500 = 12,25 millions de pixels : chaque côté est permis, la surface ne l'est pas
        byte[] enorme = ImagesDeTest.png(3500, 3500);

        assertThatThrownBy(() -> TraitementLogo.traiter(large, DEUX_MO)).isInstanceOf(RequeteInvalideException.class).hasMessageContaining("trop grande");
        assertThatThrownBy(() -> TraitementLogo.traiter(enorme, 50L * 1024 * 1024)).isInstanceOf(RequeteInvalideException.class).hasMessageContaining("trop grande");
    }

    // --- le fichier envoyé n'est jamais conservé tel quel ---

    @Test
    void lesMetadonneesDuFichierEnvoyeSontSupprimees() {
        byte[] piste = ImagesDeTest.pngAvecMetadonnee(ImagesDeTest.png(200, 100), "Author", "SECRET-GPS-48.85,2.35");
        assertThat(ImagesDeTest.contient(piste, "SECRET-GPS")).as("le fichier d'origine porte bien la métadonnée").isTrue();

        LogoTraite logo = TraitementLogo.traiter(piste, DEUX_MO);

        assertThat(ImagesDeTest.contient(logo.png(), "SECRET-GPS")).isFalse();
    }

    @Test
    void uneChargeCacheeApresLaFinDuPngEstNeutralisee() {
        byte[] polyglotte = ImagesDeTest.pngAvecCharge(ImagesDeTest.png(200, 100), "<script>fetch('/api/utilisateurs')</script>");

        LogoTraite logo = TraitementLogo.traiter(polyglotte, DEUX_MO);

        assertThat(ImagesDeTest.contient(logo.png(), "<script>")).as("le PNG réécrit ne contient que l'image").isFalse();
        assertThat(logo.png().length).isLessThan(polyglotte.length);
    }
}
