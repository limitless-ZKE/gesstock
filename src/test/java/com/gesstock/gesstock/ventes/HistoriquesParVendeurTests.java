package com.gesstock.gesstock.ventes;

import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.ReglementRequest;
import com.gesstock.gesstock.clients.dto.ReglementResponse;
import com.gesstock.gesstock.clients.service.ClientCatalogueService;
import com.gesstock.gesstock.clients.service.GestionDetteClientService;
import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.referentiels.dto.OuvertureSessionRequest;
import com.gesstock.gesstock.referentiels.service.SessionCaisseService;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.ReglementClientRepository;
import com.gesstock.gesstock.ventes.dto.LigneDocumentRequest;
import com.gesstock.gesstock.ventes.dto.LigneHistoriqueVente;
import com.gesstock.gesstock.ventes.dto.LigneReglementDette;
import com.gesstock.gesstock.ventes.dto.LigneVenteSoldee;
import com.gesstock.gesstock.ventes.dto.ReglementsDettesResponse;
import com.gesstock.gesstock.ventes.dto.TotalParMode;
import com.gesstock.gesstock.ventes.dto.VenteDirecteRequest;
import com.gesstock.gesstock.ventes.dto.VenteResponse;
import com.gesstock.gesstock.ventes.dto.VentesSoldeesResponse;
import com.gesstock.gesstock.ventes.service.GestionVentesService;
import com.gesstock.gesstock.ventes.service.HistoriqueReglementsService;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesExportService;
import com.gesstock.gesstock.ventes.service.HistoriqueVentesService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Les trois historiques (toutes les ventes, ventes soldées, règlements de dettes), pour tous les vendeurs ou pour un seul. Une vente
 * soldée est au comptant, ou à crédit avec sa dette réglée jusqu'au dernier franc ; elle reste à la date de la vente. Un règlement est
 * rattaché à qui l'a encaissé. Données du DataSeeder : comptes « admin » et « caissier », ELEC-001 (téléphone) à 245 000 et ALIM-001
 * (riz) à 1 300.
 */
@SpringBootTest
@Transactional
class HistoriquesParVendeurTests {

    private static final String ADMIN = "admin";
    private static final String CAISSIER = "caissier";
    private static final DataFormatter FORMAT = new DataFormatter();

    @Autowired
    private HistoriqueVentesService historiqueService;
    @Autowired
    private HistoriqueReglementsService reglementsService;
    @Autowired
    private HistoriqueVentesExportService exportService;
    @Autowired
    private GestionVentesService ventesService;
    @Autowired
    private SessionCaisseService sessionService;
    @Autowired
    private ClientCatalogueService clientService;
    @Autowired
    private GestionDetteClientService detteService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ReglementClientRepository reglementRepository;

    private ClientResponse client;

    @BeforeEach
    void preparer() {
        sessionService.ouvrir(new OuvertureSessionRequest(null, new BigDecimal("1000")), ADMIN);
        client = clientService.creer(new ClientRequest("Boutique Aminata", null, "0100000000", "Dakar"));
    }

    private VenteResponse vendre(String acteur, String reference, int quantite, ModeReglement mode, boolean aCredit) {
        var article = articleRepository.findByReferenceIgnoreCase(reference).orElseThrow();
        BigDecimal recu = mode == ModeReglement.ESPECES ? new BigDecimal("1000000") : article.getPrix().multiply(BigDecimal.valueOf(quantite));
        VenteResponse brouillon = ventesService.creerVenteDirecte(new VenteDirecteRequest(aCredit ? client.id() : null, aCredit,
            List.of(new LigneDocumentRequest(article.getId(), null, BigDecimal.valueOf(quantite), null, null)), BigDecimal.ZERO,
            aCredit ? null : mode, aCredit ? null : recu), acteur);
        return ventesService.validerVente(brouillon.id(), acteur);
    }

    private DetteResponse detteDuClient() {
        return detteService.listerDettesClient(client.id()).get(0);
    }

    private ReglementResponse regler(String acteur, String montant, ModeReglement mode, String reference) {
        return detteService.enregistrerReglement(detteDuClient().id(), new ReglementRequest(new BigDecimal(montant), mode, reference), acteur);
    }

    private static LocalDate aujourdhui() {
        return LocalDate.now();
    }

    @Test
    void lHistoriqueDUnVendeurNeContientQueSesVentes() {
        vendre(CAISSIER, "ELEC-001", 1, ModeReglement.ESPECES, false);
        vendre(ADMIN, "ALIM-001", 2, ModeReglement.ESPECES, false);

        assertThat(historiqueService.historique(aujourdhui(), aujourdhui(), CAISSIER).ventes())
            .extracting(LigneHistoriqueVente::vendeur).containsExactly(CAISSIER);
        assertThat(historiqueService.historique(aujourdhui(), aujourdhui(), "CAISSIER").ventes())
            .as("le nom du compte s'écrit sans souci des majuscules").hasSize(1);
        assertThat(historiqueService.historique(aujourdhui(), aujourdhui(), CAISSIER).resume().chiffreAffaires()).isEqualByComparingTo("245000");
        assertThat(historiqueService.historique(aujourdhui(), aujourdhui(), null).ventes()).hasSize(2);
        assertThat(historiqueService.historique(aujourdhui(), aujourdhui(), "personne").ventes()).isEmpty();
    }

