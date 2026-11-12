package br.ufla.citogenetica.contagem;

import java.time.Instant;
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

class ContagemExportacaoIntegrationTest extends AbstractContagemIntegrationTest {

    private static final String EXPORTACAO = LAMINAS + "/exportacao.json";

    // ---------------------------------------------------------------- formato

    @Test
    void envelopeECamposExatosDeUmaLamina() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        jdbcTemplate.update("UPDATE lamina SET especie = 'Allium cepa', tratamento = 'Controle', "
                + "observacoes = 'Obs', meta = 1000, version = 3 WHERE id = ?", lamina);
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID registro = UUID.randomUUID();
        registrar(dono, lamina, registro, anafase, "ANORMAL", List.of(ponte), 2, "2026-11-12T10:00:00Z", "America/Sao_Paulo")
                .andExpect(status().isCreated());

        MvcResult resultado = requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/exportacao.json", lamina))
                .andExpect(status().isOk()).andReturn();
        JsonNode corpo = json(resultado);

        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        assertThat(fieldNames(corpo)).containsExactly("formato", "versao", "exportadoEm", "laminas");
        assertThat(corpo.path("formato").asText()).isEqualTo("citogenetica-laminas");
        assertThat(corpo.path("versao").asInt()).isEqualTo(1);
        assertThat(Instant.parse(corpo.path("exportadoEm").asText())).isNotNull();
        assertThat(corpo.path("laminas")).hasSize(1);

        JsonNode item = corpo.path("laminas").get(0);
        assertThat(fieldNames(item)).containsExactlyInAnyOrder("id", "nome", "especie", "tratamento", "observacoes",
                "meta", "criadaEm", "atualizadaEm", "versao", "dono", "laboratorioId", "protocolo", "registros");
        assertThat(item.path("id").asText()).isEqualTo(lamina.toString());
        assertThat(item.path("nome").asText()).startsWith("Lâmina ");
        assertThat(item.path("especie").asText()).isEqualTo("Allium cepa");
        assertThat(item.path("tratamento").asText()).isEqualTo("Controle");
        assertThat(item.path("observacoes").asText()).isEqualTo("Obs");
        assertThat(item.path("meta").asInt()).isEqualTo(1000);
        assertThat(item.path("versao").asLong()).isEqualTo(3);
        assertThat(Instant.parse(item.path("criadaEm").asText())).isNotNull();
        assertThat(Instant.parse(item.path("atualizadaEm").asText())).isNotNull();
        assertThat(item.path("laboratorioId").isNull()).isTrue();
        assertThat(fieldNames(item.path("dono"))).containsExactly("id", "nome");
        assertThat(item.path("dono").path("id").asLong()).isEqualTo(dono.getId());
        assertThat(item.path("dono").path("nome").asText()).isEqualTo(dono.getNome());
        assertThat(fieldNames(item.path("protocolo"))).containsExactly("codigo", "nome", "versao");
        assertThat(item.path("protocolo").path("codigo").asText()).isEqualTo("mitose");
        assertThat(item.path("protocolo").path("nome").asText()).isEqualTo("Mitose");
        assertThat(item.path("protocolo").path("versao").asInt()).isEqualTo(1);

