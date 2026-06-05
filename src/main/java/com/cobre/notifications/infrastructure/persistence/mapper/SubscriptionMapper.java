package com.cobre.notifications.infrastructure.persistence.mapper;

import com.cobre.notifications.domain.model.Subscription;
import com.cobre.notifications.infrastructure.persistence.entity.SubscriptionJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "clientUniqueCode", source = "client.uniqueCode")
    Subscription toDomain(SubscriptionJpaEntity entity);

    @Mapping(target = "client",              ignore = true)
    @Mapping(target = "deleted",             ignore = true)
    @Mapping(target = "createdDate",         ignore = true)
    @Mapping(target = "lastModifiedDate",    ignore = true)
    @Mapping(target = "id",                  ignore = true)
    @Mapping(target = "uniqueCode",          ignore = true)
    SubscriptionJpaEntity toEntity(Subscription domain);
}
