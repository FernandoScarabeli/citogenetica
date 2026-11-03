package br.ufla.citogenetica;

import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SessionSecurityMigrationIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaSegura123!";
    private static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String TOKEN_CSRF = "csrf-de-integracao";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tokenNaQueryStringNaoDeveAutenticar() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me").param("token", "token-legado-inutilizado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postSemCsrfDeveSerRejeitado() throws Exception {
        Usuario usuario = criarUsuario();
        Cookie sessao = login(usuario);

        mockMvc.perform(put("/api/v1/usuarios/me")
                        .cookie(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Nome sem CSRF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void loginERecuperacaoPublicaSemCsrfSaoRejeitados() throws Exception {
        Usuario usuario = criarUsuario();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "senha", SENHA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/recuperar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentaCookieECsrfSemEsquemaBearer() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.sessao-cookie.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.sessao-cookie.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.csrf-token.name").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security[0]['csrf-token']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/me'].put.security[0]['sessao-cookie']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/me'].put.security[0]['csrf-token']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security[0]['sessao-cookie']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security[0]['csrf-token']").exists());
    }

    @Test
    void csrfLoginPerfilLogoutE401AposLogout() throws Exception {
        Usuario usuario = criarUsuario();
        MvcResult csrfAntesDoLogin = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie csrfDoLogin = csrfAntesDoLogin.getResponse().getCookie(COOKIE_CSRF);
        assertNotNull(csrfDoLogin);
        assertTrue(!csrfDoLogin.isHttpOnly(), "O SPA precisa ler o cookie CSRF");

        MvcResult loginRealizado = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfDoLogin)
                        .header("X-XSRF-TOKEN", csrfDoLogin.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "senha", SENHA)))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie sessao = loginRealizado.getResponse().getCookie(COOKIE_SESSAO);
        assertNotNull(sessao);

        mockMvc.perform(get("/api/v1/usuarios/me").cookie(sessao))
                .andExpect(status().isOk());

        MvcResult csrfParaLogout = mockMvc.perform(get("/api/v1/auth/csrf").cookie(sessao))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie tokenParaLogout = csrfParaLogout.getResponse().getCookie(COOKIE_CSRF);
        assertNotNull(tokenParaLogout);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(sessao, tokenParaLogout)
                        .header("X-XSRF-TOKEN", tokenParaLogout.getValue()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/usuarios/me").cookie(sessao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessoesAntigasDevemSerRevogadasDepoisDaTrocaDeSenha() throws Exception {
        Usuario usuario = criarUsuario();
        Cookie primeiraSessao = login(usuario);
        Cookie segundaSessao = login(usuario);

        requisicaoAutenticada(primeiraSessao, put("/api/v1/usuarios/me/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("senhaAtual", SENHA, "novaSenha", "NovaSenha456!")))
                .andExpect(status().isNoContent());

        requisicaoAutenticada(primeiraSessao, get("/api/v1/usuarios/me"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/usuarios/me").cookie(segundaSessao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void somenteOrigensConfiguradasEmCorsRecebemPermissaoComCredenciais() throws Exception {
        mockMvc.perform(options("/api/v1/usuarios/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/v1/usuarios/me")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertEquals(null,
                        result.getResponse().getHeader("Access-Control-Allow-Origin")));
    }

    @Test
    void usuarioDesativadoPeloAdminOuRemovidoPerdeTodasAsSessoes() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.ADMIN);
        Cookie sessaoAdmin = login(admin);

        Usuario desativado = criarUsuario();
        Cookie sessaoDesativada = login(desativado);
        requisicaoAutenticada(sessaoAdmin, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/usuarios/{id}", desativado.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/usuarios/me").cookie(sessaoDesativada))
                .andExpect(status().isUnauthorized());

        Usuario removido = criarUsuario();
        Cookie sessaoRemovida = login(removido);
        usuarioRepository.deleteById(removido.getId());
        mockMvc.perform(get("/api/v1/usuarios/me").cookie(sessaoRemovida))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginCriaCookieDeSessaoHttpOnlySecureEComSameSiteLax() throws Exception {
        Usuario usuario = criarUsuario();
        MvcResult resultado = requisicaoComCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "senha", SENHA)))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie cookie = resultado.getResponse().getCookie(COOKIE_SESSAO);
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
        String sessionSetCookie = resultado.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(COOKIE_SESSAO + "="))
                .findFirst().orElseThrow();
        assertTrue(sessionSetCookie.contains("SameSite=Lax"), "A sessão deve usar SameSite=Lax");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class,
                usuario.getId().toString()));
    }

    private Usuario criarUsuario() {
        return criarUsuario(RolesEnum.USER);
    }

    private Usuario criarUsuario(RolesEnum roleNome) {
        var role = roleRepository.findByNome(roleNome).orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .nome("Usuário de sessão")
                .email(UUID.randomUUID() + "@citogenetica.test")
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
                .emailConfirmadoEm(java.time.Instant.now())
                .role(role)
                .build());
    }

    private Cookie login(Usuario usuario) throws Exception {
        MvcResult resultado = requisicaoComCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "senha", SENHA)))
                .andExpect(status().isNoContent())
                .andReturn();
        return resultado.getResponse().getCookie(COOKIE_SESSAO);
    }

    private ResultActions requisicaoAutenticada(Cookie sessao, MockHttpServletRequestBuilder requisicao)
            throws Exception {
        return requisicaoComCsrf(requisicao.cookie(sessao));
    }

    private ResultActions requisicaoComCsrf(MockHttpServletRequestBuilder requisicao) throws Exception {
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        requisicao.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
        return mockMvc.perform(requisicao);
    }

    @AfterEach
    void limparSessoesDeIntegracao() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }

    private String json(Object... pares) throws Exception {
        var objeto = new java.util.LinkedHashMap<String, Object>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            objeto.put((String) pares[indice], pares[indice + 1]);
        }
        return objectMapper.writeValueAsString(objeto);
    }
}
