package com.nauta.triage.admin;

import com.nauta.triage.admin.dto.TenantDto;
import com.nauta.triage.persistence.repository.TenantRepository;
import com.nauta.triage.security.BearerAuthInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/tenants")
public class AdminTenantController {
    private static final Map<String, String> DEMO_TOKEN_BY_HASH = Map.of(
            BearerAuthInterceptor.hash("demo-token-a"), "demo-token-a",
            BearerAuthInterceptor.hash("demo-token-b"), "demo-token-b"
    );

    private final TenantRepository tenants;

    public AdminTenantController(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @GetMapping
    public List<TenantDto> list() {
        return tenants.findAll().stream()
                .map(t -> new TenantDto(t.getId(), t.getName(),
                        DEMO_TOKEN_BY_HASH.get(t.getApiTokenHash())))
                .toList();
    }
}
