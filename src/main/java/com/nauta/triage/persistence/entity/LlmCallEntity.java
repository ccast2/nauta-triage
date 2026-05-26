package com.nauta.triage.persistence.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "llm_calls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmCallEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "called_at", nullable = false)
    private Instant calledAt;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "prompt")
    private String prompt;

    @Type(JsonBinaryType.class)
    @Column(name = "response_json", columnDefinition = "jsonb")
    private Map<String, Object> responseJson;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "status", nullable = false)
    private String status;
}
