package br.ufla.citogenetica.contagem;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.ufla.citogenetica.AbstractIntegrationTest;
import br.ufla.citogenetica.laboratorio.internal.application.LaboratorioService;
import br.ufla.citogenetica.laboratorio.internal.application.OrientacaoService;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Montagem comum dos testes de integração da contagem: laboratório com todos os papéis, sessões, lâminas e
 * utilidades de HTTP e de banco. Cada teste usa usuários e laboratórios próprios, então não depende de ordem.
 */
@AutoConfigureMockMvc
abstract class AbstractContagemIntegrationTest extends AbstractIntegrationTest {

    protected static final String SENHA = "Frase segura para testes de contagem";
    protected static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    protected static final String COOKIE_CSRF = "XSRF-TOKEN";
    protected static final String TOKEN_CSRF = "csrf-contagem-integracao";
    protected static final String LAMINAS = "/api/v1/laminas";
    private static final String URL_ORIENTACAO = "https://front.citogenetica.test/orientacoes/convites/aceitar";
    private static final String ASSUNTO_ORIENTACAO = "Convite de orientação — Citogenética UFLA";
    private static final Pattern TOKEN = Pattern.compile(Pattern.quote(URL_ORIENTACAO) + "#token=([A-Za-z0-9_-]{43})");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(1);

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected LaboratorioRepository laboratorioRepository;
    @Autowired protected ParticipacaoRepository participacaoRepository;
    @Autowired protected ParticipacaoPapelRepository papelRepository;
    @Autowired protected LaboratorioService laboratorioService;
    @Autowired protected OrientacaoService orientacaoService;
    @Autowired protected UuidFactory uuidFactory;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected PlatformTransactionManager transactionManager;
    @MockBean protected EmailSender emailSender;

    private final Map<Long, Cookie> sessoes = new HashMap<>();
    private final Map<String, Integer> convitesPorEmail = new HashMap<>();

    protected record Cenario(UUID laboratorio, Usuario admin, Usuario orientador, Usuario coorientador,
                             Usuario orientadorEncerrado, Usuario pesquisador, Usuario membro, Usuario exMembro,
                             Usuario fora, Usuario adminGlobal) {
    }

    protected Cenario montarCenario() throws Exception {
        Usuario admin = criarUsuario();
        Usuario orientador = criarUsuario();
        Usuario coorientador = criarUsuario();
        Usuario orientadorEncerrado = criarUsuario();
        Usuario pesquisador = criarUsuario();
        Usuario membro = criarUsuario();
        Usuario exMembro = criarUsuario();
        Usuario fora = criarUsuario();
        Usuario adminGlobal = criarUsuario(RolesEnum.ADMIN);
        UUID laboratorio = laboratorioService.criar("Laboratório " + UUID.randomUUID(), admin.getId()).id();
        adicionarMembro(laboratorio, orientador, PapelLaboratorio.ORIENTADOR);
        adicionarMembro(laboratorio, coorientador, PapelLaboratorio.ORIENTADOR);
        adicionarMembro(laboratorio, orientadorEncerrado, PapelLaboratorio.ORIENTADOR);
        adicionarMembro(laboratorio, pesquisador);
        adicionarMembro(laboratorio, membro);
        adicionarMembro(laboratorio, exMembro);

        for (Usuario quem : List.of(orientador, coorientador, orientadorEncerrado)) {
            orientar(laboratorio, quem, pesquisador);
        }
        UUID vinculo = jdbcTemplate.queryForObject(
                "SELECT id FROM vinculo_orientacao WHERE laboratorio_id = ? AND orientador_usuario_id = ? "
                        + "AND pesquisador_usuario_id = ?", UUID.class, laboratorio, orientadorEncerrado.getId(),
                pesquisador.getId());
        orientacaoService.encerrarVinculo(laboratorio, vinculo, orientadorEncerrado.getId());
        laboratorioService.sair(laboratorio, exMembro.getId());
        return new Cenario(laboratorio, admin, orientador, coorientador, orientadorEncerrado, pesquisador, membro,
                exMembro, fora, adminGlobal);
    }

