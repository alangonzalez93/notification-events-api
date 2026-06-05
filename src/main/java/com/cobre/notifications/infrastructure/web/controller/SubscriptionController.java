package com.cobre.notifications.infrastructure.web.controller;

import com.cobre.notifications.domain.port.in.CreateSubscriptionUseCase;
import com.cobre.notifications.domain.port.in.DeleteSubscriptionUseCase;
import com.cobre.notifications.domain.port.in.GetSubscriptionUseCase;
import com.cobre.notifications.domain.port.in.UpdateSubscriptionUseCase;
import com.cobre.notifications.infrastructure.web.dto.request.CreateSubscriptionRequest;
import com.cobre.notifications.infrastructure.web.dto.request.UpdateSubscriptionRequest;
import com.cobre.notifications.infrastructure.web.dto.response.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/clients/{clientUniqueCode}/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final CreateSubscriptionUseCase createUseCase;
    private final GetSubscriptionUseCase getUseCase;
    private final UpdateSubscriptionUseCase updateUseCase;
    private final DeleteSubscriptionUseCase deleteUseCase;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(
            @PathVariable String clientUniqueCode,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        var subscription = createUseCase.create(new CreateSubscriptionUseCase.Command(
                clientUniqueCode, request.webhookUrl(),
                request.authHeaderName(), request.authHeaderValue(),
                request.eventTypes()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionResponse.from(subscription));
    }

    @GetMapping
    public ResponseEntity<SubscriptionResponse> get(@PathVariable String clientUniqueCode) {
        return ResponseEntity.ok(SubscriptionResponse.from(
                getUseCase.getByClientUniqueCode(clientUniqueCode)));
    }

    @PutMapping("/{subscriptionUniqueCode}")
    public ResponseEntity<SubscriptionResponse> update(
            @PathVariable String clientUniqueCode,
            @PathVariable String subscriptionUniqueCode,
            @RequestBody UpdateSubscriptionRequest request) {
        var subscription = updateUseCase.update(new UpdateSubscriptionUseCase.Command(
                clientUniqueCode, subscriptionUniqueCode,
                request.webhookUrl(), request.authHeaderName(), request.authHeaderValue(),
                request.eventTypes() != null ? request.eventTypes() : Set.of(),
                request.active()
        ));
        return ResponseEntity.ok(SubscriptionResponse.from(subscription));
    }

    @DeleteMapping("/{subscriptionUniqueCode}")
    public ResponseEntity<Void> delete(
            @PathVariable String clientUniqueCode,
            @PathVariable String subscriptionUniqueCode) {
        deleteUseCase.delete(clientUniqueCode, subscriptionUniqueCode);
        return ResponseEntity.noContent().build();
    }
}
