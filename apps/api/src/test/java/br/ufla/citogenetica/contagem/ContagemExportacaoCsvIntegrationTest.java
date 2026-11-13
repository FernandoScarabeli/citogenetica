package br.ufla.citogenetica.contagem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import com.fasterxml.jackson.databind.JsonNode;
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

class ContagemExportacaoCsvIntegrationTest extends AbstractContagemIntegrationTest {

    private static final String BOM = "﻿";
    private static final String CAB_RESUMO = "\"Lâmina\";\"Espécie\";\"Tratamento\";\"Protocolo\";\"Dono\";\"Fase\";"
            + "\"Normais\";\"Anormais\";\"Total\";\"% anormais\";\"Tipos de anormalidade\";\"Índice mitótico (%)\";"
            + "\"Observações\"\r\n";
    private static final String CAB_REGISTROS = "\"Lâmina\";\"Espécie\";\"Tratamento\";\"Protocolo\";\"Dono\";\"Data\";"
            + "\"Hora\";\"Fase\";\"Situação\";\"Tipos de anormalidade\";\"Quantidade\";\"Hora do aparelho\";"
            + "\"Registro\"\r\n";

    // ---------------------------------------------------------------- formato exato

    @Test
    void conteudoExatoDeUmaLaminaByteAByte() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        jdbcTemplate.update("UPDATE lamina SET nome = 'L1', especie = 'Allium cepa', tratamento = 'Controle', "
                + "observacoes = 'Obs' WHERE id = ?", lamina);
        UUID interfase = fase("mitose", "Interfase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        UUID r3 = UUID.randomUUID();
        registrar(dono, lamina, r1, interfase, "NORMAL", List.of(), 3, null).andExpect(status().isCreated());
        registrar(dono, lamina, r2, anafase, "NORMAL", List.of(), 2, null).andExpect(status().isCreated());
        registrar(dono, lamina, r3, anafase, "ANORMAL", List.of(ponte), 1, "2026-11-12T10:00:00Z")
                .andExpect(status().isCreated());
        // Instantes fixos (UTC-3 em novembro): 10:00:05, 10:00:06 e 23:59:59 do dia 12.
        jdbcTemplate.update("UPDATE registro_contagem SET registrado_em = '2026-11-12T13:00:05Z' WHERE id = ?", r1);
        jdbcTemplate.update("UPDATE registro_contagem SET registrado_em = '2026-11-12T13:00:06Z' WHERE id = ?", r2);
        jdbcTemplate.update("UPDATE registro_contagem SET registrado_em = '2026-11-13T02:59:59Z' WHERE id = ?", r3);
        String nomeDono = dono.getNome();

        MvcResult resumo = requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/resumo.csv", lamina))
                .andExpect(status().isOk()).andReturn();
        String base = "\"L1\";\"Allium cepa\";\"Controle\";\"Mitose\";\"" + nomeDono + "\";";
        String esperadoResumo = BOM + CAB_RESUMO
                + base + "\"Interfase\";3;0;3;0,00;\"\";50,00;\"Obs\"\r\n"
                + base + "\"Prófase\";0;0;0;0,00;\"\";50,00;\"Obs\"\r\n"
                + base + "\"Metáfase\";0;0;0;0,00;\"\";50,00;\"Obs\"\r\n"
                + base + "\"Anáfase\";2;1;3;33,33;\"Ponte: 1 (33,33%)\";50,00;\"Obs\"\r\n"
                + base + "\"Telófase\";0;0;0;0,00;\"\";50,00;\"Obs\"\r\n";
        assertThat(resumo.getResponse().getContentAsByteArray()).isEqualTo(esperadoResumo.getBytes(StandardCharsets.UTF_8));
        assertThat(resumo.getResponse().getContentAsByteArray()).startsWith(0xEF, 0xBB, 0xBF);
        assertThat(resumo.getResponse().getContentType()).isEqualTo("text/csv;charset=UTF-8");

        MvcResult registros = requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/registros.csv", lamina))
                .andExpect(status().isOk()).andReturn();
        String esperadoRegistros = BOM + CAB_REGISTROS
                + base + "\"12/11/2026\";\"10:00:05\";\"Interfase\";\"Normal\";\"\";3;;\"" + r1 + "\"\r\n"
                + base + "\"12/11/2026\";\"10:00:06\";\"Anáfase\";\"Normal\";\"\";2;;\"" + r2 + "\"\r\n"
                + base + "\"12/11/2026\";\"23:59:59\";\"Anáfase\";\"Anormal\";\"Ponte\";1;\"12/11/2026 07:00:00\";\""
                + r3 + "\"\r\n";
        assertThat(registros.getResponse().getContentAsByteArray())
                .isEqualTo(esperadoRegistros.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void cabecalhosEContentDispositionDasQuatroRotas() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        jdbcTemplate.update("UPDATE lamina SET nome = 'Ação: 1/2' WHERE id = ?", lamina);

        assertThat(arquivo(dono, LAMINAS + "/" + lamina + "/resumo.csv")).isEqualTo("lamina-Ação 1 2-resumo.csv");
        assertThat(arquivo(dono, LAMINAS + "/" + lamina + "/registros.csv")).isEqualTo("lamina-Ação 1 2-registros.csv");
        assertThat(arquivo(dono, LAMINAS + "/resumo.csv")).matches("laminas-resumo-\\d{4}-\\d{2}-\\d{2}\\.csv");
        assertThat(arquivo(dono, LAMINAS + "/registros.csv")).matches("laminas-registros-\\d{4}-\\d{2}-\\d{2}\\.csv");
        MvcResult r = requisicaoAutenticada(sessao(dono), get(LAMINAS + "/resumo.csv")).andReturn();
        assertThat(r.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        assertThat(r.getResponse().getContentType()).startsWith("text/csv");
    }

    @Test
    void textoPerigosoFicaEntreAspasEFormulaNeutralizada() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        jdbcTemplate.update("UPDATE lamina SET nome = ?, especie = '=1+1', tratamento = '+cmd', "
                + "observacoes = ? WHERE id = ?", "a;b \"c\"\nd", "@soma\nsegunda \"linha\"", lamina);
        UUID anafase = fase("mitose", "Anáfase");
        UUID personalizado = tipoPersonalizado(anafase, dono, null, "-perigo; \"x\"");
        registrar(dono, lamina, UUID.randomUUID(), anafase, "ANORMAL", List.of(personalizado), 1, null)
                .andExpect(status().isCreated());

        String resumo = corpo(dono, LAMINAS + "/" + lamina + "/resumo.csv");
        String registros = corpo(dono, LAMINAS + "/" + lamina + "/registros.csv");

        List<List<String>> linhasResumo = analisar(resumo);
        assertThat(linhasResumo).hasSize(6);
        List<String> anafaseLinha = linhasResumo.get(4);
        assertThat(anafaseLinha.get(0)).isEqualTo("a;b \"c\"\nd");
        assertThat(anafaseLinha.get(1)).isEqualTo("'=1+1");
        assertThat(anafaseLinha.get(2)).isEqualTo("'+cmd");
        assertThat(anafaseLinha.get(10)).isEqualTo("'-perigo; \"x\": 1 (100,00%)");
        assertThat(anafaseLinha.get(12)).isEqualTo("'@soma\nsegunda \"linha\"");
        assertThat(resumo).contains("\"a;b \"\"c\"\"\nd\"");
        List<String> registro = analisar(registros).get(1);
        assertThat(registro.get(0)).isEqualTo("a;b \"c\"\nd");
        assertThat(registro.get(9)).isEqualTo("'-perigo; \"x\"");
        for (List<String> linha : analisar(registros)) {
            assertThat(linha).hasSize(13);
        }
    }

    @Test
    void meioseSemIndiceMitoticoEFasesZeradas() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null, "meiose");
        registrar(dono, lamina, UUID.randomUUID(), fase("meiose", "Paquíteno"), "NORMAL", List.of(), 5, null)
                .andExpect(status().isCreated());

