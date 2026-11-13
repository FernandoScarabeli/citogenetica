package br.ufla.citogenetica.contagem;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.ufla.citogenetica.contagem.internal.web.Percentual;
import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContagemExportacaoPdfIntegrationTest extends AbstractContagemIntegrationTest {

    // ---------------------------------------------------------------- conteúdo

    @Test
    void mitoseTemCabecalhoTabelaIndiceObservacoesEPagina() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        jdbcTemplate.update("UPDATE lamina SET nome = 'Ação café Íris ôvo', especie = 'Allium cepa', "
                + "tratamento = '', observacoes = E'Primeira linha\\nSegunda linha', meta = 50 WHERE id = ?", lamina);
        UUID interfase = fase("mitose", "Interfase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        registrar(dono, lamina, interfase, "NORMAL", List.of(), 3);
        registrar(dono, lamina, anafase, "NORMAL", List.of(), 2);
        registrar(dono, lamina, anafase, "ANORMAL", List.of(ponte), 1);

        MvcResult r = requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/relatorio.pdf", lamina))
                .andExpect(status().isOk()).andReturn();
        byte[] pdf = r.getResponse().getContentAsByteArray();
        String texto = texto(pdf);
        JsonNode oficial = json(requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/resumo", lamina))
                .andExpect(status().isOk()).andReturn());

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(r.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(ContentDisposition.parse(r.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).getFilename())
                .isEqualTo("lamina-Ação café Íris ôvo-relatorio.pdf");
        assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        assertThat(texto).contains("Registro de Ciclo Celular e Índice Mitótico")
                .contains("Lâmina: Ação café Íris ôvo").contains("Espécie: Allium cepa")
                .contains("Tratamento: —").contains("Responsável: " + dono.getNome())
                .contains("Protocolo: Mitose, versão 1").contains("Total de células: 6")
                .contains("Meta: 6 de 50 células").contains("Data da emissão: ")
                .contains("Índice mitótico: " + Percentual.formatar(oficial.path("indiceMitotico").asDouble()) + "%")
                .contains("Uma célula pode ter mais de um tipo de anormalidade")
                .contains("Observações").contains("Primeira linha").contains("Segunda linha")
                .contains("Página 1 de 1");
        for (JsonNode fase : oficial.path("fases")) {
            String linha = fase.path("nome").asText() + " " + fase.path("normais").asText() + " "
                    + fase.path("anormais").asText() + " " + fase.path("total").asText() + " "
                    + Percentual.formatar(fase.path("percentualAnormais").asDouble()) + "%";
            assertThat(texto).as(linha).contains(linha);
        }
        assertThat(texto).contains("Ponte: 1 (33,33%)").contains("Total 5 1 6 16,67%");
        assertThat(new PdfReader(pdf).getInfo().get("Title")).isEqualTo("Ação café Íris ôvo");
        assertThat(new PdfReader(pdf).getInfo().get("Producer")).isEqualTo("Citogenética");
    }

    @Test
    void meioseSemIndiceMitoticoESemObservacoes() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null, "meiose");
        registrar(dono, lamina, fase("meiose", "Paquíteno"), "NORMAL", List.of(), 5);

        String texto = texto(pdf(dono, lamina));

        assertThat(texto).contains("Registro de Análise Meiótica").contains("Protocolo: Meiose")
                .contains("Paquíteno 5 0 5 0,00%").doesNotContain("Índice mitótico").doesNotContain("Meta:")
                .doesNotContain("Observações");
    }

    @Test
    void laminaSemRegistrosTemTodasAsFasesZeradas() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);

        String texto = texto(pdf(dono, lamina));

        assertThat(texto).contains("Total de células: 0").contains("Interfase 0 0 0 0,00%")
                .contains("Telófase 0 0 0 0,00%").contains("Total 0 0 0 0,00%").contains("Índice mitótico: 0,00%");
    }

    @Test
    void nomeComEmojiCjkEFormulaGeraPdfSemErro() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        jdbcTemplate.update("UPDATE lamina SET nome = ?, especie = ?, observacoes = ? WHERE id = ?",
                "=SOMA(A1) 😀 日本語 ç", "+cmd 中文", "@obs 🧬\r\nlinha\tcom\u0007controle", lamina);
        UUID anafase = fase("mitose", "Anáfase");
        UUID personalizado = tipoPersonalizado(anafase, dono, null, "Tipo 😀 ñ");
        registrar(dono, lamina, anafase, "ANORMAL", List.of(personalizado), 1);

        MvcResult r = requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/relatorio.pdf", lamina))
                .andExpect(status().isOk()).andReturn();
        String texto = texto(r.getResponse().getContentAsByteArray());

        assertThat(texto).contains("Lâmina: =SOMA(A1) ? ??? ç")
                .contains("ç").contains("Tipo ? ñ: 1 (100,00%)").contains("@obs ?").contains("linha com controle");
    }

    // ---------------------------------------------------------------- várias páginas

    @Test
    void observacoesLongasEMuitosTiposGeramMaisDeUmaPaginaComCabecalhoRepetido() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        StringBuilder observacoes = new StringBuilder();
        for (int i = 1; i <= 120; i++) {
            observacoes.append("Observação número ").append(i).append('\n');
        }
        jdbcTemplate.update("UPDATE lamina SET observacoes = ? WHERE id = ?", observacoes.toString(), lamina);
        UUID anafase = fase("mitose", "Anáfase");
        for (int i = 1; i <= 25; i++) {
            UUID tipo = tipoPersonalizado(anafase, dono, null, "Tipo personalizado " + i);
            registrar(dono, lamina, anafase, "ANORMAL", List.of(tipo), 1);
        }

        byte[] pdf = pdf(dono, lamina);
        PdfReader leitor = new PdfReader(pdf);
        String texto = texto(pdf);

        assertThat(leitor.getNumberOfPages()).isGreaterThanOrEqualTo(2);
        int paginas = leitor.getNumberOfPages();
        for (int p = 1; p <= paginas; p++) {
            assertThat(texto).contains("Página " + p + " de " + paginas);
        }
        assertThat(texto).contains("Observação número 1").contains("Observação número 120")
                .contains("Tipo personalizado 25: 1 (4,00%)");
        // Cabeçalho da tabela repetido quando a tabela quebra de página.
        Matcher m = Pattern.compile("Tipos de anormalidade").matcher(texto);
        int cabecalhos = 0;
        while (m.find()) {
            cabecalhos++;
        }
        assertThat(cabecalhos).isGreaterThanOrEqualTo(1);
    }

    // ---------------------------------------------------------------- acesso e rotas

    @Test
    void matrizDeAcessoIgualADoCsv() throws Exception {
        Cenario cenario = montarCenario();
        UUID lamina = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        Map<Usuario, Integer> esperado = new LinkedHashMap<>();
        esperado.put(cenario.pesquisador(), 200);
        esperado.put(cenario.orientador(), 200);
        esperado.put(cenario.coorientador(), 200);
        esperado.put(cenario.admin(), 200);
        esperado.put(cenario.orientadorEncerrado(), 403);
        esperado.put(cenario.membro(), 403);
        esperado.put(cenario.exMembro(), 404);
        esperado.put(cenario.fora(), 404);
        esperado.put(cenario.adminGlobal(), 404);

        for (var entrada : esperado.entrySet()) {
            MvcResult pdf = requisicaoAutenticada(sessao(entrada.getKey()), get(LAMINAS + "/{id}/relatorio.pdf", lamina))
                    .andReturn();
            MvcResult csv = requisicaoAutenticada(sessao(entrada.getKey()), get(LAMINAS + "/{id}/resumo.csv", lamina))
                    .andReturn();
            assertThat(pdf.getResponse().getStatus()).isEqualTo(entrada.getValue())
                    .isEqualTo(csv.getResponse().getStatus());
            if (entrada.getValue() != 200) {
                assertThat(pdf.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
                assertThat(corpoSemHorario(pdf).get("message")).isEqualTo(corpoSemHorario(csv).get("message"));
            }
        }
        Usuario dono = criarUsuario();
        UUID avulsa = criarLamina(dono, null);
        requisicaoAutenticada(sessao(criarUsuario()), get(LAMINAS + "/{id}/relatorio.pdf", avulsa))
                .andExpect(status().isNotFound());
    }

    @Test
    void semLoginUuidInvalidoEInexistente() throws Exception {
        Usuario dono = criarUsuario();
        mockMvc.perform(get(LAMINAS + "/{id}/relatorio.pdf", UUID.randomUUID())).andExpect(status().isUnauthorized());
        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/relatorio.pdf", "nao-e-uuid"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/relatorio.pdf", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        UUID excluida = criarLamina(dono, null);
        jdbcTemplate.update("UPDATE lamina SET excluida_em = NOW() WHERE id = ?", excluida);
        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/relatorio.pdf", excluida))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- utilidades

    private byte[] pdf(Usuario usuario, UUID lamina) throws Exception {
        return requisicaoAutenticada(sessao(usuario), get(LAMINAS + "/{id}/relatorio.pdf", lamina))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    /** Texto de todas as páginas, com qualquer espaço em branco reduzido a um espaço. */
    private static String texto(byte[] pdf) throws Exception {
        PdfReader leitor = new PdfReader(pdf);
        PdfTextExtractor extrator = new PdfTextExtractor(leitor);
        StringBuilder texto = new StringBuilder();
        for (int p = 1; p <= leitor.getNumberOfPages(); p++) {
            texto.append(extrator.getTextFromPage(p)).append(' ');
        }
        return texto.toString().replaceAll("\\s+", " ");
    }

    private void registrar(Usuario usuario, UUID lamina, UUID fase, String status, List<UUID> tipos, int quantidade)
            throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", UUID.randomUUID().toString());
        corpo.put("faseId", fase.toString());
        corpo.put("status", status);
        corpo.put("tipoIds", tipos.stream().map(UUID::toString).toList());
        corpo.put("quantidade", quantidade);
        ResultActions r = requisicaoAutenticada(sessao(usuario), post(LAMINAS + "/{id}/registros", lamina)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo)));
        r.andExpect(status().isCreated());
    }
}
