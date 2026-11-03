package br.ufla.citogenetica;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.ufla.citogenetica.laboratorio.internal.application.LaboratorioService;
import br.ufla.citogenetica.laboratorio.internal.application.ConviteLaboratorioCleanupService;
import br.ufla.citogenetica.laboratorio.internal.application.ConviteLaboratorioService;
import br.ufla.citogenetica.laboratorio.api.event.ConviteLaboratorioCriadoEvent;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao;
import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.Participacao;
import br.ufla.citogenetica.laboratorio.internal.domain.ParticipacaoPapel;
import br.ufla.citogenetica.laboratorio.internal.persistence.ConviteLaboratorioRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.LaboratorioRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoPapelRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoRepository;
import br.ufla.citogenetica.shared.email.EmailSender;
import br.ufla.citogenetica.shared.persistence.UuidFactory;
import br.ufla.citogenetica.shared.web.error.ConflictException;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConviteLaboratorioIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "Frase segura para convites de laboratório";
    private static final String URL_FRONT = "https://front.citogenetica.test/convites/aceitar";
    private static final String URL_CONFIRMACAO = "https://front.citogenetica.test/confirmar-email";
    private static final Pattern TOKEN = Pattern.compile(Pattern.quote(URL_FRONT)
            + "#token=([A-Za-z0-9_-]{43})");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(10);

    @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private LaboratorioRepository laboratorioRepository;
    @Autowired private ParticipacaoRepository participacaoRepository;
    @Autowired private ParticipacaoPapelRepository papelRepository;
    @Autowired private ConviteLaboratorioRepository conviteRepository;
    @Autowired private ConviteLaboratorioCleanupService cleanupService;
    @Autowired private ConviteLaboratorioService conviteService;
    @Autowired private LaboratorioService laboratorioService;
    @Autowired private UuidFactory uuidFactory;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private EmailSender emailSender;

    @DynamicPropertySource
    static void propriedadesDeConvite(DynamicPropertyRegistry properties) {
        properties.add("api.security.laboratory-invitations.frontend-url", () -> URL_FRONT);
        properties.add("api.security.laboratory-invitations.expiration-days", () -> 7);
        properties.add("api.security.laboratory-invitations.rate-limit.window-minutes", () -> 15);
        properties.add("api.security.laboratory-invitations.rate-limit.send-per-laboratory-author", () -> 2);
        properties.add("api.security.laboratory-invitations.rate-limit.accept-per-ip", () -> 2);
        properties.add("api.security.laboratory-invitations.rate-limit.accept-per-user", () -> 3);
    }

    @Test
    void envioExigeAdminLocalEEscondeLaboratorioAlheio() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario membro = criarUsuario("membro");
        Usuario alheio = criarUsuario("alheio");
        UUID laboratorioId = criarLaboratorio(admin, "Acesso convite");
        adicionarParticipacao(laboratorioId, membro.getId(), admin.getId(), Set.of(PapelLaboratorio.MEMBRO));

        requisicaoAutenticada(login(membro), convite(laboratorioId))
                .andExpect(status().isForbidden());
        requisicaoAutenticada(login(alheio), convite(laboratorioId))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM convite_laboratorio WHERE laboratorio_id = ?", Integer.class, laboratorioId));
    }

    @Test
    void conviteParaParticipanteAtivoRetornaConflito() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario membro = criarUsuario("membro");
        UUID laboratorioId = criarLaboratorio(admin, "Já participa");
        adicionarParticipacao(laboratorioId, membro.getId(), admin.getId(), Set.of(PapelLaboratorio.MEMBRO));

        requisicaoAutenticada(login(admin), post("/api/v1/laboratorios/{id}/convites", laboratorioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", membro.getEmail(), "papeis", List.of("MEMBRO"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Este endereço já participa ativamente do laboratório."));
    }

    @Test
    void destinatarioSemContaPodeSeCadastrarConfirmarEAceitar() throws Exception {
        Usuario admin = criarUsuario("admin");
        UUID laboratorioId = criarLaboratorio(admin, "Aceite completo");
        String email = "novo-convidado-" + UUID.randomUUID() + "@exemplo.org";
        Cookie sessaoAdmin = login(admin);
        criarConvite(sessaoAdmin, laboratorioId, email, List.of("MEMBRO", "ORIENTADOR"));
        String tokenConvite = tokenDoEmail(email, "Convite para participar de um laboratório — Citogenética UFLA");

        MvcResult cadastro = requisicaoComCsrf(post("/api/v1/auth/register")
                        .with(request -> { request.setRemoteAddr(novoIp()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Convidado novo", "email", email, "senha", SENHA)))
                .andExpect(status().isAccepted()).andReturn();
        assertNotNull(cadastro);
        String tokenConfirmacao = tokenDeConfirmacaoDoEmail(email);
        requisicaoComCsrf(post("/api/v1/auth/confirmar-email")
                        .with(request -> { request.setRemoteAddr(novoIp()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", tokenConfirmacao)))
                .andExpect(status().isNoContent());
        Usuario destinatario = usuarioRepository.findByEmail(email).orElseThrow();
        Cookie sessao = login(destinatario);

        consumir(sessao, "/aceitar", tokenConvite, novoIp()).andExpect(status().isNoContent());

        Participacao participacao = participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorioId, destinatario.getId(), EstadoParticipacao.ATIVA)
                .orElseThrow();
        assertEquals(Set.of(PapelLaboratorio.MEMBRO, PapelLaboratorio.ORIENTADOR),
                papeisAtivos(participacao.getId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM convite_laboratorio WHERE laboratorio_id = ? AND estado = 'ACEITO'",
                Integer.class, laboratorioId));
    }

    @Test
    void conviteSoPodeSerAceitoPeloEmailConfirmadoDaSessao() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario destinatario = criarUsuario("destinatario");
        Usuario outraPessoa = criarUsuario("outra-pessoa");
        UUID laboratorioId = criarLaboratorio(admin, "Destinatário");
        criarConvite(login(admin), laboratorioId, destinatario.getEmail(), List.of("MEMBRO"));
        String token = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA");

        consumir(login(outraPessoa), "/aceitar", token, novoIp())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));
        assertTrue(participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorioId, outraPessoa.getId(), EstadoParticipacao.ATIVA)
                .isEmpty());
    }

    @Test
    void hashDoTokenFicaNoBancoEOEmailUsaUrlConfigurada() throws Exception {
        Usuario admin = criarUsuario("admin");
        UUID laboratorioId = criarLaboratorio(admin, "Hash seguro");
        String email = "hash-" + UUID.randomUUID() + "@exemplo.org";
        criarConvite(login(admin), laboratorioId, email, List.of("MEMBRO"));
        String token = tokenDoEmail(email, "Convite para participar de um laboratório — Citogenética UFLA");
        UUID conviteId = jdbcTemplate.queryForObject(
                "SELECT id FROM convite_laboratorio WHERE laboratorio_id = ?", UUID.class, laboratorioId);
        byte[] hashBanco = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM convite_laboratorio WHERE id = ?", byte[].class, conviteId);
        assertEquals(32, hashBanco.length);
        assertTrue(MessageDigest.isEqual(hashBanco,
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))));
        assertFalse(new String(hashBanco, StandardCharsets.UTF_8).contains(token));
        assertTrue(TOKEN.matcher(URL_FRONT + "#token=" + token).matches());
    }

    @Test
    void expiradoReutilizadoSubstituidoERevogadoFalhamSemAceitar() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario destinatario = criarUsuario("destinatario");
        UUID laboratorioId = criarLaboratorio(admin, "Estados do convite");
        Cookie sessaoAdmin = login(admin);
        Cookie sessaoDestinatario = login(destinatario);

        criarConvite(sessaoAdmin, laboratorioId, destinatario.getEmail(), List.of("MEMBRO"));
        String tokenExpirado = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA");
        UUID conviteId = conviteId(laboratorioId);
        jdbcTemplate.update("UPDATE convite_laboratorio SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(5)), conviteId);
        consumir(sessaoDestinatario, "/aceitar", tokenExpirado, novoIp()).andExpect(status().isConflict());

        requisicaoAutenticada(sessaoAdmin, post("/api/v1/laboratorios/{id}/convites/{convite}/reenviar", laboratorioId, conviteId))
                .andExpect(status().isOk());
        String tokenSubstituido = tokenExpirado;
        String tokenAtual = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA", 2);
        consumir(sessaoDestinatario, "/aceitar", tokenSubstituido, novoIp()).andExpect(status().isConflict());

        requisicaoAutenticada(sessaoAdmin, delete("/api/v1/laboratorios/{id}/convites/{convite}", laboratorioId, conviteId))
                .andExpect(status().isNoContent());
        consumir(sessaoDestinatario, "/aceitar", tokenAtual, novoIp()).andExpect(status().isConflict());
        assertTrue(participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorioId, destinatario.getId(), EstadoParticipacao.ATIVA)
                .isEmpty());
    }

    @Test
    void conviteAceitoNaoPodeSerReutilizadoERecusaNaoCriaParticipacao() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario destinatario = criarUsuario("destinatario");
        UUID primeiroLab = criarLaboratorio(admin, "Reuso");
        criarConvite(login(admin), primeiroLab, destinatario.getEmail(), List.of("MEMBRO"));
        String tokenAceito = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA");
        Cookie sessaoDestinatario = login(destinatario);

        consumir(sessaoDestinatario, "/aceitar", tokenAceito, novoIp()).andExpect(status().isNoContent());
        consumir(sessaoDestinatario, "/aceitar", tokenAceito, novoIp()).andExpect(status().isConflict());

        Usuario outroAdmin = criarUsuario("outro-admin");
        UUID segundoLab = criarLaboratorio(outroAdmin, "Recusa");
        criarConvite(login(outroAdmin), segundoLab, destinatario.getEmail(), List.of("MEMBRO"));
        String tokenRecusado = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA", 2);
        consumir(sessaoDestinatario, "/recusar", tokenRecusado, novoIp()).andExpect(status().isNoContent());
        assertTrue(participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(segundoLab, destinatario.getId(), EstadoParticipacao.ATIVA)
                .isEmpty());
    }

    @Test
    void aceiteConcorrenteDoMesmoTokenCriaUmaParticipacao() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario destinatario = criarUsuario("destinatario");
        UUID laboratorioId = criarLaboratorio(admin, "Concorrência do token");
        criarConvite(login(admin), laboratorioId, destinatario.getEmail(), List.of("MEMBRO"));
        String token = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA");
        Cookie sessao = login(destinatario);
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> um = executor.submit(() -> consumirConcorrente(sessao, token, inicio));
            Future<Integer> dois = executor.submit(() -> consumirConcorrente(sessao, token, inicio));
            inicio.countDown();
            List<Integer> resultados = List.of(um.get(), dois.get());
            assertTrue(resultados.contains(204));
            assertTrue(resultados.contains(409));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, contarParticipacoes(laboratorioId, destinatario.getId(), EstadoParticipacao.ATIVA));
    }

    @Test
    void doisConvitesDeLaboratoriosDiferentesPodemSerAceitosConcorrentemente() throws Exception {
        Usuario adminA = criarUsuario("admin-a");
        Usuario adminB = criarUsuario("admin-b");
        Usuario destinatario = criarUsuario("destinatario");
        UUID labA = criarLaboratorio(adminA, "Concorrência A");
        UUID labB = criarLaboratorio(adminB, "Concorrência B");
        criarConvite(login(adminA), labA, destinatario.getEmail(), List.of("MEMBRO"));
        String tokenA = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA");
        criarConvite(login(adminB), labB, destinatario.getEmail(), List.of("MEMBRO"));
        String tokenB = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA", 2);
        Cookie sessao = login(destinatario);
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> um = executor.submit(() -> consumirConcorrente(sessao, tokenA, inicio));
            Future<Integer> dois = executor.submit(() -> consumirConcorrente(sessao, tokenB, inicio));
            inicio.countDown();
            assertEquals(204, um.get());
            assertEquals(204, dois.get());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, contarParticipacoes(labA, destinatario.getId(), EstadoParticipacao.ATIVA));
        assertEquals(1, contarParticipacoes(labB, destinatario.getId(), EstadoParticipacao.ATIVA));
    }

    @Test
    void reingressoAposSaidaCriaParticipacaoHistoricaNova() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario destinatario = criarUsuario("destinatario");
        UUID laboratorioId = criarLaboratorio(admin, "Reingresso");
        Cookie sessaoAdmin = login(admin);
        Cookie sessaoDestinatario = login(destinatario);
        criarConvite(sessaoAdmin, laboratorioId, destinatario.getEmail(), List.of("MEMBRO"));
        String primeiroToken = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA");
        consumir(sessaoDestinatario, "/aceitar", primeiroToken, novoIp()).andExpect(status().isNoContent());
        requisicaoAutenticada(sessaoDestinatario, delete("/api/v1/laboratorios/{id}/participacoes/me", laboratorioId))
                .andExpect(status().isNoContent());

        criarConvite(sessaoAdmin, laboratorioId, destinatario.getEmail(), List.of("MEMBRO"));
        String segundoToken = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA", 2);
        consumir(sessaoDestinatario, "/aceitar", segundoToken, novoIp()).andExpect(status().isNoContent());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participacao WHERE laboratorio_id = ? AND usuario_id = ?",
                Integer.class, laboratorioId, destinatario.getId()));
        assertEquals(1, contarParticipacoes(laboratorioId, destinatario.getId(), EstadoParticipacao.ATIVA));
        assertEquals(1, contarParticipacoes(laboratorioId, destinatario.getId(), EstadoParticipacao.SAIU));
    }

    @Test
    void laboratorioDesativadoNaoAceitaConvite() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario adminGlobal = criarUsuario("admin-global", RolesEnum.ADMIN, StatusEnum.ATIVO, true);
        Usuario destinatario = criarUsuario("destinatario");
        UUID laboratorioId = criarLaboratorio(admin, "Desativado");
        Cookie sessaoAdmin = login(admin);
        criarConvite(sessaoAdmin, laboratorioId, destinatario.getEmail(), List.of("MEMBRO"));
        String token = tokenDoEmail(destinatario.getEmail(), "Convite para participar de um laboratório — Citogenética UFLA");
        requisicaoAutenticada(login(adminGlobal), patch("/api/v1/admin/laboratorios/{id}/desativar", laboratorioId))
                .andExpect(status().isNoContent());

        consumir(login(destinatario), "/aceitar", token, novoIp()).andExpect(status().isConflict());
        UUID conviteId = conviteId(laboratorioId);
        requisicaoAutenticada(sessaoAdmin, post("/api/v1/laboratorios/{id}/convites/{convite}/reenviar",
                        laboratorioId, conviteId))
                .andExpect(status().isConflict());
        requisicaoAutenticada(sessaoAdmin, post("/api/v1/laboratorios/{id}/convites", laboratorioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", "mais-um-" + UUID.randomUUID() + "@exemplo.org", "papeis", List.of("MEMBRO"))))
                .andExpect(status().isConflict());
        requisicaoAutenticada(sessaoAdmin, delete("/api/v1/laboratorios/{id}/convites/{convite}", laboratorioId, conviteId))
                .andExpect(status().isNoContent());
    }

    @Test
    void notificacaoEEmailSaoEmitidosAposCommitEEventoNaoContemToken() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario destinatario = criarUsuario("destinatario");
        UUID laboratorioId = criarLaboratorio(admin, "Evento pós commit");
        String email = destinatario.getEmail();
        criarConvite(login(admin), laboratorioId, email, List.of("MEMBRO"));
        String token = tokenDoEmail(email, "Convite para participar de um laboratório — Citogenética UFLA");

        verify(emailSender, timeout(3000)).sendEmail(eq(email), eq("Convite para participar de um laboratório — Citogenética UFLA"), anyString());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notificacao WHERE usuario_id = ? AND titulo = 'Convite para laboratório'",
                Integer.class, destinatario.getId()));
        assertFalse(conviteRepository.findAll().get(0).toString().contains(token));
        assertFalse(conviteRepository.findAll().get(0).toString().contains(email));
        assertFalse(Arrays.stream(ConviteLaboratorioCriadoEvent.class.getRecordComponents())
                .anyMatch(componente -> componente.getName().toLowerCase().contains("token")));
        ConviteLaboratorioCriadoEvent evento = new ConviteLaboratorioCriadoEvent(UUID.randomUUID(), laboratorioId,
                admin.getId(), "Evento pós commit", email, Set.of("MEMBRO"));
        assertFalse(evento.toString().contains(email));

        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        String emailRollback = "rollback-" + UUID.randomUUID() + "@exemplo.org";
        transacao.executeWithoutResult(status -> {
            conviteService.criar(laboratorioId, admin.getId(), emailRollback, Set.of(PapelLaboratorio.MEMBRO));
            status.setRollbackOnly();
        });
        verify(emailSender, org.mockito.Mockito.after(300).never()).sendEmail(eq(emailRollback), anyString(), anyString());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM convite_laboratorio WHERE laboratorio_id = ? AND email_destino = ?",
                Integer.class, laboratorioId, emailRollback));
    }

    @Test
    void limitesDeEnvioEAceiteRetornam429ComRetryAfter() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario destinatario = criarUsuario("destinatario");
        UUID laboratorioId = criarLaboratorio(admin, "Limites");
        Cookie sessaoAdmin = login(admin);
        String email = destinatario.getEmail();
        criarConvite(sessaoAdmin, laboratorioId, email, List.of("MEMBRO"));
        criarConvite(sessaoAdmin, laboratorioId, "outro-" + UUID.randomUUID() + "@exemplo.org", List.of("MEMBRO"));
        requisicaoAutenticada(sessaoAdmin, post("/api/v1/laboratorios/{id}/convites", laboratorioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", "limite-" + UUID.randomUUID() + "@exemplo.org", "papeis", List.of("MEMBRO"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        consumir(login(destinatario), "/aceitar", "token-invalido-a", "203.0.113.40")
                .andExpect(status().isConflict());
        consumir(login(destinatario), "/aceitar", "token-invalido-b", "203.0.113.40")
                .andExpect(status().isConflict());
        consumir(login(destinatario), "/aceitar", "token-invalido-c", "203.0.113.40")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void limpezaExpiraConvitesEmLotes() throws Exception {
        Usuario admin = criarUsuario("admin");
        UUID laboratorioId = criarLaboratorio(admin, "Limpeza");
        String email = "limpeza-" + UUID.randomUUID() + "@exemplo.org";
        criarConvite(login(admin), laboratorioId, email, List.of("MEMBRO"));
        UUID id = conviteId(laboratorioId);
        jdbcTemplate.update("UPDATE convite_laboratorio SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), id);
        assertEquals(1, cleanupService.expirarVencidos());
        assertEquals("EXPIRADO", jdbcTemplate.queryForObject(
                "SELECT estado FROM convite_laboratorio WHERE id = ?", String.class, id));
    }

    private MockHttpServletRequestBuilder convite(UUID laboratorioId) throws Exception {
        return post("/api/v1/laboratorios/{id}/convites", laboratorioId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", "acesso-" + UUID.randomUUID() + "@exemplo.org", "papeis", List.of("MEMBRO")));
    }

    private int contarParticipacoes(UUID laboratorioId, Long usuarioId, EstadoParticipacao estado) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM participacao WHERE laboratorio_id = ? AND usuario_id = ? AND estado = ?",
                Integer.class, laboratorioId, usuarioId, estado.name());
    }

    private void criarConvite(Cookie sessaoAdmin, UUID laboratorioId, String email, List<String> papeis) throws Exception {
        requisicaoAutenticada(sessaoAdmin, post("/api/v1/laboratorios/{id}/convites", laboratorioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "papeis", papeis)))
                .andExpect(status().isCreated());
    }

    private ResultActions consumir(Cookie sessao, String acao, String token, String ip) throws Exception {
        return requisicaoAutenticada(sessao, post("/api/v1/convites" + acao)
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(json("token", token)));
    }

    private int consumirConcorrente(Cookie sessao, String token, CountDownLatch inicio) throws Exception {
        inicio.await();
        return consumir(sessao, "/aceitar", token, novoIp()).andReturn().getResponse().getStatus();
    }

    private String tokenDoEmail(String email, String assunto) {
        return tokenDoEmail(email, assunto, 1);
    }

    private String tokenDoEmail(String email, String assunto, int numeroEnvio) {
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000).times(numeroEnvio)).sendEmail(eq(email), eq(assunto), corpo.capture());
        Matcher matcher = TOKEN.matcher(corpo.getAllValues().get(numeroEnvio - 1));
        assertTrue(matcher.find(), "O e-mail deve conter URL configurada com token no fragmento.");
        return matcher.group(1);
    }

    private String tokenDeConfirmacaoDoEmail(String email) {
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000)).sendEmail(eq(email), eq("Confirme seu e-mail — Citogenética UFLA"), corpo.capture());
        Matcher matcher = Pattern.compile(Pattern.quote(URL_CONFIRMACAO) + "#token=([A-Za-z0-9_-]{43})")
                .matcher(corpo.getValue());
        assertTrue(matcher.find(), "O e-mail de confirmação deve usar a URL configurada.");
        return matcher.group(1);
    }

    private UUID conviteId(UUID laboratorioId) {
        return jdbcTemplate.queryForObject("SELECT id FROM convite_laboratorio WHERE laboratorio_id = ?",
                UUID.class, laboratorioId);
    }

    private Set<PapelLaboratorio> papeisAtivos(UUID participacaoId) {
        return papelRepository.findAllByParticipacaoIdAndRevogadoEmIsNull(participacaoId).stream()
                .map(ParticipacaoPapel::getPapel).collect(java.util.stream.Collectors.toSet());
    }

    private UUID criarLaboratorio(Usuario criador, String nome) {
        return laboratorioService.criar(nome, criador.getId()).id();
    }

    private void adicionarParticipacao(UUID laboratorioId, Long usuarioId, Long concedidoPor,
                                       Set<PapelLaboratorio> papeis) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Laboratorio laboratorio = laboratorioRepository.findById(laboratorioId).orElseThrow();
            Participacao participacao = new Participacao(uuidFactory.generate(), laboratorio, usuarioId, Instant.now());
            participacaoRepository.save(participacao);
            papelRepository.saveAll(papeis.stream()
                    .map(papel -> new ParticipacaoPapel(uuidFactory.generate(), participacao, papel, concedidoPor))
                    .toList());
        });
    }

    private Usuario criarUsuario(String prefixo) {
        return criarUsuario(prefixo, RolesEnum.USER, StatusEnum.ATIVO, true);
    }

    private Usuario criarUsuario(String prefixo, RolesEnum papelGlobal, StatusEnum estado, boolean confirmado) {
        Role role = roleRepository.findByNome(papelGlobal).orElseThrow();
        String email = prefixo + "-" + UUID.randomUUID() + "@exemplo.org";
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Pessoa de teste " + prefixo)
                .email(email)
                .senha(passwordEncoder.encode(SENHA))
                .status(estado)
                .role(role)
                .emailConfirmadoEm(confirmado ? Instant.now() : null)
                .build());
    }

    private Cookie login(Usuario usuario) throws Exception {
        Cookie csrf = csrfCookie();
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "senha", SENHA)))
                .andExpect(status().isNoContent()).andReturn();
        Cookie sessao = resultado.getResponse().getCookie("CITOGENETICA_SESSION");
        assertNotNull(sessao);
        return sessao;
    }

    private Cookie csrfCookie() throws Exception {
        Cookie csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrf);
        return csrf;
    }

    private ResultActions requisicaoAutenticada(Cookie sessao, MockHttpServletRequestBuilder requisicao)
            throws Exception {
        return mockMvc.perform(requisicao.cookie(sessao, new Cookie("XSRF-TOKEN", "csrf-convite-integracao"))
                .header("X-XSRF-TOKEN", "csrf-convite-integracao"));
    }

    private ResultActions requisicaoComCsrf(MockHttpServletRequestBuilder requisicao) throws Exception {
        Cookie csrf = csrfCookie();
        return mockMvc.perform(requisicao.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()));
    }

    private String json(Object... pares) throws Exception {
        var objeto = new java.util.LinkedHashMap<String, Object>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            objeto.put((String) pares[indice], pares[indice + 1]);
        }
        return objectMapper.writeValueAsString(objeto);
    }

    private String novoIp() {
        return "198.51.100." + IP_SEQUENCE.getAndIncrement();
    }
}
