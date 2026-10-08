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
import com.gesstock.gesstock.repositories.ClientRepository;
import com.gesstock.gesstock.repositories.DetteClientRepository;
import com.gesstock.gesstock.repositories.VenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ClientCatalogueService {

    private final ClientRepository clientRepository;
    private final DetteClientRepository detteRepository;
    private final VenteRepository venteRepository;

    public ClientCatalogueService(
        ClientRepository clientRepository, DetteClientRepository detteRepository, VenteRepository venteRepository
    ) {
        this.clientRepository = clientRepository;
        this.detteRepository = detteRepository;
        this.venteRepository = venteRepository;
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
        return ClientFinanceMapper.toResponse(clientRepository.save(client), 0);
    }

    public ClientResponse modifier(Long id, ClientRequest request) {
        Client client = trouver(id);
        mettreAJour(client, request);
        return reponse(clientRepository.save(client));
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
