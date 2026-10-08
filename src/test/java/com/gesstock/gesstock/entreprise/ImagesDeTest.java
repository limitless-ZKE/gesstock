package com.gesstock.gesstock.entreprise;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Images fabriquées pour les tests du logo : vraies images décodables, ou pièges (métadonnées, charge cachée, énormes). */
final class ImagesDeTest {

    private ImagesDeTest() {}

    /** Un logo reconnaissable : fond coloré et un rond clair, pour que la réduction ait quelque chose à dégrader. */
    static BufferedImage dessin(int largeur, int hauteur, boolean transparent) {
        BufferedImage image = new BufferedImage(largeur, hauteur, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D crayon = image.createGraphics();
        try {
            if (!transparent) {
                crayon.setColor(new Color(5, 150, 105));
                crayon.fillRect(0, 0, largeur, hauteur);
            }
            crayon.setColor(new Color(240, 250, 245));
            int diametre = Math.min(largeur, hauteur) * 3 / 4;
            crayon.fillOval((largeur - diametre) / 2, (hauteur - diametre) / 2, diametre, diametre);
        } finally {
            crayon.dispose();
        }
        return image;
    }

    static byte[] png(int largeur, int hauteur) {
        return encoder(dessin(largeur, hauteur, false), "png");
    }

    static byte[] pngTransparent(int largeur, int hauteur) {
        return encoder(dessin(largeur, hauteur, true), "png");
    }

    static byte[] jpeg(int largeur, int hauteur) {
        return encoder(dessin(largeur, hauteur, false), "jpg");
    }

    static byte[] encoder(BufferedImage image, String format) {
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, sortie);
            return sortie.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static int[] dimensions(byte[] image) {
        try {
            BufferedImage lue = ImageIO.read(new ByteArrayInputStream(image));
            return new int[] {lue.getWidth(), lue.getHeight()};
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Un vrai PNG auquel on ajoute un bloc tEXt (métadonnée) juste après l'en-tête IHDR. */
    static byte[] pngAvecMetadonnee(byte[] png, String cle, String valeur) {
        byte[] donnees = (cle + "\0" + valeur).getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer bloc = ByteBuffer.allocate(12 + donnees.length);
        bloc.putInt(donnees.length);
        byte[] type = "tEXt".getBytes(StandardCharsets.US_ASCII);
        bloc.put(type);
        bloc.put(donnees);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(donnees);
        bloc.putInt((int) crc.getValue());
        int finIhdr = 8 + 25;   // signature (8) + bloc IHDR (4 + 4 + 13 + 4)
        ByteBuffer resultat = ByteBuffer.allocate(png.length + bloc.capacity());
        resultat.put(png, 0, finIhdr).put(bloc.array()).put(png, finIhdr, png.length - finIhdr);
        return resultat.array();
    }

    /** Un vrai PNG suivi, après sa fin, de n'importe quel contenu : la preuve qu'un fichier « polyglotte » est neutralisé. */
    static byte[] pngAvecCharge(byte[] png, String charge) {
        byte[] ajout = charge.getBytes(StandardCharsets.UTF_8);
        byte[] resultat = new byte[png.length + ajout.length];
        System.arraycopy(png, 0, resultat, 0, png.length);
        System.arraycopy(ajout, 0, resultat, png.length, ajout.length);
        return resultat;
    }

    /** Vrai si l'expression régulière se trouve dans les octets lus comme du texte (pour inspecter un PDF). */
    static boolean contientMotif(byte[] octets, String expression) {
        return java.util.regex.Pattern.compile(expression).matcher(new String(octets, StandardCharsets.ISO_8859_1)).find();
    }

    static boolean contient(byte[] octets, String texte) {
        return new String(octets, StandardCharsets.ISO_8859_1).contains(texte);
    }
}
