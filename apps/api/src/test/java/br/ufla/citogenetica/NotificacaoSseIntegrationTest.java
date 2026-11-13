package br.ufla.citogenetica;

import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.notificacao.internal.domain.TipoNotificacao;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import br.ufla.citogenetica.notificacao.internal.sse.EventPublisherService;
import br.ufla.citogenetica.notificacao.internal.application.NotificacaoService;
import br.ufla.citogenetica.usuario.api.event.SessaoEncerradaEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE de notificações: cada evento vai só para o destinatário, só depois do commit, e as
 * conexões morrem junto com as sessões do usuário. Sem {@code @Transactional}, porque o
 * comportamento testado depende de commits reais.
 */
@AutoConfigureMockMvc
class NotificacaoSseIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaDeTesteSegura123!";
    private static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String TOKEN_CSRF = "csrf-de-integracao";
    private static final long ESPERA_MS = 2_000;

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
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private EventPublisherService eventPublisherService;

    private final List<Long> usuariosCriados = new ArrayList<>();
    private Usuario alice;
    private Usuario bruno;
    private Cookie sessaoAlice;
    private Cookie sessaoBruno;

    @BeforeEach
    void prepararUsuarios() throws Exception {
        alice = criarUsuario(RolesEnum.USER);
        bruno = criarUsuario(RolesEnum.USER);
        sessaoAlice = login(alice);
        sessaoBruno = login(bruno);
    }

    @AfterEach
    void limpar() {
        // Fecha as conexões que sobraram para não vazarem entre testes.
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
        eventPublisherService.sendHeartbeat();
        jdbcTemplate.update("DELETE FROM auth_rate_limit");
        usuariosCriados.forEach(id -> jdbcTemplate.update("DELETE FROM usuario WHERE id = ?", id));
    }

    @Test
    void notificacaoDeveChegarSomenteAoDestinatario() throws Exception {
        MvcResult streamAlice = assinar(sessaoAlice);
        MvcResult streamBruno = assinar(sessaoBruno);

        transactionTemplate.executeWithoutResult(status -> notificacaoService.criar(
                alice.getId(), TipoNotificacao.INFO, "segredo-da-alice", "conteúdo privado", null));

        assertTrue(conteudo(streamAlice).contains("segredo-da-alice"));
        assertFalse(conteudo(streamBruno).contains("segredo-da-alice"),
                "Outro usuário conectado não pode receber a notificação.");
    }

    @Test
    void notificacaoSoDeveSerPublicadaDepoisDoCommit() throws Exception {
        MvcResult streamAlice = assinar(sessaoAlice);

        transactionTemplate.executeWithoutResult(status -> {
            notificacaoService.criar(alice.getId(), TipoNotificacao.INFO, "antes-do-commit", null, null);
            try {
                assertFalse(conteudo(streamAlice).contains("antes-do-commit"),
                        "Evento enviado antes do commit pode anunciar dado que ainda pode sumir.");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertTrue(conteudo(streamAlice).contains("antes-do-commit"));
    }

    @Test
    void notificacaoDeTransacaoDesfeitaNaoDeveSerPublicada() throws Exception {
        MvcResult streamAlice = assinar(sessaoAlice);

        transactionTemplate.executeWithoutResult(status -> {
            notificacaoService.criar(alice.getId(), TipoNotificacao.INFO, "desfeita", null, null);
            status.setRollbackOnly();
        });

        assertFalse(conteudo(streamAlice).contains("desfeita"));
    }

    @Test
    void deveLimitarConexoesSimultaneasPorUsuario() throws Exception {
        for (int conexao = 0; conexao < 5; conexao++) {
            assinar(sessaoAlice);
        }

        requisicao(sessaoAlice, get("/api/events/subscribe").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));

        // O limite é por usuário: outro usuário continua conseguindo conectar.
        assinar(sessaoBruno);
    }

    @Test
    void desativarContaDeveEncerrarAsConexoesDoUsuario() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.ADMIN);
        MvcResult streamAlice = assinar(sessaoAlice);
        MvcResult streamBruno = assinar(sessaoBruno);

        requisicao(login(admin), patch("/api/v1/usuarios/{id}", alice.getId()))
                .andExpect(status().isNoContent());

        assertDoesNotThrow(() -> streamAlice.getAsyncResult(ESPERA_MS),
                "A conexão SSE de uma conta desativada precisa ser encerrada.");
        assertThrows(IllegalStateException.class, () -> streamBruno.getAsyncResult(200),
                "Conexões de outros usuários não podem ser afetadas.");
    }

    @Test
    void trocarSenhaDeveEncerrarConexoesDasOutrasSessoesEManterADaSessaoAtual() throws Exception {
        Cookie outraSessaoAlice = login(alice);
        MvcResult streamSessaoAtual = assinar(sessaoAlice);
        MvcResult streamOutraSessao = assinar(outraSessaoAlice);

        requisicao(sessaoAlice, put("/api/v1/usuarios/me/senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("senhaAtual", SENHA, "novaSenha", "NovaSenhaSegura456!")))
                .andExpect(status().isNoContent());

        assertDoesNotThrow(() -> streamOutraSessao.getAsyncResult(ESPERA_MS));
        assertThrows(IllegalStateException.class, () -> streamSessaoAtual.getAsyncResult(200));
    }

    @Test
    void logoutDeveEncerrarAsConexoesDaquelaSessao() throws Exception {
        MvcResult streamAlice = assinar(sessaoAlice);
        MvcResult streamOutraSessaoAlice = assinar(login(alice));
        MvcResult streamBruno = assinar(sessaoBruno);

        requisicao(sessaoAlice, post("/api/v1/auth/logout")).andExpect(status().isNoContent());

        assertDoesNotThrow(() -> streamAlice.getAsyncResult(ESPERA_MS));
        assertThrows(IllegalStateException.class, () -> streamOutraSessaoAlice.getAsyncResult(200),
                "Logout deve manter os streams das outras sessões do mesmo usuário.");
        assertThrows(IllegalStateException.class, () -> streamBruno.getAsyncResult(200),
                "Logout não pode encerrar streams de outros usuários.");
    }

    @Test
    void encerramentoDeSessaoEmTransacaoSoFechaOEmitterDepoisDoCommit() throws Exception {
        MvcResult streamAlice = assinar(sessaoAlice);
        String sessionId = sessionRepository.findByPrincipalName(alice.getId().toString())
                .keySet().stream().findFirst().orElseThrow();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new SessaoEncerradaEvent(alice.getId(), sessionId));
            assertThrows(IllegalStateException.class, () -> streamAlice.getAsyncResult(200),
                    "O stream deve permanecer aberto até o commit.");
        });

        assertDoesNotThrow(() -> streamAlice.getAsyncResult(ESPERA_MS));
    }

    @Test
    void sessaoQueDeixouDeExistirDeveTerAConexaoEncerradaNoHeartbeat() throws Exception {
        MvcResult streamAlice = assinar(sessaoAlice);
        MvcResult streamBruno = assinar(sessaoBruno);

        // Simula expiração ou revogação feita por outra instância da API.
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", alice.getId().toString());
        eventPublisherService.sendHeartbeat();

        assertDoesNotThrow(() -> streamAlice.getAsyncResult(ESPERA_MS));
        assertThrows(IllegalStateException.class, () -> streamBruno.getAsyncResult(200));
    }

    @Test
    void assinaturaSemSessaoDeveResponder401() throws Exception {
        mockMvc.perform(get("/api/events/subscribe").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contagemGlobalDeInscritosNaoDeveExistir() throws Exception {
        requisicao(sessaoAlice, get("/api/events/subscribers/count"))
                .andExpect(status().isNotFound());
    }

    private MvcResult assinar(Cookie sessao) throws Exception {
        return requisicao(sessao, get("/api/events/subscribe").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private String conteudo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString();
    }

    private Usuario criarUsuario(RolesEnum roleNome) {
        Role role = roleRepository.findByNome(roleNome).orElseThrow();
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Usuário de teste")
                .email(UUID.randomUUID() + "@citogenetica.test")
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
                .emailConfirmadoEm(java.time.Instant.now())
                .role(role)
                .build());
        usuariosCriados.add(usuario.getId());
        return usuario;
    }

    private Cookie login(Usuario usuario) throws Exception {
        MvcResult resultado = requisicaoComCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "senha", SENHA)))
                .andExpect(status().isNoContent())
                .andReturn();
        return resultado.getResponse().getCookie(COOKIE_SESSAO);
    }

    private ResultActions requisicao(Cookie sessao, MockHttpServletRequestBuilder requisicao) throws Exception {
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