        assertThat(item.path("registros")).hasSize(1);
        JsonNode reg = item.path("registros").get(0);
        assertThat(fieldNames(reg)).containsExactlyInAnyOrder("id", "fase", "status", "tipos", "quantidade",
                "registradoEm", "horaAparelho", "fusoAparelho", "versao");
        assertThat(reg.path("id").asText()).isEqualTo(registro.toString());
        assertThat(reg.path("fase").asText()).isEqualTo("Anáfase");
        assertThat(reg.path("status").asText()).isEqualTo("ANORMAL");
        assertThat(texts(reg.path("tipos"))).containsExactly("Ponte");
        assertThat(reg.path("quantidade").asInt()).isEqualTo(2);
        assertThat(Instant.parse(reg.path("registradoEm").asText())).isNotNull();
        assertThat(Instant.parse(reg.path("horaAparelho").asText())).isEqualTo(Instant.parse("2026-11-12T10:00:00Z"));
        assertThat(reg.path("fusoAparelho").asText()).isEqualTo("America/Sao_Paulo");
        assertThat(reg.path("versao").asLong()).isZero();
    }

    @Test
    void laminaSemRegistrosETemMetaNulaEhRegistroSemAparelho() throws Exception {
        Usuario dono = criarUsuario();
        UUID vazia = criarLamina(dono, null);

        JsonNode item = exportarUma(dono, vazia).path("laminas").get(0);

        assertThat(item.path("registros").isArray()).isTrue();
        assertThat(item.path("registros")).isEmpty();
        assertThat(item.path("meta").isNull()).isTrue();

        UUID comRegistro = criarLamina(dono, null);
        registrar(dono, comRegistro, UUID.randomUUID(), fase("mitose", "Prófase"), "NORMAL", List.of(), 1, null, null)
                .andExpect(status().isCreated());
        JsonNode registro = exportarUma(dono, comRegistro).path("laminas").get(0).path("registros").get(0);
        assertThat(registro.path("horaAparelho").isNull()).isTrue();
        assertThat(registro.path("fusoAparelho").isNull()).isTrue();
        assertThat(registro.path("tipos").isArray()).isTrue();
        assertThat(registro.path("tipos")).isEmpty();
    }

    @Test
    void registrosExcluidosEDesfeitosNaoSaemEEditadoVemComValoresAtuais() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID metafase = fase("mitose", "Metáfase");
        UUID telofase = fase("mitose", "Telófase");
        UUID excluido = UUID.randomUUID();
        UUID editado = UUID.randomUUID();
        UUID desfeito = UUID.randomUUID();
        UUID mantido = UUID.randomUUID();
        registrar(dono, lamina, excluido, profase, "NORMAL", List.of(), 1, null, null).andExpect(status().isCreated());
        registrar(dono, lamina, editado, profase, "NORMAL", List.of(), 1, null, null).andExpect(status().isCreated());
        registrar(dono, lamina, desfeito, telofase, "NORMAL", List.of(), 1, null, null).andExpect(status().isCreated());
        registrar(dono, lamina, mantido, metafase, "NORMAL", List.of(), 4, null, null).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE registro_contagem SET excluido_em = NOW() WHERE id = ?", excluido);
        jdbcTemplate.update("UPDATE registro_contagem SET quantidade = 7, fase_id = ?, version = version + 1 "
                + "WHERE id = ?", metafase, editado);
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", UUID.randomUUID().toString());
        corpo.put("faseId", telofase.toString());
        corpo.put("status", "NORMAL");
        corpo.put("tipoIds", List.of());
        corpo.put("quantidade", 1);
        requisicaoAutenticada(sessao(dono), post(LAMINAS + "/{id}/registros/desfazer", lamina)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk());

        JsonNode registros = exportarUma(dono, lamina).path("laminas").get(0).path("registros");

        List<String> ids = texts(registros, "id");
        assertThat(ids).doesNotContain(excluido.toString(), desfeito.toString());
        assertThat(ids).containsExactlyInAnyOrder(editado.toString(), mantido.toString());
        for (JsonNode registro : registros) {
            if (registro.path("id").asText().equals(editado.toString())) {
                assertThat(registro.path("quantidade").asInt()).isEqualTo(7);
                assertThat(registro.path("fase").asText()).isEqualTo("Metáfase");
                assertThat(registro.path("versao").asLong()).isEqualTo(1);
            }
        }
    }

    @Test
    void registrosEmOrdemDeRegistroEDepoisId() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            registrar(dono, lamina, id, profase, "NORMAL", List.of(), 1, null, null).andExpect(status().isCreated());
        }
        // Dois com o mesmo instante (desempate por id) e um mais antigo que os demais.
        jdbcTemplate.update("UPDATE registro_contagem SET registrado_em = '2026-01-01T00:00:00Z' WHERE id IN (?, ?)",
                ids.get(0), ids.get(1));
        jdbcTemplate.update("UPDATE registro_contagem SET registrado_em = '2025-01-01T00:00:00Z' WHERE id = ?",
                ids.get(3));

        List<String> exportados = texts(exportarUma(dono, lamina).path("laminas").get(0).path("registros"), "id");

        List<String> empatados = new ArrayList<>(List.of(ids.get(0).toString(), ids.get(1).toString()));
        empatados.sort(null);
        assertThat(exportados).containsExactly(ids.get(3).toString(), empatados.get(0), empatados.get(1),
                ids.get(2).toString());
    }

    @Test
    void variosTiposSaemPelaOrdemDoProtocoloEDepoisPersonalizadosPorNome() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponteComFragmento = tipoFixo("mitose", "Anáfase", "Ponte com fragmento");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID zeta = tipoPersonalizado(anafase, dono, null, "Zeta");
        UUID alfa = tipoPersonalizado(anafase, dono, null, "Alfa");
        registrar(dono, lamina, UUID.randomUUID(), anafase, "ANORMAL", List.of(zeta, ponteComFragmento, alfa, ponte),
                1, null, null).andExpect(status().isCreated());

        JsonNode registro = exportarUma(dono, lamina).path("laminas").get(0).path("registros").get(0);

        assertThat(texts(registro.path("tipos"))).containsExactly("Ponte", "Ponte com fragmento", "Alfa", "Zeta");
    }

    // ---------------------------------------------------------------- acesso

    @Test
    void avulsaSoParaODonoEOutroRecebe404ComoNoGetDaLamina() throws Exception {
        Usuario dono = criarUsuario();
        Usuario outro = criarUsuario();
        UUID lamina = criarLamina(dono, null);

        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/exportacao.json", lamina)).andExpect(status().isOk());
        MvcResult doOutro = requisicaoAutenticada(sessao(outro), get(LAMINAS + "/{id}/exportacao.json", lamina))
                .andExpect(status().isNotFound()).andReturn();
        MvcResult leitura = requisicaoAutenticada(sessao(outro), get(LAMINAS + "/{id}", lamina))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(doOutro.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(corpoSemHorario(doOutro).get("message")).isEqualTo(corpoSemHorario(leitura).get("message"));

        assertThat(idsExportados(dono, "")).contains(lamina.toString());
        assertThat(idsExportados(outro, "")).doesNotContain(lamina.toString());
    }

    @Test
    void matrizDeAcessoDaLaminaDeLaboratorioIgualAoGetDaLamina() throws Exception {
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

        esperado.forEach((usuario, codigo) -> {
            try {
                requisicaoAutenticada(sessao(usuario), get(LAMINAS + "/{id}/exportacao.json", lamina))
                        .andExpect(status().is(codigo));
                requisicaoAutenticada(sessao(usuario), get(LAMINAS + "/{id}", lamina)).andExpect(status().is(codigo));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void laboratorioDesativadoContinuaExportavelParaQuemVe() throws Exception {
        Cenario cenario = montarCenario();
        UUID lamina = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        laboratorioService.desativar(cenario.laboratorio(), cenario.adminGlobal().getId());

        for (Usuario quem : List.of(cenario.pesquisador(), cenario.orientador(), cenario.admin())) {
            requisicaoAutenticada(sessao(quem), get(LAMINAS + "/{id}/exportacao.json", lamina))
                    .andExpect(status().isOk());
            assertThat(idsExportados(quem, "laboratorioId=" + cenario.laboratorio())).contains(lamina.toString());
        }
        requisicaoAutenticada(sessao(cenario.membro()), get(LAMINAS + "/{id}/exportacao.json", lamina))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportacaoDeVariasTemOMesmoConjuntoEAMesmaOrdemDaListagem() throws Exception {
        Cenario cenario = montarCenario();
        UUID deAdmin = criarLamina(cenario.admin(), cenario.laboratorio());
        UUID doPesquisador1 = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        UUID doPesquisador2 = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        UUID doMembro = criarLamina(cenario.membro(), cenario.laboratorio());
        criarLamina(cenario.pesquisador(), null);
        UUID excluida = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        jdbcTemplate.update("UPDATE lamina SET excluida_em = NOW() WHERE id = ?", excluida);
        UUID outroLab = laboratorioService.criar("Outro " + UUID.randomUUID(), cenario.fora().getId()).id();
        criarLamina(cenario.fora(), outroLab);

        String lab = "laboratorioId=" + cenario.laboratorio();
        List<Usuario> leitores = List.of(cenario.pesquisador(), cenario.orientador(), cenario.coorientador(),
                cenario.orientadorEncerrado(), cenario.admin(), cenario.membro(), cenario.exMembro(),
                cenario.fora(), cenario.adminGlobal());
        List<String> consultas = List.of("", lab, lab + "&donoUsuarioId=" + cenario.pesquisador().getId(),
                lab + "&donoUsuarioId=" + cenario.membro().getId(), lab + "&donoUsuarioId=" + cenario.admin().getId(),
                "donoUsuarioId=" + cenario.pesquisador().getId(), "laboratorioId=" + UUID.randomUUID());
        for (Usuario leitor : leitores) {
            for (String consulta : consultas) {
                ResultActions listagem = requisicaoAutenticada(sessao(leitor),
                        get(LAMINAS + "?size=100" + (consulta.isEmpty() ? "" : "&" + consulta)));
                ResultActions exportacao = requisicaoAutenticada(sessao(leitor),
                        get(EXPORTACAO + (consulta.isEmpty() ? "" : "?" + consulta)));
                int codigo = listagem.andReturn().getResponse().getStatus();
                assertThat(exportacao.andReturn().getResponse().getStatus()).as("%s / %s", leitor.getId(), consulta)
                        .isEqualTo(codigo);
                if (codigo == 200) {
                    List<String> esperados = texts(json(listagem.andReturn()).path("content"), "id");
                    assertThat(texts(json(exportacao.andReturn()).path("laminas"), "id"))
                            .as("%s / %s", leitor.getId(), consulta).isEqualTo(esperados);
                }
            }
        }
        // Sanidade: o orientador só vê as de quem orienta; o admin do laboratório vê as de todos.
        assertThat(idsExportados(cenario.orientador(), lab)).containsExactlyInAnyOrder(
                doPesquisador1.toString(), doPesquisador2.toString());
        assertThat(idsExportados(cenario.admin(), lab)).contains(deAdmin.toString(), doMembro.toString()).doesNotContain(excluida.toString());
    }

    @Test
    void exportacaoDeVariasTrazRegistrosDeCadaLaminaSemMisturar() throws Exception {
        Usuario dono = criarUsuario();
        UUID primeira = criarLamina(dono, null);
        UUID segunda = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        registrar(dono, primeira, UUID.randomUUID(), profase, "NORMAL", List.of(), 1, null, null)
                .andExpect(status().isCreated());
        registrar(dono, segunda, UUID.randomUUID(), profase, "NORMAL", List.of(), 2, null, null)
                .andExpect(status().isCreated());
        registrar(dono, segunda, UUID.randomUUID(), profase, "NORMAL", List.of(), 3, null, null)
                .andExpect(status().isCreated());

        JsonNode corpo = json(requisicaoAutenticada(sessao(dono), get(EXPORTACAO)).andExpect(status().isOk()).andReturn());

        assertThat(corpo.path("laminas")).hasSize(2);
        for (JsonNode lamina : corpo.path("laminas")) {
            int esperados = lamina.path("id").asText().equals(primeira.toString()) ? 1 : 2;
            assertThat(lamina.path("registros")).hasSize(esperados);
        }
    }

    @Test
    void exportacaoDeMuitasLaminasPassaDoTamanhoDoBloco() throws Exception {
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
                SELECT gen_random_uuid(), 0, NOW(), NOW(), l.id, ?, 'NORMAL', 1, NOW(), NULL, NULL, repeat('a', 64), NULL
                  FROM lamina l WHERE l.dono_usuario_id = ?
                """, fase("mitose", "Prófase"), dono.getId());

        JsonNode laminas = json(requisicaoAutenticada(sessao(dono), get(EXPORTACAO)).andExpect(status().isOk())
                .andReturn()).path("laminas");

        assertThat(laminas).hasSize(1201);
        assertThat(texts(laminas, "id")).doesNotHaveDuplicates();
        for (JsonNode lamina : laminas) {
            assertThat(lamina.path("registros")).hasSize(1);
        }
        assertThat(laminas.get(0).path("nome").asText()).isEqualTo("Lâmina em massa 1");
    }

    // ---------------------------------------------------------------- erros, rotas e cabeçalhos

    @Test
    void semLoginResponde401() throws Exception {
        mockMvc.perform(get(EXPORTACAO)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(LAMINAS + "/{id}/exportacao.json", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void uuidInvalidoResponde400() throws Exception {
        Usuario dono = criarUsuario();

        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/exportacao.json", "nao-e-uuid"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao(dono), get(EXPORTACAO + "?laboratorioId=nao-e-uuid"))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/exportacao.json", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotaDeVariasNaoCaiNoGetPorId() throws Exception {
        Usuario dono = criarUsuario();

        JsonNode corpo = json(requisicaoAutenticada(sessao(dono), get(EXPORTACAO)).andExpect(status().isOk()).andReturn());

        assertThat(corpo.path("formato").asText()).isEqualTo("citogenetica-laminas");
        assertThat(corpo.path("laminas")).isEmpty();
    }

    @Test
    void nomeDoArquivoAcentuadoEHigienizado() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", id.toString());
        corpo.put("protocoloVersaoId", versaoPublicada("mitose").toString());
        corpo.put("nome", "Lâmina: Ação/1\\2*?\"<>|\t ");
        requisicaoAutenticada(sessao(dono), post(LAMINAS).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo))).andExpect(status().isCreated());

        MvcResult uma = requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/exportacao.json", id))
                .andExpect(status().isOk()).andReturn();
        ContentDisposition disposicao = ContentDisposition.parse(uma.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertThat(disposicao.getType()).isEqualTo("attachment");
        assertThat(disposicao.getFilename()).isEqualTo("lamina-Lâmina Ação12.json");
        assertThat(uma.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("filename*=UTF-8''");

        MvcResult varias = requisicaoAutenticada(sessao(dono), get(EXPORTACAO)).andExpect(status().isOk()).andReturn();
        String arquivo = ContentDisposition.parse(varias.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .getFilename();
        assertThat(arquivo).matches("laminas-\\d{4}-\\d{2}-\\d{2}\\.json");
        assertThat(arquivo).isEqualTo("laminas-" + json(varias).path("exportadoEm").asText().substring(0, 10) + ".json");
    }

    // ---------------------------------------------------------------- utilidades

    private JsonNode exportarUma(Usuario usuario, UUID lamina) throws Exception {
        return json(requisicaoAutenticada(sessao(usuario), get(LAMINAS + "/{id}/exportacao.json", lamina))
                .andExpect(status().isOk()).andReturn());
    }

    private List<String> idsExportados(Usuario usuario, String consulta) throws Exception {
        return texts(json(requisicaoAutenticada(sessao(usuario),
                get(EXPORTACAO + (consulta.isEmpty() ? "" : "?" + consulta))).andExpect(status().isOk()).andReturn())
                .path("laminas"), "id");
    }

    private ResultActions registrar(Usuario usuario, UUID lamina, UUID id, UUID fase, String status, List<UUID> tipos,
                                    int quantidade, String horaAparelho, String fuso) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", id.toString());
        corpo.put("faseId", fase.toString());
        corpo.put("status", status);
        corpo.put("tipoIds", tipos.stream().map(UUID::toString).toList());
        corpo.put("quantidade", quantidade);
        if (horaAparelho != null) {
            corpo.put("horaAparelho", horaAparelho);
        }
        if (fuso != null) {
            corpo.put("fusoAparelho", fuso);
        }
        return requisicaoAutenticada(sessao(usuario), post(LAMINAS + "/{id}/registros", lamina)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo)));
    }

    private static List<String> fieldNames(JsonNode no) {
        List<String> nomes = new ArrayList<>();
        no.fieldNames().forEachRemaining(nomes::add);
        return nomes;
    }

    private static List<String> texts(JsonNode array) {
        List<String> textos = new ArrayList<>();
        array.forEach(item -> textos.add(item.asText()));
        return textos;
    }

    private static List<String> texts(JsonNode array, String campo) {
        List<String> textos = new ArrayList<>();
        array.forEach(item -> textos.add(item.path(campo).asText()));
        return textos;
    }
}
