package com.nauta.triage.persistence.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "normalized_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedEventEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "raw_event_id", nullable = false)
    private UUID rawEventId;

    @Column(name = "container_id", nullable = false)
    private UUID containerId;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Type(JsonBinaryType.class)
    @Column(name = "extra_json", columnDefinition = "jsonb")
    private Map<String, Object> extraJson;

    @Column(name = "normalized_at", nullable = false)
    private Instant normalizedAt;
}
