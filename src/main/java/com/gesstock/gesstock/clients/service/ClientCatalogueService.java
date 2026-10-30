package com.gesstock.gesstock.clients.service;

import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.EncoursClientResponse;
import com.gesstock.gesstock.clients.exception.ClientIntrouvableException;
import com.gesstock.gesstock.clients.exception.OperationDetteInvalideException;
import com.gesstock.gesstock.clients.mapper.ClientFinanceMapper;
import com.gesstock.gesstock.entities.Client;
import com.gesstock.gesstock.entities.StatutVente;
import com.gesstock.gesstock.journal.service.JournalActiviteService;
import com.gesstock.gesstock.repositories.ClientRepository;
import com.gesstock.gesstock.repositories.DetteClientRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class ClientCatalogueService {

    private final ClientRepository clientRepository;
    private final DetteClientRepository detteRepository;
    private final VenteRepository venteRepository;
    private final JournalActiviteService journalService;

    public ClientCatalogueService(
        ClientRepository clientRepository, DetteClientRepository detteRepository, VenteRepository venteRepository,
        JournalActiviteService journalService
    ) {
        this.clientRepository = clientRepository;
        this.detteRepository = detteRepository;
        this.venteRepository = venteRepository;
        this.journalService = journalService;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> lister() {
        // un seul passage pour tous les compteurs, au lieu d'une requête par client
        Map<Long, Long> achats = new HashMap<>();
        venteRepository.compterVentesParClient(StatutVente.VALIDEE)
            .forEach(ligne -> achats.put((Long) ligne[0], (Long) ligne[1]));
        return clientRepository.findAll().stream()
            .map(client -> ClientFinanceMapper.toResponse(client, achats.getOrDefault(client.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse obtenir(Long id) {
        return reponse(trouver(id));
    }

    public ClientResponse creer(ClientRequest request) {
        Client client = new Client();
        mettreAJour(client, request);
        Client enregistre = clientRepository.save(client);
        journalService.enregistrer("CREATION", "CLIENT", enregistre.getId(), "Client « " + enregistre.getNom() + " » créé"
            + (enregistre.getTelephone() == null ? "" : ", téléphone " + enregistre.getTelephone()), null);
        return ClientFinanceMapper.toResponse(enregistre, 0);
    }

    public ClientResponse modifier(Long id, ClientRequest request) {
        Client client = trouver(id);
        String nom = client.getNom();
        String telephone = client.getTelephone();
        String adresse = client.getAdresse();
        String email = client.getEmail();
        mettreAJour(client, request);
        Client enregistre = clientRepository.save(client);
        List<String> changements = new ArrayList<>();
        noter(changements, "nom", nom, enregistre.getNom());
        noter(changements, "téléphone", telephone, enregistre.getTelephone());
        noter(changements, "adresse", adresse, enregistre.getAdresse());
        noter(changements, "e-mail", email, enregistre.getEmail());
        if (!changements.isEmpty()) {
            journalService.enregistrer("MODIFICATION", "CLIENT", id, "Client « " + nom + " » : " + String.join(" ; ", changements), null);
        }
        return reponse(enregistre);
    }

    /** « téléphone « 77 000 » → « 78 000 » », seulement si la valeur a changé (vide : « — »). */
    private static void noter(List<String> changements, String champ, String avant, String apres) {
        if (!Objects.equals(avant, apres)) {
            changements.add(champ + " « " + (avant == null ? "—" : avant) + " » → « " + (apres == null ? "—" : apres) + " »");
        }
    }

    private ClientResponse reponse(Client client) {
        return ClientFinanceMapper.toResponse(client, venteRepository.compterVentesClient(client.getId(), StatutVente.VALIDEE));
    }

    public void supprimer(Long id) {
        Client client = trouver(id);
        if ((client.getVentes() != null && !client.getVentes().isEmpty())
            || !client.getDettes().isEmpty() || !client.getReglements().isEmpty()) {
            throw new OperationDetteInvalideException(
                "Un client associé à des ventes, dettes ou règlements ne peut pas être supprimé");
        }
        clientRepository.delete(client);
        journalService.enregistrer("SUPPRESSION", "CLIENT", id, "Client « " + client.getNom() + " » supprimé", null);
    }

    @Transactional(readOnly = true)
    public EncoursClientResponse obtenirEncours(Long clientId) {
        Client client = trouver(clientId);
        List<DetteResponse> dettes = detteRepository.findByClientIdOrderByDateCreationDesc(clientId).stream()
            .map(ClientFinanceMapper::toResponse).toList();
        BigDecimal encours = detteRepository.calculerEncoursClient(clientId);
        return new EncoursClientResponse(client.getId(), client.getNom(), encours, dettes);
    }

    private void mettreAJour(Client client, ClientRequest request) {
        client.setNom(request.nom().trim());
        client.setEmail(normaliser(request.email()));
        client.setTelephone(normaliser(request.telephone()));
        client.setAdresse(normaliser(request.adresse()));
    }

    private String normaliser(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private Client trouver(Long id) {
        return clientRepository.findById(id)
            .orElseThrow(() -> new ClientIntrouvableException("Client introuvable: " + id));
    }
}
