package br.ufla.citogenetica;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import br.ufla.citogenetica.laboratorio.internal.application.LaboratorioService;
import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.Participacao;
import br.ufla.citogenetica.laboratorio.internal.domain.ParticipacaoPapel;
import br.ufla.citogenetica.laboratorio.internal.persistence.LaboratorioRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoPapelRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoRepository;
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
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(AdminLaboratorioListagemIntegrationTest.SqlCaptureConfiguration.class)
class AdminLaboratorioListagemIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "Frase segura para testes de laboratório";
    private static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String TOKEN_CSRF = "csrf-admin-listagem";
    private static final String ADMIN_LABORATORIOS = "/api/v1/admin/laboratorios";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LaboratorioRepository laboratorioRepository;

    @Autowired
    private ParticipacaoRepository participacaoRepository;

    @Autowired
    private ParticipacaoPapelRepository papelRepository;

    @Autowired
    private LaboratorioService laboratorioService;

    @Autowired
    private UuidFactory uuidFactory;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CapturadorSql capturadorSql;

    private Usuario adminPlataforma;
    private Cookie sessaoAdmin;
    private String marcador;

    @BeforeEach
    void preparar() throws Exception {
        marcador = UUID.randomUUID().toString().substring(0, 8);
        adminPlataforma = criarUsuario(RolesEnum.ADMIN);
        sessaoAdmin = login(adminPlataforma);
    }

    @Test
    void administradorGlobalVeTodosOsLaboratoriosInclusiveDesativados() throws Exception {
        Usuario criador = criarUsuario(RolesEnum.USER);
        UUID ativo = criarLaboratorio(criador, "Ativo " + marcador);
        UUID desativado = criarLaboratorio(criador, "Desativado " + marcador);
        laboratorioService.desativar(desativado, adminPlataforma.getId());

        JsonNode resposta = objectMapper.readTree(requisicaoAutenticada(sessaoAdmin,
                        get(ADMIN_LABORATORIOS).param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(resposta.path("totalElements").asLong()).isEqualTo(laboratorioRepository.count());
        assertThat(resposta.path("content").findValuesAsText("id"))
                .contains(ativo.toString(), desativado.toString());
        JsonNode laboratorioAtivo = encontrar(resposta.path("content"), ativo.toString());
        JsonNode laboratorioDesativado = encontrar(resposta.path("content"), desativado.toString());
        assertThat(laboratorioAtivo.path("estado").asText()).isEqualTo("ATIVO");
        assertThat(laboratorioDesativado.path("estado").asText()).isEqualTo("DESATIVADO");
        assertThat(laboratorioAtivo.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "nome", "estado", "quantidadeMembrosAtivos", "administradores");
    }

    @Test
    void usuarioRecebe403ESemSessaoRecebe401() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER);

        requisicaoAutenticada(login(usuario), get(ADMIN_LABORATORIOS))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_LABORATORIOS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contaSomenteParticipacoesAtivasEAdministradoresComPapelVigente() throws Exception {
        Usuario criador = criarUsuario(RolesEnum.USER);
        UUID laboratorioId = criarLaboratorio(criador, "Contagens " + marcador);
        Usuario adminAtivo = criarUsuario(RolesEnum.USER);
        adicionarParticipacao(laboratorioId, adminAtivo.getId(), criador.getId(),
                Set.of(PapelLaboratorio.MEMBRO, PapelLaboratorio.ADMIN));
        Usuario adminRemovido = criarUsuario(RolesEnum.USER);
        UUID participacaoAdminRemovido = adicionarParticipacao(laboratorioId, adminRemovido.getId(),
                criador.getId(), Set.of(PapelLaboratorio.MEMBRO, PapelLaboratorio.ADMIN));
        Usuario membroQueSaiu = criarUsuario(RolesEnum.USER);
        adicionarParticipacao(laboratorioId, membroQueSaiu.getId(), criador.getId(), Set.of(PapelLaboratorio.MEMBRO));
        Usuario membroRemovido = criarUsuario(RolesEnum.USER);
        UUID participacaoMembroRemovido = adicionarParticipacao(laboratorioId, membroRemovido.getId(),
                criador.getId(), Set.of(PapelLaboratorio.MEMBRO));
        Usuario membroAtivo = criarUsuario(RolesEnum.USER);
        adicionarParticipacao(laboratorioId, membroAtivo.getId(), criador.getId(), Set.of(PapelLaboratorio.MEMBRO));

        UUID participacaoQueSaiu = participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorioId, membroQueSaiu.getId(),
                        br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao.ATIVA)
                .orElseThrow().getId();
        laboratorioService.sair(laboratorioId, membroQueSaiu.getId());
        laboratorioService.remover(laboratorioId, participacaoMembroRemovido, criador.getId());
        laboratorioService.remover(laboratorioId, participacaoAdminRemovido, criador.getId());

        JsonNode resposta = objectMapper.readTree(requisicaoAutenticada(sessaoAdmin,
                        get(ADMIN_LABORATORIOS).param("nome", "Contagens " + marcador))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode laboratorio = resposta.path("content").get(0);

        assertThat(laboratorio.path("quantidadeMembrosAtivos").asLong()).isEqualTo(3);
        assertThat(laboratorio.path("administradores").size()).isEqualTo(2);
        assertThat(laboratorio.path("administradores").findValuesAsText("nome"))
                .contains(criador.getNome(), adminAtivo.getNome())
                .doesNotContain(adminRemovido.getNome(), membroAtivo.getNome(), membroRemovido.getNome());
        String json = laboratorio.toString();
        assertThat(json).contains(criador.getEmail(), adminAtivo.getEmail());
        assertThat(json).doesNotContain(adminRemovido.getEmail(), membroAtivo.getEmail(), membroRemovido.getEmail());
        assertThat(participacaoRepository.findById(participacaoQueSaiu).orElseThrow().getEstado().name())
                .isEqualTo("SAIU");
    }

    @Test
    void filtrosNomeEstadoIgnoramAcentosEValidamEstado() throws Exception {
        Usuario criador = criarUsuario(RolesEnum.USER);
        UUID ativo = criarLaboratorio(criador, "Laboratório Árvore " + marcador);
        UUID desativado = criarLaboratorio(criador, "Laboratório Árvore desativado " + marcador);
        laboratorioService.desativar(desativado, adminPlataforma.getId());

        requisicaoAutenticada(sessaoAdmin, get(ADMIN_LABORATORIOS)
                        .param("nome", "laboratorio arvore")
                        .param("estado", "DESATIVADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(desativado.toString()));

        requisicaoAutenticada(sessaoAdmin, get(ADMIN_LABORATORIOS).param("estado", "INEXISTENTE"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessaoAdmin, get(ADMIN_LABORATORIOS).param("campoInterno", "valor"))
                .andExpect(status().isBadRequest());
        assertThat(ativo).isNotEqualTo(desativado);
    }

    @Test
    void paginacaoTemOrdemEstavelELimiteMaximo() throws Exception {
        Usuario criador = criarUsuario(RolesEnum.USER);
        UUID primeiro = criarLaboratorio(criador, "Empate " + marcador);
        UUID segundo = criarLaboratorio(criador, "Empate " + marcador);
        UUID terceiro = criarLaboratorio(criador, "Empate " + marcador);

        JsonNode pagina0 = objectMapper.readTree(requisicaoAutenticada(sessaoAdmin,
                        get(ADMIN_LABORATORIOS).param("nome", marcador).param("size", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode pagina1 = objectMapper.readTree(requisicaoAutenticada(sessaoAdmin,
                        get(ADMIN_LABORATORIOS).param("nome", marcador).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        List<String> ids = java.util.stream.Stream.concat(
                        java.util.stream.StreamSupport.stream(pagina0.path("content").spliterator(), false),
                        java.util.stream.StreamSupport.stream(pagina1.path("content").spliterator(), false))
                .map(item -> item.path("id").asText())
                .toList();
        assertThat(pagina0.path("totalElements").asLong()).isEqualTo(3);
        assertThat(pagina0.path("totalPages").asInt()).isEqualTo(2);
        assertThat(ids).containsExactlyElementsOf(List.of(primeiro.toString(), segundo.toString(), terceiro.toString())
                .stream().sorted().toList());

        requisicaoAutenticada(sessaoAdmin, get(ADMIN_LABORATORIOS).param("size", "101"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessaoAdmin, get(ADMIN_LABORATORIOS).param("page", "-1"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessaoAdmin, get(ADMIN_LABORATORIOS).param("sortBy", "createdAt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultasDeContagemAdministradoresERosterNaoCrescemComLaboratorios() throws Exception {
        Usuario criador = criarUsuario(RolesEnum.USER);
        criarLaboratorio(criador, "N1 single " + marcador);
        for (int indice = 1; indice <= 6; indice++) {
            criarLaboratorio(criador, "N1 lote " + marcador + " " + indice);
        }

        capturadorSql.limpar();
        requisicaoAutenticada(sessaoAdmin, get(ADMIN_LABORATORIOS)
                        .param("nome", "N1 single " + marcador).param("size", "20"))
                .andExpect(status().isOk());
        List<String> consultasUmLaboratorio = capturadorSql.consultas();

        capturadorSql.limpar();
        requisicaoAutenticada(sessaoAdmin, get(ADMIN_LABORATORIOS)
                        .param("nome", "N1 lote " + marcador).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(6)));
        List<String> consultasVariosLaboratorios = capturadorSql.consultas();

        assertThat(consultasVariosLaboratorios).hasSameSizeAs(consultasUmLaboratorio);
        assertThat(contarConsultas(consultasVariosLaboratorios, "from participacao ")).isEqualTo(1);
        assertThat(contarConsultas(consultasVariosLaboratorios, "from participacao_papel ")).isEqualTo(1);
        assertThat(contarConsultas(consultasVariosLaboratorios, "from usuario "))
                .isEqualTo(contarConsultas(consultasUmLaboratorio, "from usuario "));
    }

    @Test
    void administradorDaPlataformaContinuaSemAcessoAosDadosInternos() throws Exception {
        Usuario criador = criarUsuario(RolesEnum.USER);
        UUID laboratorioId = criarLaboratorio(criador, "Isolamento " + marcador);

        requisicaoAutenticada(sessaoAdmin, get("/api/v1/laboratorios/{id}", laboratorioId))
                .andExpect(status().isNotFound());
    }

    private long contarConsultas(List<String> consultas, String fragmento) {
        return consultas.stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.contains(fragmento))
                .count();
    }

    private JsonNode encontrar(JsonNode laboratorios, String id) {
        for (JsonNode laboratorio : laboratorios) {
            if (laboratorio.path("id").asText().equals(id)) {
                return laboratorio;
            }
        }
        throw new AssertionError("Laboratório não encontrado na listagem.");
    }

    private UUID criarLaboratorio(Usuario criador, String nome) {
        return laboratorioService.criar(nome, criador.getId()).id();
    }

    private UUID adicionarParticipacao(UUID laboratorioId, Long usuarioId, Long concedidoPor,
                                       Set<PapelLaboratorio> papeis) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Laboratorio laboratorio = laboratorioRepository.findById(laboratorioId).orElseThrow();
            Participacao participacao = new Participacao(uuidFactory.generate(), laboratorio, usuarioId, Instant.now());
            participacaoRepository.save(participacao);
            papelRepository.saveAll(papeis.stream()
                    .map(papel -> new ParticipacaoPapel(uuidFactory.generate(), participacao, papel, concedidoPor))
                    .toList());
            return participacao.getId();
        });
    }

    private Usuario criarUsuario(RolesEnum papelGlobal) {
        Role role = roleRepository.findByNome(papelGlobal).orElseThrow();
        String email = "admin-listagem-" + UUID.randomUUID() + "@exemplo.org";
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Pessoa de teste " + email.substring(15, 23))
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
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "senha", SENHA)))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie sessao = resultado.getResponse().getCookie(COOKIE_SESSAO);
        assertNotNull(sessao);
        return sessao;
    }

    private Cookie csrfCookie() throws Exception {
        Cookie csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(COOKIE_CSRF);
        assertNotNull(csrf);
        return csrf;
    }

    private ResultActions requisicaoAutenticada(Cookie sessao, MockHttpServletRequestBuilder requisicao)
            throws Exception {
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        return mockMvc.perform(requisicao.cookie(sessao, csrf).header("X-XSRF-TOKEN", TOKEN_CSRF));
    }

    private String json(Object... pares) throws Exception {
        var objeto = new java.util.LinkedHashMap<String, Object>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            objeto.put((String) pares[indice], pares[indice + 1]);
        }
        return objectMapper.writeValueAsString(objeto);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlCaptureConfiguration {

        @Bean
        CapturadorSql capturadorSql() {
            return new CapturadorSql();
        }

        @Bean
        HibernatePropertiesCustomizer sqlInspectorCustomizer(CapturadorSql capturadorSql) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capturadorSql);
        }
    }

    static class CapturadorSql implements StatementInspector {
        private final List<String> consultas = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            consultas.add(sql);
            return sql;
        }

        void limpar() {
            consultas.clear();
        }

        List<String> consultas() {
            return List.copyOf(consultas);
        }
    }
}
