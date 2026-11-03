package br.ufla.citogenetica.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Citogenética UFLA")
                        .version("1.0")
                        .description("API para registro e consulta de lâminas de citogenética da UFLA. A autenticação usa o cookie HttpOnly CITOGENETICA_SESSION. Antes de operações POST, PUT, PATCH ou DELETE, obtenha o cookie XSRF-TOKEN em GET /api/v1/auth/csrf e envie seu valor no cabeçalho X-XSRF-TOKEN."))
                .components(new Components()
                        .addSecuritySchemes("sessao-cookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("CITOGENETICA_SESSION")
                                .description("Cookie HttpOnly emitido ao autenticar em POST /api/v1/auth/login"))
                        .addSecuritySchemes("csrf-token", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("Valor do cookie legível XSRF-TOKEN emitido por GET /api/v1/auth/csrf")));
    }

    @Bean
    public OpenApiCustomizer sessionAndCsrfRequirements() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    if ("/api/v1/auth/csrf".equals(path)
                            && method == io.swagger.v3.oas.models.PathItem.HttpMethod.GET) {
                        operation.setSecurity(List.of());
                        return;
                    }

                    SecurityRequirement requirement = new SecurityRequirement();
                    if (isPublicAuthWithCsrf(path, method)) {
                        requirement.addList("csrf-token");
                    } else {
                        requirement.addList("sessao-cookie");
                        if (requiresCsrf(method)) {
                            requirement.addList("csrf-token");
                        }
                    }
                    operation.setSecurity(List.of(requirement));
                }));
    }

    private boolean isPublicAuthWithCsrf(String path, io.swagger.v3.oas.models.PathItem.HttpMethod method) {
        return method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                && List.of(
                        "/api/v1/auth/login",
                        "/api/v1/auth/recuperar-senha",
                        "/api/v1/auth/redefinir-senha",
                        "/api/v1/auth/validar-codigo")
                .contains(path);
    }

    private boolean requiresCsrf(io.swagger.v3.oas.models.PathItem.HttpMethod method) {
        return method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                || method == io.swagger.v3.oas.models.PathItem.HttpMethod.PUT
                || method == io.swagger.v3.oas.models.PathItem.HttpMethod.PATCH
                || method == io.swagger.v3.oas.models.PathItem.HttpMethod.DELETE;
    }
}
