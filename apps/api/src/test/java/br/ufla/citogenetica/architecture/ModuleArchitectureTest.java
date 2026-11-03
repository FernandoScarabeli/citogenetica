package br.ufla.citogenetica.architecture;

import br.ufla.citogenetica.exemplo.internal.ExemploQueAcessaUsuarioInternoFixture;
import br.ufla.citogenetica.usuario.internal.UsuarioInternoFixture;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AnalyzeClasses(
        packages = "br.ufla.citogenetica",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    private static final String BASE = "br.ufla.citogenetica";

    private static final Set<String> MODULES = Set.of(
            "shared", "config", "usuario", "notificacao", "exemplo", "laboratorio",
            "protocolo", "contagem", "dados", "sincronizacao", "pesquisa",
            "colaboracao", "analise");

    private static final List<String> BUSINESS_MODULES = List.of(
            "shared", "usuario", "notificacao", "exemplo", "laboratorio", "protocolo",
            "contagem", "dados", "sincronizacao", "pesquisa", "colaboracao", "analise");

    private static final String[] BUSINESS_MODULE_PACKAGES = BUSINESS_MODULES.stream()
            .map(module -> BASE + "." + module + "..")
            .toArray(String[]::new);

    private static final String[] CONFIG_PACKAGES = {
            BASE + ".config..",
            BASE + ".configuration.."
    };

    /**
     * Pacotes legados tolerados durante a migração, mas proibidos como dependência dos módulos
     * novos. PR 2 remove configuration; PRs 3–4 removem os demais conforme os módulos migram.
     */
    private static final List<String> LEGACY_PACKAGES = List.of(
            BASE + ".configuration", // PR 2: segurança, sessão, OpenAPI, e-mail, segredos e storage.
            BASE + ".controller",     // PRs 3–4: controllers legados de usuario e notificacao.
            BASE + ".exception",      // PR 2: handler global e exceções específicas de autenticação.
            BASE + ".filter",         // PR 2: interceptor de rate limit; filtros comuns já migraram.
            BASE + ".mapper",         // PRs 3–4: mappers concretos de usuario e notificacao.
            BASE + ".model",          // PRs 3–4: entidades, tipos e DTOs dos módulos legados.
            BASE + ".repository",     // PRs 3–4: repositórios legados.
            BASE + ".service"         // PRs 2–4: serviços legados e adaptadores ainda não movidos.
    );

    private static final String[] LEGACY_PACKAGE_PATTERNS = LEGACY_PACKAGES.stream()
            .map(packageName -> packageName + "..")
            .toArray(String[]::new);

    private static final String[] SHARED_FORBIDDEN_PACKAGES = combine(
            BUSINESS_MODULES.stream()
                    .filter(module -> !"shared".equals(module))
                    .map(module -> BASE + "." + module + "..")
                    .toArray(String[]::new),
            CONFIG_PACKAGES,
            LEGACY_PACKAGE_PATTERNS);

    private static final ArchCondition<JavaClass> USES_ONLY_PUBLIC_MODULE_APIS =
            new ArchCondition<>("depend only on another module's public API") {
                @Override
                public void check(JavaClass origin, ConditionEvents events) {
                    String originModule = moduleOf(origin.getPackageName());
                    if (originModule == null) {
                        return;
                    }

                    for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                        JavaClass target = dependency.getTargetClass();
                        String targetModule = moduleOf(target.getPackageName());
                        if (targetModule == null || targetModule.equals(originModule)) {
                            continue;
                        }
                        if ("shared".equals(targetModule) || isPublicApi(targetModule, target.getPackageName())) {
                            continue;
                        }

                        events.add(SimpleConditionEvent.violated(origin,
                                origin.getName() + " depends on non-public type " + target.getName()
                                        + " in module " + targetModule));
                    }
                }
            };

    @ArchTest
    static final ArchRule modules_use_only_public_apis = classes()
            .that().resideInAnyPackage(BUSINESS_MODULE_PACKAGES)
            .should(USES_ONLY_PUBLIC_MODULE_APIS);

    @ArchTest
    static final ArchRule shared_has_no_business_config_or_legacy_dependencies = noClasses()
            .that().resideInAPackage(BASE + ".shared..")
            .should().dependOnClassesThat().resideInAnyPackage(SHARED_FORBIDDEN_PACKAGES);

    @ArchTest
    static final ArchRule modules_do_not_depend_on_config = noClasses()
            .that().resideInAnyPackage(BUSINESS_MODULE_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage(CONFIG_PACKAGES);

    @ArchTest
    static void module_dependency_graph_has_no_cycles(JavaClasses importedClasses) {
        Map<String, Set<String>> graph = new HashMap<>();
        MODULES.forEach(module -> graph.put(module, new HashSet<>()));

        for (JavaClass origin : importedClasses) {
            String originModule = moduleOf(origin.getPackageName());
            if (originModule == null) {
                continue;
            }
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                String targetModule = moduleOf(dependency.getTargetClass().getPackageName());
                if (targetModule != null && !targetModule.equals(originModule)) {
                    graph.get(originModule).add(targetModule);
                }
            }
        }

        assertThat(hasCycle(graph))
                .as("o grafo de dependências entre módulos deve ser acíclico: %s", graph)
                .isFalse();
    }

    @Test
    void regra_rejeita_dependencia_de_exemplo_para_internal_de_usuario() {
        JavaClasses fixtureClasses = new ClassFileImporter().importClasses(
                ExemploQueAcessaUsuarioInternoFixture.class,
                UsuarioInternoFixture.class);

        assertThatThrownBy(() -> modules_use_only_public_apis.check(fixtureClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("usuario.internal");
    }

    private static String moduleOf(String packageName) {
        if (isLegacyPackage(packageName) || !packageName.startsWith(BASE + ".")) {
            return null;
        }
        String relativePackage = packageName.substring(BASE.length() + 1);
        int separator = relativePackage.indexOf('.');
        String module = separator < 0 ? relativePackage : relativePackage.substring(0, separator);
        return MODULES.contains(module) ? module : null;
    }

    private static boolean isLegacyPackage(String packageName) {
        return LEGACY_PACKAGES.stream().anyMatch(legacy ->
                packageName.equals(legacy) || packageName.startsWith(legacy + "."));
    }

    private static boolean isPublicApi(String module, String packageName) {
        String apiPackage = BASE + "." + module + ".api";
        return packageName.equals(apiPackage) || packageName.startsWith(apiPackage + ".");
    }

    private static String[] combine(String[]... groups) {
        List<String> combined = new ArrayList<>();
        for (String[] group : groups) {
            combined.addAll(List.of(group));
        }
        return combined.toArray(String[]::new);
    }

    private static boolean hasCycle(Map<String, Set<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> activePath = new HashSet<>();
        for (String module : graph.keySet()) {
            if (hasCycle(module, graph, visited, activePath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycle(String module, Map<String, Set<String>> graph,
                                     Set<String> visited, Set<String> activePath) {
        if (activePath.contains(module)) {
            return true;
        }
        if (!visited.add(module)) {
            return false;
        }

        activePath.add(module);
        for (String dependency : graph.getOrDefault(module, Set.of())) {
            if (hasCycle(dependency, graph, visited, activePath)) {
                return true;
            }
        }
        activePath.remove(module);
        return false;
    }
}
