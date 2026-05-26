package com.nauta.triage.persistence;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class RepositoryArchitectureTest {

    @Test
    void tenant_owned_repositories_must_only_expose_tenant_scoped_queries() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.nauta.triage.persistence.repository");
        // NormalizedEventRepository is exempted because normalized events are queried by containerId,
        // which is itself tenant-scoped (containers table enforces tenant ownership).
        // Source and LlmCall are global (not tenant-scoped). Tenant repo manages tenants themselves.
        ArchRuleDefinition.methods()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
            .and().areDeclaredInClassesThat(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass>("are tenant-owned repositories") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaClass javaClass) {
                    String name = javaClass.getSimpleName();
                    return !name.matches("(Tenant|Source|LlmCall|NormalizedEvent)Repository");
                }
            })
            .and().arePublic()
            .and().haveNameNotMatching("(save.*|delete.*|count|findById|getById|getReferenceById|existsById|flush|.*Tenant.*|.*SystemWide.*)")
            .should().haveNameMatching(".*ByTenant.*|.*Tenant.*")
            .because("Tenant-owned repos must only expose tenant-scoped queries. Use *ByTenant... names.")
            .allowEmptyShould(true)
            .check(classes);
    }
}
