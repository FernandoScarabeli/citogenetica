package br.ufla.citogenetica;

import br.ufla.citogenetica.config.TestSecrets;
import br.ufla.citogenetica.shared.email.EmailSender;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.api.error.EmailConfirmationException;
import br.ufla.citogenetica.usuario.internal.application.EmailChangeService;
import br.ufla.citogenetica.usuario.internal.application.EmailConfirmationCleanupService;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.RecoveryChallengeRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import br.ufla.citogenetica.usuario.internal.web.dto.request.SolicitarTrocaEmailRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EmailChangeConfirmationIntegrationTest extends AbstractIntegrationTest {

    private static final String CURRENT_PASSWORD = "Uma frase correta para trocar e-mail 2026";
    private static final String FRONTEND_URL = "https://front.citogenetica.test/confirmar-troca-email";
    private static final String CONFIRMATION_SUBJECT = "Confirme a alteração do seu e-mail — Citogenética UFLA";
    private static final String OLD_ADDRESS_SUBJECT = "Aviso de alteração do e-mail — Citogenética UFLA";
    private static final String CSRF_VALUE = "csrf-de-integracao-email";
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            Pattern.quote(FRONTEND_URL) + "#token=([A-Za-z0-9_-]{43})");

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
    private RecoveryChallengeRepository recoveryChallengeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EmailChangeService emailChangeService;

    @Autowired
    private EmailConfirmationCleanupService cleanupService;

    @MockBean
    private EmailSender emailSender;

    private Role roleUser;

    @DynamicPropertySource
    static void emailChangeProperties(DynamicPropertyRegistry properties) {
        properties.add("api.security.recovery.hmac-secret", TestSecrets::generate);
        properties.add("api.security.email-change-confirmation.frontend-url", () -> FRONTEND_URL);
        properties.add("api.security.email-change-confirmation.expiration-hours", () -> 24);
        properties.add("api.security.rate-limit.window-minutes", () -> 15);
        properties.add("api.security.rate-limit.change-email.ip", () -> 2);
        properties.add("api.security.rate-limit.change-email.user", () -> 3);
        properties.add("api.security.rate-limit.confirm-email-change.ip", () -> 2);
        properties.add("api.security.rate-limit.confirm-email-change.user", () -> 3);
        properties.add("api.security.rate-limit.login.ip", () -> 100);
        properties.add("api.security.rate-limit.login.email", () -> 100);
    }

    @BeforeEach
    void carregarPapel() {
        roleUser = roleRepository.findByNome(RolesEnum.USER).orElseThrow();
    }

    @Test
    void trocaConfirmadaAtualizaLoginRevogaSessoesEAviaEnderecoAntigo() throws Exception {
        String oldEmail = emailNovo();
        String newEmail = emailNovo();
        Usuario usuario = criarUsuario(oldEmail, StatusEnum.ATIVO, true);
        Cookie sessaoAntes = login(oldEmail, CURRENT_PASSWORD);
        Cookie segundaSessaoAntes = login(oldEmail, CURRENT_PASSWORD);
        Instant recoveryCreatedAt = Instant.now();
        recoveryChallengeRepository.replace(usuario.getId(), UUID.randomUUID(), new byte[32], recoveryCreatedAt,
                recoveryCreatedAt.plusSeconds(900));

        assertEquals(202, solicitar(sessaoAntes, newEmail, CURRENT_PASSWORD, ipNovo())
                .andExpect(status().isAccepted()).andReturn().getResponse().getStatus());
        String token = capturarToken(newEmail);
        byte[] tokenHash = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
        byte[] hashPersistido = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM desafio_troca_email WHERE usuario_id = ?", byte[].class, usuario.getId());
        assertArrayEquals(tokenHash, hashPersistido);
        assertEquals(32, hashPersistido.length);

        login(oldEmail, CURRENT_PASSWORD);
        requisicaoComCsrf(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", newEmail, "senha", CURRENT_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos."));

        MvcResult resultadoConfirmacao = confirmar(token, ipNovo())
                .andExpect(status().isNoContent()).andReturn();
        assertFalse(List.of(resultadoConfirmacao.getResponse().getCookies()).stream()
                .anyMatch(cookie -> "CITOGENETICA_SESSION".equals(cookie.getName())));
        assertEquals(newEmail, usuarioRepository.findById(usuario.getId()).orElseThrow().getEmail());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM desafio_recuperacao_senha
                WHERE usuario_id = ? AND invalidado_em IS NOT NULL AND consumido_em IS NULL
                """, Integer.class, usuario.getId()));

        mockMvc.perform(get("/api/v1/usuarios/me").cookie(sessaoAntes)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/usuarios/me").cookie(segundaSessaoAntes)).andExpect(status().isUnauthorized());
        requisicaoComCsrf(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", oldEmail, "senha", CURRENT_PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(newEmail, CURRENT_PASSWORD);

        ArgumentCaptor<String> oldAddressBody = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000)).sendEmail(eq(oldEmail), eq(OLD_ADDRESS_SUBJECT), oldAddressBody.capture());
        assertTrue(oldAddressBody.getValue().contains("procure o suporte"));
        assertFalse(oldAddressBody.getValue().contains("#token="));
    }

    @Test
    void senhaAtualAcimaDoLimiteDoBcryptRetornaSenhaIncorretaSemErroInterno() throws Exception {
        String currentEmail = emailNovo();
        criarUsuario(currentEmail, StatusEnum.ATIVO, true);
        Cookie session = login(currentEmail, CURRENT_PASSWORD);

        requisicaoAutenticada(session, post("/api/v1/usuarios/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("novoEmail", emailNovo(), "senhaAtual", "é".repeat(40))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A senha atual está incorreta."));
    }

    @Test
    void senhaErradaERequisicoesParaEmailAtualOuOcupadoNaoRevelamDisponibilidade() throws Exception {
        String currentEmail = emailNovo();
        String occupiedEmail = emailNovo();
        Usuario owner = criarUsuario(currentEmail, StatusEnum.ATIVO, true);
        criarUsuario(occupiedEmail, StatusEnum.ATIVO, true);
        Cookie session = login(currentEmail, CURRENT_PASSWORD);
        clearInvocations(emailSender);

        requisicaoAutenticada(session, post("/api/v1/usuarios/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("novoEmail", emailNovo(), "senhaAtual", "senha incorreta")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A senha atual está incorreta."));

        MvcResult sameEmail = solicitar(session, currentEmail.toUpperCase(), CURRENT_PASSWORD, ipNovo())
                .andExpect(status().isAccepted()).andReturn();
        MvcResult occupied = solicitar(session, occupiedEmail, CURRENT_PASSWORD, ipNovo())
                .andExpect(status().isAccepted()).andReturn();
        assertEquals(sameEmail.getResponse().getStatus(), occupied.getResponse().getStatus());
        assertEquals(sameEmail.getResponse().getContentAsString(), occupied.getResponse().getContentAsString());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM desafio_troca_email WHERE usuario_id = ?", Integer.class, owner.getId()));
        verify(emailSender, org.mockito.Mockito.after(300).never()).sendEmail(anyString(), eq(CONFIRMATION_SUBJECT), anyString());
    }

    @Test
    void apenasContaAtivaComEmailConfirmadoPodeSolicitarTroca() throws Exception {
        Usuario pending = criarUsuario(emailNovo(), StatusEnum.ATIVO, false);
        Usuario inactive = criarUsuario(emailNovo(), StatusEnum.INATIVO, true);
        SolicitarTrocaEmailRequestDTO request = new SolicitarTrocaEmailRequestDTO(emailNovo(), CURRENT_PASSWORD);

        assertThrows(EmailConfirmationException.class,
                () -> emailChangeService.solicitarTrocaEmail(pending.getId(), request));
        assertThrows(EmailConfirmationException.class,
                () -> emailChangeService.solicitarTrocaEmail(inactive.getId(), request));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM desafio_troca_email WHERE usuario_id IN (?, ?)", Integer.class,
                pending.getId(), inactive.getId()));
    }

    @Test
    void linkSoEEnviadoDepoisDoCommitEUsaUrlDoFrontConfigurada() {
        Usuario usuario = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        String newEmail = emailNovo();
        clearInvocations(emailSender);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            emailChangeService.solicitarTrocaEmail(usuario.getId(),
                    new SolicitarTrocaEmailRequestDTO(newEmail, CURRENT_PASSWORD));
            verify(emailSender, org.mockito.Mockito.after(150).never())
                    .sendEmail(eq(newEmail), eq(CONFIRMATION_SUBJECT), anyString());
        });

        capturarToken(newEmail);
    }

    @Test
    void limpezaRemoveSolicitacaoExpiradaEPreservaConta() throws Exception {
        Usuario user = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        Cookie session = login(user.getEmail(), CURRENT_PASSWORD);
        solicitar(session, emailNovo(), CURRENT_PASSWORD, ipNovo()).andExpect(status().isAccepted());
        jdbcTemplate.update("""
                UPDATE desafio_troca_email
                SET criado_em = ?, expira_em = ?
                WHERE usuario_id = ?
                """, java.sql.Timestamp.from(Instant.now().minusSeconds(172_800)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), user.getId());

        cleanupService.removeExpiredPendingRegistrations();

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM desafio_troca_email WHERE usuario_id = ?", Integer.class, user.getId()));
        assertTrue(usuarioRepository.findById(user.getId()).isPresent());
    }

    @Test
    void expiradoSubstituidoEReutilizadoFalham() throws Exception {
        Usuario expiredUser = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        Cookie expiredSession = login(expiredUser.getEmail(), CURRENT_PASSWORD);
        String expiredToken = solicitarEObterToken(expiredSession, emailNovo());
        jdbcTemplate.update("""
                UPDATE desafio_troca_email
                SET criado_em = ?, expira_em = ?
                WHERE usuario_id = ?
                """, java.sql.Timestamp.from(Instant.now().minusSeconds(172_800)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), expiredUser.getId());
        confirmar(expiredToken, ipNovo()).andExpect(status().isBadRequest());
        assertEquals(expiredUser.getEmail(), usuarioRepository.findById(expiredUser.getId()).orElseThrow().getEmail());

        Usuario replacedUser = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        Cookie replacedSession = login(replacedUser.getEmail(), CURRENT_PASSWORD);
        String firstToken = solicitarEObterToken(replacedSession, emailNovo());
        String replacementEmail = emailNovo();
        String secondToken = solicitarEObterToken(replacedSession, replacementEmail);
        assertNotEquals(firstToken, secondToken);
        confirmar(firstToken, ipNovo()).andExpect(status().isBadRequest());
        confirmar(secondToken, ipNovo()).andExpect(status().isNoContent());
        confirmar(secondToken, ipNovo()).andExpect(status().isBadRequest());
        assertEquals(replacementEmail, usuarioRepository.findById(replacedUser.getId()).orElseThrow().getEmail());
    }

    @Test
    void contasConcorrentesNaoConseguemConfirmarMesmoEndereco() throws Exception {
        Usuario first = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        Usuario second = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        Cookie firstSession = login(first.getEmail(), CURRENT_PASSWORD);
        Cookie secondSession = login(second.getEmail(), CURRENT_PASSWORD);
        String targetEmail = emailNovo();
        ExecutorService requestExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> requestA = requestExecutor.submit(() -> solicitar(
                    firstSession, targetEmail, CURRENT_PASSWORD, ipNovo()).andReturn().getResponse().getStatus());
            Future<Integer> requestB = requestExecutor.submit(() -> solicitar(
                    secondSession, targetEmail, CURRENT_PASSWORD, ipNovo()).andReturn().getResponse().getStatus());
            assertEquals(202, requestA.get());
            assertEquals(202, requestB.get());
        } finally {
            requestExecutor.shutdownNow();
        }
        List<String> tokens = capturarTokens(targetEmail, 2);
        TokenOwner firstAttempt = localizarDono(tokens.get(0));
        TokenOwner secondAttempt = localizarDono(tokens.get(1));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> resultA = executor.submit(() -> confirmar(firstAttempt.token(), ipNovo())
                    .andReturn().getResponse().getStatus());
            Future<Integer> resultB = executor.submit(() -> confirmar(secondAttempt.token(), ipNovo())
                    .andReturn().getResponse().getStatus());
            int statusA = resultA.get();
            int statusB = resultB.get();
            assertTrue((statusA == 204 && statusB == 400) || (statusA == 400 && statusB == 204));
            long losingUserId = statusA == 400 ? firstAttempt.userId() : secondAttempt.userId();
            String expectedOldEmail = losingUserId == first.getId() ? first.getEmail() : second.getEmail();
            assertEquals(expectedOldEmail, usuarioRepository.findById(losingUserId).orElseThrow().getEmail());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, usuarioRepository.findAll().stream()
                .filter(user -> targetEmail.equals(user.getEmail())).count());
    }

    @Test
    void duasConfirmacoesConcorrentesDoMesmoTokenConsomemUmaVez() throws Exception {
        Usuario usuario = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        Cookie session = login(usuario.getEmail(), CURRENT_PASSWORD);
        String newEmail = emailNovo();
        String token = solicitarEObterToken(session, newEmail);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> confirmar(token, ipNovo()).andReturn().getResponse().getStatus();
            Future<Integer> resultA = executor.submit(call);
            Future<Integer> resultB = executor.submit(call);
            int statusA = resultA.get();
            int statusB = resultB.get();
            assertTrue((statusA == 204 && statusB == 400) || (statusA == 400 && statusB == 204));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(newEmail, usuarioRepository.findById(usuario.getId()).orElseThrow().getEmail());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM desafio_troca_email WHERE usuario_id = ? AND consumido_em IS NOT NULL",
                Integer.class, usuario.getId()));
    }

    @Test
    void rotasDeTrocaAplicamLimitesPorIpEUsuarioComRetryAfter() throws Exception {
        String sharedIp = ipNovo();
        for (int index = 0; index < 2; index++) {
            Usuario account = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
            Cookie session = login(account.getEmail(), CURRENT_PASSWORD);
            solicitar(session, emailNovo(), CURRENT_PASSWORD, sharedIp).andExpect(status().isAccepted());
        }
        Usuario blockedByIp = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        Cookie blockedByIpSession = login(blockedByIp.getEmail(), CURRENT_PASSWORD);
        solicitar(blockedByIpSession, emailNovo(), CURRENT_PASSWORD, sharedIp)
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

        Usuario blockedByUser = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        Cookie sameUserSession = login(blockedByUser.getEmail(), CURRENT_PASSWORD);
        solicitar(sameUserSession, emailNovo(), CURRENT_PASSWORD, ipNovo()).andExpect(status().isAccepted());
        solicitar(sameUserSession, emailNovo(), CURRENT_PASSWORD, ipNovo()).andExpect(status().isAccepted());
        solicitar(sameUserSession, emailNovo(), CURRENT_PASSWORD, ipNovo()).andExpect(status().isAccepted());
        solicitar(sameUserSession, emailNovo(), CURRENT_PASSWORD, ipNovo())
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

        String confirmationIp = ipNovo();
        confirmar("token-invalido-a", confirmationIp).andExpect(status().isBadRequest());
        confirmar("token-invalido-b", confirmationIp).andExpect(status().isBadRequest());
        confirmar("token-invalido-c", confirmationIp)
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

        Usuario confirmationOwner = criarUsuario(emailNovo(), StatusEnum.ATIVO, true);
        Cookie ownerSession = login(confirmationOwner.getEmail(), CURRENT_PASSWORD);
        String token = solicitarEObterToken(ownerSession, emailNovo());
        confirmar(token, ipNovo()).andExpect(status().isNoContent());
        confirmar(token, ipNovo()).andExpect(status().isBadRequest());
        confirmar(token, ipNovo()).andExpect(status().isBadRequest());
        confirmar(token, ipNovo())
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void endpointDeTrocaExigeSessaoECsrfMasConfirmacaoPublicaTambemExigeCsrf() throws Exception {
        requisicaoComCsrf(post("/api/v1/usuarios/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("novoEmail", emailNovo(), "senhaAtual", CURRENT_PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/confirmar-troca-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("token", "token-invalido")))
                .andExpect(status().isForbidden());
    }

    private Usuario criarUsuario(String email, StatusEnum status, boolean confirmado) {
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Usuário de integração")
                .email(email)
                .senha(passwordEncoder.encode(CURRENT_PASSWORD))
                .status(status)
                .role(roleUser)
                .build());
        if (confirmado) {
            jdbcTemplate.update("UPDATE usuario SET email_confirmado_em = ? WHERE id = ?",
                    java.sql.Timestamp.from(Instant.now()), usuario.getId());
        }
        return usuarioRepository.findById(usuario.getId()).orElseThrow();
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult result = requisicaoComCsrf(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "senha", password)))
                .andExpect(status().isNoContent()).andReturn();
        return result.getResponse().getCookie("CITOGENETICA_SESSION");
    }

    private ResultActions solicitar(Cookie session, String newEmail, String password, String ip) throws Exception {
        return requisicaoAutenticada(session, post("/api/v1/usuarios/me/email")
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("novoEmail", newEmail, "senhaAtual", password)));
    }

    private String solicitarEObterToken(Cookie session, String newEmail) throws Exception {
        solicitar(session, newEmail, CURRENT_PASSWORD, ipNovo()).andExpect(status().isAccepted());
        return capturarToken(newEmail);
    }

    private ResultActions confirmar(String token, String ip) throws Exception {
        return requisicaoComCsrf(post("/api/v1/auth/confirmar-troca-email")
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", token)));
    }

    private String capturarToken(String email) {
        return capturarTokens(email, 1).get(0);
    }

    private List<String> capturarTokens(String email, int quantity) {
        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000).times(quantity)).sendEmail(eq(email), eq(CONFIRMATION_SUBJECT), bodies.capture());
        List<String> tokens = new ArrayList<>();
        for (String body : bodies.getAllValues()) {
            Matcher matcher = TOKEN_PATTERN.matcher(body);
            assertTrue(matcher.find(), "O e-mail deve apontar para a URL configurada e carregar o token no fragmento.");
            tokens.add(matcher.group(1));
        }
        return tokens;
    }

    private TokenOwner localizarDono(String token) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        long userId = jdbcTemplate.queryForObject(
                "SELECT usuario_id FROM desafio_troca_email WHERE token_hash = ?", Long.class, hash);
        return new TokenOwner(token, userId);
    }

    private ResultActions requisicaoAutenticada(Cookie session, MockHttpServletRequestBuilder request) throws Exception {
        return requisicaoComCsrf(request.cookie(session));
    }

    private ResultActions requisicaoComCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie csrf = new Cookie("XSRF-TOKEN", CSRF_VALUE);
        request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
        return mockMvc.perform(request);
    }

    private String json(Object... pairs) throws Exception {
        var values = new java.util.LinkedHashMap<String, Object>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], pairs[index + 1]);
        }
        return objectMapper.writeValueAsString(values);
    }

    private String emailNovo() {
        return UUID.randomUUID() + "@citogenetica.test";
    }

    private String ipNovo() {
        long bits = UUID.randomUUID().getMostSignificantBits();
        return "198.51." + ((bits >>> 8) & 0xff) + "." + (bits & 0xff);
    }

    private record TokenOwner(String token, long userId) {
    }
}
