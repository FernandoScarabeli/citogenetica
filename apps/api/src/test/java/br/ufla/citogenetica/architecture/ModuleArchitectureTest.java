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
            "colaboracao", "analise", "infra");

    private static final List<String> BUSINESS_MODULES = List.of(
            "shared", "usuario", "notificacao", "exemplo", "laboratorio", "protocolo",
            "contagem", "dados", "sincronizacao", "pesquisa", "colaboracao", "analise");

    private static final String[] BUSINESS_MODULE_PACKAGES = BUSINESS_MODULES.stream()
            .map(module -> BASE + "." + module + "..")
            .toArray(String[]::new);

    private static final String[] MODULES_AND_INFRA_PACKAGES = combine(
            BUSINESS_MODULE_PACKAGES,
            new String[]{BASE + ".infra.."});

    private static final String[] CONFIG_PACKAGES = {
            BASE + ".config.."
    };

    /**
     * Pacotes legados tolerados durante a migração, mas proibidos como dependência dos módulos
     * novos. No PR 4, notificacao sai desses pacotes e deixa de acessar usuario.internal.
     */
    private static final List<String> LEGACY_PACKAGES = List.of(
            BASE + ".controller",    // PR 4: controllers legados de notificacao.
            BASE + ".exception",     // PR 4: ConflictException e ModelException antigos, sem uso.
            BASE + ".mapper",        // PR 4: mapper legado de notificacao.
            BASE + ".model",         // PR 4: entidades, eventos e DTOs de notificacao.
            BASE + ".repository",    // PR 4: repositório legado de notificacao.
            BASE + ".service"        // PR 4: notificacao ainda acessa usuario.internal; inclui SSE legado.
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
            new String[]{BASE + ".infra.."},
            LEGACY_PACKAGE_PATTERNS);

    private static final ArchCondition<JavaClass> USES_ONLY_PUBLIC_MODULE_APIS =
            new ArchCondition<>("depender somente da API pública de outro módulo") {
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
                                origin.getName() + " depende do tipo não público " + target.getName()
                                        + " no módulo " + targetModule));
                    }
                }
            };

    @ArchTest
    static final ArchRule modules_use_only_public_apis = classes()
            .that().resideInAnyPackage(BUSINESS_MODULE_PACKAGES)
            .should(USES_ONLY_PUBLIC_MODULE_APIS)
            .as("Módulos de domínio dependem somente de APIs públicas de outros módulos");

    @ArchTest
    static final ArchRule shared_has_no_business_config_or_legacy_dependencies = noClasses()
            .that().resideInAPackage(BASE + ".shared..")
            .should().dependOnClassesThat().resideInAnyPackage(SHARED_FORBIDDEN_PACKAGES)
            .as("shared não depende de negócio, configuração, infra nem legado");

    @ArchTest
    static final ArchRule modules_do_not_depend_on_config = noClasses()
            .that().resideInAnyPackage(MODULES_AND_INFRA_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage(CONFIG_PACKAGES)
            .as("Módulos e adaptadores não dependem da raiz de composição config");

    @ArchTest
    static final ArchRule config_uses_only_public_usuario_api = noClasses()
            .that().resideInAPackage(BASE + ".config..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".usuario.internal..")
            .as("config usa somente a API pública de usuario");

    @ArchTest
    static void grafo_de_dependencias_entre_modulos_nao_tem_ciclos(JavaClasses importedClasses) {
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
