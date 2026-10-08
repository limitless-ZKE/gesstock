package com.gesstock.gesstock.entreprise;

import com.gesstock.gesstock.common.exception.RequeteInvalideException;
import com.gesstock.gesstock.entities.JournalActivite;
import com.gesstock.gesstock.entreprise.dto.EntrepriseRequest;
import com.gesstock.gesstock.entreprise.dto.EntrepriseResponse;
import com.gesstock.gesstock.entreprise.service.EntrepriseService;
import com.gesstock.gesstock.entreprise.service.EntrepriseService.LogoServi;
import com.gesstock.gesstock.entreprise.service.TailleLogo;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les informations de l'entreprise et son logo, de l'enregistrement à la lecture au bon format. */
@SpringBootTest
@Transactional
class EntrepriseServiceTests {

    private static final String MANAGER = "admin";

    @Autowired
    private EntrepriseService service;
    @Autowired
    private JournalActiviteRepository journalRepository;

    private EntrepriseRequest demande(String nom) {
        return new EntrepriseRequest(nom, "12 rue du Marché, Dakar", "+221 33 800 00 00", "contact@baobab.example",
            "RCCM SN-DKR-2020-B-1234", "www.baobab.example", "Merci et à bientôt !", true);
    }

    @Test
    void uneEntrepriseExisteToujoursAvecDesValeursDeDepartEtSansLogo() {
        EntrepriseResponse entreprise = service.obtenir();

        assertThat(entreprise.nom()).isNotBlank();
        assertThat(entreprise.logo()).isNull();
        assertThat(entreprise.logoSurTickets()).isTrue();
        assertThat(service.logo(TailleLogo.TICKET)).isEmpty();
    }

    @Test
    void lesInformationsSEnregistrentEtSeRelisentTellesQuelles() {
        EntrepriseResponse enregistree = service.modifier(demande("Baobab SARL"), MANAGER);

        assertThat(enregistree.nom()).isEqualTo("Baobab SARL");
        assertThat(enregistree.adresse()).isEqualTo("12 rue du Marché, Dakar");
        assertThat(enregistree.identifiantLegal()).isEqualTo("RCCM SN-DKR-2020-B-1234");
        assertThat(enregistree.misAJourPar()).isEqualTo(MANAGER);
        assertThat(service.obtenir()).isEqualTo(enregistree);
    }

    @Test
    void lesEspacesSontRetiresEtLesTextesVidesDeviennentAbsents() {
        EntrepriseResponse entreprise = service.modifier(
            new EntrepriseRequest("  Baobab SARL  ", "   ", " +221 77 000 00 00 ", "", null, "  ", null, false), MANAGER);

        assertThat(entreprise.nom()).isEqualTo("Baobab SARL");
        assertThat(entreprise.adresse()).isNull();
        assertThat(entreprise.telephone()).isEqualTo("+221 77 000 00 00");
        assertThat(entreprise.email()).isNull();
        assertThat(entreprise.siteWeb()).isNull();
        assertThat(entreprise.logoSurTickets()).isFalse();
    }

    @Test
    void chaqueModificationLaisseUneTraceDansLeJournal() {
        long avant = journalRepository.count();

        service.modifier(demande("Baobab SARL"), MANAGER);
        service.remplacerLogo(ImagesDeTest.png(200, 80), MANAGER);
        service.supprimerLogo(MANAGER);

        List<JournalActivite> traces = journalRepository.findAll().stream().filter(t -> "ENTREPRISE".equals(t.getEntite())).toList();
        assertThat(journalRepository.count()).isEqualTo(avant + 3);
        assertThat(traces).extracting(JournalActivite::getAction).containsExactlyInAnyOrder("MODIFICATION", "MODIFICATION", "SUPPRESSION");
    }

    @Test
    void unLogoPoseEstServiAuFormatDemandeEtAnnonceSonEmpreinte() {
        EntrepriseResponse avecLogo = service.remplacerLogo(ImagesDeTest.png(1000, 400), MANAGER);

        assertThat(avecLogo.logo().largeur()).isEqualTo(1000);
        assertThat(avecLogo.logo().hauteur()).isEqualTo(400);
        LogoServi ticket = service.logo(TailleLogo.TICKET).orElseThrow();
        LogoServi enTete = service.logo(TailleLogo.ENTETE).orElseThrow();
        assertThat(ticket.empreinte()).isEqualTo(avecLogo.logo().empreinte()).isEqualTo(enTete.empreinte());
        assertThat(ticket.typeMime()).isEqualTo("image/png");
        assertThat(ImagesDeTest.dimensions(ticket.contenu())).containsExactly(320, 128);
        assertThat(ImagesDeTest.dimensions(enTete.contenu())).containsExactly(720, 288);
        assertThat(ImagesDeTest.dimensions(service.logo(TailleLogo.MINIATURE).orElseThrow().contenu())).containsExactly(160, 64);
    }

    @Test
    void remplacerLeLogoChangeSonEmpreinteEtLesFormatsServisSuiventAussitot() {
        EntrepriseResponse premier = service.remplacerLogo(ImagesDeTest.png(800, 300), MANAGER);
        byte[] ticketAvant = service.logo(TailleLogo.TICKET).orElseThrow().contenu();   // le format est maintenant en cache

        EntrepriseResponse second = service.remplacerLogo(ImagesDeTest.png(500, 500), MANAGER);

        assertThat(second.logo().empreinte()).isNotEqualTo(premier.logo().empreinte());
        LogoServi ticketApres = service.logo(TailleLogo.TICKET).orElseThrow();
        assertThat(ticketApres.empreinte()).isEqualTo(second.logo().empreinte());
        assertThat(ticketApres.contenu()).isNotEqualTo(ticketAvant);
        assertThat(ImagesDeTest.dimensions(ticketApres.contenu())).as("le nouveau logo est carré").containsExactly(160, 160);
    }

    @Test
    void retirerLeLogoEffaceFichierEtEmpreinteEtEstSansDangerSiAucunLogo() {
        service.remplacerLogo(ImagesDeTest.png(300, 100), MANAGER);

        assertThat(service.supprimerLogo(MANAGER).logo()).isNull();
        assertThat(service.logo(TailleLogo.ENTETE)).isEmpty();
        assertThat(service.supprimerLogo(MANAGER).logo()).as("déjà retiré : rien ne se passe").isNull();
    }

    @Test
    void unFichierInvalideNeChangeRienAuLogoActuel() {
        EntrepriseResponse actuel = service.remplacerLogo(ImagesDeTest.png(300, 100), MANAGER);

        assertThatThrownBy(() -> service.remplacerLogo("<svg onload=alert(1)/>".getBytes(), MANAGER))
            .isInstanceOf(RequeteInvalideException.class);

        assertThat(service.obtenir().logo()).isEqualTo(actuel.logo());
        assertThat(service.logo(TailleLogo.ORIGINAL)).isPresent();
    }

    @Test
    void modifierLesTextesNeToucheNiAuLogoNiASonEmpreinte() {
        EntrepriseResponse avecLogo = service.remplacerLogo(ImagesDeTest.png(300, 100), MANAGER);

        EntrepriseResponse apres = service.modifier(demande("Nouveau nom"), MANAGER);

        assertThat(apres.logo()).isEqualTo(avecLogo.logo());
        assertThat(service.logo(TailleLogo.TICKET)).isPresent();
    }
}
