package com.cobre.notifications.infrastructure.persistence.repository;

import com.cobre.notifications.infrastructure.persistence.entity.ClientJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ClientSpringDataRepository extends JpaRepository<ClientJpaEntity, Long> {

    Optional<ClientJpaEntity> findByUniqueCode(String uniqueCode);

    Optional<ClientJpaEntity> findByEmail(String email);

    // findAll() inherited from JpaRepository — @SQLRestriction on the entity
    // automatically applies "deleted = false" to every generated query
}
