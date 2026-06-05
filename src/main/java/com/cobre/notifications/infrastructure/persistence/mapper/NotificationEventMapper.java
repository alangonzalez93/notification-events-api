package com.cobre.notifications.infrastructure.persistence.mapper;

import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.infrastructure.persistence.entity.NotificationEventJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationEventMapper {

    @Mapping(target = "clientUniqueCode",       source = "client.uniqueCode")
    @Mapping(target = "subscriptionUniqueCode", source = "subscription.uniqueCode")
    NotificationEvent toDomain(NotificationEventJpaEntity entity);

    @Mapping(target = "client",           ignore = true)
    @Mapping(target = "subscription",     ignore = true)
    @Mapping(target = "deleted",          ignore = true)
    @Mapping(target = "createdDate",      ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "id",               ignore = true)
    @Mapping(target = "uniqueCode",       ignore = true)
    NotificationEventJpaEntity toEntity(NotificationEvent domain);
}