        List<List<String>> linhas = analisar(corpo(dono, LAMINAS + "/" + lamina + "/resumo.csv"));

        assertThat(linhas).hasSize(1 + 11);
        for (List<String> linha : linhas.subList(1, linhas.size())) {
            assertThat(linha.get(3)).isEqualTo("Meiose");
            assertThat(linha.get(11)).isEmpty();
        }
        assertThat(linhas.get(2).get(5)).isEqualTo("Paquíteno");
        assertThat(linhas.get(2).get(8)).isEqualTo("5");
        // Sem aspas e sem nada entre os dois separadores finais do índice mitótico.
        assertThat(corpo(dono, LAMINAS + "/" + lamina + "/resumo.csv")).contains(";\"\";;\"\"\r\n");
    }

    @Test
    void laminaSemRegistros() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);

        assertThat(corpo(dono, LAMINAS + "/" + lamina + "/registros.csv")).isEqualTo(BOM + CAB_REGISTROS);
        List<List<String>> resumo = analisar(corpo(dono, LAMINAS + "/" + lamina + "/resumo.csv"));
        assertThat(resumo).hasSize(6);
        for (List<String> linha : resumo.subList(1, 6)) {
            assertThat(linha.subList(6, 12)).containsExactly("0", "0", "0", "0,00", "", "0,00");
        }
        assertThat(corpo(dono, LAMINAS + "/registros.csv")).isEqualTo(BOM + CAB_REGISTROS)
                .doesNotContain(lamina.toString());
        assertThat(analisar(corpo(dono, LAMINAS + "/resumo.csv"))).hasSize(6);
    }

    // ---------------------------------------------------------------- paridade com /resumo e com o JSON

    @Test
    void numerosDoResumoCsvSaoOsMesmosDoGetResumoEmUmaEEmVarias() throws Exception {
        Usuario dono = criarUsuario();
        UUID mitose = criarLamina(dono, null);
        UUID meiose = criarLamina(dono, null, "meiose");
        UUID vazia = criarLamina(dono, null);
        UUID anafase = fase("mitose", "Anáfase");
        UUID metafase = fase("mitose", "Metáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID fragmento = tipoFixo("mitose", "Anáfase", "Cromossomos atrasados");
        UUID zeta = tipoPersonalizado(anafase, dono, null, "Zeta");
        registrar(dono, mitose, UUID.randomUUID(), fase("mitose", "Interfase"), "NORMAL", List.of(), 7, null)
                .andExpect(status().isCreated());
        registrar(dono, mitose, UUID.randomUUID(), anafase, "ANORMAL", List.of(ponte, zeta), 3, null)
                .andExpect(status().isCreated());
        registrar(dono, mitose, UUID.randomUUID(), anafase, "ANORMAL", List.of(fragmento), 2, null)
                .andExpect(status().isCreated());
        registrar(dono, mitose, UUID.randomUUID(), anafase, "NORMAL", List.of(), 4, null)
                .andExpect(status().isCreated());
        UUID editado = UUID.randomUUID();
        UUID desfeito = UUID.randomUUID();
        registrar(dono, mitose, editado, metafase, "NORMAL", List.of(), 1, null).andExpect(status().isCreated());
        registrar(dono, mitose, desfeito, metafase, "NORMAL", List.of(), 9, null).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE registro_contagem SET quantidade = 6, version = version + 1 WHERE id = ?", editado);
        jdbcTemplate.update("UPDATE registro_contagem SET excluido_em = NOW() WHERE id = ?", desfeito);
        registrar(dono, meiose, UUID.randomUUID(), fase("meiose", "Tétrade"), "ANORMAL",
                List.of(tipoFixo("meiose", "Tétrade", "Micronúcleo")), 2, null).andExpect(status().isCreated());

        List<List<String>> varias = analisar(corpo(dono, LAMINAS + "/resumo.csv"));
        assertThat(varias).hasSize(1 + 5 + 11 + 5);
        for (UUID lamina : List.of(mitose, meiose, vazia)) {
            JsonNode oficial = json(requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/resumo", lamina))
                    .andExpect(status().isOk()).andReturn());
            List<List<String>> uma = analisar(corpo(dono, LAMINAS + "/" + lamina + "/resumo.csv"));
            List<List<String>> dessaLamina = varias.stream()
                    .filter(linha -> linha.get(0).equals(uma.get(1).get(0)) && !linha.get(0).equals("Lâmina"))
                    .toList();
            assertThat(dessaLamina).isEqualTo(uma.subList(1, uma.size()));
            assertThat(uma).hasSize(1 + oficial.path("fases").size());
            boolean ehMitose = !lamina.equals(meiose);
            for (int i = 0; i < oficial.path("fases").size(); i++) {
                JsonNode fase = oficial.path("fases").get(i);
                List<String> linha = uma.get(i + 1);
                assertThat(linha.get(5)).isEqualTo(fase.path("nome").asText());
                assertThat(linha.get(6)).isEqualTo(fase.path("normais").asText());
                assertThat(linha.get(7)).isEqualTo(fase.path("anormais").asText());
                assertThat(linha.get(8)).isEqualTo(fase.path("total").asText());
                assertThat(linha.get(9)).isEqualTo(pct(fase.path("percentualAnormais").asDouble()));
                List<String> tipos = new ArrayList<>();
                fase.path("tipos").forEach(t -> tipos.add(t.path("nome").asText() + ": " + t.path("contagem").asText()
                        + " (" + pct(t.path("percentual").asDouble()) + "%)"));
                assertThat(linha.get(10)).isEqualTo(String.join(", ", tipos));
                assertThat(linha.get(11)).isEqualTo(ehMitose ? pct(oficial.path("indiceMitotico").asDouble()) : "");
            }
        }
        // Sanidade dos números da lâmina de mitose: 7 + (3+2+4) + 6 = 22 células, 5 anormais, 15 em divisão.
        JsonNode oficial = json(requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/resumo", mitose))
                .andReturn());
        assertThat(oficial.path("total").asInt()).isEqualTo(22);
        assertThat(analisar(corpo(dono, LAMINAS + "/" + mitose + "/resumo.csv")).get(4).get(10))
                .isEqualTo("Cromossomos atrasados: 2 (22,22%), Ponte: 3 (33,33%), Zeta: 3 (33,33%)");
    }

    @Test
    void registrosCsvSegueOJsonDeExportacaoEMesmaOrdem() throws Exception {
        Usuario dono = criarUsuario();
        UUID primeira = criarLamina(dono, null);
        UUID segunda = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID alfa = tipoPersonalizado(anafase, dono, null, "Alfa");
        UUID excluido = UUID.randomUUID();
        UUID desfeito = UUID.randomUUID();
        registrar(dono, primeira, UUID.randomUUID(), profase, "NORMAL", List.of(), 1, null).andExpect(status().isCreated());
        registrar(dono, primeira, UUID.randomUUID(), anafase, "ANORMAL", List.of(alfa, ponte), 2, "2026-01-05T03:00:00Z")
                .andExpect(status().isCreated());
        UUID editado = UUID.randomUUID();
        registrar(dono, segunda, editado, profase, "NORMAL", List.of(), 3, null).andExpect(status().isCreated());
        registrar(dono, segunda, excluido, profase, "NORMAL", List.of(), 1, null).andExpect(status().isCreated());
        registrar(dono, segunda, desfeito, profase, "NORMAL", List.of(), 1, null).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE registro_contagem SET excluido_em = NOW() WHERE id = ?", excluido);
        jdbcTemplate.update("UPDATE registro_contagem SET quantidade = 8, version = version + 1 WHERE id = ?",
                editado);
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("id", UUID.randomUUID().toString());
        pedido.put("faseId", profase.toString());
        pedido.put("status", "NORMAL");
        pedido.put("tipoIds", List.of());
        pedido.put("quantidade", 1);
        requisicaoAutenticada(sessao(dono), post(LAMINAS + "/{id}/registros/desfazer", segunda)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(pedido)))
                .andExpect(status().isOk());

        JsonNode exportacao = json(requisicaoAutenticada(sessao(dono), get(LAMINAS + "/exportacao.json"))
                .andExpect(status().isOk()).andReturn());
        List<List<String>> csv = analisar(corpo(dono, LAMINAS + "/registros.csv"));

        List<String> idsCsv = csv.stream().skip(1).map(linha -> linha.get(12)).toList();
        List<String> idsJson = new ArrayList<>();
        List<String> tiposJson = new ArrayList<>();
        exportacao.path("laminas").forEach(lamina -> lamina.path("registros").forEach(registro -> {
            idsJson.add(registro.path("id").asText());
            List<String> nomes = new ArrayList<>();
            registro.path("tipos").forEach(t -> nomes.add(t.asText()));
            tiposJson.add(String.join(", ", nomes));
        }));
        assertThat(idsCsv).isEqualTo(idsJson).doesNotContain(excluido.toString(), desfeito.toString());
        assertThat(csv.stream().skip(1).map(linha -> linha.get(9)).toList()).isEqualTo(tiposJson);
        assertThat(tiposJson).contains("Ponte, Alfa");
        assertThat(csv.stream().skip(1).map(linha -> linha.get(11)).toList()).contains("05/01/2026 00:00:00");
        assertThat(csv.stream().skip(1).map(linha -> linha.get(10)).toList()).contains("8");
        // O arquivo de uma lâmina é igual ao trecho dela no arquivo de várias.
        List<List<String>> soSegunda = analisar(corpo(dono, LAMINAS + "/" + segunda + "/registros.csv"));
        assertThat(csv).containsAll(soSegunda.subList(1, soSegunda.size()));
    }

    @Test
    void muitasLaminasPassamDoTamanhoDoBloco() throws Exception {
        Usuario dono = criarUsuario();
        jdbcTemplate.update("""
                INSERT INTO lamina (id, version, created_at, last_modified_at, dono_usuario_id, laboratorio_id,
                                    protocolo_versao_id, nome, especie, tratamento, observacoes, meta, excluida_em)
                SELECT gen_random_uuid(), 0, NOW() - make_interval(secs => n), NOW(), ?, NULL, ?,
                       'Lâmina em massa ' || n, '', '', '', NULL, NULL
                  FROM generate_series(1, 1201) AS n
                """, dono.getId(), versaoPublicada("mitose"));
        jdbcTemplate.update("""
                INSERT INTO registro_contagem (id, version, created_at, last_modified_at, lamina_id, fase_id, status,
                                               quantidade, registrado_em, hora_aparelho, fuso_aparelho,
                                               hash_conteudo_criacao, excluido_em)
                SELECT gen_random_uuid(), 0, NOW(), NOW(), l.id, ?, 'NORMAL', 2, NOW(), NULL, NULL, repeat('a', 64), NULL
                  FROM lamina l WHERE l.dono_usuario_id = ?
                """, fase("mitose", "Prófase"), dono.getId());

        List<List<String>> resumo = analisar(corpo(dono, LAMINAS + "/resumo.csv"));
        List<List<String>> registros = analisar(corpo(dono, LAMINAS + "/registros.csv"));

        assertThat(resumo).hasSize(1 + 1201 * 5);
        assertThat(registros).hasSize(1 + 1201);
        assertThat(resumo.get(1).get(0)).isEqualTo("Lâmina em massa 1");
        assertThat(resumo.stream().skip(1).filter(l -> l.get(5).equals("Prófase")).map(l -> l.get(8)).distinct().toList())
                .containsExactly("2");
        assertThat(resumo.stream().skip(1).filter(l -> l.get(5).equals("Interfase")).map(l -> l.get(11)).distinct().toList())
                .containsExactly("100,00");
    }

    // ---------------------------------------------------------------- acesso e rotas

    @Test
    void matrizDeAcessoIgualADoJsonNasQuatroRotas() throws Exception {
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
            for (String rota : List.of("resumo.csv", "registros.csv", "exportacao.json")) {
                requisicaoAutenticada(sessao(entrada.getKey()), get(LAMINAS + "/{id}/" + rota, lamina))
                        .andExpect(status().is(entrada.getValue()));
            }
        }
        // Avulsa: só o dono; outra pessoa recebe a mesma mensagem 404 do JSON.
        Usuario dono = criarUsuario();
        Usuario outro = criarUsuario();
        UUID avulsa = criarLamina(dono, null);
        MvcResult doOutro = requisicaoAutenticada(sessao(outro), get(LAMINAS + "/{id}/resumo.csv", avulsa))
                .andExpect(status().isNotFound()).andReturn();
        MvcResult doJson = requisicaoAutenticada(sessao(outro), get(LAMINAS + "/{id}/exportacao.json", avulsa))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(doOutro.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(corpoSemHorario(doOutro).get("message")).isEqualTo(corpoSemHorario(doJson).get("message"));
        requisicaoAutenticada(sessao(outro), get(LAMINAS + "/{id}/registros.csv", avulsa)).andExpect(status().isNotFound());
    }

    @Test
    void variasTemOMesmoConjuntoEOrdemDoJsonEMesmosCodigosDeErro() throws Exception {
        Cenario cenario = montarCenario();
        criarLamina(cenario.admin(), cenario.laboratorio());
        criarLamina(cenario.pesquisador(), cenario.laboratorio());
        criarLamina(cenario.membro(), cenario.laboratorio());
        criarLamina(cenario.pesquisador(), null);
        UUID excluida = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        jdbcTemplate.update("UPDATE lamina SET excluida_em = NOW() WHERE id = ?", excluida);

        String lab = "laboratorioId=" + cenario.laboratorio();
        List<String> consultas = List.of("", lab, lab + "&donoUsuarioId=" + cenario.pesquisador().getId(),
                lab + "&donoUsuarioId=" + cenario.membro().getId(), "donoUsuarioId=" + cenario.pesquisador().getId(),
                "laboratorioId=" + UUID.randomUUID(), "laboratorioId=invalido");
        List<Usuario> leitores = List.of(cenario.pesquisador(), cenario.orientador(), cenario.coorientador(),
                cenario.orientadorEncerrado(), cenario.admin(), cenario.membro(), cenario.exMembro(), cenario.fora(),
                cenario.adminGlobal());
        for (Usuario leitor : leitores) {
            for (String consulta : consultas) {
                String q = consulta.isEmpty() ? "" : "?" + consulta;
                MvcResult json = requisicaoAutenticada(sessao(leitor), get(LAMINAS + "/exportacao.json" + q)).andReturn();
                for (String rota : List.of("resumo.csv", "registros.csv")) {
                    MvcResult csv = requisicaoAutenticada(sessao(leitor), get(LAMINAS + "/" + rota + q)).andReturn();
                    assertThat(csv.getResponse().getStatus()).as("%s %s %s", leitor.getId(), rota, consulta)
                            .isEqualTo(json.getResponse().getStatus());
                    if (json.getResponse().getStatus() == 200) {
                        List<String> nomes = new ArrayList<>();
                        json(json).path("laminas").forEach(l -> nomes.add(l.path("nome").asText()));
                        List<List<String>> linhas = analisar(new String(csv.getResponse().getContentAsByteArray(),
                                StandardCharsets.UTF_8));
                        List<String> nomesCsv = linhas.stream().skip(1).map(l -> l.get(0)).distinct().toList();
                        if (rota.equals("resumo.csv")) {
                            assertThat(nomesCsv).as("%s %s", leitor.getId(), consulta).isEqualTo(nomes);
                        } else {
                            assertThat(nomes).containsAll(nomesCsv);
                        }
                    }
                }
            }
        }
    }

    @Test
    void semLoginUuidInvalidoEInexistente() throws Exception {
        Usuario dono = criarUsuario();
        for (String rota : List.of("resumo.csv", "registros.csv")) {
            mockMvc.perform(get(LAMINAS + "/" + rota)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(LAMINAS + "/{id}/" + rota, UUID.randomUUID())).andExpect(status().isUnauthorized());
            requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/" + rota, "nao-e-uuid"))
                    .andExpect(status().isBadRequest());
            requisicaoAutenticada(sessao(dono), get(LAMINAS + "/" + rota + "?laboratorioId=nao-e-uuid"))
                    .andExpect(status().isBadRequest());
            requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/" + rota, UUID.randomUUID()))
                    .andExpect(status().isNotFound());
            requisicaoAutenticada(sessao(dono), get(LAMINAS + "/" + rota + "?laboratorioId=" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void rotasDeVariasNaoCaemNoGetPorIdENemQuebramOResumoJson() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);

        for (String rota : List.of("resumo.csv", "registros.csv")) {
            MvcResult r = requisicaoAutenticada(sessao(dono), get(LAMINAS + "/" + rota)).andExpect(status().isOk()).andReturn();
            assertThat(r.getResponse().getContentType()).startsWith("text/csv");
        }
        JsonNode resumo = json(requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/resumo", lamina))
                .andExpect(status().isOk()).andReturn());
        assertThat(resumo.path("laminaId").asText()).isEqualTo(lamina.toString());
        assertThat(resumo.path("fases")).hasSize(5);
    }

    // ---------------------------------------------------------------- utilidades

    private static String pct(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private String arquivo(Usuario usuario, String caminho) throws Exception {
        MvcResult resultado = requisicaoAutenticada(sessao(usuario), get(caminho)).andExpect(status().isOk()).andReturn();
        return ContentDisposition.parse(resultado.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).getFilename();
    }

    private String corpo(Usuario usuario, String caminho) throws Exception {
        MvcResult resultado = requisicaoAutenticada(sessao(usuario), get(caminho)).andExpect(status().isOk()).andReturn();
        return new String(resultado.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /** Leitor de CSV (; e aspas dobradas) só para os testes; confere BOM, CRLF e devolve as células sem aspas. */
    private static List<List<String>> analisar(String csv) {
        assertThat(csv).startsWith(BOM).endsWith("\r\n");
        String texto = csv.substring(1);
        List<List<String>> linhas = new ArrayList<>();
        List<String> linha = new ArrayList<>();
        StringBuilder celula = new StringBuilder();
        boolean aspas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (aspas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    celula.append('"');
                    i++;
                } else if (c == '"') {
                    aspas = false;
                } else {
                    celula.append(c);
                }
            } else if (c == '"') {
                aspas = true;
            } else if (c == ';') {
                linha.add(celula.toString());
                celula.setLength(0);
            } else if (c == '\r' && texto.charAt(i + 1) == '\n') {
                linha.add(celula.toString());
                celula.setLength(0);
                linhas.add(linha);
                linha = new ArrayList<>();
                i++;
            } else {
                celula.append(c);
            }
        }
        return linhas;
    }

    private ResultActions registrar(Usuario usuario, UUID lamina, UUID id, UUID fase, String status, List<UUID> tipos,
                                    int quantidade, String horaAparelho) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", id.toString());
        corpo.put("faseId", fase.toString());
        corpo.put("status", status);
        corpo.put("tipoIds", tipos.stream().map(UUID::toString).toList());
        corpo.put("quantidade", quantidade);
        if (horaAparelho != null) {
            corpo.put("horaAparelho", horaAparelho);
            corpo.put("fusoAparelho", "America/Sao_Paulo");
        }
        return requisicaoAutenticada(sessao(usuario), post(LAMINAS + "/{id}/registros", lamina)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo)));
    }
}
