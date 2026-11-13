package br.ufla.citogenetica;

import br.ufla.citogenetica.notificacao.internal.web.dto.response.NotificacaoResponseDTO;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.notificacao.internal.domain.TipoNotificacao;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.notificacao.internal.persistence.NotificacaoRepository;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import br.ufla.citogenetica.shared.web.error.NotFoundException;
import br.ufla.citogenetica.notificacao.internal.application.NotificacaoService;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolamento das notificações por destinatário: nenhuma rota pode ler, contar ou alterar
 * notificações de outro usuário, e a paginação tem limite explícito.
 */
@AutoConfigureMockMvc
@Transactional
class NotificacaoOwnershipIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaDeTesteSegura123!";
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

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Usuario alice;
    private Usuario bruno;
    private Cookie sessaoAlice;

    @BeforeEach
    void prepararUsuarios() throws Exception {
        Role roleUser = roleRepository.findByNome(RolesEnum.USER).orElseThrow();
        alice = criarUsuario(roleUser);
        bruno = criarUsuario(roleUser);
        sessaoAlice = login(alice);
    }

    @Test
    void listagemDeveTrazerSomenteNotificacoesDoUsuarioLogado() throws Exception {
        inserirNotificacao(alice, "alice-1", false);
        inserirNotificacao(alice, "alice-2", true);
        inserirNotificacao(bruno, "bruno-1", false);
        inserirNotificacao(bruno, "bruno-2", true);

        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].titulo", everyItem(startsWith("alice-"))));

        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes").param("lida", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[*].titulo", hasItem("alice-1")));

        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes").param("lida", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[*].titulo", hasItem("alice-2")));
    }

    @Test
    void contagemDeNaoLidasDeveConsiderarSomenteOUsuarioLogado() throws Exception {
        inserirNotificacao(alice, "alice-1", false);
        inserirNotificacao(bruno, "bruno-1", false);
        inserirNotificacao(bruno, "bruno-2", false);

        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes/nao-lidas/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void marcarTodasComoLidasNaoDeveAlterarNotificacoesDeOutroUsuario() throws Exception {
        long daAlice = inserirNotificacao(alice, "alice-1", false);
        long doBruno = inserirNotificacao(bruno, "bruno-1", false);

        requisicaoAutenticada(sessaoAlice, patch("/api/v1/notificacoes/marcar-todas-lidas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(1));

        assertEquals(Boolean.TRUE, lida(daAlice));
        assertEquals(Boolean.FALSE, lida(doBruno));
    }

    @Test
    void marcarComoLidaNotificacaoDeOutroUsuarioDeveResponder404IgualAoDeIdInexistente() throws Exception {
        long doBruno = inserirNotificacao(bruno, "bruno-1", false);
        long inexistente = doBruno + 1_000_000;

        String respostaOutroDono = requisicaoAutenticada(sessaoAlice,
                patch("/api/v1/notificacoes/{id}/lida", doBruno))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String respostaInexistente = requisicaoAutenticada(sessaoAlice,
                patch("/api/v1/notificacoes/{id}/lida", inexistente))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals(Boolean.FALSE, lida(doBruno));
        assertEquals(
                objectMapper.readTree(respostaOutroDono).get("message"),
                objectMapper.readTree(respostaInexistente).get("message"));
        assertFalse(objectMapper.readTree(respostaOutroDono).get("message").asText()
                        .contains(String.valueOf(doBruno)),
                "A mensagem de 404 não deve ecoar o id consultado.");
    }

    @Test
    void marcarComoLidaDeveFuncionarParaODono() throws Exception {
        long daAlice = inserirNotificacao(alice, "alice-1", false);

        requisicaoAutenticada(sessaoAlice, patch("/api/v1/notificacoes/{id}/lida", daAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lida").value(true));

        assertEquals(Boolean.TRUE, lida(daAlice));
    }

    @Test
    void tamanhoDePaginaAcimaDoLimiteDeveResponder400Claro() throws Exception {
        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details[0]", containsString("100")));

        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes").param("size", "100"))
                .andExpect(status().isOk());
    }

    @Test
    void paginacaoInvalidaDeveResponder400EmVezDe500() throws Exception {
        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes").param("size", "0"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes").param("page", "-1"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void bancoDeveRecusarNotificacaoSemDestinatario() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO notificacao (tipo, titulo, lida) VALUES ('INFO', 'sem dono', FALSE)
                """));
    }

    @Test
    void bancoDeveRecusarNotificacaoComDestinatarioInexistente() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO notificacao (tipo, titulo, lida, usuario_id) VALUES ('INFO', 'órfã', FALSE, ?)
                """, Long.MAX_VALUE));
    }

    @Test
    void criarDeveGravarNoDestinatarioInformadoEAparecerSoParaEle() throws Exception {
        NotificacaoResponseDTO criada = notificacaoService.criar(
                bruno.getId(), TipoNotificacao.INFO, "para-bruno", "mensagem", null);

        assertEquals(bruno.getId(), jdbcTemplate.queryForObject(
                "SELECT usuario_id FROM notificacao WHERE id = ?", Long.class, criada.id()));
        requisicaoAutenticada(sessaoAlice, get("/api/v1/notificacoes"))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void criarParaUsuarioInexistenteDeveManterARespostaAnterior() {
        NotFoundException excecao = assertThrows(NotFoundException.class, () -> notificacaoService.criar(
                Long.MAX_VALUE, TipoNotificacao.INFO, "sem destinatário", null, null));

        assertEquals("Usuario não encontrado com o id: " + Long.MAX_VALUE, excecao.getMessage());
    }

    @Test
    void limpezaDeLidasAntigasDeveAtingirSomenteODestinatario() {
        long daAlice = inserirNotificacao(alice, "alice-lida", true);
        long doBruno = inserirNotificacao(bruno, "bruno-lida", true);

        int removidas = notificacaoRepository.deletarAntigasLidas(alice.getId(), LocalDateTime.now().plusDays(1));

        assertEquals(1, removidas);
        assertEquals(0, contar(daAlice));
        assertEquals(1, contar(doBruno));
    }

    private int contar(long id) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notificacao WHERE id = ?", Integer.class, id);
    }

    private long inserirNotificacao(Usuario destinatario, String titulo, boolean lida) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notificacao (tipo, titulo, mensagem, lida, usuario_id)
                VALUES ('INFO', ?, 'mensagem de teste', ?, ?)
                RETURNING id
                """, Long.class, titulo, lida, destinatario.getId());
    }

    private Boolean lida(long id) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT lida FROM notificacao WHERE id = ?", Boolean.class, id);
    }

    private Usuario criarUsuario(Role role) {
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Usuário de teste")
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

    private String json(Object... pares) throws Exception {
        var objeto = new LinkedHashMap<String, Object>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            objeto.put((String) pares[indice], pares[indice + 1]);
        }
        return objectMapper.writeValueAsString(objeto);
    }
}
