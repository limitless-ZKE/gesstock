package com.gesstock.gesstock.repositories;

import com.gesstock.gesstock.entities.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByEmailIgnoreCase(String email);
}