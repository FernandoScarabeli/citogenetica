package br.ufla.citogenetica.contagem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import br.ufla.citogenetica.AbstractIntegrationTest;
import br.ufla.citogenetica.contagem.internal.application.NomeTipoAnormalidade;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ContagemProtocolosIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "Frase segura para testes de contagem";
    private static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String TOKEN_CSRF = "csrf-contagem-integracao";
    private static final String PERSONALIZADOS = "/api/v1/tipos-anormalidade/personalizados";

    private record FaseEsperada(String nome, boolean contaComoDivisao, String... tipos) {
    }

    /** Cópia fiel de {@code protocols} em apps/web/src/domain.ts. */
    private static final Map<String, List<FaseEsperada>> SEED = Map.of(
            "meiose", List.of(
                    new FaseEsperada("Leptóteno/Zigóteno", true),
                    new FaseEsperada("Paquíteno", true),
                    new FaseEsperada("Diplóteno/Diacinese", true, "Univalente", "Trivalente", "Tetravalente"),
                    new FaseEsperada("Metáfase I", true, "Cromossomos não orientados"),
                    new FaseEsperada("Anáfase I", true, "Cromossomos retardatários", "Segregação de cromátides"),
                    new FaseEsperada("Telófase I", true, "Micronúcleo", "Fuso tripolar"),
                    new FaseEsperada("Prófase II", true, "Micronúcleo", "Núcleos assincrônicos"),
                    new FaseEsperada("Metáfase II", true, "Cromossomos não orientados", "Assincronia"),
                    new FaseEsperada("Anáfase II", true, "Cromossomos retardatários", "Assincronia"),
                    new FaseEsperada("Telófase II", true, "Micronúcleo", "Ponte"),
                    new FaseEsperada("Tétrade", true, "Micronúcleo", "Tríade", "Díade")),
            "mitose", List.of(
                    new FaseEsperada("Interfase", false, "Núcleos apoptóticos (condensados)"),
                    new FaseEsperada("Prófase", true, "Stickiness", "Cromossomos ou fragmentos desprendidos",
                            "Núcleo com brotações", "Micronúcleo"),
                    new FaseEsperada("Metáfase", true, "Cromossomos não orientados", "Metáfase colchicínica", "Stickiness"),
                    new FaseEsperada("Anáfase", true, "Cromossomos atrasados", "Fuso anormal", "Stickiness", "Ponte",
                            "Ponte com fragmento"),
                    new FaseEsperada("Telófase", true, "Fuso anormal", "Ponte", "Ponte com fragmento",
                            "Micronúcleo: 1", "Micronúcleo: 2", "Micronúcleo: 3", "Micronúcleo: mais de 3")));

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
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    // ---------------------------------------------------------------- seed

    @Test
    void seedPublicaMeioseEMitoseComFasesTiposEOrdemExatos() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER);

        JsonNode protocolos = json(requisicaoAutenticada(login(usuario), get("/api/v1/protocolos"))
                .andExpect(status().isOk()).andReturn());

        for (String codigo : List.of("meiose", "mitose")) {
            JsonNode protocolo = porCodigo(protocolos, codigo);
            assertThat(protocolo.path("nome").asText()).isEqualTo(codigo.equals("meiose") ? "Meiose" : "Mitose");
            assertThat(protocolo.path("versaoAtual").path("numero").asInt()).isEqualTo(1);
            verificarVersao(protocolo.path("versaoAtual"), SEED.get(codigo));
        }
        assertThat(porCodigo(protocolos, "meiose").path("versaoAtual").path("fases")).hasSize(11);
        assertThat(porCodigo(protocolos, "mitose").path("versaoAtual").path("fases")).hasSize(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM protocolo_versao WHERE numero = 1 AND publicada_em IS NULL "
                        + "AND protocolo_id IN (SELECT id FROM protocolo WHERE codigo IN ('meiose', 'mitose'))",
                Integer.class)).isZero();
    }

    @Test
    void nomeNormalizadoDoSeedBateComANormalizacaoDoJava() {
        List<Map<String, Object>> tipos = jdbcTemplate.queryForList(
                "SELECT nome, nome_normalizado FROM tipo_anormalidade WHERE usuario_id IS NULL");

        assertThat(tipos).hasSize(SEED.values().stream()
                .flatMap(List::stream).mapToInt(fase -> fase.tipos().length).sum());
        for (Map<String, Object> tipo : tipos) {
            assertThat(tipo.get("nome_normalizado"))
                    .as("normalização de %s", tipo.get("nome"))
                    .isEqualTo(NomeTipoAnormalidade.normalizado((String) tipo.get("nome")));
        }
    }

    // ---------------------------------------------------------------- imutabilidade

    @Test
    void triggersRecusamAlterarVersaoFaseETipoFixoPublicados() {
        UUID versaoId = jdbcTemplate.queryForObject(
                "SELECT v.id FROM protocolo_versao v JOIN protocolo p ON p.id = v.protocolo_id "
                        + "WHERE p.codigo = 'mitose' AND v.numero = 1", UUID.class);
        UUID faseId = idDaFase("mitose", "Prófase");
        UUID tipoId = jdbcTemplate.queryForObject(
                "SELECT id FROM tipo_anormalidade WHERE fase_id = ? AND usuario_id IS NULL AND ordem = 1",
                UUID.class, faseId);

        assertRecusado("UPDATE protocolo_versao SET publicada_em = NULL WHERE id = ?", versaoId);
        assertRecusado("UPDATE protocolo_versao SET numero = 99 WHERE id = ?", versaoId);
        assertRecusado("DELETE FROM protocolo_versao WHERE id = ?", versaoId);
        assertRecusado("UPDATE protocolo_fase SET nome = 'Outra' WHERE id = ?", faseId);
        assertRecusado("DELETE FROM protocolo_fase WHERE id = ?", faseId);
        assertRecusado("INSERT INTO protocolo_fase (id, version, created_at, last_modified_at, versao_id, nome, ordem, "
                + "conta_como_divisao) VALUES (gen_random_uuid(), 0, NOW(), NOW(), ?, 'Nova', 99, TRUE)", versaoId);
        assertRecusado("UPDATE tipo_anormalidade SET nome = 'Outro', nome_normalizado = 'outro' WHERE id = ?", tipoId);
        assertRecusado("DELETE FROM tipo_anormalidade WHERE id = ?", tipoId);
        assertRecusado("INSERT INTO tipo_anormalidade (id, version, created_at, last_modified_at, fase_id, nome, "
                + "nome_normalizado, ordem) VALUES (gen_random_uuid(), 0, NOW(), NOW(), ?, 'Novo', 'novo', 99)", faseId);
    }

    @Test
    void rascunhoPodeSerEditadoEPublicadoUmaUnicaVezDepoisFicaImutavel() {
        executarRevertendo(jdbc -> {
            UUID versaoId = criarRascunho(jdbc);
            UUID faseId = inserirFase(jdbc, versaoId, "Fase única", 1);
            UUID tipoId = inserirTipoFixo(jdbc, faseId, "Tipo único", 1);
            jdbc.update("UPDATE protocolo_fase SET nome = 'Fase editada' WHERE id = ?", faseId);
            jdbc.update("UPDATE tipo_anormalidade SET nome = 'Tipo editado', nome_normalizado = 'tipo editado' "
                    + "WHERE id = ?", tipoId);

            assertThat(jdbc.update("UPDATE protocolo_versao SET publicada_em = NOW() WHERE id = ?", versaoId)).isEqualTo(1);
        });
        executarRevertendo(jdbc -> {
            UUID versaoId = criarRascunho(jdbc);
            UUID faseId = inserirFase(jdbc, versaoId, "Fase única", 1);
            jdbc.update("UPDATE protocolo_versao SET publicada_em = NOW() WHERE id = ?", versaoId);

            assertThatThrownBy(() -> jdbc.update("UPDATE protocolo_versao SET publicada_em = NOW() WHERE id = ?", versaoId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Versão de protocolo publicada");
        });
        executarRevertendo(jdbc -> {
            UUID versaoId = criarRascunho(jdbc);
            UUID faseId = inserirFase(jdbc, versaoId, "Fase única", 1);
            jdbc.update("UPDATE protocolo_versao SET publicada_em = NOW() WHERE id = ?", versaoId);

            assertThatThrownBy(() -> inserirFase(jdbc, versaoId, "Outra fase", 2))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Fases de uma versão de protocolo publicada");
        });
        executarRevertendo(jdbc -> {
            UUID versaoId = criarRascunho(jdbc);
            UUID faseId = inserirFase(jdbc, versaoId, "Fase única", 1);
            jdbc.update("UPDATE protocolo_versao SET publicada_em = NOW() WHERE id = ?", versaoId);

            assertThatThrownBy(() -> inserirTipoFixo(jdbc, faseId, "Tipo novo", 1))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Tipos fixos de uma versão de protocolo publicada");
        });
    }

    @Test
    void checkDoBancoExigeEscopoCoerenteDoTipo() {
        UUID faseId = idDaFase("mitose", "Prófase");
        Usuario usuario = criarUsuario(RolesEnum.USER);

        assertThatThrownBy(() -> inserirTipoBruto(faseId, null, null, "Sem ordem", "sem ordem", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserirTipoBruto(faseId, usuario.getId(), null, "Com ordem", "com ordem", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserirTipoBruto(faseId, null, UUID.randomUUID(), "Lab sem dono", "lab sem dono", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- GET protocolos

    @Test
    void getProtocolosExigeLoginEVersaoPublicadaTemRotaPropria() throws Exception {
        mockMvc.perform(get("/api/v1/protocolos")).andExpect(status().isUnauthorized());
        UUID versaoMitose = versaoPublicada("mitose");
        mockMvc.perform(get("/api/v1/protocolos/versoes/{id}", versaoMitose)).andExpect(status().isUnauthorized());

        Cookie sessao = login(criarUsuario(RolesEnum.USER));
        JsonNode versao = json(requisicaoAutenticada(sessao, get("/api/v1/protocolos/versoes/{id}", versaoMitose))
                .andExpect(status().isOk()).andReturn());

        assertThat(versao.path("id").asText()).isEqualTo(versaoMitose.toString());
        verificarVersao(versao, SEED.get("mitose"));
    }

    @Test
    void getVersaoInexistenteOuRascunhoEh404EUuidInvalidoEh400() throws Exception {
        Cookie sessao = login(criarUsuario(RolesEnum.USER));
        UUID rascunho = UUID.randomUUID();
        UUID protocoloRascunho = UUID.randomUUID();
        String codigo = "rascunho_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("INSERT INTO protocolo (id, version, created_at, last_modified_at, codigo, nome) "
                + "VALUES (?, 0, NOW(), NOW(), ?, 'Rascunho')", protocoloRascunho, codigo);
        jdbcTemplate.update("INSERT INTO protocolo_versao (id, version, created_at, last_modified_at, protocolo_id, numero) "
                + "VALUES (?, 0, NOW(), NOW(), ?, 1)", rascunho, protocoloRascunho);
        try {
            requisicaoAutenticada(sessao, get("/api/v1/protocolos/versoes/{id}", rascunho))
                    .andExpect(status().isNotFound());
            requisicaoAutenticada(sessao, get("/api/v1/protocolos/versoes/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
            requisicaoAutenticada(sessao, get("/api/v1/protocolos/versoes/{id}", "550e8400-e29b-11d4-a716-446655440000"))
                    .andExpect(status().isBadRequest());
            requisicaoAutenticada(sessao, get("/api/v1/protocolos/versoes/{id}", "nao-e-uuid"))
                    .andExpect(status().isBadRequest());
            JsonNode lista = json(requisicaoAutenticada(sessao, get("/api/v1/protocolos"))
                    .andExpect(status().isOk()).andReturn());
            assertThat(lista.findValuesAsText("codigo")).doesNotContain(codigo);
        } finally {
            jdbcTemplate.update("DELETE FROM protocolo_versao WHERE id = ?", rascunho);
            jdbcTemplate.update("DELETE FROM protocolo WHERE id = ?", protocoloRascunho);
        }
    }

    // ---------------------------------------------------------------- POST: idempotência e nomes

    @Test
    void postCriaDepoisRepeticaoIdempotenteEConteudoDiferenteEhConflito() throws Exception {
        Usuario dono = criarUsuario(RolesEnum.USER);
        Cookie sessao = login(dono);
        UUID id = UUID.randomUUID();
        UUID fase = idDaFase("meiose", "Tétrade");

        postar(sessao, id, fase, null, "  Cromossomo   extra ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.faseId").value(fase.toString()))
                .andExpect(jsonPath("$.laboratorioId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.nome").value("Cromossomo extra"));
        postar(sessao, id, fase, null, "Cromossomo extra").andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cromossomo extra"));
        postar(sessao, id, fase, null, " Cromossomo extra  ").andExpect(status().isOk());
        assertThat(contarPersonalizados(dono)).isEqualTo(1);

        postar(sessao, id, fase, null, "Outro nome").andExpect(status().isConflict());
        postar(sessao, id, idDaFase("meiose", "Tétrade"), criarLaboratorioDe(dono), "Cromossomo extra")
                .andExpect(status().isConflict());
        postar(sessao, id, idDaFase("meiose", "Paquíteno"), null, "Cromossomo extra")
                .andExpect(status().isConflict());
        assertThat(contarPersonalizados(dono)).isEqualTo(1);
    }

    @Test
    void idDeOutroDonoTemExatamenteARespostaDeRecursoDesconhecido() throws Exception {
        Usuario dono = criarUsuario(RolesEnum.USER);
        Usuario intruso = criarUsuario(RolesEnum.USER);
        UUID id = UUID.randomUUID();
        UUID fase = idDaFase("mitose", "Telófase");
        postar(login(dono), id, fase, null, "Tipo do dono").andExpect(status().isCreated());
        Cookie sessaoIntruso = login(intruso);

        MvcResult alheio = postar(sessaoIntruso, id, fase, null, "Tipo do dono").andReturn();
        MvcResult alheioOutroConteudo = postar(sessaoIntruso, id, fase, null, "Qualquer outro").andReturn();
        MvcResult desconhecido = postar(sessaoIntruso, UUID.randomUUID(), UUID.randomUUID(), null, "Tipo do dono")
                .andReturn();

        assertThat(alheio.getResponse().getStatus()).isEqualTo(404);
        assertThat(corpoSemHorario(alheio)).isEqualTo(corpoSemHorario(desconhecido));
        assertThat(corpoSemHorario(alheioOutroConteudo)).isEqualTo(corpoSemHorario(desconhecido));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT usuario_id FROM tipo_anormalidade WHERE id = ?", Long.class, id)).isEqualTo(dono.getId());
        assertThat(contarPersonalizados(intruso)).isZero();

        UUID idDeTipoFixo = jdbcTemplate.queryForObject(
                "SELECT id FROM tipo_anormalidade WHERE fase_id = ? AND usuario_id IS NULL AND ordem = 1",
                UUID.class, fase);
        MvcResult fixo = postar(sessaoIntruso, idDeTipoFixo, fase, null, "Ponte").andReturn();
        assertThat(fixo.getResponse().getStatus()).isEqualTo(404);
        assertThat(corpoSemHorario(fixo)).isEqualTo(corpoSemHorario(desconhecido));
    }

    @Test
    void nomeDuplicadoPorEspacoCaixaOuAcentoEhConflito() throws Exception {
        Usuario dono = criarUsuario(RolesEnum.USER);
        Cookie sessao = login(dono);
        UUID fase = idDaFase("meiose", "Paquíteno");
        postar(sessao, UUID.randomUUID(), fase, null, "Fragmentação Nuclear").andExpect(status().isCreated());

        for (String variante : List.of("fragmentação nuclear", "FRAGMENTAÇÃO   NUCLEAR", "Fragmentacao Nuclear",
                "  fragmentacao\tnuclear ")) {
            postar(sessao, UUID.randomUUID(), fase, null, variante).andExpect(status().isConflict());
        }
        assertThat(contarPersonalizados(dono)).isEqualTo(1);
    }

    @Test
    void nomeIgualAoDeTipoFixoDaMesmaFaseEhConflitoMasOutraFaseNao() throws Exception {
        Cookie sessao = login(criarUsuario(RolesEnum.USER));
        UUID telofase = idDaFase("mitose", "Telófase");

        postar(sessao, UUID.randomUUID(), telofase, null, "MICRONUCLEO: 1").andExpect(status().isConflict());
        postar(sessao, UUID.randomUUID(), telofase, null, "ponte  com fragmento").andExpect(status().isConflict());
        postar(sessao, UUID.randomUUID(), idDaFase("mitose", "Metáfase"), null, "Ponte").andExpect(status().isCreated());
        postar(sessao, UUID.randomUUID(), idDaFase("meiose", "Paquíteno"), null, "Micronúcleo")
                .andExpect(status().isCreated());
    }

    @Test
    void mesmoNomePermitidoEmOutraFaseOutroUsuarioOuOutroEscopo() throws Exception {
        Usuario primeiro = criarUsuario(RolesEnum.USER);
        Usuario segundo = criarUsuario(RolesEnum.USER);
        UUID laboratorio = criarLaboratorioDe(primeiro);
        UUID faseA = idDaFase("meiose", "Paquíteno");
        UUID faseB = idDaFase("meiose", "Leptóteno/Zigóteno");
        Cookie sessao = login(primeiro);

        postar(sessao, UUID.randomUUID(), faseA, null, "Quebra").andExpect(status().isCreated());
        postar(sessao, UUID.randomUUID(), faseB, null, "Quebra").andExpect(status().isCreated());
        postar(sessao, UUID.randomUUID(), faseA, laboratorio, "Quebra").andExpect(status().isCreated());
        postar(login(segundo), UUID.randomUUID(), faseA, null, "Quebra").andExpect(status().isCreated());
        postar(sessao, UUID.randomUUID(), faseA, laboratorio, "quebra").andExpect(status().isConflict());
        postar(sessao, UUID.randomUUID(), faseA, null, "quebra").andExpect(status().isConflict());
    }

    @Test
    void entradaInvalidaEh400() throws Exception {
        Cookie sessao = login(criarUsuario(RolesEnum.USER));
        UUID fase = idDaFase("mitose", "Prófase");
        String id = UUID.randomUUID().toString();

        postarBruto(sessao, corpo("id", id, "faseId", fase.toString(), "nome", "   ")).andExpect(status().isBadRequest());
        postarBruto(sessao, corpo("id", id, "faseId", fase.toString(), "nome", "  ")).andExpect(status().isBadRequest());
        postarBruto(sessao, corpo("id", id, "faseId", fase.toString(), "nome", "́")).andExpect(status().isBadRequest());
        for (String invisivel : List.of("\u0000", "abc\u0000", "\u200B", "a\u200Bb")) {
            postar(sessao, UUID.randomUUID(), fase, null, invisivel).andExpect(status().isBadRequest());
        }
        UUID comTab = UUID.randomUUID();
        postar(sessao, comTab, fase, null, "a\tb").andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("a b"));
        postarBruto(sessao, corpo("id", id, "faseId", fase.toString())).andExpect(status().isBadRequest());
        postarBruto(sessao, corpo("id", id, "faseId", fase.toString(), "nome", "x".repeat(121)))
                .andExpect(status().isBadRequest());
        postarBruto(sessao, corpo("id", "550e8400-e29b-11d4-a716-446655440000", "faseId", fase.toString(), "nome", "Ok"))
                .andExpect(status().isBadRequest());
        postarBruto(sessao, corpo("id", id.toUpperCase(), "faseId", fase.toString(), "nome", "Ok"))
                .andExpect(status().isBadRequest());
        postarBruto(sessao, corpo("id", id, "faseId", "nao-uuid", "nome", "Ok")).andExpect(status().isBadRequest());
        postarBruto(sessao, corpo("faseId", fase.toString(), "nome", "Ok")).andExpect(status().isBadRequest());
        postarBruto(sessao, corpo("id", id, "faseId", fase.toString(), "laboratorioId", "nao-uuid", "nome", "Ok"))
                .andExpect(status().isBadRequest());
        postarBruto(sessao, "{ isto não é json").andExpect(status().isBadRequest());
        postar(sessao, UUID.randomUUID(), fase, null, "x".repeat(120)).andExpect(status().isCreated());
    }

    @Test
    void postSemCsrfEh403ESemLoginEh401() throws Exception {
        Cookie sessao = login(criarUsuario(RolesEnum.USER));
        String corpo = corpo("id", UUID.randomUUID().toString(),
                "faseId", idDaFase("mitose", "Prófase").toString(), "nome", "Sem CSRF");

        mockMvc.perform(post(PERSONALIZADOS).cookie(sessao).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        mockMvc.perform(post(PERSONALIZADOS).cookie(csrf).header("X-XSRF-TOKEN", TOKEN_CSRF)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void faseInexistenteOuDeVersaoNaoPublicadaEh404() throws Exception {
        Cookie sessao = login(criarUsuario(RolesEnum.USER));
        UUID protocoloId = UUID.randomUUID();
        UUID versaoId = UUID.randomUUID();
        UUID faseId = UUID.randomUUID();
        String codigo = "rascunho_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("INSERT INTO protocolo (id, version, created_at, last_modified_at, codigo, nome) "
                + "VALUES (?, 0, NOW(), NOW(), ?, 'Rascunho')", protocoloId, codigo);
        jdbcTemplate.update("INSERT INTO protocolo_versao (id, version, created_at, last_modified_at, protocolo_id, numero) "
                + "VALUES (?, 0, NOW(), NOW(), ?, 1)", versaoId, protocoloId);
        jdbcTemplate.update("INSERT INTO protocolo_fase (id, version, created_at, last_modified_at, versao_id, nome, "
                + "ordem, conta_como_divisao) VALUES (?, 0, NOW(), NOW(), ?, 'Fase', 1, TRUE)", faseId, versaoId);
        try {
            postar(sessao, UUID.randomUUID(), faseId, null, "Tipo").andExpect(status().isNotFound());
            requisicaoAutenticada(sessao, get(PERSONALIZADOS).param("versaoId", versaoId.toString()))
                    .andExpect(status().isNotFound());
        } finally {
            jdbcTemplate.update("DELETE FROM protocolo_fase WHERE id = ?", faseId);
            jdbcTemplate.update("DELETE FROM protocolo_versao WHERE id = ?", versaoId);
            jdbcTemplate.update("DELETE FROM protocolo WHERE id = ?", protocoloId);
        }
        postar(sessao, UUID.randomUUID(), UUID.randomUUID(), null, "Tipo").andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- acesso a laboratório

    @Test
    void matrizDeAcessoComLaboratorio() throws Exception {
        Usuario dono = criarUsuario(RolesEnum.USER);
        Usuario membro = criarUsuario(RolesEnum.USER);
        Usuario exMembro = criarUsuario(RolesEnum.USER);
        Usuario deFora = criarUsuario(RolesEnum.USER);
        Usuario adminGlobalDeFora = criarUsuario(RolesEnum.ADMIN);
        UUID laboratorio = criarLaboratorioDe(dono);
        adicionarMembro(laboratorio, membro, dono);
        adicionarMembro(laboratorio, exMembro, dono);
        laboratorioService.sair(laboratorio, exMembro.getId());
        UUID fase = idDaFase("mitose", "Anáfase");

        postar(login(dono), UUID.randomUUID(), fase, laboratorio, "Do admin local").andExpect(status().isCreated());
        postar(login(membro), UUID.randomUUID(), fase, laboratorio, "Do membro").andExpect(status().isCreated());
        postar(login(deFora), UUID.randomUUID(), fase, laboratorio, "De fora").andExpect(status().isNotFound());
        postar(login(exMembro), UUID.randomUUID(), fase, laboratorio, "Ex-membro").andExpect(status().isNotFound());
        postar(login(adminGlobalDeFora), UUID.randomUUID(), fase, laboratorio, "Admin global")
                .andExpect(status().isNotFound());
        postar(login(deFora), UUID.randomUUID(), fase, UUID.randomUUID(), "Laboratório inexistente")
                .andExpect(status().isNotFound());
        // Lâmina avulsa nunca depende de laboratório.
        postar(login(deFora), UUID.randomUUID(), fase, null, "Avulso de quem está fora").andExpect(status().isCreated());
        postar(login(adminGlobalDeFora), UUID.randomUUID(), fase, null, "Avulso do admin global")
                .andExpect(status().isCreated());

        requisicaoAutenticada(login(deFora), get(PERSONALIZADOS)
                .param("versaoId", versaoPublicada("mitose").toString())
                .param("laboratorioId", laboratorio.toString())).andExpect(status().isNotFound());
        requisicaoAutenticada(login(adminGlobalDeFora), get(PERSONALIZADOS)
                .param("versaoId", versaoPublicada("mitose").toString())
                .param("laboratorioId", laboratorio.toString())).andExpect(status().isNotFound());
        requisicaoAutenticada(login(exMembro), get(PERSONALIZADOS)
                .param("versaoId", versaoPublicada("mitose").toString())
                .param("laboratorioId", laboratorio.toString())).andExpect(status().isNotFound());
    }

    @Test
    void laboratorioDesativadoPermiteLerMasNaoCriar() throws Exception {
        Usuario dono = criarUsuario(RolesEnum.USER);
        Usuario adminPlataforma = criarUsuario(RolesEnum.ADMIN);
        UUID laboratorio = criarLaboratorioDe(dono);
        UUID fase = idDaFase("mitose", "Anáfase");
        Cookie sessao = login(dono);
        UUID existente = UUID.randomUUID();
        postar(sessao, existente, fase, laboratorio, "Antes de desativar").andExpect(status().isCreated());

        laboratorioService.desativar(laboratorio, adminPlataforma.getId());

        postar(sessao, UUID.randomUUID(), fase, laboratorio, "Depois de desativar").andExpect(status().isForbidden());
        requisicaoAutenticada(sessao, get(PERSONALIZADOS)
                .param("versaoId", versaoPublicada("mitose").toString())
                .param("laboratorioId", laboratorio.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        // Repetição de algo já criado continua sem efeito.
        postar(sessao, existente, fase, laboratorio, "Antes de desativar").andExpect(status().isOk());
        assertThat(contarPersonalizados(dono)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- GET personalizados

    @Test
    void listaSoOsTiposDoProprioUsuarioNaVersaoEEscopo() throws Exception {
        Usuario dono = criarUsuario(RolesEnum.USER);
        Usuario outroMembro = criarUsuario(RolesEnum.USER);
        UUID laboratorio = criarLaboratorioDe(dono);
        adicionarMembro(laboratorio, outroMembro, dono);
        UUID faseMeiose = idDaFase("meiose", "Paquíteno");
        UUID faseMitose = idDaFase("mitose", "Prófase");
        Cookie sessaoDono = login(dono);
        Cookie sessaoOutro = login(outroMembro);

        postar(sessaoDono, UUID.randomUUID(), faseMitose, null, "Avulso A").andExpect(status().isCreated());
        postar(sessaoDono, UUID.randomUUID(), idDaFase("mitose", "Anáfase"), laboratorio, "Lab B")
                .andExpect(status().isCreated());
        postar(sessaoDono, UUID.randomUUID(), faseMitose, laboratorio, "Lab A").andExpect(status().isCreated());
        postar(sessaoDono, UUID.randomUUID(), faseMeiose, null, "Outra versão").andExpect(status().isCreated());
        postar(sessaoOutro, UUID.randomUUID(), faseMitose, laboratorio, "Do outro membro").andExpect(status().isCreated());
        postar(sessaoOutro, UUID.randomUUID(), faseMitose, null, "Avulso do outro").andExpect(status().isCreated());
        String versaoMitose = versaoPublicada("mitose").toString();

        JsonNode doLab = json(requisicaoAutenticada(sessaoDono, get(PERSONALIZADOS)
                .param("versaoId", versaoMitose).param("laboratorioId", laboratorio.toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(doLab.findValuesAsText("nome")).containsExactly("Lab A", "Lab B");
        assertThat(doLab.findValuesAsText("laboratorioId")).containsOnly(laboratorio.toString());

        JsonNode avulsos = json(requisicaoAutenticada(sessaoDono, get(PERSONALIZADOS).param("versaoId", versaoMitose))
                .andExpect(status().isOk()).andReturn());
        assertThat(avulsos.findValuesAsText("nome")).containsExactly("Avulso A");

        JsonNode doOutro = json(requisicaoAutenticada(sessaoOutro, get(PERSONALIZADOS)
                .param("versaoId", versaoMitose).param("laboratorioId", laboratorio.toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(doOutro.findValuesAsText("nome")).containsExactly("Do outro membro");
    }

    @Test
    void listaExigeLoginEParametrosValidos() throws Exception {
        String versao = versaoPublicada("mitose").toString();
        mockMvc.perform(get(PERSONALIZADOS).param("versaoId", versao)).andExpect(status().isUnauthorized());
        Cookie sessao = login(criarUsuario(RolesEnum.USER));

        requisicaoAutenticada(sessao, get(PERSONALIZADOS)).andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao, get(PERSONALIZADOS).param("versaoId", "nao-uuid")).andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao, get(PERSONALIZADOS).param("versaoId", versao).param("laboratorioId", "nao-uuid"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao, get(PERSONALIZADOS).param("versaoId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        requisicaoAutenticada(sessao, get(PERSONALIZADOS).param("versaoId", versao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------------------------------------------------------- concorrência

    @Test
    void postsSimultaneosComOMesmoIdResultamEmUmaCriacaoEDemaisRepeticoes() throws Exception {
        Usuario dono = criarUsuario(RolesEnum.USER);
        Cookie sessao = login(dono);
        UUID fase = idDaFase("meiose", "Tétrade");
        int rodadas = 6;
        int disputantes = 6;

        for (int rodada = 0; rodada < rodadas; rodada++) {
            UUID id = UUID.randomUUID();
            String nome = "Mesmo id " + rodada;
            List<Integer> statuses = dispararSimultaneos(disputantes,
                    indice -> postar(sessao, id, fase, null, nome).andReturn().getResponse().getStatus());

            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 200).hasSize(disputantes - 1);
        }
        assertThat(contarPersonalizados(dono)).isEqualTo(rodadas);
    }

    @Test
    void postsSimultaneosComOMesmoNomeEIdsDiferentesResultamEmUm201EOsDemais409() throws Exception {
        Usuario dono = criarUsuario(RolesEnum.USER);
        Cookie sessao = login(dono);
        UUID fase = idDaFase("meiose", "Tétrade");
        int rodadas = 6;
        int disputantes = 6;

        for (int rodada = 0; rodada < rodadas; rodada++) {
            String nome = "Mesmo nome " + rodada;
            List<MvcResult> resultados = dispararSimultaneos(disputantes,
                    indice -> postar(sessao, UUID.randomUUID(), fase, null, indice % 2 == 0 ? nome : nome.toUpperCase())
                            .andReturn());

            assertThat(resultados).filteredOn(r -> r.getResponse().getStatus() == 201).hasSize(1);
            assertThat(resultados).filteredOn(r -> r.getResponse().getStatus() == 409).hasSize(disputantes - 1);
            for (MvcResult resultado : resultados) {
                assertThat(resultado.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                        .doesNotContain("uq_", "constraint", "tipo_anormalidade");
            }
        }
        assertThat(contarPersonalizados(dono)).isEqualTo(rodadas);
    }

    // ---------------------------------------------------------------- apoio

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

    private void verificarVersao(JsonNode versao, List<FaseEsperada> esperadas) {
        JsonNode fases = versao.path("fases");
        assertThat(fases).hasSize(esperadas.size());
        for (int indice = 0; indice < esperadas.size(); indice++) {
            FaseEsperada esperada = esperadas.get(indice);
            JsonNode fase = fases.get(indice);
            assertThat(fase.path("nome").asText()).isEqualTo(esperada.nome());
            assertThat(fase.path("ordem").asInt()).isEqualTo(indice + 1);
            assertThat(fase.path("contaComoDivisao").asBoolean()).as("contaComoDivisao de %s", esperada.nome())
                    .isEqualTo(esperada.contaComoDivisao());
            assertThat(fase.path("id").asText()).isNotBlank();
            List<String> tipos = new ArrayList<>();
            fase.path("tipos").forEach(tipo -> {
                assertThat(tipo.path("id").asText()).isNotBlank();
                tipos.add(tipo.path("nome").asText());
            });
            assertThat(tipos).as("tipos de %s", esperada.nome()).containsExactly(esperada.tipos());
        }
    }

    private static JsonNode porCodigo(JsonNode protocolos, String codigo) {
        for (JsonNode protocolo : protocolos) {
            if (codigo.equals(protocolo.path("codigo").asText())) {
                return protocolo;
            }
        }
        throw new AssertionError("Protocolo ausente: " + codigo);
    }

    private void assertRecusado(String sql, Object... argumentos) {
        assertThatThrownBy(() -> jdbcTemplate.update(sql, argumentos))
                .as(sql)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("publicada");
    }

    /** Executa em transação que sempre é revertida: versões publicadas de teste não podem ficar no banco. */
    private void executarRevertendo(Consumer<JdbcTemplate> acao) {
        new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
            transacao.setRollbackOnly();
            acao.accept(jdbcTemplate);
        });
    }

    private UUID criarRascunho(JdbcTemplate jdbc) {
        UUID protocoloId = UUID.randomUUID();
        UUID versaoId = UUID.randomUUID();
        jdbc.update("INSERT INTO protocolo (id, version, created_at, last_modified_at, codigo, nome) "
                + "VALUES (?, 0, NOW(), NOW(), ?, 'Teste')", protocoloId, "teste_" + protocoloId.toString().substring(0, 8));
        jdbc.update("INSERT INTO protocolo_versao (id, version, created_at, last_modified_at, protocolo_id, numero) "
                + "VALUES (?, 0, NOW(), NOW(), ?, 1)", versaoId, protocoloId);
        return versaoId;
    }

    private UUID inserirFase(JdbcTemplate jdbc, UUID versaoId, String nome, int ordem) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO protocolo_fase (id, version, created_at, last_modified_at, versao_id, nome, ordem, "
                + "conta_como_divisao) VALUES (?, 0, NOW(), NOW(), ?, ?, ?, TRUE)", id, versaoId, nome, ordem);
        return id;
    }

    private UUID inserirTipoFixo(JdbcTemplate jdbc, UUID faseId, String nome, int ordem) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tipo_anormalidade (id, version, created_at, last_modified_at, fase_id, nome, "
                + "nome_normalizado, ordem) VALUES (?, 0, NOW(), NOW(), ?, ?, ?, ?)",
                id, faseId, nome, NomeTipoAnormalidade.normalizado(nome), ordem);
        return id;
    }

    private void inserirTipoBruto(UUID faseId, Long usuarioId, UUID laboratorioId, String nome, String normalizado,
                                  Integer ordem) {
        jdbcTemplate.update("INSERT INTO tipo_anormalidade (id, version, created_at, last_modified_at, fase_id, "
                + "usuario_id, laboratorio_id, nome, nome_normalizado, ordem) "
                + "VALUES (?, 0, NOW(), NOW(), ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), faseId, usuarioId, laboratorioId, nome, normalizado, ordem);
    }

    private UUID idDaFase(String codigo, String nome) {
        return jdbcTemplate.queryForObject(
                "SELECT f.id FROM protocolo_fase f JOIN protocolo_versao v ON v.id = f.versao_id "
                        + "JOIN protocolo p ON p.id = v.protocolo_id WHERE p.codigo = ? AND v.numero = 1 AND f.nome = ?",
                UUID.class, codigo, nome);
    }

    private UUID versaoPublicada(String codigo) {
        return jdbcTemplate.queryForObject(
                "SELECT v.id FROM protocolo_versao v JOIN protocolo p ON p.id = v.protocolo_id "
                        + "WHERE p.codigo = ? AND v.numero = 1", UUID.class, codigo);
    }

    private int contarPersonalizados(Usuario usuario) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tipo_anormalidade WHERE usuario_id = ?", Integer.class, usuario.getId());
    }

    private UUID criarLaboratorioDe(Usuario criador) {
        return laboratorioService.criar("Laboratório " + UUID.randomUUID(), criador.getId()).id();
    }

    private void adicionarMembro(UUID laboratorioId, Usuario membro, Usuario concedidoPor) {
        new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
            Laboratorio laboratorio = laboratorioRepository.findById(laboratorioId).orElseThrow();
            Participacao participacao = new Participacao(uuidFactory.generate(), laboratorio, membro.getId(), Instant.now());
            participacaoRepository.save(participacao);
            papelRepository.save(new ParticipacaoPapel(
                    uuidFactory.generate(), participacao, PapelLaboratorio.MEMBRO, concedidoPor.getId()));
        });
    }

    private ResultActions postar(Cookie sessao, UUID id, UUID faseId, UUID laboratorioId, String nome) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", id.toString());
        corpo.put("faseId", faseId.toString());
        if (laboratorioId != null) {
            corpo.put("laboratorioId", laboratorioId.toString());
        }
        corpo.put("nome", nome);
        return postarBruto(sessao, objectMapper.writeValueAsString(corpo));
    }

    private ResultActions postarBruto(Cookie sessao, String corpo) throws Exception {
        return requisicaoAutenticada(sessao, post(PERSONALIZADOS).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private String corpo(String... pares) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            corpo.put(pares[indice], pares[indice + 1]);
        }
        return objectMapper.writeValueAsString(corpo);
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Corpo de erro sem o horário, que difere entre respostas. */
    private Map<String, Object> corpoSemHorario(MvcResult resultado) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> corpo = objectMapper.readValue(
                resultado.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), Map.class);
        corpo.remove("timestamp");
        return corpo;
    }

    private Usuario criarUsuario(RolesEnum papelGlobal) {
        Role role = roleRepository.findByNome(papelGlobal).orElseThrow();
        String email = "contagem-" + UUID.randomUUID() + "@exemplo.org";
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Pessoa de teste " + email.substring(9, 17))
                .email(email)
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
                .role(role)
                .emailConfirmadoEm(Instant.now())
                .build());
    }

    private Cookie login(Usuario usuario) throws Exception {
        Cookie csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(COOKIE_CSRF);
        assertNotNull(csrf);
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("email", usuario.getEmail(), "senha", SENHA)))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie sessao = resultado.getResponse().getCookie(COOKIE_SESSAO);
        assertNotNull(sessao);
        return sessao;
    }

    private ResultActions requisicaoAutenticada(Cookie sessao, MockHttpServletRequestBuilder requisicao) throws Exception {
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        return mockMvc.perform(requisicao.cookie(sessao, csrf).header("X-XSRF-TOKEN", TOKEN_CSRF));
    }
}
