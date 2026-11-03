package br.ufla.citogenetica;

import br.ufla.citogenetica.model.roles.Role;
import br.ufla.citogenetica.model.type.RolesEnum;
import br.ufla.citogenetica.model.type.StatusEnum;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.repository.RoleRepository;
import br.ufla.citogenetica.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SecurityUserBoundariesIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA_ATUAL = "SenhaAtualSegura123!";
    private static final String COOKIE_SESSAO = "CITOGENETICA_SESSION";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String TOKEN_CSRF = "csrf-de-integracao";

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

    private Role roleUser;
    private Role roleAdmin;

    @BeforeEach
    void prepararRoles() {
        roleUser = roleRepository.findByNome(RolesEnum.USER).orElseThrow();
        roleAdmin = roleRepository.findByNome(RolesEnum.ADMIN).orElseThrow();
    }

    @Test
    void sessaoDeUsuarioQueFicouInativoDeveReceber401NoFormatoDaApi() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        Cookie sessao = login(usuario);
        usuario.setStatus(StatusEnum.INATIVO);
        usuarioRepository.saveAndFlush(usuario);

        requisicaoAutenticada(sessao, get("/api/v1/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Falha na autenticação."))
                .andExpect(jsonPath("$.path").value("/api/v1/usuarios/me"));
    }

    @Test
    void sessaoDeUsuarioRemovidoDeveReceber401SemExporExcecao() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        Cookie sessao = login(usuario);
        usuarioRepository.delete(usuario);
        usuarioRepository.flush();

        requisicaoAutenticada(sessao, get("/api/v1/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Falha na autenticação."))
                .andExpect(jsonPath("$.path").value("/api/v1/usuarios/me"));
    }

    @Test
    void usuarioNaoDeveTrocarSenhaDeOutraContaPelaRotaAntigaComId() throws Exception {
        Usuario autenticado = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        Usuario alvo = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        String senhaHashOriginal = alvo.getPassword();

        requisicaoAutenticada(login(autenticado), put("/api/v1/usuarios/atualizar-senha/{id}", alvo.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("senhaAtual", SENHA_ATUAL, "novaSenha", "OutraSenhaSegura456!")))
                .andExpect(status().isForbidden());

        Usuario alvoAtualizado = usuarioRepository.findById(alvo.getId()).orElseThrow();
        assertEquals(senhaHashOriginal, alvoAtualizado.getPassword());
    }

    @Test
    void usuarioDeveTrocarSuaSenhaPelaRotaMeExigindoSenhaAtualEMantendoASessaoAtual() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        Cookie sessao = login(usuario);

        requisicaoAutenticada(sessao, put("/api/v1/usuarios/me/senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("senhaAtual", SENHA_ATUAL, "novaSenha", "NovaSenhaSegura456!")))
                .andExpect(status().isNoContent());

        Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NovaSenhaSegura456!", atualizado.getPassword()));
        assertNotEquals(SENHA_ATUAL, atualizado.getPassword());
        requisicaoAutenticada(sessao, get("/api/v1/usuarios/me")).andExpect(status().isOk());
    }

    @Test
    void senhaAtualIncorretaDeveImpedirTrocaDaSenha() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        Cookie sessao = login(usuario);
        String senhaHashOriginal = usuario.getPassword();

        requisicaoAutenticada(sessao, put("/api/v1/usuarios/me/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("senhaAtual", "SenhaIncorreta123!", "novaSenha", "NovaSenhaSegura456!")))
                .andExpect(status().isConflict());

        assertEquals(senhaHashOriginal, usuarioRepository.findById(usuario.getId()).orElseThrow().getPassword());
    }

    @Test
    void usuarioDeveEditarSeuProprioPerfilSemAlterarCamposPrivilegiados() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);

        requisicaoAutenticada(login(usuario), put("/api/v1/usuarios/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nome": "Nome atualizado",
                          "email": "%s",
                          "roleName": "ADMIN",
                          "status": "INATIVO"
                        }
                        """.formatted(usuario.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome atualizado"))
                .andExpect(jsonPath("$.email").value(usuario.getEmail()));

        Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertEquals(RolesEnum.USER, atualizado.getRole().getNome());
        assertEquals(StatusEnum.ATIVO, atualizado.getStatus());
    }

    @Test
    void usuarioComumNaoDeveEditarPerfilDeOutroId() throws Exception {
        Usuario autenticado = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        Usuario alvo = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);

        requisicaoAutenticada(login(autenticado), put("/api/v1/usuarios/{id}", alvo.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Nome indevido", "email", alvo.getEmail())))
                .andExpect(status().isForbidden());

        assertNotEquals("Nome indevido", usuarioRepository.findById(alvo.getId()).orElseThrow().getNome());
    }

    @Test
    void adminMantemPermissaoDeEditarPerfilDeOutroUsuario() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.ADMIN, StatusEnum.ATIVO);
        Usuario alvo = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);

        requisicaoAutenticada(login(admin), put("/api/v1/usuarios/{id}", alvo.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Nome alterado pelo admin", "email", alvo.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome alterado pelo admin"));
    }

    @Test
    void loginDeContaDesativadaDeveTerMesmaRespostaDeCredenciaisInvalidas() throws Exception {
        Usuario usuarioAtivo = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        Usuario usuarioInativo = criarUsuario(RolesEnum.USER, StatusEnum.INATIVO);

        requisicaoComCsrf(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", usuarioAtivo.getEmail(), "senha", "SenhaErrada123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos."));
        requisicaoComCsrf(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", usuarioInativo.getEmail(), "senha", SENHA_ATUAL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos."));
    }

    private Usuario criarUsuario(RolesEnum roleNome, StatusEnum status) {
        Role role = roleNome == RolesEnum.ADMIN ? roleAdmin : roleUser;
        return usuarioRepository.save(Usuario.builder()
                .nome("Usuário de teste")
                .email(UUID.randomUUID() + "@citogenetica.test")
                .senha(passwordEncoder.encode(SENHA_ATUAL))
                .status(status)
                .role(role)
                .build());
    }

    private Cookie login(Usuario usuario) throws Exception {
        MvcResult resultado = requisicaoComCsrf(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", usuario.getEmail(), "senha", SENHA_ATUAL)))
                .andExpect(status().isNoContent())
                .andReturn();
        return resultado.getResponse().getCookie(COOKIE_SESSAO);
    }

    private ResultActions requisicaoAutenticada(Cookie sessao, MockHttpServletRequestBuilder requisicao)
            throws Exception {
        return requisicaoComCsrf(requisicao.cookie(sessao));
    }

    private ResultActions requisicaoComCsrf(MockHttpServletRequestBuilder requisicao) throws Exception {
        Cookie csrf = new Cookie(COOKIE_CSRF, TOKEN_CSRF);
        requisicao.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
        return mockMvc.perform(requisicao);
    }

    private String json(Object... pares) throws Exception {
        var objeto = new java.util.LinkedHashMap<String, Object>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            objeto.put((String) pares[indice], pares[indice + 1]);
        }
        return objectMapper.writeValueAsString(objeto);
    }
}
