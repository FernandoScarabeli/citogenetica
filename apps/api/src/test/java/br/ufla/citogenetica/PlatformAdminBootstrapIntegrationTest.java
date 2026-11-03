package br.ufla.citogenetica;

import br.ufla.citogenetica.config.BootstrapAdminRunner;
import br.ufla.citogenetica.usuario.api.PlatformAdminBootstrap;
import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.api.error.PlatformAdminBootstrapException;
import br.ufla.citogenetica.usuario.internal.domain.Role;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlatformAdminBootstrapIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "Frase segura para teste 2026";
    private static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";

    @Autowired
    private PlatformAdminBootstrap platformAdminBootstrap;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void liberarMarcadorDeUsoUnico() {
        jdbcTemplate.update("UPDATE bootstrap_admin_plataforma SET consumido_em = NULL, usuario_id = NULL WHERE id = 1");
    }

    @Test
    void promoveContaAtivaComEmailConfirmadoEConsomeMarcador() {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO, true);

        long usuarioPromovido = platformAdminBootstrap.promoteFirstAdmin(usuario.getEmail());

        assertEquals(usuario.getId(), usuarioPromovido);
        assertEquals(RolesEnum.ADMIN, usuarioRepository.findById(usuario.getId()).orElseThrow()
                .getRole().getNome());
        assertTrue(marcadorConsumido());
        assertEquals(usuario.getId(), usuarioDoMarcador());
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT consumido_em FROM bootstrap_admin_plataforma WHERE id = 1", java.sql.Timestamp.class));
    }

    @Test
    void contaInexistentePendenteOuInativaNaoConsomeMarcador() {
        assertThrows(PlatformAdminBootstrapException.class,
                () -> platformAdminBootstrap.promoteFirstAdmin("inexistente-" + UUID.randomUUID() + "@exemplo.org"));
        assertFalse(marcadorConsumido());

        Usuario pendente = criarUsuario(StatusEnum.ATIVO, false);
        assertThrows(PlatformAdminBootstrapException.class,
                () -> platformAdminBootstrap.promoteFirstAdmin(pendente.getEmail()));
        assertFalse(marcadorConsumido());

        Usuario inativo = criarUsuario(StatusEnum.INATIVO, true);
        assertThrows(PlatformAdminBootstrapException.class,
                () -> platformAdminBootstrap.promoteFirstAdmin(inativo.getEmail()));
        assertFalse(marcadorConsumido());
        assertEquals(RolesEnum.USER, usuarioRepository.findById(pendente.getId()).orElseThrow()
                .getRole().getNome());
        assertEquals(RolesEnum.USER, usuarioRepository.findById(inativo.getId()).orElseThrow()
                .getRole().getNome());
    }

    @Test
    void segundaExecucaoFalhaEPreservaAdministradorOriginal() {
        Usuario primeiro = criarUsuario(StatusEnum.ATIVO, true);
        Usuario segundo = criarUsuario(StatusEnum.ATIVO, true);
        platformAdminBootstrap.promoteFirstAdmin(primeiro.getEmail());

        assertThrows(PlatformAdminBootstrapException.class,
                () -> platformAdminBootstrap.promoteFirstAdmin(segundo.getEmail()));

        assertEquals(primeiro.getId(), usuarioDoMarcador());
        assertEquals(RolesEnum.ADMIN, usuarioRepository.findById(primeiro.getId()).orElseThrow()
                .getRole().getNome());
        assertEquals(RolesEnum.USER, usuarioRepository.findById(segundo.getId()).orElseThrow()
                .getRole().getNome());
    }

    @Test
    void concorrenciaPromoveSomenteUmaConta() throws Exception {
        Usuario primeiro = criarUsuario(StatusEnum.ATIVO, true);
        Usuario segundo = criarUsuario(StatusEnum.ATIVO, true);
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> resultadoPrimeiro = executor.submit(() -> tentarPromover(primeiro, inicio));
            Future<Boolean> resultadoSegundo = executor.submit(() -> tentarPromover(segundo, inicio));
            inicio.countDown();

            boolean primeiroPromovido = resultadoPrimeiro.get();
            boolean segundoPromovido = resultadoSegundo.get();

            assertTrue(primeiroPromovido ^ segundoPromovido);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM usuario u JOIN role r ON r.id = u.role_id "
                            + "WHERE u.id IN (?, ?) AND r.nome = 'ADMIN'",
                    Integer.class, primeiro.getId(), segundo.getId()));
            long idVencedor = primeiroPromovido ? primeiro.getId() : segundo.getId();
            assertEquals(idVencedor, usuarioDoMarcador());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void promocaoRevogaSessoesExistentesDoUsuario() throws Exception {
        Usuario usuario = criarUsuario(StatusEnum.ATIVO, true);
        Cookie sessao = iniciarSessao(usuario);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class,
                usuario.getId().toString()));

        platformAdminBootstrap.promoteFirstAdmin(usuario.getEmail());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class,
                usuario.getId().toString()));
        mockMvc.perform(get("/api/v1/usuarios/me").cookie(sessao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aplicacaoWebNormalNaoRegistraNemExecutaOComandoDeBootstrap() {
        assertTrue(applicationContext.getBeansOfType(BootstrapAdminRunner.class).isEmpty());
        assertFalse(marcadorConsumido());
    }

    private boolean tentarPromover(Usuario usuario, CountDownLatch inicio) throws Exception {
        inicio.await();
        try {
            platformAdminBootstrap.promoteFirstAdmin(usuario.getEmail());
            return true;
        } catch (PlatformAdminBootstrapException exception) {
            return false;
        }
    }

    private Usuario criarUsuario(StatusEnum status, boolean emailConfirmado) {
        Role roleUser = roleRepository.findByNome(RolesEnum.USER).orElseThrow();
        String email = "bootstrap-" + UUID.randomUUID() + "@exemplo.org";
        Usuario usuario = Usuario.builder()
                .nome("Conta de teste")
                .email(email)
                .senha(passwordEncoder.encode(SENHA))
                .status(status)
                .role(roleUser)
                .emailConfirmadoEm(emailConfirmado ? Instant.now() : null)
                .build();
        return usuarioRepository.saveAndFlush(usuario);
    }

    private Cookie iniciarSessao(Usuario usuario) throws Exception {
        Cookie csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(COOKIE_CSRF);
        assertNotNull(csrf);

        String corpo = objectMapper.writeValueAsString(java.util.Map.of(
                "email", usuario.getEmail(), "senha", SENHA));
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie sessao = login.getResponse().getCookie(COOKIE_SESSAO);
        assertNotNull(sessao);
        return sessao;
    }

    private boolean marcadorConsumido() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT consumido_em IS NOT NULL FROM bootstrap_admin_plataforma WHERE id = 1", Boolean.class));
    }

    private Long usuarioDoMarcador() {
        return jdbcTemplate.queryForObject(
                "SELECT usuario_id FROM bootstrap_admin_plataforma WHERE id = 1", Long.class);
    }
}
