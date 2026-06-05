package com.cobre.notifications.infrastructure.persistence.repository;

import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.infrastructure.persistence.entity.NotificationEventJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

interface NotificationEventSpringDataRepository extends JpaRepository<NotificationEventJpaEntity, Long> {

    @Query("SELECT n FROM NotificationEventJpaEntity n " +
           "JOIN FETCH n.client " +
           "LEFT JOIN FETCH n.subscription " +
           "WHERE n.uniqueCode = :uniqueCode")
    Optional<NotificationEventJpaEntity> findByUniqueCode(@Param("uniqueCode") String uniqueCode);

    boolean existsByEventIdAndClient_UniqueCode(String eventId, String clientUniqueCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM NotificationEventJpaEntity n " +
           "LEFT JOIN FETCH n.subscription " +
           "WHERE n.deliveryStatus = 'PENDING' " +
           "AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now) " +
           "ORDER BY n.createdDate ASC")
    List<NotificationEventJpaEntity> findPendingBatch(@Param("now") OffsetDateTime now, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEventJpaEntity n SET n.deliveryStatus = 'PROCESSING' WHERE n.id IN :ids")
    void markAllAsProcessing(@Param("ids") List<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEventJpaEntity n SET n.deliveryStatus = 'PENDING' " +
           "WHERE n.deliveryStatus = 'PROCESSING' AND n.lastModifiedDate < :cutoff")
    void resetStuckProcessing(@Param("cutoff") OffsetDateTime cutoff);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEventJpaEntity n SET " +
           "n.deliveryStatus = :status, " +
           "n.deliveredAt   = :deliveredAt, " +
           "n.retryCount    = :retryCount, " +
           "n.nextRetryAt   = :nextRetryAt, " +
           "n.lastError     = :lastError " +
           "WHERE n.uniqueCode = :uniqueCode")
    void updateStatus(@Param("uniqueCode")   String uniqueCode,
                      @Param("status")       DeliveryStatus status,
                      @Param("deliveredAt")  OffsetDateTime deliveredAt,
                      @Param("retryCount")   int retryCount,
                      @Param("nextRetryAt")  OffsetDateTime nextRetryAt,
                      @Param("lastError")    String lastError);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEventJpaEntity n SET n.deliveryStatus = 'FAILED', " +
           "n.lastError = 'Subscription deleted' " +
           "WHERE n.subscription.uniqueCode = :subscriptionUniqueCode " +
           "AND n.deliveryStatus = 'PENDING'")
    void failAllPendingForSubscription(@Param("subscriptionUniqueCode") String subscriptionUniqueCode);

    @Query(value = "SELECT n FROM NotificationEventJpaEntity n " +
                   "JOIN FETCH n.client " +
                   "LEFT JOIN FETCH n.subscription " +
                   "WHERE n.client.uniqueCode = :clientCode " +
                   "AND n.deliveryStatus = :status " +
                   "AND n.createdDate BETWEEN :from AND :to",
           countQuery = "SELECT COUNT(n) FROM NotificationEventJpaEntity n " +
                        "WHERE n.client.uniqueCode = :clientCode " +
                        "AND n.deliveryStatus = :status " +
                        "AND n.createdDate BETWEEN :from AND :to")
    Page<NotificationEventJpaEntity> findByClientCodeAndStatusAndDateRange(
            @Param("clientCode") String clientCode,
            @Param("status") DeliveryStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    @Query(value = "SELECT n FROM NotificationEventJpaEntity n " +
                   "JOIN FETCH n.client " +
                   "LEFT JOIN FETCH n.subscription " +
                   "WHERE n.client.uniqueCode = :clientCode " +
                   "AND n.createdDate BETWEEN :from AND :to",
           countQuery = "SELECT COUNT(n) FROM NotificationEventJpaEntity n " +
                        "WHERE n.client.uniqueCode = :clientCode " +
                        "AND n.createdDate BETWEEN :from AND :to")
    Page<NotificationEventJpaEntity> findByClientCodeAndDateRange(
            @Param("clientCode") String clientCode,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);
}
