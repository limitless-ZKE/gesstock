package com.gesstock.gesstock.entreprise.service;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * Contrôle et préparation du logo envoyé par un utilisateur. Le fichier reçu n'est JAMAIS conservé ni renvoyé tel quel :
 * il est décodé puis ré-encodé en PNG par le serveur, ce qui supprime métadonnées (GPS, auteur…) et tout contenu actif.
 *
 * <ul>
 *   <li>formats acceptés : PNG et JPEG, reconnus à leur signature et non à l'extension ou au type annoncé ;</li>
 *   <li>SVG refusé (il peut contenir du script), ainsi que GIF et WebP ;</li>
 *   <li>dimensions lues AVANT le décodage : une petite image qui se décompresse en gigaoctets est refusée.</li>
 * </ul>
 */
public final class TraitementLogo {

    /** Côté maximal d'une image envoyée (avant décodage). */
    public static final int COTE_MAX_ENVOYE = 4000;
    /** Surface maximale d'une image envoyée, en pixels (12 millions, soit environ 48 Mo décodés). */
    public static final long SURFACE_MAX_ENVOYEE = 12_000_000L;
    /** Côté maximal du logo conservé : au-delà, il est réduit. */
    public static final int COTE_MAX_STOCKE = 1000;

    private TraitementLogo() {}

    /** Résultat du traitement : le PNG à stocker et ses caractéristiques. */
    public record LogoTraite(byte[] png, int largeur, int hauteur, String empreinte) {}

    public static LogoTraite traiter(byte[] brut, long octetsMax) {
        if (brut == null || brut.length == 0) {
            throw new RequeteInvalideException("Le fichier envoyé est vide");
        }
        if (brut.length > octetsMax) {
            throw new RequeteInvalideException("Le logo dépasse la taille autorisée (" + formaterTaille(octetsMax) + " au plus)");
        }
        if (!estPng(brut) && !estJpeg(brut)) {
            throw new RequeteInvalideException(
                "Format non pris en charge : envoyez une image PNG ou JPEG (le SVG, le GIF et le WebP ne sont pas acceptés)");
        }
        BufferedImage image = decoder(brut);
        BufferedImage normalisee = reduire(versRgbOuArgb(image), COTE_MAX_STOCKE, COTE_MAX_STOCKE);
        byte[] png = encoderPng(normalisee);
        if (png.length > octetsMax) {
            throw new RequeteInvalideException("Le logo est trop lourd une fois converti (" + formaterTaille(png.length)
                + ") : utilisez une image plus simple ou plus petite");
        }
        return new LogoTraite(png, normalisee.getWidth(), normalisee.getHeight(), empreinte(png));
    }

