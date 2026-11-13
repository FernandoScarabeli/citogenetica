package br.ufla.citogenetica;

import br.ufla.citogenetica.usuario.api.RolesEnum;
import br.ufla.citogenetica.usuario.internal.domain.StatusEnum;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import br.ufla.citogenetica.usuario.internal.persistence.RoleRepository;
import br.ufla.citogenetica.usuario.internal.persistence.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem @Transactional de propósito: a troca de foto apaga a anterior só depois do commit. */
@AutoConfigureMockMvc
class FotoPerfilIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaDaFotoSegura123!";
    private static final String TOKEN_CSRF = "csrf-da-foto";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 9, 9};

    private static int ultimoIp;

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

    @BeforeEach
    void limparChamadas() {
        clearInvocations(s3Client);
    }

    @Test
    void fotoEnviadaFicaNaContaEAparecemSoParaElaMesma() throws Exception {
        Usuario ana = criarUsuario();
        Usuario bruno = criarUsuario();
        Cookie sessaoAna = login(ana);

        String versao = objectMapper.readTree(enviarFoto(sessaoAna, PNG, MediaType.IMAGE_PNG_VALUE)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("fotoVersao").asText();
        assertThat(versao).matches("[0-9a-f-]{36}");

        ArgumentCaptor<PutObjectRequest> gravado = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(gravado.capture(), any(RequestBody.class));
        assertThat(gravado.getValue().key()).isEqualTo("perfis/" + versao + ".png");
        assertThat(gravado.getValue().contentType()).isEqualTo("image/png");

        autenticado(sessaoAna, get("/api/v1/usuarios/me"))
                .andExpect(jsonPath("$.fotoVersao").value(versao));

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), PNG));
        autenticado(sessaoAna, get("/api/v1/usuarios/me/foto"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control", "max-age=86400, private"));

        Cookie sessaoBruno = login(bruno);
        autenticado(sessaoBruno, get("/api/v1/usuarios/me"))
                .andExpect(jsonPath("$.fotoVersao").doesNotExist());
        autenticado(sessaoBruno, get("/api/v1/usuarios/me/foto"))
                .andExpect(status().isNotFound());
    }

    @Test
    void trocarAFotoApagaAAnteriorDoArmazenamento() throws Exception {
        Cookie sessao = login(criarUsuario());
        String primeira = objectMapper.readTree(enviarFoto(sessao, PNG, MediaType.IMAGE_PNG_VALUE)
                .andReturn().getResponse().getContentAsString()).get("fotoVersao").asText();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

        enviarFoto(sessao, JPEG, MediaType.IMAGE_JPEG_VALUE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoVersao").value(org.hamcrest.Matchers.not(primeira)));

        ArgumentCaptor<DeleteObjectRequest> apagado = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(apagado.capture());
        assertThat(apagado.getValue().key()).isEqualTo("perfis/" + primeira + ".png");
    }

    @Test
    void arquivoQueNaoEImagemERecusadoMesmoComTipoDeImagem() throws Exception {
        Cookie sessao = login(criarUsuario());

        enviarFoto(sessao, "<script>alert(1)</script>".getBytes(), MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Escolha uma imagem JPG, PNG ou WebP de até 2 MB."));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void arquivoMaiorQue2MbERecusado() throws Exception {
        // O MockMvc não passa pelo limite do servidor (413); este teste cobre a conferência do próprio serviço.
        Cookie sessao = login(criarUsuario());
        byte[] grande = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);

        enviarFoto(sessao, grande, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isUnprocessableEntity());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void armazenamentoForaDoArRespondeComAvisoClaroSemTrocarAFoto() throws Exception {
        Usuario usuario = criarUsuario();
        Cookie sessao = login(usuario);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("sem conexão"));

        enviarFoto(sessao, PNG, MediaType.IMAGE_PNG_VALUE)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Não foi possível acessar os arquivos agora. Tente novamente em instantes."));
        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getFotoChave()).isNull();
    }

    @Test
    void semSessaoNaoEnviaNemVeFoto() throws Exception {
        comCsrf(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/usuarios/me/foto")
                .file(new MockMultipartFile("arquivo", "foto.png", MediaType.IMAGE_PNG_VALUE, PNG)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/usuarios/me/foto")).andExpect(status().isUnauthorized());
    }

    private ResultActions enviarFoto(Cookie sessao, byte[] conteudo, String tipo) throws Exception {
        return autenticado(sessao, multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/usuarios/me/foto")
                .file(new MockMultipartFile("arquivo", "foto", tipo, conteudo)));
    }

    private Usuario criarUsuario() {
        return usuarioRepository.save(Usuario.builder()
                .nome("Pessoa da foto")
                .email(UUID.randomUUID() + "@citogenetica.test")
                .senha(passwordEncoder.encode(SENHA))
                .status(StatusEnum.ATIVO)
                .emailConfirmadoEm(Instant.now())
                .role(roleRepository.findByNome(RolesEnum.USER).orElseThrow())
                .build());
    }

    private Cookie login(Usuario usuario) throws Exception {
        // IP próprio a cada login: o limite de tentativas fica gravado e esta classe não desfaz a transação.
        return comCsrf(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("10.77." + (++ultimoIp / 250) + "." + (ultimoIp % 250 + 1)); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", usuario.getEmail(), "senha", SENHA))))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("CITOGENETICA_SESSION");
    }

    private ResultActions autenticado(Cookie sessao, MockHttpServletRequestBuilder requisicao) throws Exception {
        return comCsrf(requisicao.cookie(sessao));
    }

    private ResultActions comCsrf(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mockMvc.perform(requisicao.cookie(new Cookie("XSRF-TOKEN", TOKEN_CSRF)).header("X-XSRF-TOKEN", TOKEN_CSRF));
    }
}
