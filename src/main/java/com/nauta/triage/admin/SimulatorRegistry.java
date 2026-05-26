package com.nauta.triage.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "triage")
public class SimulatorRegistry {
    private Map<String, SimConfig> simulators = new LinkedHashMap<>();

    public Map<String, SimConfig> getSimulators() { return simulators; }
    public void setSimulators(Map<String, SimConfig> simulators) { this.simulators = simulators; }

    public static class SimConfig {
        /** Base URL of the WireMock service (inside the docker network). */
        private String baseUrl;
        /** Path template the corresponding connector uses to fetch. Must contain {containerId}. */
        private String urlTemplate = "/containers/{containerId}";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUrlTemplate() { return urlTemplate; }
        public void setUrlTemplate(String urlTemplate) { this.urlTemplate = urlTemplate; }

        public String pathFor(String containerId) {
            return urlTemplate.replace("{containerId}", containerId);
        }
    }
}