    /**
     * Version du logo tenant dans {@code taille}, proportions conservées. Un logo déjà plus petit est rendu tel quel : on
     * n'agrandit jamais.
     */
    public static byte[] redimensionner(byte[] png, TailleLogo taille) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IllegalStateException("Le logo stocké n'est pas une image lisible");
            }
            BufferedImage reduite = reduire(image, taille.largeurMax(), taille.hauteurMax());
            return reduite == image ? png : encoderPng(reduite);
        } catch (IOException exception) {
            throw new IllegalStateException("Le logo stocké est illisible", exception);
        }
    }

    public static boolean estPng(byte[] octets) {
        return octets.length > 8
            && (octets[0] & 0xFF) == 0x89 && octets[1] == 'P' && octets[2] == 'N' && octets[3] == 'G'
            && octets[4] == 0x0D && octets[5] == 0x0A && octets[6] == 0x1A && octets[7] == 0x0A;
    }

    public static boolean estJpeg(byte[] octets) {
        return octets.length > 3 && (octets[0] & 0xFF) == 0xFF && (octets[1] & 0xFF) == 0xD8 && (octets[2] & 0xFF) == 0xFF;
    }

    // ------------------------------------------------------------------ étapes

    private static BufferedImage decoder(byte[] brut) {
        try (ImageInputStream flux = ImageIO.createImageInputStream(new ByteArrayInputStream(brut))) {
            Iterator<ImageReader> lecteurs = ImageIO.getImageReaders(flux);
            if (!lecteurs.hasNext()) {
                throw illisible();
            }
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(flux, true, true);
                int largeur = lecteur.getWidth(0);
                int hauteur = lecteur.getHeight(0);
                if (largeur <= 0 || hauteur <= 0) {
                    throw illisible();
                }
                if (largeur > COTE_MAX_ENVOYE || hauteur > COTE_MAX_ENVOYE || (long) largeur * hauteur > SURFACE_MAX_ENVOYEE) {
                    throw new RequeteInvalideException("Image trop grande (" + largeur + " × " + hauteur + " px) : "
                        + COTE_MAX_ENVOYE + " px de côté au plus");
                }
                BufferedImage image = lecteur.read(0);
                if (image == null) {
                    throw illisible();
                }
                return image;
            } finally {
                lecteur.dispose();
            }
        } catch (RequeteInvalideException demande) {
            throw demande;
        } catch (IIOException exception) {
            if (estJpeg(brut)) {
                // typiquement un JPEG en CMJN, que le décodeur standard ne sait pas lire
                throw new RequeteInvalideException("Image illisible : enregistrez ce JPEG en couleurs RVB (pas CMJN) "
                    + "ou exportez-le en PNG");
            }
            throw illisible();
        } catch (IOException | RuntimeException exception) {
            throw illisible();
        }
    }

    private static RequeteInvalideException illisible() {
        return new RequeteInvalideException("Image illisible ou corrompue : envoyez un PNG ou un JPEG valide");
    }

    /** Ramène toute image (palette, niveaux de gris…) à un format simple, en gardant la transparence si elle existe. */
    private static BufferedImage versRgbOuArgb(BufferedImage source) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (source.getType() == type) {
            return source;
        }
        BufferedImage copie = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D crayon = copie.createGraphics();
        try {
            crayon.drawImage(source, 0, 0, null);
        } finally {
            crayon.dispose();
        }
        return copie;
    }

    /**
     * Réduit pour tenir dans la boîte, par moitiés successives (la qualité d'une réduction en un seul pas se dégrade vite
     * au-delà d'un facteur 2). Retourne {@code source} elle-même si elle tient déjà : jamais d'agrandissement.
     */
    static BufferedImage reduire(BufferedImage source, int largeurMax, int hauteurMax) {
        int largeur = source.getWidth();
        int hauteur = source.getHeight();
        double echelle = Math.min(1.0, Math.min((double) largeurMax / largeur, (double) hauteurMax / hauteur));
        if (echelle >= 1.0) {
            return source;
        }
        int cibleLargeur = Math.max(1, (int) Math.round(largeur * echelle));
        int cibleHauteur = Math.max(1, (int) Math.round(hauteur * echelle));
        BufferedImage courante = source;
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        while (courante.getWidth() != cibleLargeur || courante.getHeight() != cibleHauteur) {
            int suivantLargeur = Math.max(cibleLargeur, courante.getWidth() / 2);
            int suivantHauteur = Math.max(cibleHauteur, courante.getHeight() / 2);
            BufferedImage suivante = new BufferedImage(suivantLargeur, suivantHauteur, type);
            Graphics2D crayon = suivante.createGraphics();
            try {
                crayon.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                crayon.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                crayon.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                crayon.drawImage(courante, 0, 0, suivantLargeur, suivantHauteur, null);
            } finally {
                crayon.dispose();
            }
            courante = suivante;
        }
        return courante;
    }

    private static byte[] encoderPng(BufferedImage image) {
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", sortie)) {
                throw new IllegalStateException("Aucun encodeur PNG disponible");
            }
            return sortie.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Encodage PNG impossible", exception);
        }
    }

    private static String empreinte(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String formaterTaille(long octets) {
        return octets >= 1_048_576 ? String.format("%.1f Mo", octets / 1_048_576.0) : (octets / 1024) + " Ko";
    }
}
