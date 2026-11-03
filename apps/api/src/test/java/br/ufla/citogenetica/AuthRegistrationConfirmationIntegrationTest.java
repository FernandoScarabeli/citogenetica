package br.ufla.citogenetica;

import br.ufla.citogenetica.config.TestSecrets;
import br.ufla.citogenetica.shared.email.EmailSender;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.application.AuthService;
import br.ufla.citogenetica.usuario.internal.application.EmailConfirmationCleanupService;
import br.ufla.citogenetica.usuario.internal.web.dto.request.UsuarioRequestDTO;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.Arrays;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthRegistrationConfirmationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Uma frase correta para entrar 2026";
    private static final String FRONTEND_CONFIRMATION_URL = "https://front.citogenetica.test/confirmar-email";
    private static final String CSRF_VALUE = "csrf-de-integracao";
    private static final String TOKEN_PATTERN = "(?s)" + Pattern.quote(FRONTEND_CONFIRMATION_URL)
            + "#token=([A-Za-z0-9_-]{43})";
    private static final Pattern TOKEN_REGEX = Pattern.compile(TOKEN_PATTERN);

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
    private EmailConfirmationCleanupService cleanupService;

    @Autowired
    private AuthService authService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private EmailSender emailSender;

    private Role roleUser;
    private Role roleAdmin;

    @DynamicPropertySource
    static void registrationProperties(DynamicPropertyRegistry properties) {
        properties.add("api.security.recovery.hmac-secret", TestSecrets::generate);
        properties.add("api.security.email-confirmation.frontend-url", () -> FRONTEND_CONFIRMATION_URL);
        properties.add("api.security.email-confirmation.expiration-hours", () -> 24);
        properties.add("api.security.email-confirmation.pending-retention-days", () -> 30);
        properties.add("api.security.email-confirmation.cleanup-interval-ms", () -> 3_600_000);
        properties.add("api.security.email-confirmation.cleanup-batch-size", () -> 100);
        properties.add("api.security.rate-limit.window-minutes", () -> 15);
        properties.add("api.security.rate-limit.cleanup-interval-ms", () -> 3_600_000);
        properties.add("api.security.rate-limit.register.ip", () -> 2);
        properties.add("api.security.rate-limit.register.email", () -> 100);
        properties.add("api.security.rate-limit.resend-confirmation.ip", () -> 2);
        properties.add("api.security.rate-limit.resend-confirmation.email", () -> 100);
        properties.add("api.security.rate-limit.email-confirmation.ip", () -> 2);
        properties.add("api.security.rate-limit.email-confirmation.email", () -> 100);
        properties.add("api.security.rate-limit.login.ip", () -> 100);
        properties.add("api.security.rate-limit.login.email", () -> 100);
        properties.add("api.security.rate-limit.recovery-request.ip", () -> 100);
        properties.add("api.security.rate-limit.recovery-request.email", () -> 100);
        properties.add("api.security.rate-limit.validate.ip", () -> 100);
        properties.add("api.security.rate-limit.validate.email", () -> 100);
        properties.add("api.security.rate-limit.reset.ip", () -> 100);
        properties.add("api.security.rate-limit.reset.email", () -> 100);
    }

    @BeforeEach
    void carregarPapeis() {
        roleUser = roleRepository.findByNome(RolesEnum.USER).orElseThrow();
        roleAdmin = roleRepository.findByNome(RolesEnum.ADMIN).orElseThrow();
    }

    @Test
    void cadastroTemMesmaRespostaParaEmailNovoPendenteEConfirmado() throws Exception {
        String emailPendente = emailNovo();
        MvcResult novo = cadastrar(emailPendente, "Nome inicial", PASSWORD, ipNovo());
        String senhaOriginal = usuarioRepository.findByEmail(emailPendente).orElseThrow().getPassword();
        capturarToken(emailPendente);

        MvcResult pendente = cadastrar(emailPendente, "Nome não deve sobrescrever", "Outra frase segura 2026", ipNovo());
        capturarToken(emailPendente, 2);
        Usuario confirmado = criarUsuario("Conta confirmada", emailNovo(), RolesEnum.USER, StatusEnum.ATIVO, true);
        MvcResult jaConfirmado = cadastrar(confirmado.getEmail(), "Outro nome", PASSWORD, ipNovo());

        assertRespostaUniforme(novo, pendente, jaConfirmado);
        assertEquals(senhaOriginal, usuarioRepository.findByEmail(emailPendente).orElseThrow().getPassword());
        assertEquals("Nome inicial", usuarioRepository.findByEmail(emailPendente).orElseThrow().getNome());
    }

    @Test
    void cadastroPublicoCriaUmaUnicaContaSobConcorrencia() throws Exception {
        String email = emailNovo();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> primeira = () -> cadastrar(email, "Cadastro concorrente A", PASSWORD, ipNovo())
                    .getResponse().getStatus();
            Callable<Integer> segunda = () -> cadastrar(email, "Cadastro concorrente B", PASSWORD, ipNovo())
                    .getResponse().getStatus();
            Future<Integer> resultadoA = executor.submit(primeira);
            Future<Integer> resultadoB = executor.submit(segunda);

            assertEquals(202, resultadoA.get());
            assertEquals(202, resultadoB.get());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, usuarioRepository.findAll().stream().filter(usuario -> email.equals(usuario.getEmail())).count());
    }

    @Test
    void contaPendenteNaoFazLoginNemRecebeRecuperacaoDeSenha() throws Exception {
        String email = emailNovo();
        cadastrar(email, "Pendente", PASSWORD, ipNovo());
        capturarToken(email);
        clearInvocations(emailSender);

        requisicaoComCsrf(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "senha", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos."))
                .andExpect(result -> assertFalse(Arrays.stream(result.getResponse().getCookies())
                        .anyMatch(cookie -> "CITOGENETICA_SESSION".equals(cookie.getName()))));

        requisicaoComCsrf(post("/api/v1/auth/recuperar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email)))
                .andExpect(status().isAccepted());

        verify(emailSender, org.mockito.Mockito.after(300).never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void confirmacaoValidaLiberaLoginEBancoGuardaSomenteHash() throws Exception {
        String email = emailNovo();
        cadastrar(email, "Conta nova", PASSWORD, ipNovo());
        String token = capturarToken(email);
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        byte[] hashEsperado = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        byte[] hashPersistido = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM desafio_confirmacao_email WHERE usuario_id = ?", byte[].class, usuario.getId());

        assertArrayEquals(hashEsperado, hashPersistido);
        assertEquals(32, hashPersistido.length);
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'desafio_confirmacao_email'
                  AND column_name IN ('token', 'token_plano')
                """, Integer.class));

        MvcResult confirmacao = confirmar(token, ipNovo()).andExpect(status().isNoContent()).andReturn();
        assertFalse(Arrays.stream(confirmacao.getResponse().getCookies())
                .anyMatch(cookie -> "CITOGENETICA_SESSION".equals(cookie.getName())));
        requisicaoComCsrf(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "senha", PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    void tokenExpiradoReutilizadoEInvalidadoFalhaSemConfirmarConta() throws Exception {
        String emailExpirado = emailNovo();
        cadastrar(emailExpirado, "Expirado", PASSWORD, ipNovo());
        String tokenExpirado = capturarToken(emailExpirado);
        jdbcTemplate.update("""
                UPDATE desafio_confirmacao_email
                SET criado_em = CURRENT_TIMESTAMP - INTERVAL '2 days',
                    expira_em = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE usuario_id = ?
                """,
                usuarioRepository.findByEmail(emailExpirado).orElseThrow().getId());

        confirmar(tokenExpirado, ipNovo()).andExpect(status().isBadRequest());
        assertEmailPendente(emailExpirado);

        String emailReutilizado = emailNovo();
        cadastrar(emailReutilizado, "Reutilizado", PASSWORD, ipNovo());
        String tokenReutilizado = capturarToken(emailReutilizado);
        confirmar(tokenReutilizado, ipNovo()).andExpect(status().isNoContent());
        confirmar(tokenReutilizado, ipNovo()).andExpect(status().isBadRequest());

        String emailInvalidado = emailNovo();
        cadastrar(emailInvalidado, "Invalidado", PASSWORD, ipNovo());
        String tokenInvalidado = capturarToken(emailInvalidado);
        jdbcTemplate.update("UPDATE desafio_confirmacao_email SET invalidado_em = CURRENT_TIMESTAMP WHERE usuario_id = ?",
                usuarioRepository.findByEmail(emailInvalidado).orElseThrow().getId());
        confirmar(tokenInvalidado, ipNovo()).andExpect(status().isBadRequest());
        assertEmailPendente(emailInvalidado);
    }

    @Test
    void confirmacaoConcorrenteConsomeTokenUmaVez() throws Exception {
        String email = emailNovo();
        cadastrar(email, "Confirmação concorrente", PASSWORD, ipNovo());
        String token = capturarToken(email);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> primeira = () -> confirmar(token, ipNovo()).andReturn().getResponse().getStatus();
            Callable<Integer> segunda = () -> confirmar(token, ipNovo()).andReturn().getResponse().getStatus();
            Future<Integer> resultadoA = executor.submit(primeira);
            Future<Integer> resultadoB = executor.submit(segunda);

            int statusA = resultadoA.get();
            int statusB = resultadoB.get();
            assertTrue((statusA == 204 && statusB == 400) || (statusA == 400 && statusB == 204));
        } finally {
            executor.shutdownNow();
        }

        assertEmailConfirmado(email);
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM desafio_confirmacao_email WHERE usuario_id = ? AND consumido_em IS NOT NULL",
                Integer.class, usuarioRepository.findByEmail(email).orElseThrow().getId()));
    }

    @Test
    void reenvioInvalidaLinkAnteriorSemAlterarNomeOuSenha() throws Exception {
        String email = emailNovo();
        cadastrar(email, "Nome preservado", PASSWORD, ipNovo());
        String tokenAnterior = capturarToken(email);
        Usuario usuarioAntes = usuarioRepository.findByEmail(email).orElseThrow();
        String hashSenhaAntes = usuarioAntes.getPassword();
        clearInvocations(emailSender);

        requisicaoComCsrf(post("/api/v1/auth/reenviar-confirmacao")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email)))
                .andExpect(status().isAccepted());
        String tokenNovo = capturarToken(email);

        assertNotEquals(tokenAnterior, tokenNovo);
        confirmar(tokenAnterior, ipNovo()).andExpect(status().isBadRequest());
        confirmar(tokenNovo, ipNovo()).andExpect(status().isNoContent());
        Usuario usuarioDepois = usuarioRepository.findByEmail(email).orElseThrow();
        assertEquals("Nome preservado", usuarioDepois.getNome());
        assertEquals(hashSenhaAntes, usuarioDepois.getPassword());
    }

    @Test
    void reenvioNaoRevelaEmailInexistenteOuConfirmado() throws Exception {
        Usuario confirmado = criarUsuario("Conta confirmada", emailNovo(), RolesEnum.USER, StatusEnum.ATIVO, true);
        String emailInexistente = emailNovo();
        clearInvocations(emailSender);

        MvcResult respostaConfirmado = requisicaoComCsrf(post("/api/v1/auth/reenviar-confirmacao")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", confirmado.getEmail())))
                .andReturn();
        MvcResult respostaInexistente = requisicaoComCsrf(post("/api/v1/auth/reenviar-confirmacao")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", emailInexistente)))
                .andReturn();

        assertRespostaUniforme(respostaConfirmado, respostaInexistente);
        verify(emailSender, org.mockito.Mockito.after(300).never())
                .sendEmail(eq(confirmado.getEmail()), anyString(), anyString());
    }

    @Test
    void cadastroNuncaAceitaPapelEnviadoPeloCliente() throws Exception {
        String email = emailNovo();
        requisicaoComCsrf(post("/api/v1/auth/register")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Papel definido no servidor", "email", email,
                                "senha", PASSWORD, "roleName", "ADMIN")))
                .andExpect(status().isAccepted());

        assertEquals(RolesEnum.USER, usuarioRepository.findByEmail(email).orElseThrow().getRole().getNome());
    }

    @Test
    void cadastroReenvioEConfirmacaoAplicamRateLimitComRetryAfter() throws Exception {
        String ipCadastro = "198.51.100.210";
        cadastrar(emailNovo(), "Rate limit 1", PASSWORD, ipCadastro);
        cadastrar(emailNovo(), "Rate limit 2", PASSWORD, ipCadastro);
        requisicaoComCsrf(post("/api/v1/auth/register")
                        .with(request -> { request.setRemoteAddr(ipCadastro); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Rate limit 3", "email", emailNovo(), "senha", PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        String email = emailNovo();
        cadastrar(email, "Rate limit reenvio", PASSWORD, ipNovo());
        String ipReenvio = "198.51.100.211";
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            requisicaoComCsrf(post("/api/v1/auth/reenviar-confirmacao")
                            .with(request -> { request.setRemoteAddr(ipReenvio); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", email)))
                    .andExpect(status().isAccepted());
        }
        requisicaoComCsrf(post("/api/v1/auth/reenviar-confirmacao")
                        .with(request -> { request.setRemoteAddr(ipReenvio); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        String ipConfirmacao = "198.51.100.212";
        for (int tentativa = 0; tentativa < 2; tentativa++) {
            confirmar("token-invalido-" + tentativa, ipConfirmacao).andExpect(status().isBadRequest());
        }
        confirmar("token-invalido-final", ipConfirmacao)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void limpezaRemoveCadastroPendenteAntigoEPreservaContaConfirmada() throws Exception {
        Usuario pendente = criarUsuario("Pendente antigo", emailNovo(), RolesEnum.USER, StatusEnum.ATIVO, false);
        Usuario confirmado = criarUsuario("Confirmado antigo", emailNovo(), RolesEnum.USER, StatusEnum.ATIVO, true);
        jdbcTemplate.update("UPDATE usuario SET created_date = CURRENT_TIMESTAMP - INTERVAL '31 days' WHERE id IN (?, ?)",
                pendente.getId(), confirmado.getId());

        assertEquals(1, cleanupService.removeExpiredPendingRegistrations());
        assertTrue(usuarioRepository.findById(pendente.getId()).isEmpty());
        assertTrue(usuarioRepository.findById(confirmado.getId()).isPresent());
    }

    @Test
    void eMailDeConfirmacaoSoEEnviadoDepoisDoCommitEUsaUrlConfigurada() throws Exception {
        String email = emailNovo();
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);
        transacao.executeWithoutResult(status -> {
            authService.register(new UsuarioRequestDTO("Envio após commit", email, PASSWORD));
            status.setRollbackOnly();
        });

        verify(emailSender, org.mockito.Mockito.after(300).never())
                .sendEmail(eq(email), eq("Confirme seu e-mail — Citogenética UFLA"), anyString());
        assertTrue(usuarioRepository.findByEmail(email).isEmpty());

        String emailConfirmavel = emailNovo();
        cadastrar(emailConfirmavel, "URL configurada", PASSWORD, ipNovo());
        capturarToken(emailConfirmavel);
    }

    @Test
    void usuarioComumNaoAcessaCrudDeExemplo() throws Exception {
        Usuario usuario = criarUsuario("Usuário", emailNovo(), RolesEnum.USER, StatusEnum.ATIVO, true);
        Cookie sessao = login(usuario);

        requisicaoAutenticada(sessao, get("/api/v1/exemplos"))
                .andExpect(status().isForbidden());
        requisicaoAutenticada(sessao, post("/api/v1/exemplos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nenhumPutAlteraEmailDeConta() throws Exception {
        Usuario admin = criarUsuario("Admin de teste", emailNovo(), RolesEnum.ADMIN, StatusEnum.ATIVO, true);
        Usuario usuario = criarUsuario("Usuário de teste", emailNovo(), RolesEnum.USER, StatusEnum.ATIVO, true);
        String emailOriginal = usuario.getEmail();
        Cookie sessaoAdmin = login(admin);
        Cookie sessaoUsuario = login(usuario);
        String novoEmail = emailNovo();

        requisicaoAutenticada(sessaoAdmin, put("/api/v1/usuarios/{id}", usuario.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Nome administrativo", "email", novoEmail)))
                .andExpect(status().isOk());
        requisicaoAutenticada(sessaoUsuario, put("/api/v1/usuarios/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Nome próprio", "email", novoEmail)))
                .andExpect(status().isOk());

        assertEquals(emailOriginal, usuarioRepository.findById(usuario.getId()).orElseThrow().getEmail());
    }

    @Test
    void politicaDeSenhaAplicaLimitesComunsEBytesBcryptNoCadastro() throws Exception {
        assertEquals(202, cadastrar(emailNovo(), "Oito caracteres", "Frase 88", ipNovo()).getResponse().getStatus());
        assertSenhaRejeitadaNoCadastro("Curta7!");
        assertSenhaRejeitadaNoCadastro("a".repeat(65));
        assertSenhaRejeitadaNoCadastro("SenhaMuitoLonga😀".repeat(5));
        assertSenhaRejeitadaNoCadastro("password");

        // Limites inclusivos: 64 caracteres e exatamente 72 bytes UTF-8 são aceitos.
        assertEquals(202, cadastrar(emailNovo(), "Sessenta e quatro", "Q".repeat(64), ipNovo()).getResponse().getStatus());
        assertEquals(202, cadastrar(emailNovo(), "Setenta e dois bytes", "😀".repeat(18), ipNovo()).getResponse().getStatus());
    }

    @Test
    void politicaDeSenhaAplicaSeNaRedefinicaoETrocaAutenticada() throws Exception {
        Usuario usuario = criarUsuario("Conta verificada", emailNovo(), RolesEnum.USER, StatusEnum.ATIVO, true);
        String codigo = pedirRecuperacaoEObterCodigo(usuario.getEmail());
        assertSenhaRejeitadaNaRedefinicao(usuario.getEmail(), codigo, "password");
        assertSenhaRejeitadaNaRedefinicao(usuario.getEmail(), codigo, "x".repeat(65));
        assertSenhaRejeitadaNaRedefinicao(usuario.getEmail(), codigo, "😀".repeat(19));

        Cookie sessao = login(usuario);
        assertSenhaRejeitadaNaTroca(sessao, "password");
        assertSenhaRejeitadaNaTroca(sessao, "x".repeat(65));
        assertSenhaRejeitadaNaTroca(sessao, "😀".repeat(19));
    }

    private void assertSenhaRejeitadaNoCadastro(String senha) throws Exception {
        MvcResult resultado = cadastrar(emailNovo(), "Senha inválida", senha, ipNovo());
        assertEquals(400, resultado.getResponse().getStatus());
        assertTrue(resultado.getResponse().getContentAsString().contains("senha"));
    }

    private void assertSenhaRejeitadaNaRedefinicao(String email, String codigo, String novaSenha) throws Exception {
        requisicaoComCsrf(post("/api/v1/auth/redefinir-senha")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "codigo", codigo, "novaSenha", novaSenha)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("senha")));
    }

    private void assertSenhaRejeitadaNaTroca(Cookie sessao, String novaSenha) throws Exception {
        requisicaoAutenticada(sessao, put("/api/v1/usuarios/me/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("senhaAtual", PASSWORD, "novaSenha", novaSenha)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("senha")));
    }

    private String pedirRecuperacaoEObterCodigo(String email) throws Exception {
        requisicaoComCsrf(post("/api/v1/auth/recuperar-senha")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email)))
                .andExpect(status().isAccepted());
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000)).sendEmail(eq(email), eq("Seu código de recuperação de senha"), corpo.capture());
        Matcher matcher = Pattern.compile("<span[^>]*>\\s*(\\d{6})\\s*</span>").matcher(corpo.getValue());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private MvcResult cadastrar(String email, String nome, String senha, String ip) throws Exception {
        return requisicaoComCsrf(post("/api/v1/auth/register")
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", nome, "email", email, "senha", senha)))
                .andReturn();
    }

    private ResultActions confirmar(String token, String ip) throws Exception {
        return requisicaoComCsrf(post("/api/v1/auth/confirmar-email")
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", token)));
    }

    private String capturarToken(String email) {
        return capturarToken(email, 1);
    }

    private String capturarToken(String email, int quantidade) {
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000).times(quantidade))
                .sendEmail(eq(email), eq("Confirme seu e-mail — Citogenética UFLA"), corpo.capture());
        Matcher matcher = TOKEN_REGEX.matcher(corpo.getAllValues().get(quantidade - 1));
        String corpoSeguro = corpo.getAllValues().get(quantidade - 1)
                .replaceAll("(?<=#token=)[A-Za-z0-9_-]+", "[TOKEN PROTEGIDO]");
        assertTrue(matcher.find(), "O e-mail deve apontar para o front e conter um token no fragmento: " + corpoSeguro);
        return matcher.group(1);
    }

    private void assertRespostaUniforme(MvcResult... respostas) throws Exception {
        assertEquals(202, respostas[0].getResponse().getStatus());
        String corpoEsperado = respostas[0].getResponse().getContentAsString();
        for (MvcResult resposta : respostas) {
            assertEquals(202, resposta.getResponse().getStatus());
            assertEquals(corpoEsperado, resposta.getResponse().getContentAsString());
        }
    }

    private void assertEmailPendente(String email) {
        Integer confirmado = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE email = ? AND email_confirmado_em IS NOT NULL", Integer.class, email);
        assertEquals(0, confirmado);
    }

    private void assertEmailConfirmado(String email) {
        Integer confirmado = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE email = ? AND email_confirmado_em IS NOT NULL", Integer.class, email);
        assertEquals(1, confirmado);
    }

    private Usuario criarUsuario(String nome, String email, RolesEnum roleNome, StatusEnum status, boolean confirmado) {
        Role role = roleNome == RolesEnum.ADMIN ? roleAdmin : roleUser;
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.builder()
                .nome(nome)
                .email(email)
                .senha(passwordEncoder.encode(PASSWORD))
                .status(status)
                .role(role)
                .build());
        if (confirmado) {
            jdbcTemplate.update("UPDATE usuario SET email_confirmado_em = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now()), usuario.getId());
            usuarioRepository.flush();
        }
        return usuarioRepository.findById(usuario.getId()).orElseThrow();
    }

    private Cookie login(Usuario usuario) throws Exception {
        MvcResult resultado = requisicaoComCsrf(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr(ipNovo()); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "senha", PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        return resultado.getResponse().getCookie("CITOGENETICA_SESSION");
    }

    private ResultActions requisicaoAutenticada(Cookie sessao, MockHttpServletRequestBuilder request) throws Exception {
        return requisicaoComCsrf(request.cookie(sessao));
    }

    private ResultActions requisicaoComCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie csrf = new Cookie("XSRF-TOKEN", CSRF_VALUE);
        request.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
        return mockMvc.perform(request);
    }

    private String json(Object... pares) throws Exception {
        var objeto = new java.util.LinkedHashMap<String, Object>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            objeto.put((String) pares[indice], pares[indice + 1]);
        }
        return objectMapper.writeValueAsString(objeto);
    }

    private String emailNovo() {
        return UUID.randomUUID() + "@citogenetica.test";
    }

    private String ipNovo() {
        long bits = UUID.randomUUID().getMostSignificantBits();
        return "203.0." + ((bits >>> 8) & 0xff) + "." + (bits & 0xff);
    }
}
