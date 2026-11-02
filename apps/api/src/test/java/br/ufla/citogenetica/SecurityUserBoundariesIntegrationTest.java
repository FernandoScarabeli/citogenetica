package br.ufla.citogenetica;

import br.ufla.citogenetica.configuration.security.JWTConfig;
import br.ufla.citogenetica.model.roles.Role;
import br.ufla.citogenetica.model.type.RolesEnum;
import br.ufla.citogenetica.model.type.StatusEnum;
import br.ufla.citogenetica.model.usuario.Usuario;
import br.ufla.citogenetica.repository.RoleRepository;
import br.ufla.citogenetica.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SecurityUserBoundariesIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA_ATUAL = "SenhaAtualSegura123!";

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
    private JWTConfig jwtConfig;

    private Role roleUser;
    private Role roleAdmin;

    @BeforeEach
    void prepararRoles() {
        roleUser = roleRepository.findByNome(RolesEnum.USER).orElseThrow();
        roleAdmin = roleRepository.findByNome(RolesEnum.ADMIN).orElseThrow();
    }

    @Test
    void tokenDeUsuarioInativoDeveReceber401NoFormatoDaApi() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.INATIVO);

        mockMvc.perform(requisicaoAutenticada(usuario, put("/api/v1/usuarios/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Falha na autenticação."))
                .andExpect(jsonPath("$.path").value("/api/v1/usuarios/me"));
    }

    @Test
    void tokenDeUsuarioRemovidoDeveReceber401SemExporExcecao() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        String token = jwtConfig.generateToken(usuario);
        usuarioRepository.delete(usuario);
        usuarioRepository.flush();

        mockMvc.perform(put("/api/v1/usuarios/me")
                        .header("Authorization", "Bearer " + token))
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

        mockMvc.perform(requisicaoAutenticada(autenticado,
                put("/api/v1/usuarios/atualizar-senha/{id}", alvo.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("senhaAtual", SENHA_ATUAL,
                        "novaSenha", "OutraSenhaSegura456!")))
                .andExpect(status().isForbidden());

        Usuario alvoAtualizado = usuarioRepository.findById(alvo.getId()).orElseThrow();
        assertEquals(senhaHashOriginal, alvoAtualizado.getPassword());
    }

    @Test
    void usuarioDeveTrocarSuaSenhaPelaRotaMeExigindoSenhaAtual() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);

        mockMvc.perform(requisicaoAutenticada(usuario, put("/api/v1/usuarios/me/senha"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("senhaAtual", SENHA_ATUAL,
                        "novaSenha", "NovaSenhaSegura456!")))
                .andExpect(status().isNoContent());

        Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NovaSenhaSegura456!", atualizado.getPassword()));
        assertNotEquals(SENHA_ATUAL, atualizado.getPassword());
    }

    @Test
    void senhaAtualIncorretaDeveImpedirTrocaDaSenha() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        String senhaHashOriginal = usuario.getPassword();

        mockMvc.perform(requisicaoAutenticada(usuario, put("/api/v1/usuarios/me/senha"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("senhaAtual", "SenhaIncorreta123!",
                                "novaSenha", "NovaSenhaSegura456!")))
                .andExpect(status().isConflict());

        assertEquals(senhaHashOriginal, usuarioRepository.findById(usuario.getId()).orElseThrow().getPassword());
    }

    @Test
    void usuarioDeveEditarSeuProprioPerfilPelaRotaMeSemAlterarCamposPrivilegiados() throws Exception {
        Usuario usuario = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);

        mockMvc.perform(requisicaoAutenticada(usuario, put("/api/v1/usuarios/me"))
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

        mockMvc.perform(requisicaoAutenticada(autenticado, put("/api/v1/usuarios/{id}", alvo.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Nome indevido", "email", alvo.getEmail())))
                .andExpect(status().isForbidden());

        assertNotEquals("Nome indevido", usuarioRepository.findById(alvo.getId()).orElseThrow().getNome());
    }

    @Test
    void adminMantemPermissaoDeEditarPerfilDeOutroUsuario() throws Exception {
        Usuario admin = criarUsuario(RolesEnum.ADMIN, StatusEnum.ATIVO);
        Usuario alvo = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);

        mockMvc.perform(requisicaoAutenticada(admin, put("/api/v1/usuarios/{id}", alvo.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("nome", "Nome alterado pelo admin", "email", alvo.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome alterado pelo admin"));
    }

    @Test
    void loginDeContaDesativadaDeveTerMesmaRespostaDeCredenciaisInvalidas() throws Exception {
        Usuario usuarioAtivo = criarUsuario(RolesEnum.USER, StatusEnum.ATIVO);
        Usuario usuarioInativo = criarUsuario(RolesEnum.USER, StatusEnum.INATIVO);

        ResultActions credenciaisInvalidas = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", usuarioAtivo.getEmail(), "senha", "SenhaErrada123!")));

        ResultActions contaDesativada = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", usuarioInativo.getEmail(), "senha", SENHA_ATUAL)));

        credenciaisInvalidas.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos."));
        contaDesativada.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou senha inválidos."));
    }

    private Usuario criarUsuario(RolesEnum roleNome, StatusEnum status) {
        Role role = roleNome == RolesEnum.ADMIN ? roleAdmin : roleUser;
        String identificador = UUID.randomUUID().toString();
        return usuarioRepository.save(Usuario.builder()
                .nome("Usuário de teste")
                .email(identificador + "@citogenetica.test")
                .senha(passwordEncoder.encode(SENHA_ATUAL))
                .status(status)
                .role(role)
                .build());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicaoAutenticada(
            Usuario usuario,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao) {
        return requisicao.header("Authorization", "Bearer " + jwtConfig.generateToken(usuario));
    }

    private String json(Object... pares) throws Exception {
        var objeto = new java.util.LinkedHashMap<String, Object>();
        for (int indice = 0; indice < pares.length; indice += 2) {
            objeto.put((String) pares[indice], pares[indice + 1]);
        }
        return objectMapper.writeValueAsString(objeto);
    }
}
