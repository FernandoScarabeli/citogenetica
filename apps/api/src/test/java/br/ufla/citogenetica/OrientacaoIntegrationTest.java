package br.ufla.citogenetica;

import java.time.Instant;
import java.util.EnumSet;
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

import br.ufla.citogenetica.laboratorio.api.LaboratorioAccessQuery;
import br.ufla.citogenetica.laboratorio.api.event.ConviteOrientacaoCriadoEvent;
import br.ufla.citogenetica.laboratorio.internal.application.LaboratorioService;
import br.ufla.citogenetica.laboratorio.internal.application.OrientacaoService;
import br.ufla.citogenetica.laboratorio.internal.application.ConviteLaboratorioCleanupService;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoVinculoOrientacao;
import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.Participacao;
import br.ufla.citogenetica.laboratorio.internal.domain.ParticipacaoPapel;
import br.ufla.citogenetica.laboratorio.internal.persistence.LaboratorioRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoPapelRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoRepository;
import br.ufla.citogenetica.shared.email.EmailSender;
import br.ufla.citogenetica.shared.persistence.UuidFactory;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class OrientacaoIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "Frase segura para convites de orientação";
    private static final String URL_FRONT = "https://front.citogenetica.test/orientacoes/convites/aceitar";
    private static final String URL_CONFIRMACAO = "https://front.citogenetica.test/confirmar-email";
    private static final String ASSUNTO = "Convite de orientação — Citogenética UFLA";
    private static final Pattern TOKEN = Pattern.compile(Pattern.quote(URL_FRONT)
            + "#token=([A-Za-z0-9_-]{43})");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(30);

    @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private LaboratorioRepository laboratorioRepository;
    @Autowired private ParticipacaoRepository participacaoRepository;
    @Autowired private ParticipacaoPapelRepository papelRepository;
    @Autowired private LaboratorioService laboratorioService;
    @Autowired private OrientacaoService orientacaoService;
    @Autowired private ConviteLaboratorioCleanupService cleanupService;
    @Autowired private LaboratorioAccessQuery laboratorioAccessQuery;
    @Autowired private UuidFactory uuidFactory;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @MockBean private EmailSender emailSender;

    @DynamicPropertySource
    static void propriedadesDeOrientacao(DynamicPropertyRegistry properties) {
        properties.add("api.security.orientation-invitations.frontend-url", () -> URL_FRONT);
        properties.add("api.security.laboratory-invitations.expiration-days", () -> 7);
        properties.add("api.security.laboratory-invitations.rate-limit.window-minutes", () -> 15);
        properties.add("api.security.laboratory-invitations.rate-limit.send-per-laboratory-author", () -> 5);
        properties.add("api.security.laboratory-invitations.rate-limit.accept-per-ip", () -> 2);
        properties.add("api.security.laboratory-invitations.rate-limit.accept-per-user", () -> 20);
    }

    @Test
    void pessoaSemContaSeCadastraConfirmaEaceitaViraMembroERecebeNotificacaoSemToken() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        UUID laboratorioId = criarLaboratorio(orientador, "Fluxo novo pesquisador");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        String email = "novo-orientado-" + UUID.randomUUID() + "@exemplo.org";
        convidar(login(orientador), laboratorioId, email).andExpect(status().isCreated());
        String token = tokenDoEmail(email, 1);

        requisicaoComCsrf(post("/api/v1/auth/register")
                        .with(request -> { request.setRemoteAddr(novoIp()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Pesquisador novo", "email", email, "senha", SENHA)))
                .andExpect(status().isAccepted());
        requisicaoComCsrf(post("/api/v1/auth/confirmar-email")
                        .with(request -> { request.setRemoteAddr(novoIp()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", tokenDeConfirmacao(email))))
                .andExpect(status().isNoContent());
        Usuario pesquisador = usuarioRepository.findByEmail(email).orElseThrow();

        byte[] hash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM convite_orientacao WHERE email_destino = ?", byte[].class, email);
        assertEquals(32, hash.length);
        assertFalse(new String(hash, java.nio.charset.StandardCharsets.UTF_8).contains(token));
        consumir(login(pesquisador), "/aceitar", token, novoIp()).andExpect(status().isNoContent());
        Participacao participacao = participacaoRepository.findByLaboratorioIdAndUsuarioIdAndEstado(
                laboratorioId, pesquisador.getId(), EstadoParticipacao.ATIVA).orElseThrow();
        assertEquals(Set.of(PapelLaboratorio.MEMBRO), papeisAtivos(participacao.getId()));
        assertEquals(1, contarVinculos(laboratorioId));
        ConviteOrientacaoCriadoEvent evento = new ConviteOrientacaoCriadoEvent(
                UUID.randomUUID(), laboratorioId, orientador.getId(), "Fluxo novo pesquisador", email);
        assertFalse(evento.toString().contains(token));
        assertFalse(java.util.Arrays.stream(ConviteOrientacaoCriadoEvent.class.getRecordComponents())
                .anyMatch(componente -> componente.getName().toLowerCase().contains("token")));
        verify(emailSender, timeout(3000)).sendEmail(eq(email), eq(ASSUNTO), anyString());
    }

    @Test
    void membroExistentePodeAceitarEHaCoorientacaoMasParAtivoOuPendenteEhUnico() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario primeiroOrientador = criarUsuario("orientador-a");
        Usuario segundoOrientador = criarUsuario("orientador-b");
        Usuario pesquisador = criarUsuario("pesquisador");
        Usuario membro = criarUsuario("membro");
        UUID laboratorioId = criarLaboratorio(admin, "Coorientação");
        adicionarParticipacao(laboratorioId, primeiroOrientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        adicionarParticipacao(laboratorioId, segundoOrientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        adicionarParticipacao(laboratorioId, pesquisador.getId(), Set.of(PapelLaboratorio.MEMBRO));
        adicionarParticipacao(laboratorioId, membro.getId(), Set.of(PapelLaboratorio.MEMBRO));

        Cookie sessaoA = login(primeiroOrientador);
        Cookie sessaoB = login(segundoOrientador);
        convidar(sessaoA, laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        convidar(sessaoA, laboratorioId, pesquisador.getEmail())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe convite de orientação pendente para este endereço; use o reenvio."));
        String outroEmail = "outro-pesquisador-" + UUID.randomUUID() + "@exemplo.org";
        convidar(sessaoA, laboratorioId, outroEmail).andExpect(status().isCreated());
        convidar(sessaoB, laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        requisicaoAutenticada(sessaoA, get("/api/v1/laboratorios/{id}/orientacoes/convites", laboratorioId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        requisicaoAutenticada(sessaoB, get("/api/v1/laboratorios/{id}/orientacoes/convites", laboratorioId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        String tokenA = tokenDoEmail(pesquisador.getEmail(), 1);
        String tokenB = tokenDoEmail(pesquisador.getEmail(), 2);
        Cookie sessaoPesquisador = login(pesquisador);
        consumir(sessaoPesquisador, "/aceitar", tokenA, novoIp()).andExpect(status().isNoContent());
        consumir(sessaoPesquisador, "/aceitar", tokenB, novoIp()).andExpect(status().isNoContent());

        UUID participacaoId = participacaoRepository.findByLaboratorioIdAndUsuarioIdAndEstado(
                laboratorioId, pesquisador.getId(), EstadoParticipacao.ATIVA).orElseThrow().getId();
        assertEquals(Set.of(PapelLaboratorio.MEMBRO), papeisAtivos(participacaoId));
        assertEquals(2, contarVinculos(laboratorioId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notificacao WHERE usuario_id = ? AND titulo = 'Convite de orientação'",
                Integer.class, pesquisador.getId()));
        convidar(sessaoA, laboratorioId, pesquisador.getEmail())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Este pesquisador já possui vínculo ativo com você neste laboratório."));

        requisicaoAutenticada(sessaoA, get("/api/v1/laboratorios/{id}/orientacoes/convites", laboratorioId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        requisicaoAutenticada(sessaoB, get("/api/v1/laboratorios/{id}/orientacoes/convites", laboratorioId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        requisicaoAutenticada(sessaoA, get("/api/v1/laboratorios/{id}/orientacoes", laboratorioId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        requisicaoAutenticada(login(pesquisador), get("/api/v1/laboratorios/{id}/orientacoes", laboratorioId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        requisicaoAutenticada(login(membro), get("/api/v1/laboratorios/{id}/orientacoes", laboratorioId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        requisicaoAutenticada(login(admin), get("/api/v1/laboratorios/{id}/orientacoes", laboratorioId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1]").exists());
    }

    @Test
    void somenteOrientadorAtivoConvidaEAutoOrientacaoConflita() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario membro = criarUsuario("membro");
        Usuario fora = criarUsuario("fora");
        UUID laboratorioId = criarLaboratorio(admin, "Autorização convite");
        adicionarParticipacao(laboratorioId, membro.getId(), Set.of(PapelLaboratorio.MEMBRO));

        convidar(login(admin), laboratorioId, "destino-" + UUID.randomUUID() + "@exemplo.org")
                .andExpect(status().isForbidden());
        convidar(login(membro), laboratorioId, "destino-" + UUID.randomUUID() + "@exemplo.org")
                .andExpect(status().isForbidden());
        convidar(login(fora), laboratorioId, "destino-" + UUID.randomUUID() + "@exemplo.org")
                .andExpect(status().isNotFound());
        convidar(login(admin), UUID.randomUUID(), "destino-" + UUID.randomUUID() + "@exemplo.org")
                .andExpect(status().isNotFound());
        adicionarParticipacao(laboratorioId, admin.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        convidar(login(admin), laboratorioId, admin.getEmail())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Você não pode enviar um convite de orientação para si mesmo."));
        assertEquals(0, contarVinculos(laboratorioId));
    }

    @Test
    void tokensInvalidosExpiradosSubstituidosRevogadosReutilizadosEDeOutraContaSaoGenericos() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        Usuario pesquisador = criarUsuario("pesquisador");
        Usuario outraConta = criarUsuario("outra-conta");
        UUID laboratorioId = criarLaboratorio(orientador, "Tokens genéricos");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        Cookie sessaoOrientador = login(orientador);
        Cookie sessaoPesquisador = login(pesquisador);

        consumir(sessaoPesquisador, "/aceitar", "token-invalido", novoIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));

        convidar(sessaoOrientador, laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        UUID conviteExpirado = conviteId(laboratorioId, orientador.getId());
        String tokenExpirado = tokenDoEmail(pesquisador.getEmail(), 1);
        jdbcTemplate.update("UPDATE convite_orientacao SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), conviteExpirado);
        consumir(sessaoPesquisador, "/aceitar", tokenExpirado, novoIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));
        requisicaoAutenticada(sessaoOrientador,
                post("/api/v1/laboratorios/{id}/orientacoes/convites/{convite}/reenviar", laboratorioId, conviteExpirado))
                .andExpect(status().isOk());
        String tokenNovo = tokenDoEmail(pesquisador.getEmail(), 2);
        consumir(sessaoPesquisador, "/aceitar", tokenExpirado, novoIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));
        consumir(login(outraConta), "/aceitar", tokenNovo, novoIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));

        String emailRevogado = "revogado-" + UUID.randomUUID() + "@exemplo.org";
        convidar(sessaoOrientador, laboratorioId, emailRevogado)
                .andExpect(status().isCreated());
        String tokenRevogado = tokenDoEmail(emailRevogado, 1);
        UUID conviteRevogado = conviteIdPorEmail(emailRevogado);
        requisicaoAutenticada(sessaoOrientador,
                delete("/api/v1/laboratorios/{id}/orientacoes/convites/{convite}", laboratorioId, conviteRevogado))
                .andExpect(status().isNoContent());
        consumir(sessaoPesquisador, "/aceitar", tokenRevogado, novoIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));

        consumir(sessaoPesquisador, "/aceitar", tokenNovo, novoIp()).andExpect(status().isNoContent());
        consumir(sessaoPesquisador, "/aceitar", tokenNovo, novoIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));
        assertEquals(1, contarVinculos(laboratorioId));
    }

    @Test
    void previaPublicaMostraLaboratorioOrientadorEEmailMascaradoEEscondeDemaisEstados() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        UUID laboratorioId = criarLaboratorio(orientador, "Prévia orientação");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        String email = "ana.silva-" + UUID.randomUUID() + "@exemplo.org";
        Cookie sessao = login(orientador);
        convidar(sessao, laboratorioId, email).andExpect(status().isCreated());
        String token = tokenDoEmail(email, 1);

        previa(token, novoIp()).andExpect(status().isOk())
                .andExpect(jsonPath("$.laboratorioNome").value("Prévia orientação"))
                .andExpect(jsonPath("$.convidadoPorNome").value(orientador.getNome()))
                .andExpect(jsonPath("$.emailMascarado").value("an***@exemplo.org"))
                .andExpect(jsonPath("$.estado").value("PENDENTE"));
        previa("token-inexistente", novoIp()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));

        jdbcTemplate.update("UPDATE convite_orientacao SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(5)), conviteIdPorEmail(email));
        previa(token, novoIp()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EXPIRADO"));

        requisicaoAutenticada(sessao, post("/api/v1/laboratorios/{id}/orientacoes/convites/{convite}/reenviar",
                laboratorioId, conviteIdPorEmail(email))).andExpect(status().isOk());
        String tokenNovo = tokenDoEmail(email, 2);
        previa(token, novoIp()).andExpect(status().isBadRequest());

        requisicaoAutenticada(sessao, delete("/api/v1/laboratorios/{id}/orientacoes/convites/{convite}",
                laboratorioId, conviteIdPorEmail(email))).andExpect(status().isNoContent());
        previa(tokenNovo, novoIp()).andExpect(status().isBadRequest());
    }

    @Test
    void previaInformaSeOConviteEParaAContaDaSessao() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        Usuario destinatario = criarUsuario("destinatario");
        Usuario outra = criarUsuario("outra");
        UUID laboratorioId = criarLaboratorio(orientador, "Prévia conta orientação");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        convidar(login(orientador), laboratorioId, destinatario.getEmail()).andExpect(status().isCreated());
        String token = tokenDoEmail(destinatario.getEmail(), 1);

        previa(token, novoIp()).andExpect(status().isOk()).andExpect(jsonPath("$.paraContaAtual").doesNotExist());
        previaComSessao(login(destinatario), token).andExpect(status().isOk()).andExpect(jsonPath("$.paraContaAtual").value(true));
        previaComSessao(login(outra), token).andExpect(status().isOk()).andExpect(jsonPath("$.paraContaAtual").value(false));
    }

    @Test
    void previaPublicaTemLimitePorEnderecoERequerCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/orientacoes/convites/previa")
                        .contentType(MediaType.APPLICATION_JSON).content(json("token", "x")))
                .andExpect(status().isForbidden());
        String ip = novoIp();
        previa("token-a", ip).andExpect(status().isBadRequest());
        previa("token-b", ip).andExpect(status().isBadRequest());
        previa("token-c", ip).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void destinatarioAceitaOuRecusaPeloSinoSemOLinkEOutraContaNao() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        Usuario pesquisador = criarUsuario("pesquisador");
        Usuario outraPessoa = criarUsuario("outra-pessoa");
        UUID laboratorioId = criarLaboratorio(orientador, "Orientação pelo sino");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        convidar(login(orientador), laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        UUID convite = conviteId(laboratorioId, orientador.getId());

        responderPeloSino(login(outraPessoa), convite, "aceitar")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));
        assertEquals(0, contarVinculos(laboratorioId));

        Cookie sessao = login(pesquisador);
        responderPeloSino(sessao, convite, "aceitar").andExpect(status().isNoContent());
        assertEquals(1, contarVinculosAtivos(laboratorioId));
        assertEquals(1, contarParticipacoes(laboratorioId, pesquisador.getId(), EstadoParticipacao.ATIVA));
        responderPeloSino(sessao, convite, "recusar").andExpect(status().isBadRequest());

        Usuario outroPesquisador = criarUsuario("pesquisador-b");
        convidar(login(orientador), laboratorioId, outroPesquisador.getEmail()).andExpect(status().isCreated());
        responderPeloSino(login(outroPesquisador), conviteIdPorEmail(outroPesquisador.getEmail()), "recusar")
                .andExpect(status().isNoContent());
        assertEquals(1, contarVinculos(laboratorioId));
    }

    @Test
    void recusaNaoCriaParticipacaoNemVinculo() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        Usuario pesquisador = criarUsuario("pesquisador");
        UUID laboratorioId = criarLaboratorio(orientador, "Recusa");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        convidar(login(orientador), laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        consumir(login(pesquisador), "/recusar", tokenDoEmail(pesquisador.getEmail(), 1), novoIp())
                .andExpect(status().isNoContent());
        assertTrue(participacaoRepository.findByLaboratorioIdAndUsuarioIdAndEstado(
                laboratorioId, pesquisador.getId(), EstadoParticipacao.ATIVA).isEmpty());
        assertEquals(0, contarVinculos(laboratorioId));
    }

    @Test
    void pesquisadorNaoEncerraMasOrientadorOuAdminPodeEncerrar() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario orientadorA = criarUsuario("orientador-a");
        Usuario orientadorB = criarUsuario("orientador-b");
        Usuario pesquisador = criarUsuario("pesquisador");
        UUID laboratorioId = criarLaboratorio(admin, "Permissões de encerramento");
        adicionarParticipacao(laboratorioId, orientadorA.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        adicionarParticipacao(laboratorioId, orientadorB.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        adicionarParticipacao(laboratorioId, pesquisador.getId(), Set.of(PapelLaboratorio.MEMBRO));
        convidar(login(orientadorA), laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        consumir(login(pesquisador), "/aceitar", tokenDoEmail(pesquisador.getEmail(), 1), novoIp())
                .andExpect(status().isNoContent());
        convidar(login(orientadorB), laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        consumir(login(pesquisador), "/aceitar", tokenDoEmail(pesquisador.getEmail(), 2), novoIp())
                .andExpect(status().isNoContent());
        UUID vinculoA = vinculoId(laboratorioId, orientadorA.getId(), pesquisador.getId());
        UUID vinculoB = vinculoId(laboratorioId, orientadorB.getId(), pesquisador.getId());

        requisicaoAutenticada(login(pesquisador), delete("/api/v1/laboratorios/{id}/orientacoes/{vinculo}", laboratorioId, vinculoA))
                .andExpect(status().isForbidden());
        requisicaoAutenticada(login(orientadorA), delete("/api/v1/laboratorios/{id}/orientacoes/{vinculo}", laboratorioId, vinculoA))
                .andExpect(status().isNoContent());
        requisicaoAutenticada(login(admin), delete("/api/v1/laboratorios/{id}/orientacoes/{vinculo}", laboratorioId, vinculoB))
                .andExpect(status().isNoContent());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vinculo_orientacao WHERE laboratorio_id = ? AND estado = 'ENCERRADO'",
                Integer.class, laboratorioId));
    }

    @Test
    void retirarPapelOrientadorComVinculoAtivoConflitaEAposEncerrarPermite() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario orientador = criarUsuario("orientador");
        Usuario pesquisador = criarUsuario("pesquisador");
        UUID laboratorioId = criarLaboratorio(admin, "Retirada papel orientação");
        UUID participacaoOrientador = adicionarParticipacao(
                laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        adicionarParticipacao(laboratorioId, pesquisador.getId(), Set.of(PapelLaboratorio.MEMBRO));
        convidar(login(orientador), laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        consumir(login(pesquisador), "/aceitar", tokenDoEmail(pesquisador.getEmail(), 1), novoIp())
                .andExpect(status().isNoContent());
        long versao = participacaoRepository.findById(participacaoOrientador).orElseThrow().getVersion();
        String rota = "/api/v1/laboratorios/{lab}/participacoes/{participacao}/papeis";

        requisicaoAutenticada(login(admin), patch(rota, laboratorioId, participacaoOrientador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("versaoEsperada", versao, "papeis", List.of("MEMBRO"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Encerre os vínculos ativos de orientação antes de retirar o papel ORIENTADOR."));

        UUID vinculo = vinculoId(laboratorioId, orientador.getId(), pesquisador.getId());
        requisicaoAutenticada(login(orientador), delete("/api/v1/laboratorios/{id}/orientacoes/{vinculo}", laboratorioId, vinculo))
                .andExpect(status().isNoContent());
        requisicaoAutenticada(login(admin), patch(rota, laboratorioId, participacaoOrientador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("versaoEsperada", versao, "papeis", List.of("MEMBRO"))))
                .andExpect(status().isOk());
        assertEquals(0, contarVinculosAtivos(laboratorioId));
    }

    @Test
    void saidaERemocaoDoPesquisadorEncerramVinculosEOrientadorComOrientandoNaoPodeSerRemovido() throws Exception {
        Usuario admin = criarUsuario("admin");
        List<Usuario> pesquisadores = List.of(criarUsuario("p-sai"), criarUsuario("p-rem"),
                criarUsuario("p-sai-orientador"), criarUsuario("p-rem-orientador"));
        List<Usuario> orientadores = List.of(criarUsuario("o-p-sai"), criarUsuario("o-p-rem"),
                criarUsuario("o-sai"), criarUsuario("o-rem"));
        UUID laboratorioId = criarLaboratorio(admin, "Saídas e remoções");
        orientadores.forEach(orientador -> adicionarParticipacao(
                laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR)));
        pesquisadores.forEach(pesquisador -> adicionarParticipacao(
                laboratorioId, pesquisador.getId(), Set.of(PapelLaboratorio.MEMBRO)));

        Cookie sessaoAdmin = login(admin);
        for (int indice = 0; indice < orientadores.size(); indice++) {
            Usuario orientador = orientadores.get(indice);
            Usuario pesquisador = pesquisadores.get(indice);
            MvcResult respostaConvite = convidar(login(orientador), laboratorioId, pesquisador.getEmail()).andReturn();
            assertEquals(201, respostaConvite.getResponse().getStatus(),
                    respostaConvite.getResponse().getContentAsString());
            consumir(login(pesquisador), "/aceitar", tokenDoEmail(pesquisador.getEmail(), 1), novoIp())
                    .andExpect(status().isNoContent());
        }

        requisicaoAutenticada(login(pesquisadores.get(0)), delete("/api/v1/laboratorios/{id}/participacoes/me", laboratorioId))
                .andExpect(status().isNoContent());
        UUID participacaoPesquisadorRemovido = participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorioId, pesquisadores.get(1).getId(), EstadoParticipacao.ATIVA)
                .orElseThrow().getId();
        requisicaoAutenticada(sessaoAdmin, delete("/api/v1/laboratorios/{id}/participacoes/{participacao}",
                        laboratorioId, participacaoPesquisadorRemovido))
                .andExpect(status().isNoContent());

        requisicaoAutenticada(login(orientadores.get(2)), delete("/api/v1/laboratorios/{id}/participacoes/me", laboratorioId))
                .andExpect(status().isNoContent());
        UUID participacaoOrientadorRemovido = participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorioId, orientadores.get(3).getId(), EstadoParticipacao.ATIVA)
                .orElseThrow().getId();
        // Quem ainda orienta alguém não pode ser removido: primeiro encerra-se a orientação.
        requisicaoAutenticada(sessaoAdmin, delete("/api/v1/laboratorios/{id}/participacoes/{participacao}",
                        laboratorioId, participacaoOrientadorRemovido))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Encerre as orientações de "
                        + orientadores.get(3).getNome() + " antes de removê-lo do laboratório."));
        requisicaoAutenticada(sessaoAdmin, delete("/api/v1/laboratorios/{id}/orientacoes/{vinculo}", laboratorioId,
                        vinculoId(laboratorioId, orientadores.get(3).getId(), pesquisadores.get(3).getId())))
                .andExpect(status().isNoContent());
        requisicaoAutenticada(sessaoAdmin, delete("/api/v1/laboratorios/{id}/participacoes/{participacao}",
                        laboratorioId, participacaoOrientadorRemovido))
                .andExpect(status().isNoContent());

        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vinculo_orientacao WHERE laboratorio_id = ? AND estado = 'ENCERRADO'",
                Integer.class, laboratorioId));
    }

    @Test
    void laboratorioDesativadoBloqueiaEnvioReenvioAceiteERecusaComRespostasPrevistas() throws Exception {
        Usuario adminLocal = criarUsuario("admin-local");
        Usuario adminGlobal = criarUsuario("admin-global", RolesEnum.ADMIN);
        Usuario orientador = criarUsuario("orientador");
        Usuario aceitar = criarUsuario("aceitar");
        Usuario recusar = criarUsuario("recusar");
        UUID laboratorioId = criarLaboratorio(adminLocal, "Laboratório inativo");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        Cookie sessaoOrientador = login(orientador);
        convidar(sessaoOrientador, laboratorioId, aceitar.getEmail()).andExpect(status().isCreated());
        String tokenAceitar = tokenDoEmail(aceitar.getEmail(), 1);
        convidar(sessaoOrientador, laboratorioId, recusar.getEmail()).andExpect(status().isCreated());
        String tokenRecusar = tokenDoEmail(recusar.getEmail(), 1);
        UUID conviteAceitar = conviteIdPorEmail(aceitar.getEmail());

        requisicaoAutenticada(login(adminGlobal), patch("/api/v1/admin/laboratorios/{id}/desativar", laboratorioId))
                .andExpect(status().isNoContent());
        convidar(sessaoOrientador, laboratorioId, "bloqueado-" + UUID.randomUUID() + "@exemplo.org")
                .andExpect(status().isConflict());
        requisicaoAutenticada(sessaoOrientador,
                post("/api/v1/laboratorios/{id}/orientacoes/convites/{convite}/reenviar", laboratorioId, conviteAceitar))
                .andExpect(status().isConflict());

        consumir(login(aceitar), "/aceitar", tokenAceitar, novoIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));
        consumir(login(recusar), "/recusar", tokenRecusar, novoIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Convite inválido ou indisponível."));
        assertEquals(0, contarVinculos(laboratorioId));
    }

    @Test
    void laboratorioDesativadoNaoPermiteEncerrarVinculo() throws Exception {
        Usuario adminLocal = criarUsuario("admin-local");
        Usuario adminGlobal = criarUsuario("admin-global", RolesEnum.ADMIN);
        Usuario orientador = criarUsuario("orientador");
        Usuario pesquisador = criarUsuario("pesquisador");
        UUID laboratorioId = criarLaboratorio(adminLocal, "Inativo sem alterações");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        convidar(login(orientador), laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        consumir(login(pesquisador), "/aceitar", tokenDoEmail(pesquisador.getEmail(), 1), novoIp())
                .andExpect(status().isNoContent());
        UUID vinculo = vinculoId(laboratorioId, orientador.getId(), pesquisador.getId());

        requisicaoAutenticada(login(adminGlobal), patch("/api/v1/admin/laboratorios/{id}/desativar", laboratorioId))
                .andExpect(status().isNoContent());
        requisicaoAutenticada(login(orientador), delete("/api/v1/laboratorios/{id}/orientacoes/{vinculo}", laboratorioId, vinculo))
                .andExpect(status().isConflict());
        requisicaoAutenticada(login(adminLocal), delete("/api/v1/laboratorios/{id}/orientacoes/{vinculo}", laboratorioId, vinculo))
                .andExpect(status().isConflict());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vinculo_orientacao WHERE laboratorio_id = ? AND estado = 'ENCERRADO'",
                Integer.class, laboratorioId));
    }

    @Test
    void aceiteConcorrenteDoMesmoTokenCriaUmVinculo() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        Usuario pesquisador = criarUsuario("pesquisador");
        UUID laboratorioId = criarLaboratorio(orientador, "Concorrência orientação");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        convidar(login(orientador), laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
        String token = tokenDoEmail(pesquisador.getEmail(), 1);
        Cookie sessao = login(pesquisador);
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> primeira = executor.submit(() -> consumirConcorrente(sessao, token, inicio));
            Future<String> segunda = executor.submit(() -> consumirConcorrente(sessao, token, inicio));
            inicio.countDown();
            List<String> resultados = List.of(primeira.get(), segunda.get());
            assertTrue(resultados.stream().anyMatch(resposta -> resposta.startsWith("204|")),
                    "Respostas concorrentes: " + resultados);
            assertTrue(resultados.stream().anyMatch(resposta -> resposta.startsWith("400|")),
                    "Respostas concorrentes: " + resultados);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, contarVinculos(laboratorioId));
        assertEquals(1, contarParticipacoes(laboratorioId, pesquisador.getId(), EstadoParticipacao.ATIVA));
    }

    @Test
    void rateLimitDeEnvioEConsumoRetornaRetryAfter() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        Usuario pesquisador = criarUsuario("pesquisador");
        UUID laboratorioId = criarLaboratorio(orientador, "Rate limit orientação");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        Cookie sessaoOrientador = login(orientador);
        for (int indice = 0; indice < 5; indice++) {
            convidar(sessaoOrientador, laboratorioId, "limite-" + indice + "-" + UUID.randomUUID() + "@exemplo.org")
                    .andExpect(status().isCreated());
        }
        convidar(sessaoOrientador, laboratorioId, "limite-final-" + UUID.randomUUID() + "@exemplo.org")
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

        Cookie sessaoPesquisador = login(pesquisador);
        String enderecoIp = "198.51.100.250";
        for (int indice = 0; indice < 2; indice++) {
            consumir(sessaoPesquisador, "/aceitar", "token-invalido-" + indice, enderecoIp)
                    .andExpect(status().isBadRequest());
        }
        consumir(sessaoPesquisador, "/aceitar", "token-invalido-3", enderecoIp)
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void limpezaExpiraConviteVencidoEApagaSeuHash() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        UUID laboratorioId = criarLaboratorio(orientador, "Limpeza orientação");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        String email = "limpeza-" + UUID.randomUUID() + "@exemplo.org";
        convidar(login(orientador), laboratorioId, email).andExpect(status().isCreated());
        UUID conviteId = conviteIdPorEmail(email);
        jdbcTemplate.update("UPDATE convite_orientacao SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), conviteId);

        assertEquals(1, cleanupService.expirarConvitesOrientacaoVencidos());
        assertEquals("EXPIRADO", jdbcTemplate.queryForObject(
                "SELECT estado FROM convite_orientacao WHERE id = ?", String.class, conviteId));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT token_hash FROM convite_orientacao WHERE id = ?", byte[].class, conviteId));
    }

    @Test
    void emailEEventoDeOrientacaoSoSaoPublicadosDepoisDoCommit() throws Exception {
        Usuario orientador = criarUsuario("orientador");
        UUID laboratorioId = criarLaboratorio(orientador, "Rollback orientação");
        adicionarParticipacao(laboratorioId, orientador.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        String email = "rollback-" + UUID.randomUUID() + "@exemplo.org";
        new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
            orientacaoService.convidar(laboratorioId, orientador.getId(), email);
            transacao.setRollbackOnly();
        });

        verify(emailSender, org.mockito.Mockito.after(300).never())
                .sendEmail(eq(email), eq(ASSUNTO), anyString());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM convite_orientacao WHERE laboratorio_id = ? AND email_destino = ?",
                Integer.class, laboratorioId, email));
    }

    @Test
    void accessQueryAplicaMatrizDeVisibilidadeEEdicaoInclusiveLaboratorioInativo() throws Exception {
        Usuario admin = criarUsuario("admin");
        Usuario orientadorA = criarUsuario("orientador-a");
        Usuario orientadorB = criarUsuario("orientador-b");
        Usuario pesquisador = criarUsuario("pesquisador");
        Usuario membro = criarUsuario("membro");
        Usuario exMembro = criarUsuario("ex-membro");
        Usuario alheio = criarUsuario("alheio");
        Usuario adminGlobal = criarUsuario("admin-global", RolesEnum.ADMIN);
        UUID laboratorioId = criarLaboratorio(admin, "Matriz de acesso");
        adicionarParticipacao(laboratorioId, orientadorA.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        adicionarParticipacao(laboratorioId, orientadorB.getId(), Set.of(PapelLaboratorio.ORIENTADOR));
        adicionarParticipacao(laboratorioId, pesquisador.getId(), Set.of(PapelLaboratorio.MEMBRO));
        adicionarParticipacao(laboratorioId, membro.getId(), Set.of(PapelLaboratorio.MEMBRO));
        adicionarParticipacao(laboratorioId, exMembro.getId(), Set.of(PapelLaboratorio.MEMBRO));

        for (Usuario orientador : List.of(orientadorA, orientadorB)) {
            convidar(login(orientador), laboratorioId, pesquisador.getEmail()).andExpect(status().isCreated());
            consumir(login(pesquisador), "/aceitar", tokenDoEmail(pesquisador.getEmail(),
                    orientador.equals(orientadorA) ? 1 : 2), novoIp()).andExpect(status().isNoContent());
        }

        assertTrue(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), pesquisador.getId()));
        assertTrue(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), orientadorA.getId()));
        assertTrue(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), orientadorB.getId()));
        assertTrue(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), admin.getId()));
        assertFalse(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), membro.getId()));
        assertFalse(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), alheio.getId()));
        assertTrue(laboratorioAccessQuery.podeEditarDadosDoPesquisador(laboratorioId, pesquisador.getId(), pesquisador.getId()));
        assertFalse(laboratorioAccessQuery.podeEditarDadosDoPesquisador(laboratorioId, pesquisador.getId(), orientadorA.getId()));
        assertFalse(laboratorioAccessQuery.podeEditarDadosDoPesquisador(laboratorioId, pesquisador.getId(), admin.getId()));

        UUID vinculoA = vinculoId(laboratorioId, orientadorA.getId(), pesquisador.getId());
        orientacaoService.encerrarVinculo(laboratorioId, vinculoA, orientadorA.getId());
        assertFalse(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), orientadorA.getId()));
        assertTrue(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), orientadorB.getId()));

        UUID participacaoExMembro = participacaoRepository.findByLaboratorioIdAndUsuarioIdAndEstado(
                laboratorioId, exMembro.getId(), EstadoParticipacao.ATIVA).orElseThrow().getId();
        laboratorioService.sair(laboratorioId, exMembro.getId());
        assertFalse(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), exMembro.getId()));
        assertFalse(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, exMembro.getId(), exMembro.getId()));
        assertTrue(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, exMembro.getId(), admin.getId()));
        assertFalse(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, alheio.getId(), admin.getId()));
        assertNotNull(participacaoExMembro);

        laboratorioService.desativar(laboratorioId, adminGlobal.getId());
        assertTrue(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), pesquisador.getId()));
        assertTrue(laboratorioAccessQuery.podeVerDadosDoPesquisador(laboratorioId, pesquisador.getId(), admin.getId()));
        assertFalse(laboratorioAccessQuery.podeEditarDadosDoPesquisador(laboratorioId, pesquisador.getId(), pesquisador.getId()));
    }

    private ResultActions convidar(Cookie sessao, UUID laboratorioId, String email) throws Exception {
        return requisicaoAutenticada(sessao, post("/api/v1/laboratorios/{id}/orientacoes/convites", laboratorioId)
                .contentType(MediaType.APPLICATION_JSON).content(json("email", email)));
    }

    private ResultActions previa(String token, String ip) throws Exception {
        return requisicaoComCsrf(post("/api/v1/orientacoes/convites/previa")
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(json("token", token)));
    }

    private ResultActions previaComSessao(Cookie sessao, String token) throws Exception {
        return requisicaoAutenticada(sessao, post("/api/v1/orientacoes/convites/previa")
                .with(request -> { request.setRemoteAddr(novoIp()); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(json("token", token)));
    }

    private ResultActions consumir(Cookie sessao, String acao, String token, String ip) throws Exception {
        return requisicaoAutenticada(sessao, post("/api/v1/orientacoes/convites" + acao)
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON).content(json("token", token)));
    }

    private ResultActions responderPeloSino(Cookie sessao, UUID conviteId, String acao) throws Exception {
        return requisicaoAutenticada(sessao, post("/api/v1/orientacoes/convites/{id}/" + acao, conviteId)
                .with(request -> { request.setRemoteAddr(novoIp()); return request; }));
    }

    private String consumirConcorrente(Cookie sessao, String token, CountDownLatch inicio) throws Exception {
        inicio.await();
        var resposta = consumir(sessao, "/aceitar", token, novoIp()).andReturn().getResponse();
        return resposta.getStatus() + "|" + resposta.getContentAsString();
    }

    private String tokenDoEmail(String email, int numeroEnvio) {
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000).atLeast(numeroEnvio)).sendEmail(eq(email), eq(ASSUNTO), corpo.capture());
        Matcher matcher = TOKEN.matcher(corpo.getAllValues().get(numeroEnvio - 1));
        assertTrue(matcher.find(), "O e-mail deve conter a URL configurada com token no fragmento.");
        return matcher.group(1);
    }

    private String tokenDeConfirmacao(String email) {
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000)).sendEmail(eq(email), eq("Confirme seu e-mail — Citogenética UFLA"), corpo.capture());
        Matcher matcher = Pattern.compile(Pattern.quote(URL_CONFIRMACAO) + "#token=([A-Za-z0-9_-]{43})")
                .matcher(corpo.getValue());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private UUID conviteId(UUID laboratorioId, Long orientadorId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM convite_orientacao WHERE laboratorio_id = ? AND orientador_usuario_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class, laboratorioId, orientadorId);
    }

    private UUID conviteIdPorEmail(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM convite_orientacao WHERE email_destino = ?", UUID.class, email);
    }

    private UUID vinculoId(UUID laboratorioId, Long orientadorId, Long pesquisadorId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM vinculo_orientacao WHERE laboratorio_id = ? AND orientador_usuario_id = ? AND pesquisador_usuario_id = ? AND estado = 'ATIVO'",
                UUID.class, laboratorioId, orientadorId, pesquisadorId);
    }

    private int contarVinculos(UUID laboratorioId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vinculo_orientacao WHERE laboratorio_id = ?", Integer.class, laboratorioId);
    }

    private int contarVinculosAtivos(UUID laboratorioId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vinculo_orientacao WHERE laboratorio_id = ? AND estado = 'ATIVO'",
                Integer.class, laboratorioId);
    }

    private int contarParticipacoes(UUID laboratorioId, Long usuarioId, EstadoParticipacao estado) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participacao WHERE laboratorio_id = ? AND usuario_id = ? AND estado = ?",
                Integer.class, laboratorioId, usuarioId, estado.name());
    }

    private Set<PapelLaboratorio> papeisAtivos(UUID participacaoId) {
        return papelRepository.findAllByParticipacaoIdAndRevogadoEmIsNull(participacaoId).stream()
                .map(ParticipacaoPapel::getPapel).collect(java.util.stream.Collectors.toSet());
    }

    private UUID criarLaboratorio(Usuario criador, String nome) {
        return laboratorioService.criar(nome, criador.getId()).id();
    }

    private UUID adicionarParticipacao(UUID laboratorioId, Long usuarioId, Set<PapelLaboratorio> papeis) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Laboratorio laboratorio = laboratorioRepository.findById(laboratorioId).orElseThrow();
            Participacao existente = participacaoRepository.findByLaboratorioIdAndUsuarioIdAndEstado(
                    laboratorioId, usuarioId, EstadoParticipacao.ATIVA).orElse(null);
            if (existente == null) {
                Participacao participacao = new Participacao(uuidFactory.generate(), laboratorio, usuarioId, Instant.now());
                participacaoRepository.save(participacao);
                existente = participacao;
            }
            Participacao participacao = existente;
            Set<PapelLaboratorio> papeisDesejados = EnumSet.of(PapelLaboratorio.MEMBRO);
            papeisDesejados.addAll(papeis);
            Set<PapelLaboratorio> papeisAtuais = papelRepository
                    .findAllByParticipacaoIdAndRevogadoEmIsNull(participacao.getId()).stream()
                    .map(ParticipacaoPapel::getPapel).collect(java.util.stream.Collectors.toSet());
            papelRepository.saveAll(papeisDesejados.stream().filter(papel -> !papeisAtuais.contains(papel))
                    .map(papel -> new ParticipacaoPapel(uuidFactory.generate(), participacao, papel,
                            laboratorio.getCriadoPorUsuarioId()))
                    .toList());
            return participacao.getId();
        });
    }

    private Usuario criarUsuario(String prefixo) {
        return criarUsuario(prefixo, RolesEnum.USER);
    }

    private Usuario criarUsuario(String prefixo, RolesEnum papelGlobal) {
        Role role = roleRepository.findByNome(papelGlobal).orElseThrow();
        String email = prefixo + "-" + UUID.randomUUID() + "@exemplo.org";
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Pessoa de teste " + prefixo)
                .email(email)
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
                .role(role)
                .emailConfirmadoEm(Instant.now())
                .build());
    }

    private Cookie login(Usuario usuario) throws Exception {
        Cookie csrf = csrfCookie();
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr(novoIp()); return request; })
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
        return mockMvc.perform(requisicao.cookie(sessao, new Cookie("XSRF-TOKEN", "csrf-orientacao-integracao"))
                .header("X-XSRF-TOKEN", "csrf-orientacao-integracao"));
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
        int valor = IP_SEQUENCE.getAndUpdate(atual -> atual >= 240 ? 30 : atual + 1);
        return "203.0.113." + valor;
    }
}
