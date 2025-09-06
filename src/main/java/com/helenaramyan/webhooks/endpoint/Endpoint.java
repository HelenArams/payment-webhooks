package com.helenaramyan.webhooks.endpoint;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A receiver URL subscribed to a set of event types, with its own signing secret. */
@Entity
@Table(name = "endpoint")
public class Endpoint {

    @Id
    private UUID id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 128)
    private String secret;

    @Column(nullable = false)
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "endpoint_event_type", joinColumns = @JoinColumn(name = "endpoint_id"))
    @Column(name = "event_type", nullable = false, length = 100)
    private Set<String> eventTypes = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Endpoint() {
        // JPA
    }

    public Endpoint(UUID id, String url, String secret, Set<String> eventTypes, Instant createdAt) {
        this.id = id;
        this.url = url;
        this.secret = secret;
        this.eventTypes = new HashSet<>(eventTypes);
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getEventTypes() {
        return Collections.unmodifiableSet(eventTypes);
    }

    public boolean subscribesTo(String eventType) {
        return eventTypes.contains(eventType);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
