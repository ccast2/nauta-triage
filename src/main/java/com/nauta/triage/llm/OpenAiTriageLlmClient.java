package com.nauta.triage.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nauta.triage.domain.ContainerStatus;
import com.nauta.triage.persistence.entity.LlmCallEntity;
import com.nauta.triage.persistence.repository.LlmCallRepository;
import com.nauta.triage.reconciliation.TriageLLMClient;
import com.nauta.triage.reconciliation.rules.SourceSnapshot;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI-backed {@link TriageLLMClient}.
 *
 * <p>Calls the OpenAI Chat Completions API directly via {@link java.net.http.HttpClient}
 * to keep the dependency surface small. The API key is read from
 * {@code triage.llm.api-key} (bound to env var {@code OPENAI_API_KEY}).
 */
@Component
@ConditionalOnProperty(name = "triage.llm.enabled", havingValue = "true", matchIfMissing = true)
public class OpenAiTriageLlmClient implements TriageLLMClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiTriageLlmClient.class);
    private static final ObjectMapper M = new ObjectMapper();
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String SYSTEM_PROMPT =
            "Return ONLY a JSON object with keys: status (ON_TRACK|DELAYED|NEEDS_REVIEW|LOST), "
                    + "confidence (0..1), reasoning (<300 chars).";

    private final LlmCallRepository repo;
    private final CircuitBreaker breaker;
    private final ExecutorService exec;
    private final HttpClient http;
    private final String model;
    private final Duration timeout;
    private final String apiKey;

    public OpenAiTriageLlmClient(LlmCallRepository repo,
                                 CircuitBreakerRegistry registry,
                                 @Value("${triage.llm.model}") String model,
                                 @Value("${triage.llm.timeout-ms}") long timeoutMs,
                                 @Value("${triage.llm.api-key:}") String apiKey) {
        this.repo = repo;
        this.breaker = registry.circuitBreaker("llm");
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.apiKey = apiKey;
        this.exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "llm-triage");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000)))
                .build();
    }

    @Override
    public LlmTriageResult triage(List<SourceSnapshot> snapshots, LocalDate eta, String reason) {
        long t0 = System.currentTimeMillis();
        String prompt = TriagePromptBuilder.build(snapshots, eta, reason);

        if (!breaker.tryAcquirePermission()) {
            persist(prompt, null, "circuit_open", 0, 0, 0);
            return new LlmTriageResult(false, null, 0.0, "circuit open", null);
        }

        if (apiKey == null || apiKey.isBlank()) {
            breaker.onError(0, TimeUnit.MILLISECONDS, new IllegalStateException("no api key"));
            persist(prompt, null, "error", 0, 0, 0);
            return new LlmTriageResult(false, null, 0.0, "no api key", null);
        }

        Future<String> future = exec.submit(() -> callOpenAi(prompt));
        try {
            String responseText = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = M.readValue(responseText, Map.class);
            ContainerStatus status = ContainerStatus.valueOf((String) parsed.get("status"));
            double conf = ((Number) parsed.get("confidence")).doubleValue();
            String reasoning = (String) parsed.get("reasoning");
            int latency = (int) (System.currentTimeMillis() - t0);
            var rec = persist(prompt, parsed, "ok", latency, 0, 0);
            breaker.onSuccess(latency, TimeUnit.MILLISECONDS);
            return new LlmTriageResult(true, status, conf, reasoning, rec.getId());
        } catch (TimeoutException te) {
            future.cancel(true);
            breaker.onError(timeout.toMillis(), TimeUnit.MILLISECONDS, te);
            persist(prompt, null, "timeout", (int) timeout.toMillis(), 0, 0);
            return new LlmTriageResult(false, null, 0.0, "timeout", null);
        } catch (Exception e) {
            breaker.onError(System.currentTimeMillis() - t0, TimeUnit.MILLISECONDS, e);
            persist(prompt, null, "error", (int) (System.currentTimeMillis() - t0), 0, 0);
            log.warn("LLM call failed: {}", e.toString());
            return new LlmTriageResult(false, null, 0.0, "error: " + e.getMessage(), null);
        }
    }

    private String callOpenAi(String prompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 512,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", prompt)
                )
        );
        String bodyJson = M.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = M.readValue(resp.body(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("OpenAI response missing choices[]");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null || message.get("content") == null) {
            throw new RuntimeException("OpenAI response missing message.content");
        }
        return (String) message.get("content");
    }

    @SuppressWarnings("unchecked")
    private LlmCallEntity persist(String prompt, Object responseJson, String status, int latencyMs, int tIn, int tOut) {
        return repo.save(LlmCallEntity.builder()
                .calledAt(Instant.now())
                .model(model)
                .prompt(prompt)
                .responseJson(responseJson == null ? null : M.convertValue(responseJson, Map.class))
                .latencyMs(latencyMs)
                .status(status)
                .tokensIn(tIn)
                .tokensOut(tOut)
                .build());
    }
}
