package com.nauta.triage.sources.mapping;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.nauta.triage.domain.EventType;
import com.nauta.triage.domain.NormalizedEvent;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Component
public class MappingEngine {

    private static final Configuration JSONPATH_CFG = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)
            .build();

    public List<NormalizedEvent> normalize(Map<String, Object> payload, Map<String, Object> mapping, UUID sourceId, String sourceName) {
        String containerId = readString(payload, asString(mapping, "container_id_path"), "container_id_path");
        Object eventsObj = JsonPath.using(JSONPATH_CFG).parse(payload).read(asString(mapping, "events_path"));
        if (!(eventsObj instanceof List<?> rawEvents)) {
            throw new MappingError("events_path did not resolve to a list");
        }
        String typePath = asString(mapping, "event_type_path");
        String tsPath = asString(mapping, "event_timestamp_path");
        @SuppressWarnings("unchecked")
        Map<String, String> typeMap = (Map<String, String>) mapping.getOrDefault("event_type_map", Map.of());
        String etaPath = (String) mapping.get("eta_path");
        String notePath = (String) mapping.get("note_path");

        String etaStr = etaPath == null ? null : readOptionalString(payload, etaPath);
        String note = notePath == null ? null : readOptionalString(payload, notePath);

        List<NormalizedEvent> result = new ArrayList<>();
        for (Object item : rawEvents) {
            if (!(item instanceof Map<?,?> ev)) continue;
            String typeStr = readNested(ev, typePath);
            String tsStr = readNested(ev, tsPath);
            if (typeStr == null || tsStr == null) continue;
            String canonical = typeMap.getOrDefault(typeStr, typeStr);
            EventType type;
            try { type = EventType.valueOf(canonical); }
            catch (IllegalArgumentException e) { continue; }

            Map<String, Object> extra = new HashMap<>();
            if (note != null) extra.put("note", note);
            if (etaStr != null) extra.put("declared_eta", etaStr);

            result.add(NormalizedEvent.builder()
                .sourceId(sourceId).sourceName(sourceName)
                .containerBusinessId(containerId)
                .type(type)
                .timestamp(parseTimestamp(tsStr))
                .extra(extra)
                .build());
        }
        return result;
    }

    private String asString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof String s)) throw new MappingError("mapping field " + key + " missing or not a string");
        return s;
    }
    private String readString(Map<String,Object> payload, String path, String label) {
        Object v = JsonPath.using(JSONPATH_CFG).parse(payload).read(path);
        if (v == null) throw new MappingError("required path " + label + " (" + path + ") returned null");
        return v.toString();
    }
    private String readOptionalString(Map<String,Object> payload, String path) {
        Object v = JsonPath.using(JSONPATH_CFG).parse(payload).read(path);
        return v == null ? null : v.toString();
    }
    @SuppressWarnings("unchecked")
    private String readNested(Map<?,?> ev, String path) {
        String key = path.startsWith("$.") ? path.substring(2) : path;
        Object v = ((Map<String,Object>) ev).get(key);
        return v == null ? null : v.toString();
    }
    private Instant parseTimestamp(String s) {
        try { return Instant.parse(s); }
        catch (Exception e) {
            try { return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant(); }
            catch (Exception e2) { throw new MappingError("unparseable timestamp: " + s, e2); }
        }
    }
}
