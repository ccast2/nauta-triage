package com.nauta.triage.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nauta.triage.admin.SimulatorRegistry.SimConfig;
import com.nauta.triage.admin.dto.StubDto;
import com.nauta.triage.admin.error.AdminException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SimulatorProxyClient {
    private static final ObjectMapper M = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    public boolean isReachable(String baseUrl) {
        try {
            HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/__admin/mappings"))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
            return r.statusCode() / 100 == 2;
        } catch (Exception e) { return false; }
    }

    @SuppressWarnings("unchecked")
    public List<StubDto> listStubs(SimConfig sim) {
        Pattern templatePattern = templateToRegex(sim.getUrlTemplate());
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(sim.getBaseUrl() + "/__admin/mappings"))
                    .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw upstream(r.statusCode(), r.body());
            var doc = M.readValue(r.body(), Map.class);
            var mappings = (List<Map<String, Object>>) doc.getOrDefault("mappings", List.of());
            List<StubDto> out = new ArrayList<>();
            for (Map<String, Object> m : mappings) {
                String id = String.valueOf(m.get("id"));
                Map<String, Object> req = (Map<String, Object>) m.getOrDefault("request", Map.of());
                Map<String, Object> resp = (Map<String, Object>) m.getOrDefault("response", Map.of());
                String url = String.valueOf(req.getOrDefault("url", ""));
                Matcher matcher = templatePattern.matcher(url);
                if (!matcher.matches()) continue;
                String containerId = matcher.group(1);
                int status = ((Number) resp.getOrDefault("status", 200)).intValue();
                Map<String, Object> body = (Map<String, Object>) resp.getOrDefault("jsonBody", Map.of());
                out.add(new StubDto(id, containerId, status, body));
            }
            return out;
        } catch (AdminException e) { throw e; }
        catch (Exception e) { throw unreachable(e); }
    }

    public StubDto upsertStub(SimConfig sim, String containerId, int status, Map<String, Object> body) {
        try {
            for (StubDto existing : listStubs(sim)) {
                if (containerId.equals(existing.containerId())) deleteStub(sim.getBaseUrl(), existing.id());
            }
            Map<String, Object> mapping = Map.of(
                    "request", Map.of("method", "GET", "url", sim.pathFor(containerId)),
                    "response", Map.of(
                            "status", status,
                            "headers", Map.of("Content-Type", "application/json"),
                            "jsonBody", body));
            String json = M.writeValueAsString(mapping);
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(sim.getBaseUrl() + "/__admin/mappings"))
                    .timeout(Duration.ofSeconds(2))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw upstream(r.statusCode(), r.body());
            @SuppressWarnings("unchecked")
            Map<String, Object> saved = M.readValue(r.body(), Map.class);
            return new StubDto(String.valueOf(saved.get("id")), containerId, status, body);
        } catch (AdminException e) { throw e; }
        catch (Exception e) { throw unreachable(e); }
    }

    public void deleteStubForContainer(SimConfig sim, String containerId) {
        for (StubDto existing : listStubs(sim)) {
            if (containerId.equals(existing.containerId())) deleteStub(sim.getBaseUrl(), existing.id());
        }
    }

    private void deleteStub(String baseUrl, String id) {
        try {
            HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/__admin/mappings/" + id))
                    .timeout(Duration.ofSeconds(2)).DELETE().build(), HttpResponse.BodyHandlers.discarding());
            if (r.statusCode() / 100 != 2 && r.statusCode() != 404) throw upstream(r.statusCode(), "delete failed");
        } catch (AdminException e) { throw e; }
        catch (Exception e) { throw unreachable(e); }
    }

    /** Converts "/containers/{containerId}" → regex matching anything with one capture group. */
    private static Pattern templateToRegex(String template) {
        String literal = Pattern.quote(template);
        String pattern = literal.replace("{containerId}", "\\E([^/]+)\\Q");
        return Pattern.compile("^" + pattern + "$");
    }

    private static AdminException upstream(int code, String body) {
        return new AdminException(HttpStatus.BAD_GATEWAY, "simulator_error",
                "simulator returned HTTP " + code, Map.of("body", body == null ? "" : body));
    }

    private static AdminException unreachable(Exception e) {
        return new AdminException(HttpStatus.BAD_GATEWAY, "simulator_unreachable",
                "simulator unreachable: " + e.getMessage(), Map.of());
    }
}
