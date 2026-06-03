package com.cobre.notifications.infrastructure.persistence.repository;

import com.cobre.notifications.domain.model.Client;
import com.cobre.notifications.domain.port.out.ClientRepository;
import com.cobre.notifications.infrastructure.persistence.mapper.ClientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ClientJpaRepository implements ClientRepository {

    private final ClientSpringDataRepository springDataRepository;
    private final ClientMapper mapper;

    @Override
    public Client save(Client client) {
        var entity = mapper.toEntity(client);
        return mapper.toDomain(springDataRepository.save(entity));
    }

    @Override
    public Optional<Client> findByUniqueCode(String uniqueCode) {
        return springDataRepository.findByUniqueCode(uniqueCode)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findByEmail(String email) {
        return springDataRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public List<Client> findAllActive() {
        return springDataRepository.findAll()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void softDelete(String uniqueCode) {
        springDataRepository.findByUniqueCode(uniqueCode).ifPresent(entity -> {
            entity.setDeleted(true);
            springDataRepository.save(entity);
        });
    }
}
