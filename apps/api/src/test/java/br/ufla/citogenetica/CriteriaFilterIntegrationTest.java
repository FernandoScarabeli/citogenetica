package br.ufla.citogenetica;

import br.ufla.citogenetica.model.roles.Role;
import br.ufla.citogenetica.model.type.RolesEnum;
import br.ufla.citogenetica.model.type.StatusEnum;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.repository.RoleRepository;
import br.ufla.citogenetica.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtros e ordenação dinâmicos: só campos da lista permitida, nada ignorado em silêncio
 * e erro do cliente responde 400, nunca 500.
 */
@AutoConfigureMockMvc
@Transactional
class CriteriaFilterIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaDeTesteSegura123!";
    private static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String TOKEN_CSRF = "csrf-de-integracao";
    private static final String USUARIOS = "/api/v1/usuarios";

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

    private Cookie sessaoAdmin;
    private String marcador;

    @BeforeEach
    void preparar() throws Exception {
        marcador = UUID.randomUUID().toString().substring(0, 8);
        Usuario admin = criarUsuario("Administrador " + marcador, RolesEnum.ADMIN);
        criarUsuario("José Antônio " + marcador, RolesEnum.USER);
        criarUsuario("Maria 100% " + marcador, RolesEnum.USER);
        sessaoAdmin = login(admin);
    }

    @Test
    void campoDeFiltroDesconhecidoDeveResponder400() throws Exception {
        listar("campoInexistente", "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details[0]", containsString("campoInexistente")));
    }

    @Test
    void filtroPeloHashDaSenhaDeveSerRecusado() throws Exception {
        // Sem lista de campos permitidos, LIKE em "senha" vira oráculo do hash, caractere a caractere.
        listar("senha", "$2a$")
                .andExpect(status().isBadRequest());
    }

    @Test
    void grupoOuComCampoInvalidoNaoPodeSerIgnoradoEAlargarABusca() throws Exception {
        listar("campoInexistente!outroInexistente", "qualquer")
                .andExpect(status().isBadRequest());
        listar("nome!campoInexistente", marcador)
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordenacaoPorCampoDesconhecidoOuSensivelDeveResponder400() throws Exception {
        requisicao(get(USUARIOS).param("sortBy", "campoInexistente"))
                .andExpect(status().isBadRequest());
        requisicao(get(USUARIOS).param("sortBy", "senha"))
                .andExpect(status().isBadRequest());
        requisicao(get(USUARIOS).param("sortDirection", "lateral"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void valorInvalidoParaOTipoDoCampoDeveResponder400() throws Exception {
        listar("status", "INEXISTENTE").andExpect(status().isBadRequest());
        listar("createdDate", "gte:ontem").andExpect(status().isBadRequest());
        listar("createdDate", "quase:2026-01-01").andExpect(status().isBadRequest());
    }

    @Test
    void filtroDeTextoDeveIgnorarAcentosComAExtensaoUnaccent() throws Exception {
        listar("nome", "jose antonio " + marcador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nome").value("José Antônio " + marcador));
    }

    @Test
    void curingasDoLikeDevemSerTratadosComoTextoLiteral() throws Exception {
        listar("nome", "% " + marcador)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nome").value("Maria 100% " + marcador));
    }

    @Test
    void filtrosEOrdenacaoPermitidosContinuamFuncionando() throws Exception {
        requisicao(get(USUARIOS)
                        .param("nome", marcador)
                        .param("role.nome", "USER")
                        .param("status", "ATIVO")
                        .param("sortBy", "nome")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].nome").value("Maria 100% " + marcador))
                .andExpect(jsonPath("$.content[1].nome").value("José Antônio " + marcador));
    }

    private ResultActions listar(String campo, String valor) throws Exception {
        return requisicao(get(USUARIOS).param(campo, valor));
    }

    private Usuario criarUsuario(String nome, RolesEnum roleNome) {
        Role role = roleRepository.findByNome(roleNome).orElseThrow();
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome(nome)
                .email(UUID.randomUUID() + "@citogenetica.test")
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
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

    private ResultActions requisicao(MockHttpServletRequestBuilder requisicao) throws Exception {
        return requisicaoComCsrf(requisicao.cookie(sessaoAdmin));
    }

    private ResultActions requisicaoComCsrf(MockHttpServletRequestBuilder requisicao) throws Exception {
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        requisicao.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
        return mockMvc.perform(requisicao);
    }

    private String json(Object... pares) throws Exception {
        var objeto = new LinkedHashMap<String, Object>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            objeto.put((String) pares[indice], pares[indice + 1]);
        }
        return objectMapper.writeValueAsString(objeto);
    }
}
