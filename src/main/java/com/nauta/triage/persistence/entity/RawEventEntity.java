package com.nauta.triage.persistence.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "raw_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawEventEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "container_id_ref")
    private UUID containerIdRef;

    @Column(name = "container_business_id", nullable = false)
    private String containerBusinessId;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Type(JsonBinaryType.class)
    @Column(name = "payload_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payloadJson;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_status", nullable = false)
    private String processingStatus;

    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts;

    @Column(name = "last_error")
    private String lastError;
}