    @Test
    void uneVenteACreditNEstSoldeeQueQuandSaDetteEstEntierementReglee() {
        vendre(CAISSIER, "ELEC-001", 1, ModeReglement.ESPECES, false);                // 245 000 au comptant
        VenteResponse credit = vendre(CAISSIER, "ALIM-001", 10, null, true);          // 13 000 à crédit

        assertThat(historiqueService.ventesSoldees(aujourdhui(), aujourdhui(), null).ventes())
            .as("le crédit n'est pas encore payé").extracting(LigneVenteSoldee::aCredit).containsExactly(false);

        regler(CAISSIER, "4000", ModeReglement.ESPECES, "R1");
        assertThat(historiqueService.ventesSoldees(aujourdhui(), aujourdhui(), null).ventes())
            .as("payé en partie seulement").hasSize(1);

        ReglementResponse dernier = regler(ADMIN, "9000", ModeReglement.MOBILE_MONEY, "R2");
        VentesSoldeesResponse soldees = historiqueService.ventesSoldees(aujourdhui(), aujourdhui(), null);

        assertThat(soldees.ventes()).hasSize(2);
        LigneVenteSoldee creditSolde = soldees.ventes().stream().filter(LigneVenteSoldee::aCredit).findFirst().orElseThrow();
        assertThat(creditSolde.code()).isEqualTo(credit.code());
        assertThat(creditSolde.modeReglement()).isNull();
        assertThat(creditSolde.soldeeLe()).as("date du dernier règlement").isEqualTo(reglementRepository.findById(dernier.id()).orElseThrow().getDateReglement());
        assertThat(creditSolde.client()).isEqualTo("Boutique Aminata");
        assertThat(soldees.resume().nombre()).isEqualTo(2);
        assertThat(soldees.resume().montant()).isEqualByComparingTo("258000");
        assertThat(soldees.resume().nombreComptant()).isEqualTo(1);
        assertThat(soldees.resume().montantComptant()).isEqualByComparingTo("245000");
        assertThat(soldees.resume().nombreCreditsSoldes()).isEqualTo(1);
        assertThat(soldees.resume().montantCreditsSoldes()).isEqualByComparingTo("13000");
    }

    @Test
    void lesVentesSoldeesSeFiltrentParVendeurEtLesVentesAnnuleesNYSontPas() {
        vendre(CAISSIER, "ELEC-001", 1, ModeReglement.CARTE, false);
        vendre(ADMIN, "ALIM-001", 2, ModeReglement.ESPECES, false);
        VenteResponse annulee = vendre(CAISSIER, "ALIM-001", 1, ModeReglement.ESPECES, false);
        ventesService.annulerVente(annulee.id(), "Erreur de saisie", true, ADMIN);

        VentesSoldeesResponse duCaissier = historiqueService.ventesSoldees(aujourdhui(), aujourdhui(), CAISSIER);

        assertThat(duCaissier.ventes()).extracting(LigneVenteSoldee::vendeur, LigneVenteSoldee::modeReglement)
            .containsExactly(tuple(CAISSIER, ModeReglement.CARTE));
        assertThat(duCaissier.ventes().get(0).soldeeLe()).as("payée au comptant : soldée à la vente").isEqualTo(duCaissier.ventes().get(0).dateVente());
        assertThat(historiqueService.ventesSoldees(aujourdhui(), aujourdhui(), null).resume().nombre()).isEqualTo(2);
    }

    @Test
    void chaqueReglementDonneQuiLAEncaisseEtCeQuiRestaitDuJusteApres() {
        VenteResponse credit = vendre(ADMIN, "ALIM-001", 10, null, true);             // 13 000 à crédit
        regler(CAISSIER, "4000", ModeReglement.ESPECES, "R1");
        regler(ADMIN, "9000", ModeReglement.MOBILE_MONEY, "R2");

        ReglementsDettesResponse tous = reglementsService.historique(aujourdhui(), aujourdhui(), null);

        assertThat(tous.reglements()).extracting(LigneReglementDette::reference, LigneReglementDette::recuPar)
            .as("les plus récents d'abord").containsExactly(tuple("R2", ADMIN), tuple("R1", CAISSIER));
        assertThat(tous.reglements().get(0).resteApres()).isEqualByComparingTo("0");
        assertThat(tous.reglements().get(1).resteApres()).isEqualByComparingTo("9000");
        assertThat(tous.reglements()).allSatisfy(ligne -> {
            assertThat(ligne.client()).isEqualTo("Boutique Aminata");
            assertThat(ligne.vente()).isEqualTo(credit.code());
            assertThat(ligne.detteAnnulee()).isFalse();
        });
        assertThat(tous.resume().nombre()).isEqualTo(2);
        assertThat(tous.resume().total()).isEqualByComparingTo("13000");
        assertThat(tous.resume().dettesSoldees()).isEqualTo(1);
        assertThat(tous.resume().parMode()).extracting(TotalParMode::mode, mode -> mode.montant().intValue())
            .containsExactly(tuple(ModeReglement.ESPECES, 4000), tuple(ModeReglement.MOBILE_MONEY, 9000));

        ReglementsDettesResponse duCaissier = reglementsService.historique(aujourdhui(), aujourdhui(), CAISSIER);
        assertThat(duCaissier.reglements()).extracting(LigneReglementDette::reference).containsExactly("R1");
        assertThat(duCaissier.reglements().get(0).resteApres()).isEqualByComparingTo("9000");
        assertThat(duCaissier.resume().total()).isEqualByComparingTo("4000");
        assertThat(duCaissier.resume().dettesSoldees()).isZero();
    }

