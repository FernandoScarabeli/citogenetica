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
import br.ufla.citogenetica.usuario.api.UsuarioRosterQuery;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ContagemLaminasIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "Frase segura para testes de lâminas";
    private static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String TOKEN_CSRF = "csrf-laminas-integracao";
    private static final String LAMINAS = "/api/v1/laminas";
    private static final String URL_ORIENTACAO = "https://front.citogenetica.test/orientacoes/convites/aceitar";
    private static final String ASSUNTO_ORIENTACAO = "Convite de orientação — Citogenética UFLA";
    private static final Pattern TOKEN = Pattern.compile(Pattern.quote(URL_ORIENTACAO) + "#token=([A-Za-z0-9_-]{43})");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private LaboratorioRepository laboratorioRepository;
    @Autowired private ParticipacaoRepository participacaoRepository;
    @Autowired private ParticipacaoPapelRepository papelRepository;
    @Autowired private LaboratorioService laboratorioService;
    @Autowired private OrientacaoService orientacaoService;
    @Autowired private UuidFactory uuidFactory;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private EmailSender emailSender;
    @SpyBean private UsuarioRosterQuery usuarioRosterQuery;

    private final Map<Long, Cookie> sessoes = new HashMap<>();
    private final Map<String, Integer> convitesPorEmail = new HashMap<>();

    /** Quem faz a requisição e o que se espera de cada operação sobre a lâmina do pesquisador. */
    private record Ator(String nome, Usuario usuario, int get, int patch, int delete, boolean veNaListagem,
                        int statusListagem) {
    }

    private record Cenario(UUID laboratorio, Usuario admin, Usuario orientador, Usuario coorientador,
                           Usuario orientadorEncerrado, Usuario pesquisador, Usuario membro, Usuario exMembro,
                           Usuario fora, Usuario adminGlobal) {

        List<Ator> atores() {
            return List.of(
                    new Ator("dono", pesquisador, 200, 200, 204, true, 200),
                    new Ator("orientador ativo", orientador, 200, 403, 403, true, 200),
                    new Ator("coorientador", coorientador, 200, 403, 403, true, 200),
                    new Ator("orientador com vínculo encerrado", orientadorEncerrado, 403, 403, 403, false, 200),
                    new Ator("admin do laboratório", admin, 200, 403, 403, true, 200),
                    new Ator("membro comum", membro, 403, 403, 403, false, 200),
                    new Ator("ex-membro", exMembro, 404, 404, 404, false, 404),
                    new Ator("pessoa de fora", fora, 404, 404, 404, false, 404),
                    new Ator("ADMIN global de fora", adminGlobal, 404, 404, 404, false, 404));
        }
    }

    // ---------------------------------------------------------------- matriz de acesso

    @Test
    void matrizDeAcessoNaLeituraDeItemNaListagemNoPatchENoDelete() throws Exception {
        Cenario cenario = montarCenario();

        verificarMatriz(cenario, cenario.atores());
    }

    @Test
    void laboratorioDesativadoDonoEAdminEOrientadoresLeemMasNinguemEscreve() throws Exception {
        Cenario cenario = montarCenario();
        UUID leitura = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        laboratorioService.desativar(cenario.laboratorio(), cenario.adminGlobal().getId());

        List<Ator> atores = List.of(
                new Ator("dono", cenario.pesquisador(), 200, 403, 403, true, 200),
                new Ator("orientador ativo", cenario.orientador(), 200, 403, 403, true, 200),
                new Ator("coorientador", cenario.coorientador(), 200, 403, 403, true, 200),
                new Ator("admin do laboratório", cenario.admin(), 200, 403, 403, true, 200),
                new Ator("membro comum", cenario.membro(), 403, 403, 403, false, 200),
                new Ator("ex-membro", cenario.exMembro(), 404, 404, 404, false, 404),
                new Ator("pessoa de fora", cenario.fora(), 404, 404, 404, false, 404),
                new Ator("ADMIN global de fora", cenario.adminGlobal(), 404, 404, 404, false, 404));
        for (Ator ator : atores) {
            requisicaoAutenticada(sessao(ator.usuario()), get(LAMINAS + "/{id}", leitura))
                    .andExpect(status().is(ator.get()));
            requisicaoAutenticada(sessao(ator.usuario()), patch(LAMINAS + "/{id}", leitura)
                    .contentType(MediaType.APPLICATION_JSON).content(corpoPatch(versaoNoBanco(leitura), "Novo", null)))
                    .andExpect(status().is(ator.patch()));
            requisicaoAutenticada(sessao(ator.usuario()), delete(LAMINAS + "/{id}", leitura)
                    .param("versaoEsperada", String.valueOf(versaoNoBanco(leitura))))
                    .andExpect(status().is(ator.delete()));
            if (ator.statusListagem() == 200) {
                JsonNode lista = json(listar(ator.usuario(), "laboratorioId", cenario.laboratorio().toString())
                        .andExpect(status().isOk()).andReturn());
                assertThat(ids(lista).contains(leitura.toString())).as(ator.nome()).isEqualTo(ator.veNaListagem());
            } else {
                listar(ator.usuario(), "laboratorioId", cenario.laboratorio().toString())
                        .andExpect(status().isNotFound());
            }
        }
        assertThat(excluidaEm(leitura)).isNull();
        assertThat(nomeNoBanco(leitura)).isNotEqualTo("Novo");

        // Criar também é bloqueado: 403 para membro ativo, mesmo com protocolo válido.
        postarLamina(sessao(cenario.pesquisador()), corpoPadrao(UUID.randomUUID(), cenario.laboratorio()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDoLaboratorioContinuaVendoLaminaDeQuemJaSaiuMasOProprioExMembroNao() throws Exception {
        Cenario cenario = montarCenario();
        Usuario saiu = criarUsuario();
        adicionarMembro(cenario.laboratorio(), saiu);
        UUID id = criarLamina(saiu, cenario.laboratorio());
        laboratorioService.sair(cenario.laboratorio(), saiu.getId());

        requisicaoAutenticada(sessao(cenario.admin()), get(LAMINAS + "/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.donoUsuarioId").value(saiu.getId()));
        assertThat(ids(json(listar(cenario.admin(), "laboratorioId", cenario.laboratorio().toString())
                .andExpect(status().isOk()).andReturn()))).contains(id.toString());
        requisicaoAutenticada(sessao(cenario.admin()), patch(LAMINAS + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(corpoPatch(versaoNoBanco(id), "X", null)))
                .andExpect(status().isForbidden());
        requisicaoAutenticada(sessao(saiu), get(LAMINAS + "/{id}", id)).andExpect(status().isNotFound());
        requisicaoAutenticada(sessao(saiu), patch(LAMINAS + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(corpoPatch(versaoNoBanco(id), "X", null)))
                .andExpect(status().isNotFound());
        requisicaoAutenticada(sessao(saiu), delete(LAMINAS + "/{id}", id)
                .param("versaoEsperada", String.valueOf(versaoNoBanco(id)))).andExpect(status().isNotFound());
        listar(saiu, "laboratorioId", cenario.laboratorio().toString()).andExpect(status().isNotFound());
        requisicaoAutenticada(sessao(cenario.membro()), get(LAMINAS + "/{id}", id)).andExpect(status().isForbidden());
    }

    @Test
    void laminaAvulsaEhSoDoDonoParaLerEscreverEListar() throws Exception {
        Cenario cenario = montarCenario();
        UUID id = criarLamina(cenario.pesquisador(), null);

        requisicaoAutenticada(sessao(cenario.pesquisador()), get(LAMINAS + "/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.laboratorioId").value(org.hamcrest.Matchers.nullValue()));
        for (Usuario outro : List.of(cenario.admin(), cenario.orientador(), cenario.membro(), cenario.fora(),
                cenario.adminGlobal(), cenario.exMembro())) {
            requisicaoAutenticada(sessao(outro), get(LAMINAS + "/{id}", id)).andExpect(status().isNotFound());
            requisicaoAutenticada(sessao(outro), patch(LAMINAS + "/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON).content(corpoPatch(versaoNoBanco(id), "Invasor", null)))
                    .andExpect(status().isNotFound());
            requisicaoAutenticada(sessao(outro), delete(LAMINAS + "/{id}", id)
                    .param("versaoEsperada", String.valueOf(versaoNoBanco(id)))).andExpect(status().isNotFound());
            assertThat(ids(json(listar(outro).andExpect(status().isOk()).andReturn()))).doesNotContain(id.toString());
        }
        assertThat(nomeNoBanco(id)).isNotEqualTo("Invasor");
        assertThat(excluidaEm(id)).isNull();
        assertThat(ids(json(listar(cenario.pesquisador()).andExpect(status().isOk()).andReturn())))
                .containsExactly(id.toString());
        requisicaoAutenticada(sessao(cenario.pesquisador()), patch(LAMINAS + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content(corpoPatch(versaoNoBanco(id), "Meu nome", null)))
                .andExpect(status().isOk());
        requisicaoAutenticada(sessao(cenario.pesquisador()), delete(LAMINAS + "/{id}", id)
                .param("versaoEsperada", String.valueOf(versaoNoBanco(id)))).andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- criação com laboratório

    @Test
    void criacaoComLaboratorioRespeitaVisibilidadeEEdicaoEODonoEhSempreQuemEstaLogado() throws Exception {
        Cenario cenario = montarCenario();

        for (Usuario permitido : List.of(cenario.pesquisador(), cenario.admin(), cenario.orientador(), cenario.membro())) {
            UUID id = UUID.randomUUID();
            postarLamina(sessao(permitido), corpoPadrao(id, cenario.laboratorio()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.donoUsuarioId").value(permitido.getId()))
                    .andExpect(jsonPath("$.donoNome").value(permitido.getNome()))
                    .andExpect(jsonPath("$.laboratorioId").value(cenario.laboratorio().toString()));
        }
        for (Usuario semAcesso : List.of(cenario.fora(), cenario.adminGlobal(), cenario.exMembro())) {
            UUID id = UUID.randomUUID();
            postarLamina(sessao(semAcesso), corpoPadrao(id, cenario.laboratorio())).andExpect(status().isNotFound());
            assertThat(existeNoBanco(id)).isFalse();
        }
        // O dono vem da sessão: um donoUsuarioId no corpo é ignorado.
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpoPadrao(id, cenario.laboratorio());
        corpo.put("donoUsuarioId", cenario.admin().getId());
        postarLamina(sessao(cenario.membro()), corpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.donoUsuarioId").value(cenario.membro().getId()));
    }

    @Test
    void criacaoNaoRevelaOEstadoDoLaboratorioAQuemNaoOEnxerga() throws Exception {
        Cenario cenario = montarCenario();
        laboratorioService.desativar(cenario.laboratorio(), cenario.adminGlobal().getId());
        UUID rascunho = criarVersaoRascunho();

        Map<String, Object> deFora = corpoPadrao(UUID.randomUUID(), cenario.laboratorio());
        MvcResult foraAtivo = postarLamina(sessao(cenario.fora()), deFora).andExpect(status().isNotFound()).andReturn();
        MvcResult foraLabInexistente = postarLamina(sessao(cenario.fora()),
                corpoPadrao(UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isNotFound()).andReturn();
        assertThat(corpoSemHorario(foraAtivo)).isEqualTo(corpoSemHorario(foraLabInexistente));

        Map<String, Object> comRascunho = corpoPadrao(UUID.randomUUID(), cenario.laboratorio());
        comRascunho.put("protocoloVersaoId", rascunho.toString());
        MvcResult foraRascunho = postarLamina(sessao(cenario.fora()), comRascunho)
                .andExpect(status().isNotFound()).andReturn();
        assertThat(corpoSemHorario(foraRascunho)).isEqualTo(corpoSemHorario(foraAtivo));
        // Quem enxerga o laboratório desativado recebe 403, e 404 se o protocolo for inválido.
        postarLamina(sessao(cenario.membro()), corpoPadrao(UUID.randomUUID(), cenario.laboratorio()))
                .andExpect(status().isForbidden());
        postarLamina(sessao(cenario.membro()), comRascunho).andExpect(status().isNotFound());
    }

    @Test
    void protocoloInexistenteOuEmRascunhoEh404() throws Exception {
        Usuario dono = criarUsuario();
        UUID rascunho = criarVersaoRascunho();

        for (UUID invalido : List.of(rascunho, UUID.randomUUID())) {
            UUID id = UUID.randomUUID();
            Map<String, Object> corpo = corpoPadrao(id, null);
            corpo.put("protocoloVersaoId", invalido.toString());
            postarLamina(sessao(dono), corpo).andExpect(status().isNotFound());
            assertThat(existeNoBanco(id)).isFalse();
        }
    }

    // ---------------------------------------------------------------- listagem

    @Test
    void listagemPaginadaTemTotalCorretoMesmoComDonosQueOLeitorNaoPodeVer() throws Exception {
        Cenario cenario = montarCenario();
        Usuario outroMembro = criarUsuario();
        adicionarMembro(cenario.laboratorio(), outroMembro);
        List<UUID> minhas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            minhas.add(criarLamina(cenario.membro(), cenario.laboratorio()));
        }
        for (int i = 0; i < 4; i++) {
            criarLamina(cenario.pesquisador(), cenario.laboratorio());
        }
        for (int i = 0; i < 2; i++) {
            criarLamina(outroMembro, cenario.laboratorio());
        }
        UUID avulsa = criarLamina(cenario.membro(), null);
        UUID laboratorio = cenario.laboratorio();

        JsonNode pagina0 = json(listar(cenario.membro(), "laboratorioId", laboratorio.toString(), "size", "2", "page", "0")
                .andExpect(status().isOk()).andReturn());
        JsonNode pagina1 = json(listar(cenario.membro(), "laboratorioId", laboratorio.toString(), "size", "2", "page", "1")
                .andExpect(status().isOk()).andReturn());
        JsonNode pagina2 = json(listar(cenario.membro(), "laboratorioId", laboratorio.toString(), "size", "2", "page", "2")
                .andExpect(status().isOk()).andReturn());

        assertThat(pagina0.path("totalElements").asLong()).isEqualTo(3);
        assertThat(pagina0.path("totalPages").asInt()).isEqualTo(2);
        assertThat(pagina1.path("totalElements").asLong()).isEqualTo(3);
        assertThat(pagina2.path("content")).isEmpty();
        List<String> vistas = new ArrayList<>(ids(pagina0));
        vistas.addAll(ids(pagina1));
        // Mais recente primeiro, sem as avulsas e sem as lâminas de quem ele não pode ver.
        assertThat(vistas).containsExactly(minhas.get(2).toString(), minhas.get(1).toString(), minhas.get(0).toString());
        assertThat(vistas).doesNotContain(avulsa.toString());

        JsonNode doAdmin = json(listar(cenario.admin(), "laboratorioId", laboratorio.toString(), "size", "100")
                .andExpect(status().isOk()).andReturn());
        assertThat(doAdmin.path("totalElements").asLong()).isEqualTo(9);
        assertThat(ids(doAdmin)).doesNotContain(avulsa.toString());
    }

    @Test
    void listagemFiltraPorDonoSemRevelarOQueOLeitorNaoPodeVer() throws Exception {
        Cenario cenario = montarCenario();
        criarLamina(cenario.membro(), cenario.laboratorio());
        criarLamina(cenario.pesquisador(), cenario.laboratorio());
        criarLamina(cenario.pesquisador(), cenario.laboratorio());
        String lab = cenario.laboratorio().toString();

        JsonNode doPesquisador = json(listar(cenario.admin(), "laboratorioId", lab,
                "donoUsuarioId", String.valueOf(cenario.pesquisador().getId())).andExpect(status().isOk()).andReturn());
        assertThat(doPesquisador.path("totalElements").asLong()).isEqualTo(2);
        for (JsonNode lamina : doPesquisador.path("content")) {
            assertThat(lamina.path("donoUsuarioId").asLong()).isEqualTo(cenario.pesquisador().getId());
        }
        JsonNode proibido = json(listar(cenario.membro(), "laboratorioId", lab,
                "donoUsuarioId", String.valueOf(cenario.pesquisador().getId())).andExpect(status().isOk()).andReturn());
        assertThat(proibido.path("content")).isEmpty();
        assertThat(proibido.path("totalElements").asLong()).isZero();
        JsonNode proprio = json(listar(cenario.membro(), "laboratorioId", lab,
                "donoUsuarioId", String.valueOf(cenario.membro().getId())).andExpect(status().isOk()).andReturn());
        assertThat(proprio.path("totalElements").asLong()).isEqualTo(1);
        // Sem laboratório, o filtro por outra pessoa não mostra nada.
        UUID avulsa = criarLamina(cenario.membro(), null);
        assertThat(json(listar(cenario.membro(), "donoUsuarioId", String.valueOf(cenario.admin().getId()))
                .andExpect(status().isOk()).andReturn()).path("content")).isEmpty();
        assertThat(ids(json(listar(cenario.membro(), "donoUsuarioId", String.valueOf(cenario.membro().getId()))
                .andExpect(status().isOk()).andReturn()))).containsExactly(avulsa.toString());
    }

    @Test
    void listagemSemLaboratorioMostraSoAsAvulsasDoProprioUsuario() throws Exception {
        Cenario cenario = montarCenario();
        UUID minhaAvulsa = criarLamina(cenario.membro(), null);
        UUID minhaDeLab = criarLamina(cenario.membro(), cenario.laboratorio());
        UUID avulsaAlheia = criarLamina(cenario.pesquisador(), null);

        List<String> vistas = ids(json(listar(cenario.membro()).andExpect(status().isOk()).andReturn()));

        assertThat(vistas).containsExactly(minhaAvulsa.toString());
        assertThat(vistas).doesNotContain(minhaDeLab.toString(), avulsaAlheia.toString());
    }

    @Test
    void listagemDesempataPorIdQuandoACriacaoEhIgual() throws Exception {
        Usuario dono = criarUsuario();
        List<UUID> criadas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            criadas.add(criarLamina(dono, null));
        }
        jdbcTemplate.update("UPDATE lamina SET created_at = TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE dono_usuario_id = ?",
                dono.getId());

        List<String> esperado = criadas.stream().map(UUID::toString).sorted().toList();

        assertThat(ids(json(listar(dono).andExpect(status().isOk()).andReturn()))).containsExactlyElementsOf(esperado);
    }

    @Test
    void listagemResolveNomesDosDonosEmUmaUnicaConsulta() throws Exception {
        Cenario cenario = montarCenario();
        Usuario outroMembro = criarUsuario();
        adicionarMembro(cenario.laboratorio(), outroMembro);
        for (Usuario dono : List.of(cenario.pesquisador(), cenario.membro(), outroMembro, cenario.pesquisador())) {
            criarLamina(dono, cenario.laboratorio());
        }
        Cookie sessaoAdmin = sessao(cenario.admin());
        Mockito.clearInvocations(usuarioRosterQuery);

        JsonNode lista = json(requisicaoAutenticada(sessaoAdmin, get(LAMINAS).param("laboratorioId",
                cenario.laboratorio().toString())).andExpect(status().isOk()).andReturn());

        assertThat(lista.path("content")).hasSize(4);
        verify(usuarioRosterQuery, times(1)).buscarPorIds(Mockito.anyCollection());
        for (JsonNode lamina : lista.path("content")) {
            assertThat(lamina.path("donoNome").asText()).startsWith("Pessoa de teste");
        }
    }

    @Test
    void listagemRecusaPaginacaoEUuidInvalidos() throws Exception {
        Usuario usuario = criarUsuario();
        Cookie sessao = sessao(usuario);

        for (String[] parametros : new String[][]{{"size", "101"}, {"size", "0"}, {"page", "-1"},
                {"size", "abc"}, {"laboratorioId", "nao-e-uuid"}, {"donoUsuarioId", "abc"}}) {
            requisicaoAutenticada(sessao, get(LAMINAS).param(parametros[0], parametros[1]))
                    .andExpect(status().isBadRequest());
        }
        requisicaoAutenticada(sessao, get(LAMINAS).param("size", "100")).andExpect(status().isOk());
        requisicaoAutenticada(sessao, get(LAMINAS).param("laboratorioId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- criação, idempotência, conteúdo

    @Test
    void criacaoDevolveTodosOsCamposELeituraBateComACriacao() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpoPadrao(id, null);
        corpo.put("nome", "  Lâmina   de   teste ");
        corpo.put("observacoes", "Linha 1\nLinha 2");

        MvcResult criada = postarLamina(sessao(dono), corpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.versao").value(0))
                .andExpect(jsonPath("$.donoUsuarioId").value(dono.getId()))
                .andExpect(jsonPath("$.donoNome").value(dono.getNome()))
                .andExpect(jsonPath("$.laboratorioId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.protocoloVersaoId").value(versaoPublicada("mitose").toString()))
                .andExpect(jsonPath("$.nome").value("Lâmina de teste"))
                .andExpect(jsonPath("$.especie").value("Allium cepa"))
                .andExpect(jsonPath("$.tratamento").value("Colchicina"))
                .andExpect(jsonPath("$.observacoes").value("Linha 1\nLinha 2"))
                .andExpect(jsonPath("$.meta").value(100))
                .andExpect(jsonPath("$.criadaEm").isNotEmpty())
                .andExpect(jsonPath("$.atualizadaEm").isNotEmpty())
                .andReturn();

        MvcResult lida = requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}", id))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(lida)).isEqualTo(json(criada));
    }

    @Test
    void camposOpcionaisAusentesViramTextoVazioEMetaFicaNula() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", id.toString());
        corpo.put("protocoloVersaoId", versaoPublicada("meiose").toString());
        corpo.put("nome", "Só o nome");

        postarLamina(sessao(dono), corpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.especie").value(""))
                .andExpect(jsonPath("$.tratamento").value(""))
                .andExpect(jsonPath("$.observacoes").value(""))
                .andExpect(jsonPath("$.meta").value(org.hamcrest.Matchers.nullValue()));
        postarLamina(sessao(dono), corpo).andExpect(status().isOk());
    }

    @Test
    void repeticaoComMesmoIdDonoEConteudoEhOkSemEfeitoEConteudoDiferenteEhConflito() throws Exception {
        Cenario cenario = montarCenario();
        Cookie sessao = sessao(cenario.membro());
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpoPadrao(id, cenario.laboratorio());
        MvcResult primeira = postarLamina(sessao, corpo).andExpect(status().isCreated()).andReturn();

        MvcResult repetida = postarLamina(sessao, corpo).andExpect(status().isOk()).andReturn();
        Map<String, Object> normalizavel = corpoPadrao(id, cenario.laboratorio());
        normalizavel.put("nome", "  " + normalizavel.get("nome") + "  ");
        postarLamina(sessao, normalizavel).andExpect(status().isOk());

        assertThat(json(repetida)).isEqualTo(json(primeira));
        assertThat(versaoNoBanco(id)).isZero();
        assertThat(contarLaminas(cenario.membro())).isEqualTo(1);
        for (String campo : List.of("nome", "especie", "tratamento", "observacoes", "meta", "laboratorioId",
                "protocoloVersaoId")) {
            Map<String, Object> diferente = corpoPadrao(id, cenario.laboratorio());
            diferente.put(campo, switch (campo) {
                case "meta" -> 7;
                case "laboratorioId" -> null;
                case "protocoloVersaoId" -> versaoPublicada("meiose").toString();
                default -> "outro valor";
            });
            postarLamina(sessao, diferente).andExpect(status().isConflict());
        }
        Map<String, Object> semMeta = corpoPadrao(id, cenario.laboratorio());
        semMeta.remove("meta");
        postarLamina(sessao, semMeta).andExpect(status().isConflict());
        assertThat(nomeNoBanco(id)).isEqualTo(corpo.get("nome"));
    }

    @Test
    void idDeOutroDonoRespondeIgualAoDeRecursoDesconhecidoSemSobrescrever() throws Exception {
        Cenario cenario = montarCenario();
        UUID id = criarLamina(cenario.pesquisador(), null);
        UUID idDeLab = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        MvcResult desconhecido = postarLamina(sessao(cenario.membro()),
                comProtocolo(corpoPadrao(UUID.randomUUID(), null), UUID.randomUUID()))
                .andExpect(status().isNotFound()).andReturn();

        for (Usuario invasor : List.of(cenario.membro(), cenario.admin(), cenario.adminGlobal())) {
            Map<String, Object> mesmoConteudo = corpoPadrao(id, null);
            MvcResult resposta = postarLamina(sessao(invasor), mesmoConteudo).andExpect(status().isNotFound()).andReturn();
            assertThat(corpoSemHorario(resposta)).isEqualTo(corpoSemHorario(desconhecido));
            Map<String, Object> outroConteudo = corpoPadrao(id, null);
            outroConteudo.put("nome", "Sobrescrita");
            assertThat(corpoSemHorario(postarLamina(sessao(invasor), outroConteudo).andExpect(status().isNotFound())
                    .andReturn())).isEqualTo(corpoSemHorario(desconhecido));
            assertThat(corpoSemHorario(postarLamina(sessao(invasor), corpoPadrao(idDeLab, cenario.laboratorio()))
                    .andExpect(status().isNotFound()).andReturn())).isEqualTo(corpoSemHorario(desconhecido));
        }
        assertThat(nomeNoBanco(id)).isNotEqualTo("Sobrescrita");
        assertThat(donoNoBanco(id)).isEqualTo(cenario.pesquisador().getId());
    }

    @Test
    void repeticaoDeLaminaExcluidaNaoRessuscita() throws Exception {
        Usuario dono = criarUsuario();
        Cookie sessao = sessao(dono);
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpoPadrao(id, null);
        postarLamina(sessao, corpo).andExpect(status().isCreated());
        requisicaoAutenticada(sessao, delete(LAMINAS + "/{id}", id).param("versaoEsperada", "0"))
                .andExpect(status().isNoContent());
        Object excluidaAntes = excluidaEm(id);

        postarLamina(sessao, corpo).andExpect(status().isOk());

        assertThat(excluidaEm(id)).isNotNull().isEqualTo(excluidaAntes);
        requisicaoAutenticada(sessao, get(LAMINAS + "/{id}", id)).andExpect(status().isNotFound());
        assertThat(ids(json(listar(dono).andExpect(status().isOk()).andReturn()))).isEmpty();
        Map<String, Object> diferente = corpoPadrao(id, null);
        diferente.put("nome", "Outra");
        postarLamina(sessao, diferente).andExpect(status().isConflict());
    }

    @Test
    void repeticaoPeloDonoVaiAntesDasChecagensDeAcesso() throws Exception {
        Cenario cenario = montarCenario();
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpoPadrao(id, cenario.laboratorio());
        postarLamina(sessao(cenario.membro()), corpo).andExpect(status().isCreated());
        laboratorioService.sair(cenario.laboratorio(), cenario.membro().getId());

        postarLamina(sessao(cenario.membro()), corpo).andExpect(status().isOk());
        requisicaoAutenticada(sessao(cenario.membro()), get(LAMINAS + "/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void textosEMetaInvalidosRespondem400SemGravarNada() throws Exception {
        Usuario dono = criarUsuario();
        Cookie sessao = sessao(dono);
        List<Map<String, Object>> invalidos = new ArrayList<>();
        invalidos.add(com("nome", null));
        invalidos.add(com("nome", ""));
        invalidos.add(com("nome", "   \t "));
        invalidos.add(com("nome", "a".repeat(121)));
        invalidos.add(com("nome", "Lâmina\u0000"));
        invalidos.add(com("nome", "Lâmina​"));
        invalidos.add(com("especie", "a".repeat(121)));
        invalidos.add(com("especie", "Espécie\u0000"));
        invalidos.add(com("tratamento", "a".repeat(121)));
        invalidos.add(com("tratamento", "Trat\u0007"));
        invalidos.add(com("observacoes", "a".repeat(5001)));
        invalidos.add(com("observacoes", "Obs\u0000erva"));
        invalidos.add(com("observacoes", "Obs\u001Berva"));
        invalidos.add(com("meta", 0));
        invalidos.add(com("meta", -3));
        invalidos.add(com("id", "nao-e-uuid"));
        invalidos.add(com("id", "550e8400-e29b-11d4-a716-446655440000"));
        invalidos.add(com("id", null));
        invalidos.add(com("protocoloVersaoId", null));
        invalidos.add(com("protocoloVersaoId", "nao-e-uuid"));
        invalidos.add(com("laboratorioId", "nao-e-uuid"));

        for (Map<String, Object> corpo : invalidos) {
            postarLamina(sessao, corpo).andExpect(status().isBadRequest());
        }
        for (String metaBruta : List.of("1.5", "2.0", "\"5\"", "2147483648", "true", "[]")) {
            assertThat(postarBruto(sessao, "{\"id\":\"" + UUID.randomUUID() + "\",\"protocoloVersaoId\":\""
                    + versaoPublicada("mitose") + "\",\"nome\":\"Lâmina\",\"meta\":" + metaBruta + "}")
                    .andReturn().getResponse().getStatus()).as("meta %s", metaBruta).isEqualTo(400);
        }
        postarBruto(sessao, "{").andExpect(status().isBadRequest());
        postarBruto(sessao, "").andExpect(status().isBadRequest());
        assertThat(contarLaminas(dono)).isZero();
    }

    @Test
    void limitesExatosESomenteQuebrasDeLinhaTabEEspacosSaoAceitos() throws Exception {
        Usuario dono = criarUsuario();
        Map<String, Object> corpo = corpoPadrao(UUID.randomUUID(), null);
        corpo.put("nome", "a ".repeat(60) + "   ");
        corpo.put("especie", "e".repeat(120));
        corpo.put("tratamento", "t".repeat(120));
        corpo.put("observacoes", "linha\r\ncom\ttab\n" + "o".repeat(4900));
        corpo.put("meta", Integer.MAX_VALUE);

        postarLamina(sessao(dono), corpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.observacoes").value(corpo.get("observacoes")))
                .andExpect(jsonPath("$.meta").value(Integer.MAX_VALUE));
        Map<String, Object> cincoMil = corpoPadrao(UUID.randomUUID(), null);
        cincoMil.put("observacoes", "o".repeat(5000));
        postarLamina(sessao(dono), cincoMil).andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- PATCH

    @Test
    void patchSubstituiOsCamposDescritivosMasNuncaProtocoloLaboratorioOuDono() throws Exception {
        Cenario cenario = montarCenario();
        UUID id = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        Cookie sessao = sessao(cenario.pesquisador());
        Instant antes = atualizadaEmNoBanco(id);

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("versaoEsperada", 0);
        corpo.put("nome", "  Nome   novo ");
        corpo.put("observacoes", "Nova\nobservação");
        corpo.put("meta", 250);
        // Campos que nunca mudam são ignorados.
        corpo.put("laboratorioId", null);
        corpo.put("protocoloVersaoId", versaoPublicada("meiose").toString());
        corpo.put("donoUsuarioId", cenario.admin().getId());
        requisicaoAutenticada(sessao, patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(1))
                .andExpect(jsonPath("$.nome").value("Nome novo"))
                .andExpect(jsonPath("$.especie").value(""))
                .andExpect(jsonPath("$.tratamento").value(""))
                .andExpect(jsonPath("$.observacoes").value("Nova\nobservação"))
                .andExpect(jsonPath("$.meta").value(250))
                .andExpect(jsonPath("$.donoUsuarioId").value(cenario.pesquisador().getId()))
                .andExpect(jsonPath("$.laboratorioId").value(cenario.laboratorio().toString()))
                .andExpect(jsonPath("$.protocoloVersaoId").value(versaoPublicada("mitose").toString()));

        assertThat(atualizadaEmNoBanco(id)).isAfter(antes);
        requisicaoAutenticada(sessao, patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(corpoPatch(1L, "Sem meta", null))).andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.versao").value(2));
        assertThat(jdbcTemplate.queryForObject("SELECT meta FROM lamina WHERE id = ?", Integer.class, id)).isNull();
    }

    @Test
    void patchInvalidoRespondeSempre400SemAlterar() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = criarLamina(dono, null);
        Cookie sessao = sessao(dono);
        List<String> corpos = new ArrayList<>();
        corpos.add("{\"nome\":\"Sem versão\"}");
        corpos.add("{\"versaoEsperada\":null,\"nome\":\"Versão nula\"}");
        corpos.add("{\"versaoEsperada\":0.5,\"nome\":\"Versão fracionária\"}");
        corpos.add("{\"versaoEsperada\":\"0\",\"nome\":\"Versão texto\"}");
        corpos.add("{\"versaoEsperada\":0}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"  \"}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"" + "a".repeat(121) + "\"}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"Nome\\u0000\"}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"Nome\",\"especie\":\"E\\u0000\"}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"Nome\",\"tratamento\":\"" + "a".repeat(121) + "\"}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"Nome\",\"observacoes\":\"O\\u0000\"}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"Nome\",\"observacoes\":\"" + "a".repeat(5001) + "\"}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"Nome\",\"meta\":0}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"Nome\",\"meta\":-1}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"Nome\",\"meta\":1.5}");
        corpos.add("{\"versaoEsperada\":0,\"nome\":\"Nome\",\"meta\":\"5\"}");
        corpos.add("{");
        for (String corpo : corpos) {
            assertThat(requisicaoAutenticada(sessao, patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content(corpo)).andReturn().getResponse().getStatus()).as("corpo %s", corpo).isEqualTo(400);
        }
        requisicaoAutenticada(sessao, patch(LAMINAS + "/{id}", "nao-e-uuid").contentType(MediaType.APPLICATION_JSON)
                .content(corpoPatch(0L, "X", null))).andExpect(status().isBadRequest());
        assertThat(versaoNoBanco(id)).isZero();
    }

    @Test
    void versaoDesatualizadaEm409NoPatchENoDeleteEExclusaoIncrementaAVersao() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = criarLamina(dono, null);
        Cookie sessao = sessao(dono);

        requisicaoAutenticada(sessao, patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(corpoPatch(5L, "Versão errada", null))).andExpect(status().isConflict());
        requisicaoAutenticada(sessao, delete(LAMINAS + "/{id}", id).param("versaoEsperada", "5"))
                .andExpect(status().isConflict());
        requisicaoAutenticada(sessao, patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(corpoPatch(0L, "Primeira", null))).andExpect(status().isOk());
        requisicaoAutenticada(sessao, patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(corpoPatch(0L, "Repetida com versão velha", null))).andExpect(status().isConflict());
        requisicaoAutenticada(sessao, delete(LAMINAS + "/{id}", id).param("versaoEsperada", "0"))
                .andExpect(status().isConflict());

        assertThat(nomeNoBanco(id)).isEqualTo("Primeira");
        assertThat(excluidaEm(id)).isNull();
        requisicaoAutenticada(sessao, delete(LAMINAS + "/{id}", id).param("versaoEsperada", "1"))
                .andExpect(status().isNoContent());
        assertThat(excluidaEm(id)).isNotNull();
        assertThat(versaoNoBanco(id)).isEqualTo(2);
    }

    @Test
    void patchsSimultaneosComAMesmaVersaoResultamEmUmSucessoEOsDemais409() throws Exception {
        Usuario dono = criarUsuario();
        Cookie sessao = sessao(dono);
        int disputantes = 6;

        for (int rodada = 0; rodada < 5; rodada++) {
            UUID id = criarLamina(dono, null);
            List<Integer> statuses = dispararSimultaneos(disputantes, indice ->
                    requisicaoAutenticada(sessao, patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content(corpoPatch(0L, "Concorrente " + indice, null)))
                            .andReturn().getResponse().getStatus());

            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 409).hasSize(disputantes - 1);
            assertThat(versaoNoBanco(id)).isEqualTo(1);
        }
    }

    @Test
    void postsSimultaneosComOMesmoIdResultamEmUmaCriacaoEDemaisRepeticoes() throws Exception {
        Usuario dono = criarUsuario();
        Cookie sessao = sessao(dono);
        int rodadas = 6;
        int disputantes = 6;

        for (int rodada = 0; rodada < rodadas; rodada++) {
            UUID id = UUID.randomUUID();
            Map<String, Object> corpo = corpoPadrao(id, null);
            List<Integer> statuses = dispararSimultaneos(disputantes,
                    indice -> postarLamina(sessao, corpo).andReturn().getResponse().getStatus());

            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 200).hasSize(disputantes - 1);
        }
        assertThat(contarLaminas(dono)).isEqualTo(rodadas);
    }

    @Test
    void postsSimultaneosComOMesmoIdEConteudosDiferentesNaoDao500() throws Exception {
        Usuario dono = criarUsuario();
        Cookie sessao = sessao(dono);
        int disputantes = 6;

        for (int rodada = 0; rodada < 4; rodada++) {
            UUID id = UUID.randomUUID();
            List<Integer> statuses = dispararSimultaneos(disputantes, indice -> {
                Map<String, Object> corpo = corpoPadrao(id, null);
                corpo.put("nome", "Conteúdo " + indice);
                return postarLamina(sessao, corpo).andReturn().getResponse().getStatus();
            });

            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).as("statuses %s", statuses).allMatch(s -> s == 201 || s == 200 || s == 409);
        }
    }

    @Test
    void deletesSimultaneosNaoDao500() throws Exception {
        Usuario dono = criarUsuario();
        Cookie sessao = sessao(dono);

        for (int rodada = 0; rodada < 4; rodada++) {
            UUID id = criarLamina(dono, null);
            List<Integer> statuses = dispararSimultaneos(6, indice -> requisicaoAutenticada(sessao,
                    delete(LAMINAS + "/{id}", id).param("versaoEsperada", "0")).andReturn().getResponse().getStatus());

            assertThat(statuses).as("statuses %s", statuses).allMatch(s -> s == 204 || s == 409);
            assertThat(statuses).filteredOn(s -> s == 204).isNotEmpty();
            assertThat(excluidaEm(id)).isNotNull();
        }
    }

    // ---------------------------------------------------------------- DELETE

    @Test
    void deleteRepetidoPeloDonoEh204SejaQualForAVersaoEOutrasPessoasRecebem404() throws Exception {
        Cenario cenario = montarCenario();
        UUID id = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        Cookie dono = sessao(cenario.pesquisador());
        requisicaoAutenticada(dono, delete(LAMINAS + "/{id}", id).param("versaoEsperada", "0"))
                .andExpect(status().isNoContent());
        Object excluidaEm = excluidaEm(id);
        long versao = versaoNoBanco(id);

        requisicaoAutenticada(dono, delete(LAMINAS + "/{id}", id).param("versaoEsperada", "0"))
                .andExpect(status().isNoContent());
        requisicaoAutenticada(dono, delete(LAMINAS + "/{id}", id).param("versaoEsperada", "999"))
                .andExpect(status().isNoContent());

        assertThat(excluidaEm(id)).isEqualTo(excluidaEm);
        assertThat(versaoNoBanco(id)).isEqualTo(versao);
        for (Usuario outro : List.of(cenario.admin(), cenario.orientador(), cenario.membro(), cenario.fora(),
                cenario.adminGlobal())) {
            requisicaoAutenticada(sessao(outro), delete(LAMINAS + "/{id}", id).param("versaoEsperada", "0"))
                    .andExpect(status().isNotFound());
            requisicaoAutenticada(sessao(outro), get(LAMINAS + "/{id}", id)).andExpect(status().isNotFound());
            requisicaoAutenticada(sessao(outro), patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content(corpoPatch(versao, "X", null))).andExpect(status().isNotFound());
        }
        requisicaoAutenticada(dono, get(LAMINAS + "/{id}", id)).andExpect(status().isNotFound());
        requisicaoAutenticada(dono, patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(corpoPatch(versao, "X", null))).andExpect(status().isNotFound());
        assertThat(ids(json(listar(cenario.admin(), "laboratorioId", cenario.laboratorio().toString())
                .andExpect(status().isOk()).andReturn()))).doesNotContain(id.toString());
    }

    @Test
    void deleteSemVersaoOuComIdInvalidoEh400EIdDesconhecidoEh404() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = criarLamina(dono, null);
        Cookie sessao = sessao(dono);

        requisicaoAutenticada(sessao, delete(LAMINAS + "/{id}", id)).andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao, delete(LAMINAS + "/{id}", id).param("versaoEsperada", "abc"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao, delete(LAMINAS + "/{id}", "nao-e-uuid").param("versaoEsperada", "0"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao, delete(LAMINAS + "/{id}", UUID.randomUUID()).param("versaoEsperada", "0"))
                .andExpect(status().isNotFound());
        requisicaoAutenticada(sessao, get(LAMINAS + "/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        requisicaoAutenticada(sessao, get(LAMINAS + "/{id}", "nao-e-uuid")).andExpect(status().isBadRequest());
        assertThat(excluidaEm(id)).isNull();
    }

    // ---------------------------------------------------------------- sessão e CSRF

    @Test
    void semLoginEh401ESemCsrfEh403() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = criarLamina(dono, null);
        String patchCorpo = corpoPatch(0L, "X", null);
        String postCorpo = objectMapper.writeValueAsString(corpoPadrao(UUID.randomUUID(), null));

        mockMvc.perform(get(LAMINAS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(LAMINAS + "/{id}", id)).andExpect(status().isUnauthorized());
        comCsrfSemLogin(post(LAMINAS).contentType(MediaType.APPLICATION_JSON).content(postCorpo))
                .andExpect(status().isUnauthorized());
        comCsrfSemLogin(patch(LAMINAS + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(patchCorpo))
                .andExpect(status().isUnauthorized());
        comCsrfSemLogin(delete(LAMINAS + "/{id}", id).param("versaoEsperada", "0"))
                .andExpect(status().isUnauthorized());

        Cookie sessao = sessao(dono);
        mockMvc.perform(post(LAMINAS).cookie(sessao).contentType(MediaType.APPLICATION_JSON).content(postCorpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(LAMINAS + "/{id}", id).cookie(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(patchCorpo)).andExpect(status().isForbidden());
        mockMvc.perform(delete(LAMINAS + "/{id}", id).cookie(sessao).param("versaoEsperada", "0"))
                .andExpect(status().isForbidden());
        assertThat(nomeNoBanco(id)).isNotEqualTo("X");
        assertThat(excluidaEm(id)).isNull();
    }

    // ---------------------------------------------------------------- banco

    @Test
    void bancoRecusaMetaNaoPositivaNomeComEspacosNasPontasEExigeIndicesParciais() {
        Usuario dono = criarUsuario();
        UUID versao = versaoPublicada("mitose");
        String insert = "INSERT INTO lamina (id, version, created_at, last_modified_at, dono_usuario_id, "
                + "protocolo_versao_id, nome, meta) VALUES (gen_random_uuid(), 0, NOW(), NOW(), ?, ?, ?, ?)";

        for (Object[] invalido : new Object[][]{{"ok", 0}, {"ok", -1}, {" ok", 1}, {"ok ", 1}, {"", 1},
                {"a".repeat(121), 1}}) {
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(insert, dono.getId(), versao, invalido[0], invalido[1]));
        }
        List<String> indices = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'lamina' AND indexname LIKE 'idx_lamina_%'",
                String.class);
        assertThat(indices).hasSize(2).allMatch(def -> def.contains("excluida_em IS NULL"));
    }

    // ---------------------------------------------------------------- apoio: cenário

    private Cenario montarCenario() throws Exception {
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

    private void verificarMatriz(Cenario cenario, List<Ator> atores) throws Exception {
        String lab = cenario.laboratorio().toString();
        for (Ator ator : atores) {
            Cookie sessao = sessao(ator.usuario());
            UUID paraLer = criarLamina(cenario.pesquisador(), cenario.laboratorio());
            requisicaoAutenticada(sessao, get(LAMINAS + "/{id}", paraLer)).andExpect(status().is(ator.get()));

            UUID paraEditar = criarLamina(cenario.pesquisador(), cenario.laboratorio());
            String nomeAntes = nomeNoBanco(paraEditar);
            requisicaoAutenticada(sessao, patch(LAMINAS + "/{id}", paraEditar).contentType(MediaType.APPLICATION_JSON)
                    .content(corpoPatch(versaoNoBanco(paraEditar), "Editada por " + ator.nome(), 5)))
                    .andExpect(status().is(ator.patch()));
            assertThat(nomeNoBanco(paraEditar)).as("PATCH de %s", ator.nome())
                    .isEqualTo(ator.patch() == 200 ? "Editada por " + ator.nome() : nomeAntes);

            UUID paraExcluir = criarLamina(cenario.pesquisador(), cenario.laboratorio());
            requisicaoAutenticada(sessao, delete(LAMINAS + "/{id}", paraExcluir)
                    .param("versaoEsperada", String.valueOf(versaoNoBanco(paraExcluir))))
                    .andExpect(status().is(ator.delete()));
            assertThat(excluidaEm(paraExcluir) != null).as("DELETE de %s", ator.nome()).isEqualTo(ator.delete() == 204);

            if (ator.statusListagem() == 200) {
                List<String> vistas = ids(json(listar(ator.usuario(), "laboratorioId", lab, "size", "100")
                        .andExpect(status().isOk()).andReturn()));
                assertThat(vistas.contains(paraLer.toString())).as("listagem de %s", ator.nome())
                        .isEqualTo(ator.veNaListagem());
            } else {
                listar(ator.usuario(), "laboratorioId", lab).andExpect(status().isNotFound());
            }
        }
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

    // ---------------------------------------------------------------- apoio: lâminas e banco

    private UUID criarLamina(Usuario dono, UUID laboratorio) throws Exception {
        UUID id = UUID.randomUUID();
        postarLamina(sessao(dono), corpoPadrao(id, laboratorio)).andExpect(status().isCreated());
        return id;
    }

    private Map<String, Object> corpoPadrao(UUID id, UUID laboratorio) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", id.toString());
        corpo.put("protocoloVersaoId", versaoPublicada("mitose").toString());
        if (laboratorio != null) {
            corpo.put("laboratorioId", laboratorio.toString());
        }
        corpo.put("nome", "Lâmina " + id.toString().substring(0, 8));
        corpo.put("especie", "Allium cepa");
        corpo.put("tratamento", "Colchicina");
        corpo.put("observacoes", "Linha 1\nLinha 2");
        corpo.put("meta", 100);
        return corpo;
    }

    private Map<String, Object> com(String campo, Object valor) {
        Map<String, Object> corpo = corpoPadrao(UUID.randomUUID(), null);
        corpo.put(campo, valor);
        return corpo;
    }

    private Map<String, Object> comProtocolo(Map<String, Object> corpo, UUID protocolo) {
        corpo.put("protocoloVersaoId", protocolo.toString());
        return corpo;
    }

    private String corpoPatch(long versaoEsperada, String nome, Integer meta) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("versaoEsperada", versaoEsperada);
        corpo.put("nome", nome);
        corpo.put("meta", meta);
        return objectMapper.writeValueAsString(corpo);
    }

    private ResultActions postarLamina(Cookie sessao, Map<String, Object> corpo) throws Exception {
        return postarBruto(sessao, objectMapper.writeValueAsString(corpo));
    }

    private ResultActions postarBruto(Cookie sessao, String corpo) throws Exception {
        return requisicaoAutenticada(sessao, post(LAMINAS).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions listar(Usuario usuario, String... parametros) throws Exception {
        MockHttpServletRequestBuilder requisicao = get(LAMINAS);
        for (int i = 0; i < parametros.length; i += 2) {
            requisicao.param(parametros[i], parametros[i + 1]);
        }
        return requisicaoAutenticada(sessao(usuario), requisicao);
    }

    private static List<String> ids(JsonNode pagina) {
        List<String> ids = new ArrayList<>();
        pagina.path("content").forEach(lamina -> ids.add(lamina.path("id").asText()));
        return ids;
    }

    private UUID versaoPublicada(String codigo) {
        return jdbcTemplate.queryForObject(
                "SELECT v.id FROM protocolo_versao v JOIN protocolo p ON p.id = v.protocolo_id "
                        + "WHERE p.codigo = ? AND v.numero = 1", UUID.class, codigo);
    }

    private UUID criarVersaoRascunho() {
        UUID protocolo = UUID.randomUUID();
        UUID versao = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO protocolo (id, version, created_at, last_modified_at, codigo, nome) "
                + "VALUES (?, 0, NOW(), NOW(), ?, 'Rascunho')", protocolo,
                "rascunho_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        jdbcTemplate.update("INSERT INTO protocolo_versao (id, version, created_at, last_modified_at, protocolo_id, numero) "
                + "VALUES (?, 0, NOW(), NOW(), ?, 1)", versao, protocolo);
        return versao;
    }

    private long versaoNoBanco(UUID id) {
        return jdbcTemplate.queryForObject("SELECT version FROM lamina WHERE id = ?", Long.class, id);
    }

    private String nomeNoBanco(UUID id) {
        return jdbcTemplate.queryForObject("SELECT nome FROM lamina WHERE id = ?", String.class, id);
    }

    private Long donoNoBanco(UUID id) {
        return jdbcTemplate.queryForObject("SELECT dono_usuario_id FROM lamina WHERE id = ?", Long.class, id);
    }

    private Object excluidaEm(UUID id) {
        return jdbcTemplate.queryForObject("SELECT excluida_em FROM lamina WHERE id = ?", java.sql.Timestamp.class, id);
    }

    private Instant atualizadaEmNoBanco(UUID id) {
        return jdbcTemplate.queryForObject("SELECT last_modified_at FROM lamina WHERE id = ?", java.sql.Timestamp.class, id)
                .toInstant();
    }

    private boolean existeNoBanco(UUID id) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lamina WHERE id = ?", Integer.class, id) > 0;
    }

    private int contarLaminas(Usuario dono) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lamina WHERE dono_usuario_id = ?", Integer.class,
                dono.getId());
    }

    // ---------------------------------------------------------------- apoio: usuários e HTTP

    private void adicionarMembro(UUID laboratorioId, Usuario membro, PapelLaboratorio... papeisExtras) {
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

    private Usuario criarUsuario() {
        return criarUsuario(RolesEnum.USER);
    }

    private Usuario criarUsuario(RolesEnum papelGlobal) {
        Role role = roleRepository.findByNome(papelGlobal).orElseThrow();
        String email = "laminas-" + UUID.randomUUID() + "@exemplo.org";
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Pessoa de teste " + email.substring(8, 16))
                .email(email)
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
                .role(role)
                .emailConfirmadoEm(Instant.now())
                .build());
    }

    private Cookie sessao(Usuario usuario) throws Exception {
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

    private ResultActions requisicaoAutenticada(Cookie sessao, MockHttpServletRequestBuilder requisicao) throws Exception {
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        return mockMvc.perform(requisicao.cookie(sessao, csrf).header("X-XSRF-TOKEN", TOKEN_CSRF));
    }

    private ResultActions comCsrfSemLogin(MockHttpServletRequestBuilder requisicao) throws Exception {
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        return mockMvc.perform(requisicao.cookie(csrf).header("X-XSRF-TOKEN", TOKEN_CSRF));
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Corpo de erro sem o horário, que difere entre respostas. */
    private Map<String, Object> corpoSemHorario(MvcResult resultado) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> corpo = objectMapper.readValue(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        corpo.remove("timestamp");
        return corpo;
    }

    private <T> List<T> dispararSimultaneos(int quantidade, TarefaComIndice<T> tarefa) throws Exception {
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
    private interface TarefaComIndice<T> {
        T executar(int indice) throws Exception;
    }
}
