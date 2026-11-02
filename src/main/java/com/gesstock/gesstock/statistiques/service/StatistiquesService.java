package com.gesstock.gesstock.statistiques.service;

import com.gesstock.gesstock.entities.ModeReglement;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.entities.JournalActivite;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.DetteClientRepository;
import com.gesstock.gesstock.repositories.JournalActiviteRepository;
import com.gesstock.gesstock.repositories.MouvementCaisseRepository;
import com.gesstock.gesstock.repositories.ReglementClientRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import com.gesstock.gesstock.journal.dto.JournalActiviteResponse;
import com.gesstock.gesstock.statistiques.dto.ArticleTopVentesResponse;
import com.gesstock.gesstock.statistiques.dto.DashboardStatistiquesResponse;
import com.gesstock.gesstock.statistiques.dto.MargeArticleResponse;
import com.gesstock.gesstock.statistiques.dto.MargeCategorieResponse;
import com.gesstock.gesstock.statistiques.dto.PointVentesResponse;
import com.gesstock.gesstock.statistiques.dto.RapportStatistiquesResponse;
import com.gesstock.gesstock.statistiques.dto.SessionCaisseRapportResponse;
import com.gesstock.gesstock.statistiques.dto.SyntheseStatistiquesResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@Transactional(readOnly = true)
public class StatistiquesService {

    private final VenteRepository venteRepository;
    private final ArticleRepository articleRepository;
    private final DetteClientRepository detteRepository;
    private final SessionCaisseRepository sessionCaisseRepository;
    private final JournalActiviteRepository journalRepository;
    private final MouvementCaisseRepository mouvementCaisseRepository;
    private final ReglementClientRepository reglementRepository;

    public StatistiquesService(
        VenteRepository venteRepository,
        ArticleRepository articleRepository,
        DetteClientRepository detteRepository,
        SessionCaisseRepository sessionCaisseRepository,
        JournalActiviteRepository journalRepository,
        MouvementCaisseRepository mouvementCaisseRepository,
        ReglementClientRepository reglementRepository
    ) {
        this.venteRepository = venteRepository;
        this.articleRepository = articleRepository;
        this.detteRepository = detteRepository;
        this.sessionCaisseRepository = sessionCaisseRepository;
        this.journalRepository = journalRepository;
        this.mouvementCaisseRepository = mouvementCaisseRepository;
        this.reglementRepository = reglementRepository;
    }

    public DashboardStatistiquesResponse dashboard() {
        LocalDate aujourdHui = LocalDate.now();
        LocalDateTime debutJour = aujourdHui.atStartOfDay();
        LocalDateTime finJour = aujourdHui.plusDays(1).atStartOfDay();
        LocalDate debutSemaine = aujourdHui.minusDays(6);
        LocalDateTime debutPeriode = debutSemaine.atStartOfDay();
        List<Object[]> top = venteRepository.articlesLesPlusVendus(
            StatutVente.VALIDEE, debutPeriode, finJour, PageRequest.of(0, 5));
        List<ArticleTopVentesResponse> topArticles = top.stream()
            .map(row -> new ArticleTopVentesResponse(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                valeurOuZero((Number) row[3])))
            .toList();

        Map<LocalDate, PointVentesResponse> ventesParDate = new HashMap<>();
        venteRepository.findByStatutAndDateVenteGreaterThanEqualAndDateVenteLessThan(
            StatutVente.VALIDEE, debutPeriode, finJour).forEach(vente -> {
                LocalDate jour = vente.getDateVente().toLocalDate();
                PointVentesResponse precedent = ventesParDate.getOrDefault(
                    jour, new PointVentesResponse(jour, 0, BigDecimal.ZERO));
                ventesParDate.put(jour, new PointVentesResponse(
                    jour,
                    precedent.nombreVentes() + 1,
                    precedent.chiffreAffaires().add(vente.getMontantTotal())
                ));
            });
        List<PointVentesResponse> tendances = new ArrayList<>(7);
        for (LocalDate jour = debutSemaine; !jour.isAfter(aujourdHui); jour = jour.plusDays(1)) {
            tendances.add(ventesParDate.getOrDefault(jour,
                new PointVentesResponse(jour, 0, BigDecimal.ZERO)));
        }

        return new DashboardStatistiquesResponse(
            aujourdHui,
            valeurOuZero(venteRepository.chiffreAffairesEntre(StatutVente.VALIDEE, debutJour, finJour)),
            venteRepository.compterVentesEntre(StatutVente.VALIDEE, debutJour, finJour),
            valeurOuZero(detteRepository.calculerEncoursGlobal()),
            articleRepository.compterArticlesSousSeuilAlerte(),
            tendances,
            topArticles
        );
    }

