package br.ufla.citogenetica.contagem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContagemTotaisLaminasIntegrationTest extends AbstractContagemIntegrationTest {

    private static final String TOTAIS = LAMINAS + "/totais";

    @Test
    void cadaUmContaSoAsLaminasQuePodeVerEmCadaLaboratorio() throws Exception {
        Cenario cenario = montarCenario();
        UUID laboratorio = cenario.laboratorio();
        for (int i = 0; i < 3; i++) {
            criarLamina(cenario.pesquisador(), laboratorio);
        }
        for (int i = 0; i < 2; i++) {
            criarLamina(cenario.membro(), laboratorio);
        }
        UUID excluida = criarLamina(cenario.membro(), laboratorio);
        jdbcTemplate.update("UPDATE lamina SET excluida_em = now() WHERE id = ?", excluida);
        criarLamina(cenario.membro(), null);
        UUID vazio = laboratorioService.criar("Laboratório vazio " + UUID.randomUUID(), cenario.membro().getId()).id();
        UUID alheio = laboratorioService.criar("Laboratório alheio " + UUID.randomUUID(), cenario.fora().getId()).id();
        criarLamina(cenario.fora(), alheio);

        assertThat(totais(cenario.membro(), laboratorio, vazio, alheio))
                .isEqualTo(Map.of(laboratorio.toString(), 2L, vazio.toString(), 0L));
        assertThat(totais(cenario.admin(), laboratorio)).isEqualTo(Map.of(laboratorio.toString(), 5L));
        assertThat(totais(cenario.orientador(), laboratorio)).isEqualTo(Map.of(laboratorio.toString(), 3L));
        assertThat(totais(cenario.orientadorEncerrado(), laboratorio)).isEqualTo(Map.of(laboratorio.toString(), 0L));
        assertThat(totais(cenario.exMembro(), laboratorio)).isEmpty();
        assertThat(totais(cenario.adminGlobal(), laboratorio)).isEmpty();
    }

    @Test
    void laboratorioRepetidoApareceUmaVez() throws Exception {
        Cenario cenario = montarCenario();
        criarLamina(cenario.membro(), cenario.laboratorio());

        JsonNode corpo = json(requisicaoAutenticada(sessao(cenario.membro()), get(TOTAIS)
                .param("laboratorioId", cenario.laboratorio().toString(), cenario.laboratorio().toString()))
                .andExpect(status().isOk()).andReturn());

        assertThat(corpo).hasSize(1);
        assertThat(corpo.get(0).path("total").asLong()).isEqualTo(1);
    }

    @Test
    void pedidoInvalidoEh400ESemLoginEh401() throws Exception {
        Cookie sessao = sessao(criarUsuario());
        requisicaoAutenticada(sessao, get(TOTAIS)).andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao, get(TOTAIS).param("laboratorioId", "nao-eh-uuid"))
                .andExpect(status().isBadRequest());
        String[] demais = new String[101];
        for (int i = 0; i < demais.length; i++) {
            demais[i] = UUID.randomUUID().toString();
        }
        requisicaoAutenticada(sessao, get(TOTAIS).param("laboratorioId", demais)).andExpect(status().isBadRequest());
        comCsrfSemLogin(get(TOTAIS).param("laboratorioId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    private Map<String, Long> totais(Usuario leitor, UUID... laboratorios) throws Exception {
        MockHttpServletRequestBuilder pedido = get(TOTAIS);
        for (UUID laboratorio : laboratorios) {
            pedido.param("laboratorioId", laboratorio.toString());
        }
        JsonNode corpo = json(requisicaoAutenticada(sessao(leitor), pedido).andExpect(status().isOk()).andReturn());
        Map<String, Long> totais = new HashMap<>();
        corpo.forEach(item -> totais.put(item.path("laboratorioId").asText(), item.path("total").asLong()));
        return totais;
    }
}
