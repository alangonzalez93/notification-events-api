package com.cobre.notifications.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@SQLRestriction("deleted = false")
@Table(
        name = "clients",
        indexes = {
                @Index(name = "idx_clients_email", columnList = "email"),
                @Index(name = "idx_clients_deleted", columnList = "deleted")
        }
)
public class ClientJpaEntity extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;
}
