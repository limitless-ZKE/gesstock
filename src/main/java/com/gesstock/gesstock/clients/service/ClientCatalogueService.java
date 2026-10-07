package com.gesstock.gesstock.clients.service;

import com.gesstock.gesstock.clients.dto.ClientRequest;
import com.gesstock.gesstock.clients.dto.ClientResponse;
import com.gesstock.gesstock.clients.dto.DetteResponse;
import com.gesstock.gesstock.clients.dto.EncoursClientResponse;
import com.gesstock.gesstock.clients.exception.ClientIntrouvableException;
import com.gesstock.gesstock.clients.exception.OperationDetteInvalideException;
import com.gesstock.gesstock.clients.mapper.ClientFinanceMapper;
import com.gesstock.gesstock.entities.Client;
import com.gesstock.gesstock.repositories.ClientRepository;
import com.gesstock.gesstock.repositories.DetteClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class ClientCatalogueService {

    private final ClientRepository clientRepository;
    private final DetteClientRepository detteRepository;

    public ClientCatalogueService(ClientRepository clientRepository, DetteClientRepository detteRepository) {
        this.clientRepository = clientRepository;
        this.detteRepository = detteRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> lister() {
        return clientRepository.findAll().stream().map(ClientFinanceMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse obtenir(Long id) {
        return ClientFinanceMapper.toResponse(trouver(id));
    }

    public ClientResponse creer(ClientRequest request) {
        Client client = new Client();
        mettreAJour(client, request);
        return ClientFinanceMapper.toResponse(clientRepository.save(client));
    }

    public ClientResponse modifier(Long id, ClientRequest request) {
        Client client = trouver(id);
        mettreAJour(client, request);
        return ClientFinanceMapper.toResponse(clientRepository.save(client));
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