    public RapportStatistiquesResponse rapport(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            throw new IllegalArgumentException("La période doit contenir une date de début antérieure ou égale à la date de fin");
        }
        LocalDateTime debutDateTime = debut.atStartOfDay();
        LocalDateTime finDateTime = fin.plusDays(1).atStartOfDay();
        List<Object[]> rawArticles = venteRepository.margeParArticle(
            StatutVente.VALIDEE, debutDateTime, finDateTime);
        List<MargeArticleResponse> margesArticles = rawArticles.stream().map(row -> {
            BigDecimal chiffreAffaires = valeurOuZero((Number) row[4]);
            BigDecimal cout = valeurOuZero((Number) row[5]);
            return new MargeArticleResponse(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                valeurOuZero((Number) row[3]),
                chiffreAffaires,
                cout,
                chiffreAffaires.subtract(cout)
            );
        }).toList();
        List<MargeCategorieResponse> margesCategories = venteRepository.margeParCategorie(
            StatutVente.VALIDEE, debutDateTime, finDateTime).stream().map(row -> {
                BigDecimal chiffreAffaires = valeurOuZero((Number) row[2]);
                BigDecimal cout = valeurOuZero((Number) row[3]);
                return new MargeCategorieResponse(
                    row[0] == null ? null : ((Number) row[0]).longValue(),
                    row[1] == null ? "Sans catégorie" : (String) row[1],
                    chiffreAffaires,
                    cout,
                    chiffreAffaires.subtract(cout)
                );
            }).toList();
        List<SessionCaisseRapportResponse> sessions = sessionCaisseRepository
            .findByJourneeBetweenOrderByJourneeDescOuverteLeDesc(debut, fin).stream()
            .map(session -> new SessionCaisseRapportResponse(
                session.getId(),
                session.getCode(),
                session.getJournee(),
                session.getOuvertePar() == null ? "Inconnu" : session.getOuvertePar().getUsername(),
                session.getStatut(),
                session.getFondDeCaisse(),
                valeurOuZero(venteRepository.chiffreAffairesSession(session.getId(), StatutVente.VALIDEE)),
                valeurOuZero(venteRepository.encaissementsSession(session.getId(), StatutVente.VALIDEE)),
                valeurOuZero(reglementRepository.totalParSessionEtMode(session.getId(), ModeReglement.ESPECES)),
                valeurOuZero(mouvementCaisseRepository.totalSorties(session.getId())),
                session.getMontantCloture(),
                venteRepository.compterVentesSession(session.getId(), StatutVente.VALIDEE),
                session.getOuverteLe(),
                session.getFermeeLe()
            )).toList();

        BigDecimal chiffreAffairesTtc = valeurOuZero(venteRepository.chiffreAffairesEntre(
            StatutVente.VALIDEE, debutDateTime, finDateTime));
        BigDecimal chiffreAffairesLignes = margesArticles.stream()
            .map(MargeArticleResponse::chiffreAffaires)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal coutTotal = margesArticles.stream().map(MargeArticleResponse::coutAchat)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<JournalActiviteResponse> journal = journalRepository
            .findByDateActiviteBetweenOrderByDateActiviteAsc(debutDateTime, finDateTime).stream()
            .map(StatistiquesService::toJournalResponse)
            .toList();
        return new RapportStatistiquesResponse(
            debut,
            fin,
            chiffreAffairesTtc,
            venteRepository.compterVentesEntre(StatutVente.VALIDEE, debutDateTime, finDateTime),
            chiffreAffairesLignes.subtract(coutTotal),
            margesCategories,
            margesArticles,
            sessions,
            journal
        );
    }

    public SyntheseStatistiquesResponse synthese(LocalDate journee) {
        LocalDate jour = journee == null ? LocalDate.now() : journee;
        YearMonth mois = YearMonth.from(jour);
        LocalDateTime debutJour = jour.atStartOfDay();
        LocalDateTime finJour = jour.plusDays(1).atStartOfDay();
        LocalDateTime debutMois = mois.atDay(1).atStartOfDay();
        LocalDateTime finMois = mois.plusMonths(1).atDay(1).atStartOfDay();

        List<Object[]> top = venteRepository.articlesLesPlusVendus(
            StatutVente.VALIDEE, debutJour, finJour, PageRequest.of(0, 1));
        ArticleTopVentesResponse articleTop = top.isEmpty() ? null : new ArticleTopVentesResponse(
            ((Number) top.get(0)[0]).longValue(),
            (String) top.get(0)[1],
            (String) top.get(0)[2],
            valeurOuZero((Number) top.get(0)[3])
        );
        return new SyntheseStatistiquesResponse(
            jour,
            mois.toString(),
            articleTop,
            venteRepository.compterVentesEntre(StatutVente.VALIDEE, debutJour, finJour),
            valeurOuZero(venteRepository.chiffreAffairesEntre(StatutVente.VALIDEE, debutJour, finJour)),
            valeurOuZero(venteRepository.chiffreAffairesEntre(StatutVente.VALIDEE, debutMois, finMois)),
            valeurOuZero(articleRepository.valeurTotaleStock())
        );
    }


    private BigDecimal valeurOuZero(Number value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private BigDecimal valeurOuZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static JournalActiviteResponse toJournalResponse(JournalActivite activite) {
        return new JournalActiviteResponse(
            activite.getId(),
            activite.getAction(),
            activite.getEntite(),
            activite.getEntiteId(),
            activite.getDetails(),
            activite.getDateActivite(),
            activite.getUtilisateur() == null ? null : activite.getUtilisateur().getId(),
            activite.getUtilisateur() == null ? null : activite.getUtilisateur().getUsername()
        );
    }
}
