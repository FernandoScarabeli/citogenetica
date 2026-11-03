package br.ufla.citogenetica;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import br.ufla.citogenetica.laboratorio.internal.application.LaboratorioService;
import br.ufla.citogenetica.laboratorio.internal.domain.EstadoParticipacao;
import br.ufla.citogenetica.laboratorio.internal.domain.Laboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.PapelLaboratorio;
import br.ufla.citogenetica.laboratorio.internal.domain.Participacao;
import br.ufla.citogenetica.laboratorio.internal.domain.ParticipacaoPapel;
import br.ufla.citogenetica.laboratorio.internal.persistence.LaboratorioRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoPapelRepository;
import br.ufla.citogenetica.laboratorio.internal.persistence.ParticipacaoRepository;
import br.ufla.citogenetica.shared.persistence.UuidFactory;
import br.ufla.citogenetica.shared.web.error.ConflictException;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LaboratorioIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "Frase segura para testes de laboratório";
    private static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String TOKEN_CSRF = "csrf-laboratorio-integracao";

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

    @Test
    void criarLaboratorioTornaCriadorAdministradorLocal() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);

        requisicaoAutenticada(login(usuario), post("/api/v1/laboratorios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "  Citogenética  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Citogenética"))
                .andExpect(jsonPath("$.estado").value("ATIVO"))
                .andExpect(jsonPath("$.papeis").isArray())
                .andExpect(jsonPath("$.papeis").value(org.hamcrest.Matchers.containsInAnyOrder("MEMBRO", "ADMIN")));

        Laboratorio laboratorio = laboratorioRepository.findAll().stream()
                .filter(item -> item.getNome().equals("Citogenética"))
                .findFirst().orElseThrow();
        Participacao participacao = participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorio.getId(), usuario.getId(), EstadoParticipacao.ATIVA)
                .orElseThrow();
        assertEquals(Set.of(PapelLaboratorio.MEMBRO, PapelLaboratorio.ADMIN),
                laboratorioService.buscar(laboratorio.getId(), usuario.getId()).papeis());
        assertEquals(36, laboratorio.getId().toString().length());
        assertEquals(participacao.getId(), UUID.fromString(participacao.getId().toString()));
    }

    @Test
    void contaPendenteOuInativaNaoPodeCriarLaboratorio() throws Exception {
        long laboratoriosAntes = laboratorioRepository.count();
        for (boolean confirmada : List.of(false, true)) {
            Usuario usuario = criarUsuario(RolesEnum.USER,
                    confirmada ? StatusEnum.INATIVO : StatusEnum.ATIVO, confirmada);
            Cookie csrf = csrfCookie();
            mockMvc.perform(post("/api/v1/auth/login")
                            .cookie(csrf)
                            .header("X-XSRF-TOKEN", csrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", usuario.getEmail(), "senha", SENHA)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/laboratorios")
                            .cookie(csrf)
                            .header("X-XSRF-TOKEN", csrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("nome", "Não deve existir")))
                    .andExpect(status().isUnauthorized());
        }
        assertEquals(laboratoriosAntes, laboratorioRepository.count());
    }

    @Test
    void administradorGlobalSemParticipacaoNaoVeNemAlteraLaboratorio() throws Exception {
        Usuario criador = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(criador, "Privado");
        Usuario adminGlobal = criarUsuario(RolesEnum.ADMIN, StatusEnum.ATIVO, true);
        Cookie sessao = login(adminGlobal);

        requisicaoAutenticada(sessao, get("/api/v1/laboratorios/{id}", laboratorioId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Laboratório não encontrado."));
        requisicaoAutenticada(sessao, patch("/api/v1/laboratorios/{id}", laboratorioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Alteração indevida")))
                .andExpect(status().isNotFound());
        requisicaoAutenticada(sessao, get("/api/v1/laboratorios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        assertEquals("Privado", laboratorioRepository.findById(laboratorioId).orElseThrow().getNome());
    }

    @Test
    void laboratorioAlheioRetorna404EMembroSemPapelRecebe403() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(admin, "Acesso controlado");
        Usuario naoMembro = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        Cookie sessaoNaoMembro = login(naoMembro);

        requisicaoAutenticada(sessaoNaoMembro, get("/api/v1/laboratorios/{id}", laboratorioId))
                .andExpect(status().isNotFound());

        adicionarParticipacao(laboratorioId, naoMembro.getId(), admin.getId(), Set.of(PapelLaboratorio.MEMBRO));
        requisicaoAutenticada(sessaoNaoMembro, patch("/api/v1/laboratorios/{id}", laboratorioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Sem permissão")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarMeusRetornaSomenteLaboratoriosComParticipacaoAtiva() throws Exception {
        Usuario primeiro = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        Usuario segundo = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID labPrimeiro = criarLaboratorio(primeiro, "Laboratório primeiro");
        criarLaboratorio(segundo, "Laboratório segundo");

        requisicaoAutenticada(login(primeiro), get("/api/v1/laboratorios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(labPrimeiro.toString()));
    }

    @Test
    void adminConcedeRetiraPapeisEIncrementaVersao() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(admin, "Papéis");
        Usuario membro = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID participacaoId = adicionarParticipacao(
                laboratorioId, membro.getId(), admin.getId(), Set.of(PapelLaboratorio.MEMBRO));
        Cookie sessaoAdmin = login(admin);

        requisicaoAutenticada(sessaoAdmin, patch("/api/v1/laboratorios/{lab}/participacoes/{participacao}/papeis",
                        laboratorioId, participacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("versaoEsperada", 0, "papeis", List.of("MEMBRO", "ORIENTADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis").value(org.hamcrest.Matchers.contains("MEMBRO", "ORIENTADOR")))
                .andExpect(jsonPath("$.versao").value(1));

        requisicaoAutenticada(sessaoAdmin, patch("/api/v1/laboratorios/{lab}/participacoes/{participacao}/papeis",
                        laboratorioId, participacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("versaoEsperada", 1, "papeis", List.of("MEMBRO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis").value(org.hamcrest.Matchers.contains("MEMBRO")))
                .andExpect(jsonPath("$.versao").value(2));
    }

    @Test
    void rosterMostraEmailSomenteParaAdminLocal() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(admin, "Roster");
        Usuario membro = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        adicionarParticipacao(laboratorioId, membro.getId(), admin.getId(), Set.of(PapelLaboratorio.MEMBRO));

        String rosterAdmin = requisicaoAutenticada(login(admin), get("/api/v1/laboratorios/{id}/participacoes", laboratorioId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(rosterAdmin.contains(membro.getEmail()));

        String rosterMembro = requisicaoAutenticada(login(membro), get("/api/v1/laboratorios/{id}/participacoes", laboratorioId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(rosterMembro.contains(membro.getEmail()));
        assertTrue(rosterMembro.contains(membro.getNome()));
    }

    @Test
    void membroPodeSairEAdminPodeRemoverSemApagarParticipacoesHistoricas() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(admin, "Saída e remoção");
        Usuario membroQueSai = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID participacaoQueSai = adicionarParticipacao(
                laboratorioId, membroQueSai.getId(), admin.getId(), Set.of(PapelLaboratorio.MEMBRO));
        Usuario membroRemovido = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID participacaoRemovida = adicionarParticipacao(
                laboratorioId, membroRemovido.getId(), admin.getId(), Set.of(PapelLaboratorio.MEMBRO));

        requisicaoAutenticada(login(membroQueSai), delete("/api/v1/laboratorios/{lab}/participacoes/me", laboratorioId))
                .andExpect(status().isNoContent());
        requisicaoAutenticada(login(admin), delete("/api/v1/laboratorios/{lab}/participacoes/{participacao}",
                        laboratorioId, participacaoRemovida))
                .andExpect(status().isNoContent());

        assertEquals(EstadoParticipacao.SAIU,
                participacaoRepository.findById(participacaoQueSai).orElseThrow().getEstado());
        assertEquals(EstadoParticipacao.REMOVIDA,
                participacaoRepository.findById(participacaoRemovida).orElseThrow().getEstado());
    }

    @Test
    void ultimoAdminNaoPodeSairSerRemovidoNemRebaixado() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(admin, "Último admin");
        Participacao participacao = participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorioId, admin.getId(), EstadoParticipacao.ATIVA)
                .orElseThrow();
        Cookie sessao = login(admin);

        requisicaoAutenticada(sessao, delete("/api/v1/laboratorios/{lab}/participacoes/me", laboratorioId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A operação não pode ser concluída."));
        requisicaoAutenticada(sessao, delete("/api/v1/laboratorios/{lab}/participacoes/{participacao}",
                        laboratorioId, participacao.getId()))
                .andExpect(status().isConflict());
        requisicaoAutenticada(sessao, patch("/api/v1/laboratorios/{lab}/participacoes/{participacao}/papeis",
                        laboratorioId, participacao.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("versaoEsperada", participacao.getVersion(), "papeis", List.of("MEMBRO"))))
                .andExpect(status().isConflict());

        assertEquals(1, papelRepository.contarParticipacoesAtivasComPapel(laboratorioId, PapelLaboratorio.ADMIN));
    }

    @Test
    void corridaDeDoisAdminsSaindoDeixaExatamenteUmAtivo() throws Exception {
        Usuario primeiro = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        Usuario segundo = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(primeiro, "Corrida de admins");
        UUID participacaoPrimeiro = participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorioId, primeiro.getId(), EstadoParticipacao.ATIVA)
                .orElseThrow().getId();
        UUID participacaoSegundo = adicionarParticipacao(laboratorioId, segundo.getId(), primeiro.getId(),
                Set.of(PapelLaboratorio.MEMBRO, PapelLaboratorio.ADMIN));
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> primeiroSaiu = executor.submit(() -> tentarSair(laboratorioId, primeiro.getId(), inicio));
            Future<Boolean> segundoSaiu = executor.submit(() -> tentarSair(laboratorioId, segundo.getId(), inicio));
            inicio.countDown();

            assertTrue(primeiroSaiu.get() ^ segundoSaiu.get());
            assertEquals(1, papelRepository.contarParticipacoesAtivasComPapel(laboratorioId, PapelLaboratorio.ADMIN));
            List<Participacao> participacoes = List.of(
                    participacaoRepository.findById(participacaoPrimeiro).orElseThrow(),
                    participacaoRepository.findById(participacaoSegundo).orElseThrow());
            assertTrue(participacoes.stream().anyMatch(p -> p.getEstado() == EstadoParticipacao.SAIU));
            assertEquals(1, participacoes.stream().filter(p -> p.getEstado() == EstadoParticipacao.ATIVA).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void triggerPostgresImpedeRemoverUltimoAdminPorSqlDireto() {
        Usuario admin = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(admin, "Trigger de admin");
        UUID participacaoId = participacaoRepository
                .findByLaboratorioIdAndUsuarioIdAndEstado(laboratorioId, admin.getId(), EstadoParticipacao.ATIVA)
                .orElseThrow().getId();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                UPDATE participacao_papel
                   SET revogado_em = ?, revogado_por_usuario_id = ?
                 WHERE participacao_id = ? AND papel = 'ADMIN' AND revogado_em IS NULL
                """, java.sql.Timestamp.from(Instant.now()), admin.getId(), participacaoId));
        assertEquals(1, papelRepository.contarParticipacoesAtivasComPapel(laboratorioId, PapelLaboratorio.ADMIN));
    }

    @Test
    void desativacaoBloqueiaMutacoesMantemLeituraEReativacaoEAuditada() throws Exception {
        Usuario participante = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(participante, "Desativado");
        Usuario adminPlataforma = criarUsuario(RolesEnum.ADMIN, StatusEnum.ATIVO, true);
        Cookie sessaoPlataforma = login(adminPlataforma);
        Cookie sessaoParticipante = login(participante);

        requisicaoAutenticada(sessaoPlataforma,
                patch("/api/v1/admin/laboratorios/{id}/desativar", laboratorioId))
                .andExpect(status().isNoContent());
        assertEquals(adminPlataforma.getId(), jdbcTemplate.queryForObject(
                "SELECT desativado_por_usuario_id FROM laboratorio WHERE id = ?", Long.class, laboratorioId));
        requisicaoAutenticada(sessaoParticipante, get("/api/v1/laboratorios/{id}", laboratorioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESATIVADO"));
        requisicaoAutenticada(sessaoParticipante, patch("/api/v1/laboratorios/{id}", laboratorioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Alteração bloqueada")))
                .andExpect(status().isConflict());
        requisicaoAutenticada(sessaoParticipante,
                delete("/api/v1/laboratorios/{id}/participacoes/me", laboratorioId))
                .andExpect(status().isConflict());

        requisicaoAutenticada(sessaoPlataforma,
                patch("/api/v1/admin/laboratorios/{id}/reativar", laboratorioId))
                .andExpect(status().isNoContent());
        requisicaoAutenticada(sessaoParticipante, patch("/api/v1/laboratorios/{id}", laboratorioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nome", "Reativado")))
                .andExpect(status().isOk());
    }

    @Test
    void versaoObsoletaRetorna409SemAlterarPapeis() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID laboratorioId = criarLaboratorio(admin, "Versão");
        Usuario membro = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);
        UUID participacaoId = adicionarParticipacao(laboratorioId, membro.getId(), admin.getId(),
                Set.of(PapelLaboratorio.MEMBRO));
        Cookie sessao = login(admin);

        requisicaoAutenticada(sessao, patch("/api/v1/laboratorios/{lab}/participacoes/{participacao}/papeis",
                        laboratorioId, participacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("versaoEsperada", 0, "papeis", List.of("MEMBRO", "ORIENTADOR"))))
                .andExpect(status().isOk());
        requisicaoAutenticada(sessao, patch("/api/v1/laboratorios/{lab}/participacoes/{participacao}/papeis",
                        laboratorioId, participacaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("versaoEsperada", 0, "papeis", List.of("MEMBRO"))))
                .andExpect(status().isConflict());
        assertEquals(Set.of(PapelLaboratorio.MEMBRO, PapelLaboratorio.ORIENTADOR),
                laboratorioService.listarParticipacoes(laboratorioId, admin.getId()).stream()
                        .filter(p -> p.id().equals(participacaoId)).findFirst().orElseThrow().papeis());
    }

    @Test
    void pathUuidDeveSerCanonicoEV4() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO, true);

        requisicaoAutenticada(login(usuario), get("/api/v1/laboratorios/{id}", "550E8400-E29B-41D4-A716-446655440000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parâmetros inválidos."));
        requisicaoAutenticada(login(usuario), get("/api/v1/laboratorios/{id}", "550e8400-e29b-11d4-a716-446655440000"))
                .andExpect(status().isBadRequest());
    }

    private boolean tentarSair(UUID laboratorioId, Long usuarioId, CountDownLatch inicio) throws Exception {
        inicio.await();
        try {
            laboratorioService.sair(laboratorioId, usuarioId);
            return true;
        } catch (ConflictException conflito) {
            return false;
        }
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

    private Usuario criarUsuario(RolesEnum papelGlobal, StatusEnum estado, boolean emailConfirmado) {
        Role role = roleRepository.findByNome(papelGlobal).orElseThrow();
        String email = "laboratorio-" + UUID.randomUUID() + "@exemplo.org";
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .nome("Pessoa de teste " + email.substring(0, 12))
                .email(email)
                .senha(passwordEncoder.encode(SENHA))
                .status(estado)
                .role(role)
                .emailConfirmadoEm(emailConfirmado ? Instant.now() : null)
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
}
