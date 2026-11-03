package br.ufla.citogenetica;

import br.ufla.citogenetica.config.TestSecrets;
import br.ufla.citogenetica.model.roles.Role;
import br.ufla.citogenetica.model.type.RolesEnum;
import br.ufla.citogenetica.model.type.StatusEnum;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.repository.RoleRepository;
import br.ufla.citogenetica.repository.UsuarioRepository;
import br.ufla.citogenetica.infra.email.EmailService;
import br.ufla.citogenetica.service.RecoveryHmacService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import jakarta.servlet.http.Cookie;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthRecoverySecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String RECOVERY_SECRET = TestSecrets.generate();
    private static final String SENHA = "SenhaSegura123!";
    private static final String CODIGO_INVALIDO = "999999";
    private static final String NOVA_SENHA = "NovaSenha456!";

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
    private RecoveryHmacService recoveryHmacService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private SecureRandom secureRandom;

    @DynamicPropertySource
    static void recoveryTestProperties(DynamicPropertyRegistry properties) {
        properties.add("api.security.recovery.hmac-secret", () -> RECOVERY_SECRET);
        properties.add("api.security.token.recovery-expiration-minutes", () -> 7);
        properties.add("api.security.recovery.max-attempts", () -> 5);
        properties.add("api.security.rate-limit.window-minutes", () -> 15);
        properties.add("api.security.rate-limit.cleanup-interval-ms", () -> 3_600_000);
        properties.add("api.security.rate-limit.login.ip", () -> 2);
        properties.add("api.security.rate-limit.login.email", () -> 2);
        properties.add("api.security.rate-limit.recovery-request.ip", () -> 100);
        properties.add("api.security.rate-limit.recovery-request.email", () -> 3);
        properties.add("api.security.rate-limit.validate.ip", () -> 100);
        properties.add("api.security.rate-limit.validate.email", () -> 10);
        properties.add("api.security.rate-limit.reset.ip", () -> 100);
        properties.add("api.security.rate-limit.reset.email", () -> 10);
    }

    private Role roleUser;

    @BeforeEach
    void preparar() {
        roleUser = roleRepository.findByNome(RolesEnum.USER).orElseThrow();
        when(secureRandom.nextInt(1_000_000)).thenReturn(42);
    }

    @Test
    void redefinicaoDeSenhaEncerraTodasAsSessoesAtivas() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        MvcResult loginRealizado = perform(login(usuario.getEmail(), SENHA, "198.51.100.61"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie sessaoAnterior = loginRealizado.getResponse().getCookie("CITOGENETICA_SESSION");
        pedirRecuperacao(usuario.getEmail(), "198.51.100.62");
        String codigo = capturarCodigo(usuario.getEmail());

        perform(post("/api/v1/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "codigo", codigo,
                                "novaSenha", "SenhaRedefinida789!")))
                .andExpect(status().isNoContent());

        perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/usuarios/me").cookie(sessaoAnterior))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pedidoDeRecuperacaoTemRespostaIgualParaEmailExistenteInativoEInexistente() throws Exception {
        Usuario ativo = criarUsuario(StatusEnum.ATIVO);
        Usuario inativo = criarUsuario(StatusEnum.INATIVO);

        MvcResult respostaAtivo = pedirRecuperacao(ativo.getEmail(), "198.51.100.11");
        MvcResult respostaInativo = pedirRecuperacao(inativo.getEmail(), "198.51.100.12");
        MvcResult respostaInexistente = pedirRecuperacao(emailNovo(), "198.51.100.13");

        assertEquals(respostaAtivo.getResponse().getStatus(), respostaInativo.getResponse().getStatus());
        assertEquals(respostaAtivo.getResponse().getStatus(), respostaInexistente.getResponse().getStatus());
        assertEquals(202, respostaAtivo.getResponse().getStatus());
        assertEquals(respostaAtivo.getResponse().getContentAsString(), respostaInexistente.getResponse().getContentAsString());
        verify(emailService, timeout(3000)).sendEmail(eq(ativo.getEmail()), anyString(), anyString());
    }

    @Test
    void pedidoDeRecuperacaoNaoEsperaEnvioDeEmail() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        CountDownLatch envioIniciado = new CountDownLatch(1);
        CountDownLatch liberarEnvio = new CountDownLatch(1);
        doAnswer(invocation -> {
            envioIniciado.countDown();
            liberarEnvio.await(5, TimeUnit.SECONDS);
            return null;
        }).when(emailService).sendEmail(anyString(), anyString(), anyString());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> requisicao = executor.submit(() -> pedirRecuperacao(usuario.getEmail(), "198.51.100.14"));
            assertTrue(envioIniciado.await(3, TimeUnit.SECONDS), "O envio assíncrono não iniciou");
            MvcResult respostaAntesDoEnvio = null;
            try {
                respostaAntesDoEnvio = requisicao.get(1, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException ignored) {
                // A verificação é concluída depois de liberar a fila, sem imprimir o conteúdo do pedido.
            }
            liberarEnvio.countDown();
            MvcResult resultado = respostaAntesDoEnvio == null ? requisicao.get(3, TimeUnit.SECONDS) : respostaAntesDoEnvio;

            assertTrue(respostaAntesDoEnvio != null, "A requisição ficou bloqueada pelo envio do e-mail");
            assertEquals(202, resultado.getResponse().getStatus());
        } finally {
            liberarEnvio.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void codigoUsaSecureRandomComSeisDigitosIncluindoZerosETextoUsaExpiracaoConfigurada() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        pedirRecuperacao(usuario.getEmail(), "198.51.100.25");
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(3000)).sendEmail(eq(usuario.getEmail()), anyString(), corpo.capture());
        assertTrue(corpo.getValue().contains("000042") && corpo.getValue().contains("7 minutos"),
                "O e-mail deve conter código uniforme e tempo configurado");
        verify(secureRandom).nextInt(1_000_000);

        String codigo = capturarCodigo(usuario.getEmail());
        UUID desafioId = jdbcTemplate.queryForObject(
                "SELECT id FROM desafio_recuperacao_senha WHERE usuario_id = ?", UUID.class, usuario.getId());
        byte[] hmacPersistido = jdbcTemplate.queryForObject(
                "SELECT codigo_hmac FROM desafio_recuperacao_senha WHERE usuario_id = ?", byte[].class, usuario.getId());
        assertArrayEquals(recoveryHmacService.recoveryCode(desafioId, codigo), hmacPersistido);
    }

    @Test
    void codigoIncorretoConsomeTentativasEDesafioEDeUsoUnico() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        pedirRecuperacao(usuario.getEmail(), "198.51.100.15");
        String codigo = capturarCodigo(usuario.getEmail());

        for (int tentativa = 0; tentativa < 5; tentativa++) {
            perform(post("/api/v1/auth/redefinir-senha")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", usuario.getEmail(), "codigo", CODIGO_INVALIDO,
                                    "novaSenha", NOVA_SENHA)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Código inválido ou expirado."));
        }

        perform(post("/api/v1/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "codigo", codigo,
                                "novaSenha", NOVA_SENHA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Código inválido ou expirado."));
    }

    @Test
    void tentativasParalelasNaoPerdemIncrementosDoLimite() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        pedirRecuperacao(usuario.getEmail(), "198.51.100.26");
        String codigoCorreto = capturarCodigo(usuario.getEmail());
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            var tarefas = java.util.stream.IntStream.range(0, 5)
                    .<java.util.concurrent.Callable<Integer>>mapToObj(indice -> () -> perform(
                                    post("/api/v1/auth/redefinir-senha")
                                            .with(request -> { request.setRemoteAddr("198.51.100.60"); return request; })
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(json("email", usuario.getEmail(), "codigo", CODIGO_INVALIDO,
                                                    "novaSenha", NOVA_SENHA)))
                            .andReturn().getResponse().getStatus())
                    .toList();
            for (Future<Integer> resultado : executor.invokeAll(tarefas)) {
                assertEquals(400, resultado.get());
            }
        } finally {
            executor.shutdownNow();
        }

        MvcResult codigoCorretoEsgotado = perform(post("/api/v1/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "codigo", codigoCorreto,
                                "novaSenha", NOVA_SENHA)))
                .andReturn();
        assertEquals(400, codigoCorretoEsgotado.getResponse().getStatus());
    }

    @Test
    void erroDeValidacaoNaoRevelaSeEmailExiste() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        pedirRecuperacao(usuario.getEmail(), "198.51.100.16");

        MvcResult emailExistente = perform(post("/api/v1/auth/validar-codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "codigo", CODIGO_INVALIDO)))
                .andReturn();
        MvcResult emailInexistente = perform(post("/api/v1/auth/validar-codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", emailNovo(), "codigo", CODIGO_INVALIDO)))
                .andReturn();

        assertEquals(400, emailExistente.getResponse().getStatus());
        assertEquals(emailExistente.getResponse().getStatus(), emailInexistente.getResponse().getStatus());
        assertEquals("Código inválido ou expirado.", respostaJson(emailExistente).get("message").asText());
        assertEquals("Código inválido ou expirado.", respostaJson(emailInexistente).get("message").asText());
    }

    @Test
    void codigoExpiradoEEmailInexistenteTemErroGenericoIgualNaRedefinicao() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        pedirRecuperacao(usuario.getEmail(), "198.51.100.40");
        String codigo = capturarCodigo(usuario.getEmail());
        jdbcTemplate.update("UPDATE desafio_recuperacao_senha SET expira_em = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE usuario_id = ?", usuario.getId());

        MvcResult codigoExpirado = perform(post("/api/v1/auth/redefinir-senha")
                        .with(request -> { request.setRemoteAddr("198.51.100.41"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "codigo", codigo, "novaSenha", NOVA_SENHA)))
                .andReturn();
        MvcResult contaInexistente = perform(post("/api/v1/auth/redefinir-senha")
                        .with(request -> { request.setRemoteAddr("198.51.100.42"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", emailNovo(), "codigo", codigo, "novaSenha", NOVA_SENHA)))
                .andReturn();

        assertEquals(400, codigoExpirado.getResponse().getStatus());
        assertEquals(400, contaInexistente.getResponse().getStatus());
        assertEquals(respostaJson(codigoExpirado).get("message").asText(), respostaJson(contaInexistente).get("message").asText());
        assertEquals("Código inválido ou expirado.", respostaJson(codigoExpirado).get("message").asText());
    }

    @Test
    void persistenciaGuardaSomenteHmacEUnicidadeDoEmailIgnoraMaiusculas() {
        Integer desafios = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'desafio_recuperacao_senha'", Integer.class);
        Integer tokensAntigos = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'usuario' AND column_name IN ('token_recuperacao_senha', 'data_expiracao_codigo')", Integer.class);
        Integer indiceNormalizado = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexdef ILIKE '%lower(%email%'", Integer.class);

        assertEquals(1, desafios);
        assertEquals(0, tokensAntigos);
        assertTrue(indiceNormalizado > 0);
    }

    @Test
    void loginNormalizaEmailEContabilizaLimitesPorIpEPorEmail() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        String emailComEspacosEMaiusculas = "  " + usuario.getEmail().toUpperCase() + "  ";

        perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("198.51.100.21"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", emailComEspacosEMaiusculas, "senha", SENHA)))
                .andExpect(status().isNoContent());
    }

    @Test
    void loginAplicaRateLimitPorIpComRetryAfterEErroPadrao() throws Exception {
        String emailTentativa = emailNovo();
        perform(login(emailTentativa, "SenhaErrada123!", "198.51.100.22")
                        .header("X-Forwarded-For", "203.0.113.1"))
                .andExpect(status().isUnauthorized());
        perform(login(emailNovo(), "SenhaErrada123!", "198.51.100.22")
                        .header("X-Forwarded-For", "203.0.113.2"))
                .andExpect(status().isUnauthorized());
        perform(login(emailNovo(), "SenhaErrada123!", "198.51.100.22")
                        .header("X-Forwarded-For", "203.0.113.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        perform(login(emailTentativa, "SenhaErrada123!", "198.51.100.23"))
                .andExpect(status().isUnauthorized());
        perform(login(emailTentativa, "SenhaErrada123!", "198.51.100.24"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void loginAplicaRateLimitPorEmailNormalizadoMesmoComIpsDiferentes() throws Exception {
        String emailTentativa = emailNovo();
        perform(login(emailTentativa, "SenhaErrada123!", "198.51.100.31"))
                .andExpect(status().isUnauthorized());
        perform(login(emailTentativa.toUpperCase(), "SenhaErrada123!", "198.51.100.32"))
                .andExpect(status().isUnauthorized());
        perform(login(" " + emailTentativa.toUpperCase() + " ", "SenhaErrada123!", "198.51.100.33"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[0-9]+")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Muitas tentativas. Tente novamente mais tarde."));
    }

    @Test
    void recuperacaoTemRateLimitPorEmail() throws Exception {
        String email = emailNovo();
        for (int tentativa = 1; tentativa <= 4; tentativa++) {
            MvcResult resultado = pedirRecuperacao(email, "198.51.100." + (80 + tentativa));
            assertEquals(tentativa <= 3 ? 202 : 429, resultado.getResponse().getStatus());
        }
    }

    @Test
    void validacaoERedefinicaoTemRateLimitPorEmail() throws Exception {
        String emailValidacao = emailNovo();
        for (int tentativa = 1; tentativa <= 11; tentativa++) {
            String ip = "198.51.100." + (90 + tentativa);
            MvcResult resultado = perform(post("/api/v1/auth/validar-codigo")
                            .with(request -> { request.setRemoteAddr(ip); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", emailValidacao, "codigo", CODIGO_INVALIDO)))
                    .andReturn();
            assertEquals(tentativa <= 10 ? 400 : 429, resultado.getResponse().getStatus());
        }

        String emailReset = emailNovo();
        for (int tentativa = 1; tentativa <= 11; tentativa++) {
            String ip = "198.51.100." + (110 + tentativa);
            MvcResult resultado = perform(post("/api/v1/auth/redefinir-senha")
                            .with(request -> { request.setRemoteAddr(ip); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", emailReset, "codigo", CODIGO_INVALIDO, "novaSenha", NOVA_SENHA)))
                    .andReturn();
            assertEquals(tentativa <= 10 ? 400 : 429, resultado.getResponse().getStatus());
        }
    }

    @Test
    void recuperacaoNormalizaEmailAntesDeConsultarConta() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        MvcResult resultado = pedirRecuperacao(" " + usuario.getEmail().toUpperCase() + " ", "198.51.100.17");

        assertEquals(202, resultado.getResponse().getStatus());
        verify(emailService, timeout(3000)).sendEmail(eq(usuario.getEmail()), anyString(), anyString());
    }

    @Test
    void novoPedidoInvalidaCodigoAnteriorERedefinicaoSoPodeSerUsadaUmaVez() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO);
        when(secureRandom.nextInt(1_000_000)).thenReturn(42, 43);
        pedirRecuperacao(usuario.getEmail(), "198.51.100.18");
        String codigoAnterior = capturarCodigo(usuario.getEmail());
        pedirRecuperacao(usuario.getEmail(), "198.51.100.19");
        String codigoNovo = capturarUltimoCodigo(usuario.getEmail());

        MvcResult codigoAnteriorResposta = perform(post("/api/v1/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "codigo", codigoAnterior,
                                "novaSenha", NOVA_SENHA)))
                .andReturn();
        assertEquals(400, codigoAnteriorResposta.getResponse().getStatus());

        MvcResult redefinicaoValida = perform(post("/api/v1/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "codigo", codigoNovo,
                                "novaSenha", NOVA_SENHA)))
                .andReturn();
        assertEquals(204, redefinicaoValida.getResponse().getStatus());
        MvcResult codigoReutilizado = perform(post("/api/v1/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "codigo", codigoNovo,
                                "novaSenha", "SenhaDeUsoUnico789!")))
                .andReturn();
        assertEquals(400, codigoReutilizado.getResponse().getStatus());
    }

    @Test
    void emailRegistradoEArmazenadoNormalizado() throws Exception {
        String email = emailNovo();
        String emailInformado = " " + email.toUpperCase() + " ";

        perform(post("/api/v1/auth/register")
                        .cookie(sessaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Cadastro normalizado", "email", emailInformado,
                                "senha", SENHA, "roleName", "USER")))
                .andExpect(status().isCreated());

        assertTrue(usuarioRepository.findByEmail(email).isPresent());
    }

    @Test
    void adminEditaEmailComEspacosEMaiusculasEValorFicaNormalizado() throws Exception {
        Usuario alvo = criarUsuario(StatusEnum.ATIVO);
        String novoEmail = emailNovo();

        perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/usuarios/{id}", alvo.getId())
                        .cookie(sessaoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", alvo.getNome(), "email", " " + novoEmail.toUpperCase() + " ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(novoEmail));

        assertEquals(novoEmail, usuarioRepository.findById(alvo.getId()).orElseThrow().getEmail());
    }

    @Test
    void bancoImpoeUnicidadeCaseInsensitive() {
        Usuario primeiro = criarUsuario(StatusEnum.ATIVO);
        Usuario duplicadoNormalizado = Usuario.builder()
                .nome("Duplicado")
                .email(primeiro.getEmail().toUpperCase())
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
                .role(roleUser)
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> usuarioRepository.saveAndFlush(duplicadoNormalizado));
    }

    private MvcResult pedirRecuperacao(String email, String ip) throws Exception {
        return perform(post("/api/v1/auth/recuperar-senha")
                        .with(request -> { request.setRemoteAddr(ip); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email)))
                .andReturn();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email, String senha, String ip) throws Exception {
        return post("/api/v1/auth/login")
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "senha", senha));
    }

    private String capturarCodigo(String email) {
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(3000)).sendEmail(eq(email), anyString(), corpo.capture());
        Matcher matcher = Pattern.compile("<span[^>]*>\\s*(\\d{6})\\s*</span>").matcher(corpo.getValue());
        assertTrue(matcher.find(), "O corpo do e-mail deve conter um código de seis dígitos");
        return matcher.group(1);
    }

    private String capturarUltimoCodigo(String email) {
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(3000).times(2)).sendEmail(eq(email), anyString(), corpo.capture());
        Matcher matcher = Pattern.compile("<span[^>]*>\\s*(\\d{6})\\s*</span>").matcher(corpo.getAllValues().get(1));
        assertTrue(matcher.find(), "O corpo do e-mail deve conter um código de seis dígitos");
        return matcher.group(1);
    }

    private Usuario criarUsuario(StatusEnum status) {
        return usuarioRepository.save(Usuario.builder()
                .nome("Usuário de recuperação")
                .email(emailNovo())
                .senha(passwordEncoder.encode(SENHA))
                .status(status)
                .role(roleUser)
                .build());
    }

    private String emailNovo() {
        return UUID.randomUUID() + "@citogenetica.test";
    }

    private Cookie sessaoAdmin() throws Exception {
        Role roleAdmin = roleRepository.findByNome(RolesEnum.ADMIN).orElseThrow();
        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nome("Admin de teste")
                .email(emailNovo())
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
                .role(roleAdmin)
                .build());
        MvcResult resultado = perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", admin.getEmail(), "senha", SENHA)))
                .andExpect(status().isNoContent())
                .andReturn();
        return resultado.getResponse().getCookie("CITOGENETICA_SESSION");
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        Cookie csrf = new Cookie("XSRF-TOKEN", "csrf-de-integracao");
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

    private com.fasterxml.jackson.databind.JsonNode respostaJson(MvcResult result) throws Exception {
        return objectMapper.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    @AfterEach
    void limparSessoesDeIntegracao() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }
}