    private void orientar(UUID laboratorio, Usuario orientador, Usuario pesquisador) throws Exception {
        requisicaoAutenticada(sessao(orientador), post("/api/v1/laboratorios/{id}/orientacoes/convites", laboratorio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", pesquisador.getEmail()))))
                .andExpect(status().isCreated());
        int envio = convitesPorEmail.merge(pesquisador.getEmail(), 1, Integer::sum);
        org.mockito.ArgumentCaptor<String> corpo = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000).atLeast(envio))
                .sendEmail(eq(pesquisador.getEmail()), eq(ASSUNTO_ORIENTACAO), corpo.capture());
        Matcher matcher = TOKEN.matcher(corpo.getAllValues().get(envio - 1));
        assertTrue(matcher.find(), "O e-mail deve conter a URL configurada com o token no fragmento.");
        String ip = "203.0.113." + IP_SEQUENCE.getAndUpdate(atual -> atual >= 240 ? 1 : atual + 1);
        requisicaoAutenticada(sessao(pesquisador), post("/api/v1/orientacoes/convites/aceitar")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", matcher.group(1)))))
                .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- lâminas e protocolos

    protected UUID criarLamina(Usuario dono, UUID laboratorio) throws Exception {
        return criarLamina(dono, laboratorio, "mitose");
    }

    protected UUID criarLamina(Usuario dono, UUID laboratorio, String protocolo) throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", id.toString());
        corpo.put("protocoloVersaoId", versaoPublicada(protocolo).toString());
        if (laboratorio != null) {
            corpo.put("laboratorioId", laboratorio.toString());
        }
        corpo.put("nome", "Lâmina " + id.toString().substring(0, 8));
        requisicaoAutenticada(sessao(dono), post(LAMINAS).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo))).andExpect(status().isCreated());
        return id;
    }

    protected UUID versaoPublicada(String codigo) {
        return jdbcTemplate.queryForObject(
                "SELECT v.id FROM protocolo_versao v JOIN protocolo p ON p.id = v.protocolo_id "
                        + "WHERE p.codigo = ? AND v.numero = 1", UUID.class, codigo);
    }

    protected UUID fase(String protocolo, String nome) {
        return jdbcTemplate.queryForObject(
                "SELECT f.id FROM protocolo_fase f JOIN protocolo_versao v ON v.id = f.versao_id "
                        + "JOIN protocolo p ON p.id = v.protocolo_id WHERE p.codigo = ? AND v.numero = 1 AND f.nome = ?",
                UUID.class, protocolo, nome);
    }

    protected UUID tipoFixo(String protocolo, String fase, String nome) {
        return jdbcTemplate.queryForObject(
                "SELECT t.id FROM tipo_anormalidade t JOIN protocolo_fase f ON f.id = t.fase_id "
                        + "JOIN protocolo_versao v ON v.id = f.versao_id JOIN protocolo p ON p.id = v.protocolo_id "
                        + "WHERE p.codigo = ? AND v.numero = 1 AND f.nome = ? AND t.nome = ? AND t.usuario_id IS NULL",
                UUID.class, protocolo, fase, nome);
    }

    protected UUID tipoPersonalizado(UUID fase, Usuario dono, UUID laboratorio, String nome) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tipo_anormalidade (id, version, created_at, last_modified_at, fase_id, "
                + "usuario_id, laboratorio_id, nome, nome_normalizado, ordem) "
                + "VALUES (?, 0, NOW(), NOW(), ?, ?, ?, ?, ?, NULL)",
                id, fase, dono.getId(), laboratorio, nome, nome.toLowerCase());
        return id;
    }

    // ---------------------------------------------------------------- usuários e HTTP

    protected void adicionarMembro(UUID laboratorioId, Usuario membro, PapelLaboratorio... papeisExtras) {
        new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
            Laboratorio laboratorio = laboratorioRepository.findById(laboratorioId).orElseThrow();
            Participacao participacao = participacaoRepository.save(
                    new Participacao(uuidFactory.generate(), laboratorio, membro.getId(), Instant.now()));
            Set<PapelLaboratorio> papeis = new java.util.LinkedHashSet<>();
            papeis.add(PapelLaboratorio.MEMBRO);
            papeis.addAll(List.of(papeisExtras));
            for (PapelLaboratorio papel : papeis) {
                papelRepository.save(new ParticipacaoPapel(
                        uuidFactory.generate(), participacao, papel, laboratorio.getCriadoPorUsuarioId()));
            }
        });
    }

    protected Usuario criarUsuario() {
        return criarUsuario(RolesEnum.USER);
    }

    protected Usuario criarUsuario(RolesEnum papelGlobal) {
        Role role = roleRepository.findByNome(papelGlobal).orElseThrow();
        String email = "registros-" + UUID.randomUUID() + "@exemplo.org";
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Pessoa de teste " + email.substring(10, 18))
                .email(email)
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
                .role(role)
                .emailConfirmadoEm(Instant.now())
                .build());
    }

    protected Cookie sessao(Usuario usuario) throws Exception {
        Cookie existente = sessoes.get(usuario.getId());
        if (existente != null) {
            return existente;
        }
        Cookie csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(COOKIE_CSRF);
        assertNotNull(csrf);
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> {
                            request.setRemoteAddr(ipNovo());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", usuario.getEmail(), "senha", SENHA))))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie sessao = resultado.getResponse().getCookie(COOKIE_SESSAO);
        assertNotNull(sessao);
        sessoes.put(usuario.getId(), sessao);
        return sessao;
    }

    /** Cada login usa um IP próprio para não esgotar o limite de tentativas por IP, que é compartilhado. */
    private static String ipNovo() {
        java.util.concurrent.ThreadLocalRandom aleatorio = java.util.concurrent.ThreadLocalRandom.current();
        return "10." + aleatorio.nextInt(1, 255) + "." + aleatorio.nextInt(1, 255) + "." + aleatorio.nextInt(1, 255);
    }

    protected ResultActions requisicaoAutenticada(Cookie sessao, MockHttpServletRequestBuilder requisicao)
            throws Exception {
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        return mockMvc.perform(requisicao.cookie(sessao, csrf).header("X-XSRF-TOKEN", TOKEN_CSRF));
    }

    protected ResultActions comCsrfSemLogin(MockHttpServletRequestBuilder requisicao) throws Exception {
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        return mockMvc.perform(requisicao.cookie(csrf).header("X-XSRF-TOKEN", TOKEN_CSRF));
    }

    protected JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Corpo de erro sem o horário e o caminho, que mudam entre requisições à lâmina de cada um. */
    protected Map<String, Object> corpoSemHorario(MvcResult resultado) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> corpo = objectMapper.readValue(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        corpo.remove("timestamp");
        corpo.remove("path");
        return corpo;
    }

    protected <T> List<T> dispararSimultaneos(int quantidade, TarefaComIndice<T> tarefa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(quantidade);
        try {
            CountDownLatch pronto = new CountDownLatch(quantidade);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<T>> futuros = new ArrayList<>();
            for (int indice = 0; indice < quantidade; indice++) {
                int copia = indice;
                futuros.add(executor.submit(() -> {
                    pronto.countDown();
                    largada.await();
                    return tarefa.executar(copia);
                }));
            }
            pronto.await();
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get());
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    protected interface TarefaComIndice<T> {
        T executar(int indice) throws Exception;
    }

    protected static void assertQueTodos(List<Integer> statuses, Integer... permitidos) {
        assertThat(statuses).as("statuses %s", statuses).isNotEmpty().allMatch(List.of(permitidos)::contains);
    }
}
