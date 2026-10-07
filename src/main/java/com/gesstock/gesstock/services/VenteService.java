package com.gesstock.gesstock.services;

import com.gesstock.gesstock.entities.Article;
import com.gesstock.gesstock.entities.Client;
import com.gesstock.gesstock.entities.LigneVente; // N'oubliez pas cet import
import com.gesstock.gesstock.entities.SessionCaisse;
import com.gesstock.gesstock.entities.StatutSessionCaisse;
import com.gesstock.gesstock.entities.Vente;
import com.gesstock.gesstock.repositories.ArticleRepository;
import com.gesstock.gesstock.repositories.ClientRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Service
public class VenteService {

    @Autowired
    private VenteRepository venteRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private SessionCaisseRepository sessionCaisseRepository;

    @Transactional
    public void enregistrerVentePanier(Vente vente, Long clientId, List<Long> articleIds, List<Integer> quantites) {
        SessionCaisse sessionCaisse = sessionCaisseRepository.findByJourneeAndStatutForUpdate(
                LocalDate.now(), StatutSessionCaisse.OUVERTE)
            .orElseThrow(() -> new IllegalStateException(
                "La journée de caisse doit être ouverte avant d'enregistrer une vente"));
        vente.setSessionCaisse(sessionCaisse);

        // 1. Initialisations de base de la vente
        vente.setDateVente(LocalDateTime.now());
        if (vente.getCode() == null || vente.getCode().isEmpty()) {
            vente.setCode("VTE-" + System.currentTimeMillis());
        }

        // 2. Associer le client (si présent)
        if (clientId != null) {
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new RuntimeException("Client introuvable avec l'ID : " + clientId));
            vente.setClient(client);
        } else {
            vente.setClient(null);
        }

        java.math.BigDecimal montantTotalGlobal = java.math.BigDecimal.ZERO;

        // 3. Parcourir les articles sélectionnés pour construire le panier
        if (articleIds != null && !articleIds.isEmpty()) {
            for (int i = 0; i < articleIds.size(); i++) {
                Long articleId = articleIds.get(i);
                int quantiteDemandee = quantites.get(i);

                if (articleId == null || quantiteDemandee <= 0) continue;

                Article article = articleRepository.findByIdForUpdate(articleId)
                        .orElseThrow(() -> new RuntimeException("Article introuvable avec l'ID : " + articleId));

                // Vérifier le stock
                if (article.getQuantite() < quantiteDemandee) {
                    throw new RuntimeException("Stock insuffisant pour l'article : " + article.getNom() + " (Disponible : " + article.getQuantite() + ")");
                }

                // Décrémenter le stock
                article.setQuantite(article.getQuantite() - quantiteDemandee);
                articleRepository.save(article);

                // Créer la ligne de vente
                LigneVente ligne = new LigneVente();
                ligne.setArticle(article);
                ligne.setQuantite(quantiteDemandee);
                ligne.setPrixUnitaire(article.getPrix());
                ligne.setVente(vente);

                // Ajouter la ligne à la collection de la vente
                vente.getLignesVente().add(ligne);

                // Ajouter au montant total global
                montantTotalGlobal = montantTotalGlobal.add(article.getPrix().multiply(java.math.BigDecimal.valueOf(quantiteDemandee)));
            }
        }

        if (vente.getLignesVente().isEmpty()) {
            throw new RuntimeException("Veuillez sélectionner au moins un article valide pour la vente.");
        }

        vente.setMontantTotal(montantTotalGlobal);

        // 4. Sauvegarder la vente (CascadeType.ALL enregistrera automatiquement les lignes de vente)
        venteRepository.save(vente);
    }

    public long compterTotalVentes() {
        return venteRepository.count();
    }

    public java.math.BigDecimal calculerChiffreAffairesTotal() {
        List<Vente> ventes = venteRepository.findAll();
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (Vente v : ventes) {
            total = total.add(v.getMontantTotal());
        }
        return total;
    }
}