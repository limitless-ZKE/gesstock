package com.gesstock.gesstock.services;

import com.gesstock.gesstock.entities.Client;
import com.gesstock.gesstock.repositories.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClientService {

    @Autowired
    private ClientRepository clientRepository;

    // Récupérer tous les clients
    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    // Enregistrer ou modifier un client
    public Client saveClient(Client client) {
        return clientRepository.save(client);
    }

    // Trouver un client par son ID
    public Client getClientById(Long id) {
        Optional<Client> client = clientRepository.findById(id);
        return client.orElse(null);
    }

    // Supprimer un client
    public void deleteClient(Long id) {
        clientRepository.deleteById(id);
    }
}