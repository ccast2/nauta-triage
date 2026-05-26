package com.nauta.triage.persistence.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "connector_type", nullable = false)
    private String connectorType;

    @Type(JsonBinaryType.class)
    @Column(name = "config_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> configJson;

    @Type(JsonBinaryType.class)
    @Column(name = "mapping_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> mappingJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "polling_interval_sec")
    private Integer pollingIntervalSec;

    @Column(name = "supports_webhook", nullable = false)
    private boolean supportsWebhook;

    @Column(name = "webhook_secret")
    private String webhookSecret;
}
