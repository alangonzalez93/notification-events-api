package com.cobre.notifications.infrastructure.persistence.mapper;

import com.cobre.notifications.domain.model.Client;
import com.cobre.notifications.infrastructure.persistence.entity.ClientJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    Client toDomain(ClientJpaEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uniqueCode", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    ClientJpaEntity toEntity(Client domain);
}
