package com.nauta.triage.admin;

import com.nauta.triage.admin.SimulatorRegistry.SimConfig;
import com.nauta.triage.admin.dto.SimulatorDto;
import com.nauta.triage.admin.dto.StubDto;
import com.nauta.triage.admin.dto.StubUpsertRequest;
import com.nauta.triage.admin.error.AdminException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/simulators")
public class AdminSimulatorController {
    private final SimulatorRegistry registry;
    private final SimulatorProxyClient client;

    public AdminSimulatorController(SimulatorRegistry r, SimulatorProxyClient c) {
        this.registry = r; this.client = c;
    }

    @GetMapping
    public List<SimulatorDto> list() {
        return registry.getSimulators().entrySet().stream()
                .map(e -> new SimulatorDto(e.getKey(), e.getValue().getBaseUrl(),
                        client.isReachable(e.getValue().getBaseUrl())))
                .toList();
    }

    @GetMapping("/{sourceName}/stubs")
    public List<StubDto> stubs(@PathVariable String sourceName) {
        return client.listStubs(simulator(sourceName));
    }

    @PutMapping("/{sourceName}/stubs/{containerId}")
    public StubDto upsert(@PathVariable String sourceName,
                          @PathVariable String containerId,
                          @RequestBody StubUpsertRequest req) {
        int status = req.status() == null ? 200 : req.status();
        return client.upsertStub(simulator(sourceName), containerId, status,
                req.body() == null ? Map.of() : req.body());
    }

    @DeleteMapping("/{sourceName}/stubs/{containerId}")
    public ResponseEntity<Void> delete(@PathVariable String sourceName, @PathVariable String containerId) {
        client.deleteStubForContainer(simulator(sourceName), containerId);
        return ResponseEntity.noContent().build();
    }

    private SimConfig simulator(String sourceName) {
        var sim = registry.getSimulators().get(sourceName);
        if (sim == null) throw new AdminException(HttpStatus.NOT_FOUND, "unknown_simulator",
                "no simulator configured for source " + sourceName);
        return sim;
    }
}
