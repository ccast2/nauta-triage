package com.nauta.triage.security;

import com.nauta.triage.persistence.entity.TenantEntity;
import com.nauta.triage.persistence.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BearerAuthInterceptorTest {

    private final TenantRepository repo = mock(TenantRepository.class);
    private final BearerAuthInterceptor interceptor = new BearerAuthInterceptor(repo);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void rejects_request_without_token() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean ok = interceptor.preHandle(req, res, new Object());
        assertThat(ok).isFalse();
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void resolves_tenant_from_valid_token() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity t = TenantEntity.builder()
                .id(tenantId)
                .name("acme")
                .apiTokenHash("hash-of-secret")
                .build();
        when(repo.findByApiTokenHash(BearerAuthInterceptor.hash("secret"))).thenReturn(Optional.of(t));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer secret");
        MockHttpServletResponse res = new MockHttpServletResponse();
        boolean ok = interceptor.preHandle(req, res, new Object());
        assertThat(ok).isTrue();
        assertThat(TenantContext.currentTenantId()).isEqualTo(tenantId);
    }
}