    @Test
    void leResteDuCompteAussiLesReglementsFaitsAvantLaPeriode() {
        vendre(ADMIN, "ALIM-001", 10, null, true);                                    // 13 000 à crédit
        ReglementResponse ancien = regler(CAISSIER, "5000", ModeReglement.ESPECES, "AVANT");
        var reglement = reglementRepository.findById(ancien.id()).orElseThrow();
        reglement.setDateReglement(reglement.getDateReglement().minusDays(3));
        reglementRepository.saveAndFlush(reglement);
        regler(CAISSIER, "2000", ModeReglement.ESPECES, "AUJOURDHUI");

        ReglementsDettesResponse duJour = reglementsService.historique(aujourdhui(), aujourdhui(), null);

        assertThat(duJour.reglements()).extracting(LigneReglementDette::reference).containsExactly("AUJOURDHUI");
        assertThat(duJour.reglements().get(0).resteApres()).as("13 000 − 5 000 − 2 000").isEqualByComparingTo("6000");
    }

    @Test
    void unReglementDUneVenteACreditAnnuleeEstSignaleSansResteDu() {
        VenteResponse credit = vendre(ADMIN, "ALIM-001", 10, null, true);
        regler(CAISSIER, "3000", ModeReglement.ESPECES, "R1");
        ventesService.annulerVente(credit.id(), "Client parti sans payer le reste", true, ADMIN);

        LigneReglementDette ligne = reglementsService.historique(aujourdhui(), aujourdhui(), null).reglements().get(0);

        assertThat(ligne.detteAnnulee()).isTrue();
        assertThat(ligne.resteApres()).isNull();
    }

    @Test
    void lesDocumentsNommentLeVendeurEtReprennentLesMemesChiffres() throws Exception {
        vendre(CAISSIER, "ELEC-001", 1, ModeReglement.ESPECES, false);
        VenteResponse credit = vendre(CAISSIER, "ALIM-001", 10, null, true);
        regler(CAISSIER, "13000", ModeReglement.MOBILE_MONEY, "MM-77");

        String soldees = texteDuPdf(exportService.exporterSoldeesPdf(aujourdhui(), aujourdhui(), CAISSIER));
        assertThat(soldees).contains("Ventes soldées", "vendeur : caissier", credit.code(), "Crédit soldé le", "Espèces", "258 000");

        String reglements = texteDuPdf(exportService.exporterReglementsPdf(aujourdhui(), aujourdhui(), null));
        assertThat(reglements).contains("Règlements de dettes", "tous les vendeurs", "MM-77", "Mobile Money", "Soldée", "13 000", "Boutique Aminata");

        String toutes = texteDuPdf(exportService.exporterPdf(aujourdhui(), aujourdhui(), ADMIN));
        assertThat(toutes).contains("Historique des ventes", "vendeur : admin", "Aucune vente sur cette période.");

        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(exportService.exporterReglementsExcel(aujourdhui(), aujourdhui(), CAISSIER)))) {
            List<String> textes = new ArrayList<>();
            for (Row ligne : classeur.getSheetAt(0)) ligne.forEach(cellule -> textes.add(FORMAT.formatCellValue(cellule)));
            assertThat(textes).contains("Règlements de dettes", "MM-77", "Mobile Money", "caissier", credit.code());
        }
        try (Workbook classeur = new XSSFWorkbook(new ByteArrayInputStream(exportService.exporterSoldeesExcel(aujourdhui(), aujourdhui(), null)))) {
            assertThat(classeur.getSheetAt(0).getSheetName()).isEqualTo("Ventes soldées");
        }
    }

    /** Texte du PDF, espaces insécables (séparateurs de milliers) ramenées à des espaces ordinaires. */
    private static String texteDuPdf(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader lecteur = new PdfReader(pdf);
        PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
        StringBuilder texte = new StringBuilder();
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) texte.append(extracteur.getTextFromPage(page)).append(' ');
        lecteur.close();
        String brut = texte.toString().replace((char) 0xA0, ' ').replace((char) 0x202F, ' ');
        while (brut.contains("  ")) brut = brut.replace("  ", " ");
        return brut;
    }
}
