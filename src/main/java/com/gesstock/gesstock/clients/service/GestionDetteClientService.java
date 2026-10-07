package com.gesstock.gesstock.clients.service;

import com.gesstock.gesstock.clients.dto.DetteAvecClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.ReglementRequest;
import com.gesstock.gesstock.clients.dto.ReglementResponse;
import com.gesstock.gesstock.clients.exception.ClientIntrouvableException;
import com.gesstock.gesstock.clients.exception.OperationDetteInvalideException;
import com.gesstock.gesstock.clients.mapper.ClientFinanceMapper;
import com.gesstock.gesstock.entities.*;
import com.gesstock.gesstock.repositories.ClientRepository;
import com.gesstock.gesstock.repositories.DetteClientRepository;
import com.gesstock.gesstock.repositories.ReglementClientRepository;
import com.gesstock.gesstock.repositories.UtilisateurRepository;
import com.gesstock.gesstock.repositories.SessionCaisseRepository;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class GestionDetteClientService {

    private static final int ECHELLE_MONETAIRE = 4;

    private final DetteClientRepository detteRepository;
    private final ReglementClientRepository reglementRepository;
    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalActiviteService journalActiviteService;
    private final SessionCaisseRepository sessionCaisseRepository;

    public GestionDetteClientService(
        DetteClientRepository detteRepository,
        ReglementClientRepository reglementRepository,
        ClientRepository clientRepository,
        UtilisateurRepository utilisateurRepository,
        JournalActiviteService journalActiviteService,
        SessionCaisseRepository sessionCaisseRepository
    ) {
        this.detteRepository = detteRepository;
        this.reglementRepository = reglementRepository;
        this.clientRepository = clientRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.journalActiviteService = journalActiviteService;
        this.sessionCaisseRepository = sessionCaisseRepository;
    }

    @Transactional(readOnly = true)
    public List<DetteAvecClientResponse> listerToutesLesDettes() {
        return detteRepository.findToutesAvecClient().stream()
            .map(ClientFinanceMapper::toResponseAvecClient).toList();
    }

    @Transactional(readOnly = true)
    public List<DetteResponse> listerDettesClient(Long clientId) {
        verifierClientExiste(clientId);
        return detteRepository.findByClientIdOrderByDateCreationDesc(clientId).stream()
            .map(ClientFinanceMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DetteResponse obtenirDette(Long detteId) {
        return ClientFinanceMapper.toResponse(trouverDette(detteId));
    }

    @Transactional(readOnly = true)
    public List<ReglementResponse> listerReglementsDette(Long detteId) {
        trouverDette(detteId);
        return reglementRepository.findByDetteIdOrderByDateReglementDesc(detteId).stream()
            .map(ClientFinanceMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReglementResponse> listerReglementsClient(Long clientId) {
        verifierClientExiste(clientId);
        return reglementRepository.findByClientIdOrderByDateReglementDesc(clientId).stream()
            .map(ClientFinanceMapper::toResponse).toList();
    }

    public ReglementResponse enregistrerReglement(Long detteId, ReglementRequest request, String username) {
        DetteClient dette = detteRepository.findByIdForUpdate(detteId)
            .orElseThrow(() -> new ClientIntrouvableException("Dette client introuvable: " + detteId));
        if (dette.getStatut() == StatutDette.ANNULEE || dette.getStatut() == StatutDette.REGLEE) {
            throw new OperationDetteInvalideException("Cette dette est déjà clôturée ou annulée");
        }

        BigDecimal montant = request.montant().setScale(ECHELLE_MONETAIRE, RoundingMode.UNNECESSARY);
        BigDecimal solde = dette.getMontantInitial().subtract(dette.getMontantRegle());
        if (montant.compareTo(solde) > 0) {
            throw new OperationDetteInvalideException(
                "Le versement dépasse le solde restant de " + solde.toPlainString());
        }

        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
            .orElseThrow(() -> new ClientIntrouvableException("Utilisateur connecté introuvable"));
        if (!utilisateur.isActif()) {
            throw new OperationDetteInvalideException("Un utilisateur désactivé ne peut pas enregistrer un règlement");
        }

        ReglementClient reglement = new ReglementClient();
        reglement.setDette(dette);
        reglement.setClient(dette.getClient());
        reglement.setMontant(montant);
        reglement.setModeReglement(request.modeReglement());
        reglement.setReference(request.reference() == null || request.reference().isBlank()
            ? null : request.reference().trim());
        reglement.setDateReglement(LocalDateTime.now());
        reglement.setRecuPar(utilisateur);
        if (request.modeReglement() == ModeReglement.ESPECES) {
            SessionCaisse session = sessionCaisseRepository.findByJourneeAndStatutForUpdate(
                    LocalDate.now(), StatutSessionCaisse.OUVERTE)
                .orElseThrow(() -> new OperationDetteInvalideException(
                    "Une session de caisse ouverte est requise pour encaisser un règlement en espèces"));
            reglement.setSessionCaisse(session);
        }

        BigDecimal montantRegle = dette.getMontantRegle().add(montant);
        dette.setMontantRegle(montantRegle);
        dette.setStatut(montantRegle.compareTo(dette.getMontantInitial()) == 0
            ? StatutDette.REGLEE
            : StatutDette.PARTIELLEMENT_REGLEE);
        dette.getReglements().add(reglement);
        reglementRepository.save(reglement);
        detteRepository.save(dette);
        journalActiviteService.enregistrer("REGLEMENT", "DETTE_CLIENT", dette.getId(),
            "Versement de " + montant + " " + request.modeReglement(), username);
        return ClientFinanceMapper.toResponse(reglement);
    }

    private void verifierClientExiste(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ClientIntrouvableException("Client introuvable: " + clientId);
        }
    }

    private DetteClient trouverDette(Long id) {
        return detteRepository.findById(id)
            .orElseThrow(() -> new ClientIntrouvableException("Dette client introuvable: " + id));
    }
}
