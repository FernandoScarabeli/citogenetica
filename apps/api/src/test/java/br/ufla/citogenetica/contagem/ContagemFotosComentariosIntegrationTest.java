package br.ufla.citogenetica.contagem;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import br.ufla.citogenetica.usuario.internal.domain.Usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContagemFotosComentariosIntegrationTest extends AbstractContagemIntegrationTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @BeforeEach
    void limparChamadas() {
        clearInvocations(s3Client);
    }

    // ---------------------------------------------------------------- fotos

    @Test
    void pesquisadorEnviaFotoOrientadorVeMembroEDeForaNao() throws Exception {
        Cenario c = montarCenario();
        UUID lamina = criarLamina(c.pesquisador(), c.laboratorio());

        JsonNode foto = json(enviarFoto(sessao(c.pesquisador()), lamina, JPEG, "Metáfase com ponte")
                .andExpect(status().isCreated()).andReturn());
        assertThat(foto.path("legenda").asText()).isEqualTo("Metáfase com ponte");
        assertThat(foto.path("contentType").asText()).isEqualTo("image/jpeg");
        assertThat(foto.path("enviadaPorNome").asText()).isEqualTo(c.pesquisador().getNome());
        ArgumentCaptor<PutObjectRequest> gravado = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(gravado.capture(), any(RequestBody.class));
        assertThat(gravado.getValue().key()).matches("laminas/[0-9a-f-]{36}\\.jpg");

        requisicaoAutenticada(sessao(c.orientador()), get(LAMINAS + "/{id}/fotos", lamina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(foto.path("id").asText()));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), JPEG));
        requisicaoAutenticada(sessao(c.orientador()), get(LAMINAS + "/{id}/fotos/{foto}/arquivo", lamina, foto.path("id").asText()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control", "max-age=2592000, private, immutable"));

        requisicaoAutenticada(sessao(c.membro()), get(LAMINAS + "/{id}/fotos", lamina)).andExpect(status().isForbidden());
        requisicaoAutenticada(sessao(c.fora()), get(LAMINAS + "/{id}/fotos", lamina)).andExpect(status().isNotFound());
        enviarFoto(sessao(c.orientador()), lamina, JPEG, null).andExpect(status().isForbidden());
    }

    @Test
    void arquivoQueNaoEImagemERecusadoEArmazenamentoForaDoArNaoGravaNada() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);

        enviarFoto(sessao(dono), lamina, "não é imagem".getBytes(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Escolha uma imagem JPG, PNG ou WebP de até 10 MB."));

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("sem conexão"));
        enviarFoto(sessao(dono), lamina, JPEG, null).andExpect(status().isServiceUnavailable());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM foto_lamina WHERE lamina_id = ?", Integer.class, lamina))
                .isZero();
    }

    @Test
    void apagarFotoTiraDoBancoEDepoisDoArmazenamento() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        JsonNode foto = json(enviarFoto(sessao(dono), lamina, JPEG, null).andReturn());
        String chave = jdbcTemplate.queryForObject("SELECT chave FROM foto_lamina WHERE id = ?", String.class,
                UUID.fromString(foto.path("id").asText()));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));

        requisicaoAutenticada(sessao(dono), delete(LAMINAS + "/{id}/fotos/{foto}", lamina, foto.path("id").asText()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<DeleteObjectRequest> apagado = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(apagado.capture());
        assertThat(apagado.getValue().key()).isEqualTo(chave);
        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/fotos", lamina)).andExpect(jsonPath("$.length()").value(0));
        requisicaoAutenticada(sessao(dono), delete(LAMINAS + "/{id}/fotos/{foto}", lamina, foto.path("id").asText()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- comentários

    @Test
    void orientadorComentaNaLaminaDoAlunoESoOAutorApaga() throws Exception {
        Cenario c = montarCenario();
        UUID lamina = criarLamina(c.pesquisador(), c.laboratorio());

        JsonNode doOrientador = json(comentar(sessao(c.orientador()), lamina, "  Confira a anáfase da linha 3.  ")
                .andExpect(status().isCreated()).andReturn());
        assertThat(doOrientador.path("texto").asText()).isEqualTo("Confira a anáfase da linha 3.");
        assertThat(doOrientador.path("autorNome").asText()).isEqualTo(c.orientador().getNome());
        comentar(sessao(c.pesquisador()), lamina, "Conferi, era uma ponte.").andExpect(status().isCreated());

        requisicaoAutenticada(sessao(c.pesquisador()), get(LAMINAS + "/{id}/comentarios", lamina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].texto").value("Confira a anáfase da linha 3."))
                .andExpect(jsonPath("$[1].autorUsuarioId").value(c.pesquisador().getId()));

        String idOrientador = doOrientador.path("id").asText();
        requisicaoAutenticada(sessao(c.pesquisador()), delete(LAMINAS + "/{id}/comentarios/{c}", lamina, idOrientador))
                .andExpect(status().isForbidden());
        requisicaoAutenticada(sessao(c.orientador()), delete(LAMINAS + "/{id}/comentarios/{c}", lamina, idOrientador))
                .andExpect(status().isNoContent());
        requisicaoAutenticada(sessao(c.pesquisador()), get(LAMINAS + "/{id}/comentarios", lamina))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void quemNaoVeALaminaNaoComentaETextoVazioERecusado() throws Exception {
        Cenario c = montarCenario();
        UUID lamina = criarLamina(c.pesquisador(), c.laboratorio());

        comentar(sessao(c.membro()), lamina, "Oi").andExpect(status().isForbidden());
        comentar(sessao(c.fora()), lamina, "Oi").andExpect(status().isNotFound());
        comentar(sessao(c.exMembro()), lamina, "Oi").andExpect(status().isNotFound());
        comentar(sessao(c.pesquisador()), lamina, "   ")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Escreva o comentário."));
        comentar(sessao(c.pesquisador()), lamina, "x".repeat(2001)).andExpect(status().isUnprocessableEntity());
        requisicaoAutenticada(sessao(c.membro()), get(LAMINAS + "/{id}/comentarios", lamina)).andExpect(status().isForbidden());
    }

    @Test
    void laboratorioDesativadoMostraMasNaoAceitaComentarioNemFoto() throws Exception {
        Cenario c = montarCenario();
        UUID lamina = criarLamina(c.pesquisador(), c.laboratorio());
        comentar(sessao(c.orientador()), lamina, "Antes de desativar").andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE laboratorio SET estado = 'DESATIVADO', desativado_em = now(), desativado_por_usuario_id = ? WHERE id = ?", c.adminGlobal().getId(), c.laboratorio());

        requisicaoAutenticada(sessao(c.pesquisador()), get(LAMINAS + "/{id}/comentarios", lamina))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        comentar(sessao(c.orientador()), lamina, "Depois").andExpect(status().isForbidden());
        enviarFoto(sessao(c.pesquisador()), lamina, JPEG, null).andExpect(status().isForbidden());
    }

    private ResultActions enviarFoto(Cookie sessao, UUID lamina, byte[] conteudo, String legenda) throws Exception {
        var requisicao = multipart(HttpMethod.POST, LAMINAS + "/{id}/fotos", lamina)
                .file(new MockMultipartFile("arquivo", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, conteudo));
        if (legenda != null) {
            requisicao.param("legenda", legenda);
        }
        return requisicaoAutenticada(sessao, requisicao);
    }

    private ResultActions comentar(Cookie sessao, UUID lamina, String texto) throws Exception {
        return requisicaoAutenticada(sessao, post(LAMINAS + "/{id}/comentarios", lamina)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("texto", texto))));
    }
}
