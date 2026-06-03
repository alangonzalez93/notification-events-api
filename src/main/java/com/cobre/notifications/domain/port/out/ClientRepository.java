package com.cobre.notifications.domain.port.out;

import com.cobre.notifications.domain.model.Client;

import java.util.List;
import java.util.Optional;

public interface ClientRepository {
    Client save(Client client);
    Optional<Client> findByUniqueCode(String uniqueCode);
    Optional<Client> findByEmail(String email);
    List<Client> findAllActive();
    void softDelete(String uniqueCode);
}
