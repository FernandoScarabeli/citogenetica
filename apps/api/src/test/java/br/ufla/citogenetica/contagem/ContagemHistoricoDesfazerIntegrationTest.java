package br.ufla.citogenetica.contagem;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Histórico, edição e desfazer de registros de contagem. */
class ContagemHistoricoDesfazerIntegrationTest extends AbstractContagemIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired private EntityManagerFactory entityManagerFactory;

    private int relogio;

    private record Ator(String nome, Usuario usuario, int leitura, int escrita) {
    }

    // ---------------------------------------------------------------- histórico

    @Test
    void historicoVemDoMaisRecenteAoMaisAntigoComPaginacaoETotal() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        List<UUID> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ids.add(registro(dono, lamina, profase, "NORMAL", List.of(), i));
        }

        JsonNode primeira = historico(dono, lamina, "page=0&size=2");
        assertThat(primeira.path("totalElements").asLong()).isEqualTo(5);
        assertThat(primeira.path("totalPages").asInt()).isEqualTo(3);
        assertThat(primeira.path("currentPage").asInt()).isZero();
        assertThat(idsDe(primeira)).containsExactly(ids.get(4).toString(), ids.get(3).toString());
        assertThat(idsDe(historico(dono, lamina, "page=1&size=2")))
                .containsExactly(ids.get(2).toString(), ids.get(1).toString());
        assertThat(idsDe(historico(dono, lamina, "page=2&size=2"))).containsExactly(ids.get(0).toString());
        assertThat(historico(dono, lamina, "page=3&size=2").path("content")).isEmpty();
        assertThat(idsDe(historico(dono, lamina, ""))).hasSize(5);
        // Empate na hora oficial: o id decide, do maior para o menor.
        jdbcTemplate.update("UPDATE registro_contagem SET registrado_em = ? WHERE lamina_id = ?",
                Timestamp.from(BASE), lamina);
        List<String> esperados = ids.stream().map(UUID::toString).sorted(java.util.Comparator.reverseOrder()).toList();
        assertThat(idsDe(historico(dono, lamina, "size=100"))).containsExactlyElementsOf(esperados);
    }

    @Test
    void historicoTemOsCamposDoRegistroEOsTiposComNome() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID stickiness = tipoFixo("mitose", "Anáfase", "Stickiness");
        UUID id = registro(dono, lamina, anafase, "ANORMAL", List.of(ponte, stickiness), 4);

        JsonNode item = historico(dono, lamina, "").path("content").get(0);

        assertThat(item.path("id").asText()).isEqualTo(id.toString());
        assertThat(item.path("versao").asLong()).isZero();
        assertThat(item.path("laminaId").asText()).isEqualTo(lamina.toString());
        assertThat(item.path("faseId").asText()).isEqualTo(anafase.toString());
        assertThat(item.path("status").asText()).isEqualTo("ANORMAL");
        assertThat(item.path("quantidade").asInt()).isEqualTo(4);
        assertThat(item.path("registradoEm").asText()).isNotEmpty();
        List<String> ordenados = ordenados(ponte, stickiness);
        assertThat(item.path("tipoIds")).extracting(JsonNode::asText).containsExactlyElementsOf(ordenados);
        assertThat(item.path("tipos")).hasSize(2);
        for (int i = 0; i < 2; i++) {
            JsonNode tipo = item.path("tipos").get(i);
            assertThat(tipo.path("id").asText()).isEqualTo(ordenados.get(i));
            assertThat(tipo.path("nome").asText())
                    .isEqualTo(ordenados.get(i).equals(ponte.toString()) ? "Ponte" : "Stickiness");
        }
    }

    @Test
    void historicoFiltraPorFaseESituacaoEIgnoraExcluidos() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID a = registro(dono, lamina, profase, "NORMAL", List.of(), 1);
        UUID b = registro(dono, lamina, anafase, "NORMAL", List.of(), 2);
        UUID c = registro(dono, lamina, anafase, "ANORMAL", List.of(ponte), 3);
        UUID excluido = registro(dono, lamina, anafase, "ANORMAL", List.of(ponte), 4);
        jdbcTemplate.update("UPDATE registro_contagem SET excluido_em = NOW() WHERE id = ?", excluido);

        assertThat(idsDe(historico(dono, lamina, ""))).containsExactly(c.toString(), b.toString(), a.toString());
        assertThat(idsDe(historico(dono, lamina, "faseId=" + anafase))).containsExactly(c.toString(), b.toString());
        assertThat(idsDe(historico(dono, lamina, "status=ANORMAL"))).containsExactly(c.toString());
        assertThat(idsDe(historico(dono, lamina, "status=NORMAL&faseId=" + anafase))).containsExactly(b.toString());
        assertThat(historico(dono, lamina, "faseId=" + UUID.randomUUID()).path("content")).isEmpty();
        JsonNode filtrado = historico(dono, lamina, "faseId=" + anafase + "&size=1");
        assertThat(filtrado.path("totalElements").asLong()).isEqualTo(2);
        assertThat(filtrado.path("totalPages").asInt()).isEqualTo(2);

        for (String consulta : List.of("status=normal", "status=TALVEZ", "status=", "faseId=nao-e-uuid",
                "faseId=" + UUID.randomUUID().toString().toUpperCase(), "page=-1", "size=0", "size=101", "page=x")) {
            requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/registros?" + consulta, lamina))
                    .andExpect(status().isBadRequest());
        }
        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/registros", "nao-e-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historicoDeUmaLaminaNaoMostraRegistrosDeOutra() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID outra = criarLamina(dono, null);
        UUID fase = fase("mitose", "Prófase");
        UUID a = registro(dono, lamina, fase, "NORMAL", List.of(), 1);
        registro(dono, outra, fase, "NORMAL", List.of(), 1);

        assertThat(idsDe(historico(dono, lamina, ""))).containsExactly(a.toString());
    }

    @Test
    void orientadorVeOsNomesDosTiposPersonalizadosDoDono() throws Exception {
        Cenario cenario = montarCenario();
        Usuario dono = cenario.pesquisador();
        UUID lamina = criarLamina(dono, cenario.laboratorio());
        UUID profase = fase("mitose", "Prófase");
        UUID personalizado = tipoPersonalizado(profase, dono, cenario.laboratorio(), "Meu tipo secreto");
        UUID micronucleo = tipoFixo("mitose", "Prófase", "Micronúcleo");
        registro(dono, lamina, profase, "ANORMAL", List.of(personalizado, micronucleo), 2);

        for (Usuario leitor : List.of(cenario.orientador(), cenario.coorientador(), cenario.admin())) {
            JsonNode item = historico(leitor, lamina, "").path("content").get(0);
            List<String> nomes = new ArrayList<>();
            item.path("tipos").forEach(tipo -> nomes.add(tipo.path("nome").asText()));
            assertThat(nomes).containsExactlyInAnyOrder("Meu tipo secreto", "Micronúcleo");
        }
    }

    @Test
    void historicoNaoFazConsultasPorRegistro() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID stickiness = tipoFixo("mitose", "Anáfase", "Stickiness");
        for (int i = 0; i < 30; i++) {
            registro(dono, lamina, anafase, "ANORMAL", List.of(ponte, stickiness), 1);
        }
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.setStatisticsEnabled(true);
        try {
            historico(dono, lamina, "size=1");
            estatisticas.clear();
            historico(dono, lamina, "size=1");
            long comUm = estatisticas.getPrepareStatementCount();
            estatisticas.clear();
            assertThat(historico(dono, lamina, "size=30").path("content")).hasSize(30);
            long comTrinta = estatisticas.getPrepareStatementCount();

            assertThat(comTrinta).as("consultas com 1 e com 30 registros").isEqualTo(comUm);
        } finally {
            estatisticas.setStatisticsEnabled(false);
        }
    }

    // ---------------------------------------------------------------- total de células na lâmina

    @Test
    void laminaSemRegistrosTemTotalDeCelulasZeroEmTodasAsRotas() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", id.toString());
        corpo.put("protocoloVersaoId", versaoPublicada("mitose").toString());
        corpo.put("nome", "Sem registros");
        String json = objectMapper.writeValueAsString(corpo);

        JsonNode criada = json(requisicaoAutenticada(sessao(dono), post(LAMINAS)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn());
        JsonNode repetida = json(requisicaoAutenticada(sessao(dono), post(LAMINAS)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk()).andReturn());
        JsonNode editada = json(requisicaoAutenticada(sessao(dono), patch(LAMINAS + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("versaoEsperada", 0, "nome", "Renomeada"))))
                .andExpect(status().isOk()).andReturn());

        assertThat(criada.path("totalCelulas").isIntegralNumber()).isTrue();
        assertThat(criada.path("totalCelulas").asLong()).isZero();
        assertThat(repetida.path("totalCelulas").asLong()).isZero();
        assertThat(editada.path("totalCelulas").asLong()).isZero();
        assertThat(lamina(dono, id).path("totalCelulas").asLong()).isZero();
        assertThat(totalNaListagem(dono, id)).isZero();
    }

    @Test
    void totalDeCelulasSomaLotesIgnoraExcluidosEIgualaOResumo() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        registro(dono, id, profase, "NORMAL", List.of(), 10);
        registro(dono, id, profase, "NORMAL", List.of(), 5);
        UUID excluido = registro(dono, id, anafase, "ANORMAL", List.of(ponte), 4);
        jdbcTemplate.update("UPDATE registro_contagem SET excluido_em = now() WHERE id = ?", excluido);

        assertThat(lamina(dono, id).path("totalCelulas").asLong()).isEqualTo(15);
        assertThat(totalNaListagem(dono, id)).isEqualTo(15);
        assertThat(resumo(dono, id).path("total").asLong()).isEqualTo(15);

        registro(dono, id, anafase, "ANORMAL", List.of(ponte), 6);
        assertThat(lamina(dono, id).path("totalCelulas").asLong()).isEqualTo(21);
        assertThat(resumo(dono, id).path("total").asLong()).isEqualTo(21);
    }

    @Test
    void totalDeCelulasReduzDepoisDeDesfazerParteDeUmLote() throws Exception {
        Usuario dono = criarUsuario();
        UUID id = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        registro(dono, id, profase, "NORMAL", List.of(), 10);
        assertThat(lamina(dono, id).path("totalCelulas").asLong()).isEqualTo(10);

        desfazer(dono, id, UUID.randomUUID(), profase, "NORMAL", List.of(), 3).andExpect(status().isOk());

        assertThat(lamina(dono, id).path("totalCelulas").asLong()).isEqualTo(7);
        assertThat(resumo(dono, id).path("total").asLong()).isEqualTo(7);
    }

    @Test
    void listagemTrazOTotalCertoDeCadaLaminaSemConsultaPorLamina() throws Exception {
        Usuario dono = criarUsuario();
        UUID profase = fase("mitose", "Prófase");
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        UUID primeira = criarLamina(dono, null);
        registro(dono, primeira, profase, "NORMAL", List.of(), 3);
        JsonNode comUma = listarLaminas(dono);
        estatisticas.setStatisticsEnabled(true);
        try {
            estatisticas.clear();
            listarLaminas(dono);
            long consultasComUma = estatisticas.getPrepareStatementCount();

            Map<UUID, Long> esperado = new LinkedHashMap<>();
            esperado.put(primeira, 3L);
            for (int i = 1; i <= 5; i++) {
                UUID outra = criarLamina(dono, null);
                if (i % 2 == 1) {
                    registro(dono, outra, profase, "NORMAL", List.of(), i);
                    registro(dono, outra, profase, "NORMAL", List.of(), 100);
                    esperado.put(outra, i + 100L);
                } else {
                    esperado.put(outra, 0L);
                }
            }
            estatisticas.clear();
            JsonNode comSeis = listarLaminas(dono);
            long consultasComSeis = estatisticas.getPrepareStatementCount();

            assertThat(comUma.path("content")).hasSize(1);
            assertThat(comSeis.path("content")).hasSize(6);
            for (JsonNode item : comSeis.path("content")) {
                assertThat(item.path("totalCelulas").asLong())
                        .as("total da lâmina %s", item.path("id").asText())
                        .isEqualTo(esperado.get(UUID.fromString(item.path("id").asText())));
            }
            assertThat(consultasComSeis).as("consultas com 1 e com 6 lâminas").isEqualTo(consultasComUma);
        } finally {
            estatisticas.setStatisticsEnabled(false);
        }
    }

    // ---------------------------------------------------------------- edição

    @Test
    void editaCadaCampoEAumentaAVersaoSemMudarAHoraOficialNemOHash() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID micronucleo = tipoFixo("mitose", "Prófase", "Micronúcleo");
        UUID stickiness = tipoFixo("mitose", "Prófase", "Stickiness");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID id = UUID.randomUUID();
        JsonNode criado = json(registrar(dono, lamina, id, profase, "NORMAL", List.of(), 5,
                "2026-11-08T12:30:00Z").andExpect(status().isCreated()).andReturn());
        String registradoEm = criado.path("registradoEm").asText();
        String hash = hashDaCriacao(id);

        // Só a quantidade.
        JsonNode r1 = editarOk(dono, lamina, id, 0, profase, "NORMAL", List.of(), 9);
        assertThat(r1.path("versao").asLong()).isEqualTo(1);
        assertThat(r1.path("quantidade").asInt()).isEqualTo(9);
        // Situação e tipos.
        JsonNode r2 = editarOk(dono, lamina, id, 1, profase, "ANORMAL", List.of(micronucleo), 9);
        assertThat(r2.path("versao").asLong()).isEqualTo(2);
        assertThat(r2.path("status").asText()).isEqualTo("ANORMAL");
        assertThat(r2.path("tipoIds")).extracting(JsonNode::asText).containsExactly(micronucleo.toString());
        // Só os tipos: a versão aumenta mesmo assim.
        JsonNode r3 = editarOk(dono, lamina, id, 2, profase, "ANORMAL", List.of(micronucleo, stickiness), 9);
        assertThat(r3.path("versao").asLong()).isEqualTo(3);
        assertThat(r3.path("tipoIds")).extracting(JsonNode::asText)
                .containsExactlyElementsOf(ordenados(micronucleo, stickiness));
        JsonNode r4 = editarOk(dono, lamina, id, 3, profase, "ANORMAL", List.of(stickiness), 9);
        assertThat(r4.path("versao").asLong()).isEqualTo(4);
        // Fase e tipo juntos.
        JsonNode r5 = editarOk(dono, lamina, id, 4, anafase, "ANORMAL", List.of(ponte), 9);
        assertThat(r5.path("versao").asLong()).isEqualTo(5);
        assertThat(r5.path("faseId").asText()).isEqualTo(anafase.toString());

        assertThat(r5.path("registradoEm").asText()).isEqualTo(registradoEm);
        assertThat(r5.path("horaAparelho").asText()).isEqualTo("2026-11-08T12:30:00Z");
        assertThat(hashDaCriacao(id)).isEqualTo(hash);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM registro_contagem WHERE id = ?", Long.class, id))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForList("SELECT tipo_id FROM registro_contagem_tipo WHERE registro_id = ?",
                UUID.class, id)).containsExactly(ponte);
        // O histórico e a resposta de criação repetida mostram o estado atual.
        assertThat(historico(dono, lamina, "").path("content").get(0).path("versao").asLong()).isEqualTo(5);
        JsonNode repeticao = json(registrar(dono, lamina, id, profase, "NORMAL", List.of(), 5,
                "2026-11-08T12:30:00Z").andExpect(status().isOk()).andReturn());
        assertThat(repeticao.path("versao").asLong()).isEqualTo(5);
        assertThat(repeticao.path("faseId").asText()).isEqualTo(anafase.toString());
        assertThat(repeticao.path("quantidade").asInt()).isEqualTo(9);
    }

    @Test
    void edicaoRefleteNoResumo() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID interfase = fase("mitose", "Interfase");
        UUID metafase = fase("mitose", "Metáfase");
        UUID id = registro(dono, lamina, interfase, "NORMAL", List.of(), 10);
        registro(dono, lamina, metafase, "NORMAL", List.of(), 10);
        assertThat(resumo(dono, lamina).path("indiceMitotico").asDouble()).isEqualTo(50.0);

        editarOk(dono, lamina, id, 0, metafase, "NORMAL", List.of(), 30);

        JsonNode resumo = resumo(dono, lamina);
        assertThat(resumo.path("total").asLong()).isEqualTo(40);
        assertThat(resumo.path("indiceMitotico").asDouble()).isEqualTo(100.0);
        assertThat(resumo.path("fases").get(0).path("total").asLong()).isZero();
        assertThat(resumo.path("fases").get(2).path("normais").asLong()).isEqualTo(40);
    }

    @Test
    void edicaoIdenticaAoEstadoAtualNaoAumentaAVersao() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID micronucleo = tipoFixo("mitose", "Prófase", "Micronúcleo");
        UUID id = registro(dono, lamina, profase, "ANORMAL", List.of(micronucleo), 3);

        JsonNode resposta = editarOk(dono, lamina, id, 0, profase, "ANORMAL", List.of(micronucleo), 3);

        assertThat(resposta.path("versao").asLong()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM registro_contagem WHERE id = ?", Long.class, id))
                .isZero();
    }

    @Test
    void versaoDesatualizadaOuAdiantadaEh409SemAlterarNada() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 3);
        editarOk(dono, lamina, id, 0, profase, "NORMAL", List.of(), 4);

        for (long versao : List.of(0L, 2L, 99L)) {
            editar(dono, lamina, id, versao, profase, "NORMAL", List.of(), 50).andExpect(status().isConflict());
        }
        assertThat(jdbcTemplate.queryForObject("SELECT quantidade FROM registro_contagem WHERE id = ?",
                Integer.class, id)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM registro_contagem WHERE id = ?", Long.class, id))
                .isEqualTo(1);
    }

    @Test
    void edicaoComDadosInvalidosEh400SemAlterarNada() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID micronucleo = tipoFixo("mitose", "Prófase", "Micronúcleo");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 3);
        UUID faseDeMeiose = fase("meiose", "Metáfase I");
        UUID tipoDeMeiose = tipoFixo("meiose", "Metáfase I", "Cromossomos não orientados");
        UUID personalizadoAlheio = tipoPersonalizado(profase, criarUsuario(), null, "De outra pessoa");

        List<Map<String, Object>> invalidos = new ArrayList<>();
        invalidos.add(corpoEdicao(0L, profase, "NORMAL", List.of(), 1001));
        invalidos.add(corpoEdicao(0L, profase, "NORMAL", List.of(), 0));
        invalidos.add(corpoEdicao(0L, profase, "NORMAL", List.of(), -1));
        invalidos.add(corpoEdicao(0L, profase, "NORMAL", List.of(), null));
        invalidos.add(corpoEdicao(null, profase, "NORMAL", List.of(), 1));
        invalidos.add(corpoEdicao(0L, profase, "normal", List.of(), 1));
        invalidos.add(corpoEdicao(0L, profase, null, List.of(), 1));
        invalidos.add(corpoEdicao(0L, profase, "NORMAL", List.of(micronucleo), 1));
        invalidos.add(corpoEdicao(0L, profase, "ANORMAL", List.of(), 1));
        invalidos.add(corpoEdicao(0L, profase, "ANORMAL", List.of(micronucleo, micronucleo), 1));
        invalidos.add(corpoEdicao(0L, profase, "ANORMAL", List.of(ponte), 1)); // tipo de outra fase
        invalidos.add(corpoEdicao(0L, profase, "ANORMAL", List.of(tipoDeMeiose), 1)); // outro protocolo
        invalidos.add(corpoEdicao(0L, profase, "ANORMAL", List.of(personalizadoAlheio), 1));
        invalidos.add(corpoEdicao(0L, profase, "ANORMAL", List.of(UUID.randomUUID()), 1));
        invalidos.add(corpoEdicao(0L, faseDeMeiose, "NORMAL", List.of(), 1)); // fase de outro protocolo
        invalidos.add(corpoEdicao(0L, UUID.randomUUID(), "NORMAL", List.of(), 1));
        invalidos.add(corpoEdicao(0L, anafase, "ANORMAL", List.of(micronucleo), 1)); // tipo não é da nova fase
        Map<String, Object> semFase = corpoEdicao(0L, profase, "NORMAL", List.of(), 1);
        semFase.remove("faseId");
        invalidos.add(semFase);
        Map<String, Object> tipoTexto = corpoEdicao(0L, profase, "ANORMAL", List.of(), 1);
        tipoTexto.put("tipoIds", List.of("nao-e-uuid"));
        invalidos.add(tipoTexto);
        Map<String, Object> tipoNulo = corpoEdicao(0L, profase, "ANORMAL", List.of(), 1);
        tipoNulo.put("tipoIds", java.util.Arrays.asList(micronucleo.toString(), null));
        invalidos.add(tipoNulo);
        Map<String, Object> tipoComNulo = corpoEdicao(0L, profase, "ANORMAL", List.of(), 1);
        tipoComNulo.put("tipoIds", List.of(micronucleo + "\u0000"));
        invalidos.add(tipoComNulo);
        invalidos.add(corpoEdicao(0L, profase, "ANORMAL\u0000", List.of(micronucleo), 1));

        for (Map<String, Object> corpo : invalidos) {
            assertThat(patchBruto(dono, lamina, id, objectMapper.writeValueAsString(corpo)).andReturn()
                    .getResponse().getStatus()).as("corpo %s", corpo).isEqualTo(400);
        }
        for (String quantidade : List.of("1.5", "2.0", "\"5\"", "2147483648", "true", "[]", "1e2")) {
            patchBruto(dono, lamina, id, "{\"versaoEsperada\":0,\"faseId\":\"" + profase
                    + "\",\"status\":\"NORMAL\",\"quantidade\":" + quantidade + "}").andExpect(status().isBadRequest());
        }
        for (String versao : List.of("0.5", "\"0\"", "true", "null", "[]")) {
            patchBruto(dono, lamina, id, "{\"versaoEsperada\":" + versao + ",\"faseId\":\"" + profase
                    + "\",\"status\":\"NORMAL\",\"quantidade\":1}").andExpect(status().isBadRequest());
        }
        patchBruto(dono, lamina, id, "{").andExpect(status().isBadRequest());
        patchBruto(dono, lamina, id, "").andExpect(status().isBadRequest());
        patchBruto(dono, lamina, id, "{\"versaoEsperada\":0,\"faseId\":\"" + profase
                + "\",\"status\":\"NORMAL\\u0000\",\"quantidade\":1}").andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao(dono), patch(LAMINAS + "/{l}/registros/{r}", "nao-e-uuid", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpoEdicao(0L, profase, "NORMAL", List.of(), 1))))
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao(dono), patch(LAMINAS + "/{l}/registros/{r}", lamina, "nao-e-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpoEdicao(0L, profase, "NORMAL", List.of(), 1))))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT version FROM registro_contagem WHERE id = ?", Long.class, id))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT quantidade FROM registro_contagem WHERE id = ?",
                Integer.class, id)).isEqualTo(3);
    }

    @Test
    void edicaoRespeitaOLimiteLidoDoBanco() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 3);
        try {
            jdbcTemplate.update("UPDATE parametro_contagem SET limite_celulas_por_registro = 20");
            editarOk(dono, lamina, id, 0, profase, "NORMAL", List.of(), 20);
            editar(dono, lamina, id, 1, profase, "NORMAL", List.of(), 21).andExpect(status().isBadRequest());
        } finally {
            jdbcTemplate.update("UPDATE parametro_contagem SET limite_celulas_por_registro = 1000");
        }
        editarOk(dono, lamina, id, 1, profase, "NORMAL", List.of(), 1000);
    }

    @Test
    void registroDeOutraLaminaExcluidoOuInexistenteEh404() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID outra = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 3);
        UUID excluido = registro(dono, lamina, profase, "NORMAL", List.of(), 3);
        jdbcTemplate.update("UPDATE registro_contagem SET excluido_em = NOW() WHERE id = ?", excluido);

        editar(dono, outra, id, 0, profase, "NORMAL", List.of(), 4).andExpect(status().isNotFound());
        editar(dono, lamina, excluido, 0, profase, "NORMAL", List.of(), 4).andExpect(status().isNotFound());
        editar(dono, lamina, UUID.randomUUID(), 0, profase, "NORMAL", List.of(), 4).andExpect(status().isNotFound());
        editar(dono, UUID.randomUUID(), id, 0, profase, "NORMAL", List.of(), 4).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT quantidade FROM registro_contagem WHERE id = ?",
                Integer.class, id)).isEqualTo(3);

        requisicaoAutenticada(sessao(dono), delete(LAMINAS + "/{id}", lamina).param("versaoEsperada", "0"))
                .andExpect(status().isNoContent());
        editar(dono, lamina, id, 0, profase, "NORMAL", List.of(), 4).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- desfazer: casos fechados

    @Test
    void desfazerUmLoteInteiroExcluiOUltimoRegistro() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID antigo = registro(dono, lamina, profase, "NORMAL", List.of(), 5);
        UUID recente = registro(dono, lamina, profase, "NORMAL", List.of(), 7);
        UUID id = UUID.randomUUID();

        JsonNode resposta = json(desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 7)
                .andExpect(status().isOk()).andReturn());

        assertThat(resposta.path("id").asText()).isEqualTo(id.toString());
        assertThat(resposta.path("quantidadeDesfeita").asInt()).isEqualTo(7);
        assertThat(resposta.path("registros")).hasSize(1);
        verificarAfetado(resposta.path("registros").get(0), recente, 0, true);
        assertThat(excluido(recente)).isTrue();
        assertThat(excluido(antigo)).isFalse();
        assertThat(quantidadeNoBanco(recente)).isEqualTo(7);
        assertThat(versaoNoBanco(recente)).isEqualTo(1);
        assertThat(versaoNoBanco(antigo)).isZero();
        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(5);
    }

    @Test
    void desfazerParteDeUmLoteReduzASuaQuantidade() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID lote = registro(dono, lamina, profase, "NORMAL", List.of(), 10);

        JsonNode resposta = json(desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 3)
                .andExpect(status().isOk()).andReturn());

        assertThat(resposta.path("quantidadeDesfeita").asInt()).isEqualTo(3);
        assertThat(resposta.path("registros")).hasSize(1);
        verificarAfetado(resposta.path("registros").get(0), lote, 7, false);
        assertThat(excluido(lote)).isFalse();
        assertThat(quantidadeNoBanco(lote)).isEqualTo(7);
        assertThat(versaoNoBanco(lote)).isEqualTo(1);
        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(7);
        assertThat(idsDe(historico(dono, lamina, ""))).containsExactly(lote.toString());
        assertThat(historico(dono, lamina, "").path("content").get(0).path("quantidade").asInt()).isEqualTo(7);
    }

    @Test
    void desfazerAtravessaVariosLotesDoMaisRecenteAoMaisAntigo() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID primeiro = registro(dono, lamina, profase, "NORMAL", List.of(), 10);
        UUID segundo = registro(dono, lamina, profase, "NORMAL", List.of(), 4);
        UUID terceiro = registro(dono, lamina, profase, "NORMAL", List.of(), 6);
        UUID quarto = registro(dono, lamina, profase, "NORMAL", List.of(), 2);

        // 2 (quarto) + 6 (terceiro) + 1 do segundo = 9.
        JsonNode resposta = json(desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 9)
                .andExpect(status().isOk()).andReturn());

        assertThat(resposta.path("quantidadeDesfeita").asInt()).isEqualTo(9);
        assertThat(resposta.path("registros")).hasSize(3);
        verificarAfetado(resposta.path("registros").get(0), quarto, 0, true);
        verificarAfetado(resposta.path("registros").get(1), terceiro, 0, true);
        verificarAfetado(resposta.path("registros").get(2), segundo, 3, false);
        assertThat(excluido(quarto)).isTrue();
        assertThat(excluido(terceiro)).isTrue();
        assertThat(excluido(segundo)).isFalse();
        assertThat(excluido(primeiro)).isFalse();
        assertThat(quantidadeNoBanco(segundo)).isEqualTo(3);
        assertThat(quantidadeNoBanco(primeiro)).isEqualTo(10);
        assertThat(versaoNoBanco(primeiro)).isZero();
        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(13);
    }

    @Test
    void desfazerTudoOQueHaDisponivelEAcimaDisponivelEh409SemAlterarNada() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID a = registro(dono, lamina, profase, "NORMAL", List.of(), 3);
        UUID b = registro(dono, lamina, profase, "NORMAL", List.of(), 4);

        UUID rejeitado = UUID.randomUUID();
        JsonNode erro = json(desfazer(dono, lamina, rejeitado, profase, "NORMAL", List.of(), 8)
                .andExpect(status().isConflict()).andReturn());
        assertThat(erro.path("message").asText()).contains("8").contains("7");
        assertThat(excluido(a)).isFalse();
        assertThat(excluido(b)).isFalse();
        assertThat(quantidadeNoBanco(a)).isEqualTo(3);
        assertThat(versaoNoBanco(a)).isZero();
        assertThat(versaoNoBanco(b)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operacao_desfazer WHERE id = ?",
                Integer.class, rejeitado)).isZero();
        // O mesmo id rejeitado pode ser tentado de novo com outra quantidade? O pedido é outro: não houve operação gravada.
        JsonNode resposta = json(desfazer(dono, lamina, rejeitado, profase, "NORMAL", List.of(), 7)
                .andExpect(status().isOk()).andReturn());

        assertThat(resposta.path("quantidadeDesfeita").asInt()).isEqualTo(7);
        assertThat(resposta.path("registros")).hasSize(2);
        assertThat(excluido(a)).isTrue();
        assertThat(excluido(b)).isTrue();
        assertThat(resumo(dono, lamina).path("total").asLong()).isZero();
        // Nada mais a desfazer.
        desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 1).andExpect(status().isConflict());
    }

    @Test
    void desfazerCasaSoOConjuntoExatoDeTiposESeparaNormalDeAnormalEFases() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID anafase = fase("mitose", "Anáfase");
        UUID profase = fase("mitose", "Prófase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID stickiness = tipoFixo("mitose", "Anáfase", "Stickiness");
        UUID soA = registro(dono, lamina, anafase, "ANORMAL", List.of(ponte), 2);
        UUID aEB = registro(dono, lamina, anafase, "ANORMAL", List.of(ponte, stickiness), 5);
        UUID soB = registro(dono, lamina, anafase, "ANORMAL", List.of(stickiness), 3);
        UUID normal = registro(dono, lamina, anafase, "NORMAL", List.of(), 8);
        UUID outraFase = registro(dono, lamina, profase, "NORMAL", List.of(), 9);

        // Desfazer só o tipo A não toca o registro com A e B, que é mais recente que o de A sozinho... e mais antigo
        // que os demais: a ordem não importa, o conjunto tem que ser exato.
        JsonNode r1 = json(desfazer(dono, lamina, UUID.randomUUID(), anafase, "ANORMAL", List.of(ponte), 2)
                .andExpect(status().isOk()).andReturn());
        verificarAfetado(r1.path("registros").get(0), soA, 0, true);
        desfazer(dono, lamina, UUID.randomUUID(), anafase, "ANORMAL", List.of(ponte), 1)
                .andExpect(status().isConflict());
        assertThat(quantidadeNoBanco(aEB)).isEqualTo(5);

        // O conjunto {A, B} é o mesmo em qualquer ordem enviada.
        JsonNode r2 = json(desfazer(dono, lamina, UUID.randomUUID(), anafase, "ANORMAL", List.of(stickiness, ponte), 2)
                .andExpect(status().isOk()).andReturn());
        verificarAfetado(r2.path("registros").get(0), aEB, 3, false);
        assertThat(quantidadeNoBanco(soB)).isEqualTo(3);

        // Normal: nenhum tipo; não pega anormais.
        JsonNode r3 = json(desfazer(dono, lamina, UUID.randomUUID(), anafase, "NORMAL", List.of(), 8)
                .andExpect(status().isOk()).andReturn());
        verificarAfetado(r3.path("registros").get(0), normal, 0, true);
        desfazer(dono, lamina, UUID.randomUUID(), anafase, "NORMAL", List.of(), 1).andExpect(status().isConflict());

        // Outra fase intacta.
        assertThat(excluido(outraFase)).isFalse();
        assertThat(quantidadeNoBanco(outraFase)).isEqualTo(9);
        assertThat(versaoNoBanco(outraFase)).isZero();
        assertThat(quantidadeNoBanco(soB)).isEqualTo(3);
        assertThat(versaoNoBanco(soB)).isZero();
    }

    @Test
    void desfazerRefleteNoResumoEORegistroDesfeitoContinuaReconhecidoNaCriacao() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID id = UUID.randomUUID();
        registrar(dono, lamina, id, anafase, "ANORMAL", List.of(ponte), 4, null).andExpect(status().isCreated());
        registro(dono, lamina, anafase, "NORMAL", List.of(), 6);
        assertThat(resumo(dono, lamina).path("anormais").asLong()).isEqualTo(4);

        desfazer(dono, lamina, UUID.randomUUID(), anafase, "ANORMAL", List.of(ponte), 4).andExpect(status().isOk());

        JsonNode resumo = resumo(dono, lamina);
        assertThat(resumo.path("anormais").asLong()).isZero();
        assertThat(resumo.path("total").asLong()).isEqualTo(6);
        assertThat(resumo.path("fases").get(3).path("tipos")).isEmpty();
        // A repetição da criação (fila offline) não ressuscita o registro: 200 com o estado atual.
        JsonNode repeticao = json(registrar(dono, lamina, id, anafase, "ANORMAL", List.of(ponte), 4, null)
                .andExpect(status().isOk()).andReturn());
        assertThat(repeticao.path("versao").asLong()).isEqualTo(1);
        assertThat(excluido(id)).isTrue();
        assertThat(resumo(dono, lamina).path("anormais").asLong()).isZero();
    }

    @Test
    void desfazerEnxergaAEdicaoAnteriorDoRegistro() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 5);
        editarOk(dono, lamina, id, 0, anafase, "ANORMAL", List.of(ponte), 8);

        desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 1).andExpect(status().isConflict());
        JsonNode resposta = json(desfazer(dono, lamina, UUID.randomUUID(), anafase, "ANORMAL", List.of(ponte), 3)
                .andExpect(status().isOk()).andReturn());

        verificarAfetado(resposta.path("registros").get(0), id, 5, false);
        assertThat(versaoNoBanco(id)).isEqualTo(2);
        // E a edição depois do desfazer usa a versão nova.
        editar(dono, lamina, id, 1, anafase, "ANORMAL", List.of(ponte), 5).andExpect(status().isConflict());
        editarOk(dono, lamina, id, 2, anafase, "ANORMAL", List.of(ponte), 5);
    }

    @Test
    void quantidadeAcimaDoLimiteDeUmRegistroNaoEhErroSePossuiDisponivel() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        for (int i = 0; i < 3; i++) {
            registro(dono, lamina, profase, "NORMAL", List.of(), 1000);
        }

        JsonNode resposta = json(desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 2500)
                .andExpect(status().isOk()).andReturn());

        assertThat(resposta.path("quantidadeDesfeita").asInt()).isEqualTo(2500);
        assertThat(resposta.path("registros")).hasSize(3);
        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(500);
        desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), Integer.MAX_VALUE)
                .andExpect(status().isConflict());
    }

    @Test
    void desfazerValidaFaseETiposComo400EQuantidadeEstrita() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID micronucleo = tipoFixo("mitose", "Prófase", "Micronúcleo");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 5);

        List<Map<String, Object>> invalidos = new ArrayList<>();
        invalidos.add(corpoDesfazer(UUID.randomUUID(), fase("meiose", "Metáfase I"), "NORMAL", List.of(), 1));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), UUID.randomUUID(), "NORMAL", List.of(), 1));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, "ANORMAL", List.of(ponte), 1)); // tipo de outra fase
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, "ANORMAL", List.of(UUID.randomUUID()), 1));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, "ANORMAL", List.of(), 1));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, "NORMAL", List.of(micronucleo), 1));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, "ANORMAL", List.of(micronucleo, micronucleo), 1));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), anafase, "normal", List.of(), 1));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, "NORMAL", List.of(), 0));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, "NORMAL", List.of(), -3));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, "NORMAL", List.of(), null));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, null, List.of(), 1));
        invalidos.add(corpoDesfazer(UUID.randomUUID(), profase, "ANORMAL\u0000", List.of(micronucleo), 1));
        Map<String, Object> semId = corpoDesfazer(UUID.randomUUID(), profase, "NORMAL", List.of(), 1);
        semId.remove("id");
        invalidos.add(semId);
        Map<String, Object> idRuim = corpoDesfazer(UUID.randomUUID(), profase, "NORMAL", List.of(), 1);
        idRuim.put("id", "nao-e-uuid");
        invalidos.add(idRuim);
        Map<String, Object> idMaiusculo = corpoDesfazer(UUID.randomUUID(), profase, "NORMAL", List.of(), 1);
        idMaiusculo.put("id", UUID.randomUUID().toString().toUpperCase());
        invalidos.add(idMaiusculo);
        Map<String, Object> tipoRuim = corpoDesfazer(UUID.randomUUID(), profase, "ANORMAL", List.of(), 1);
        tipoRuim.put("tipoIds", List.of(micronucleo + "\u0000"));
        invalidos.add(tipoRuim);

        for (Map<String, Object> corpo : invalidos) {
            assertThat(desfazerBruto(dono, lamina, objectMapper.writeValueAsString(corpo)).andReturn()
                    .getResponse().getStatus()).as("corpo %s", corpo).isEqualTo(400);
        }
        for (String quantidade : List.of("1.5", "2.0", "\"5\"", "2147483648", "true", "[]", "1e2")) {
            desfazerBruto(dono, lamina, "{\"id\":\"" + UUID.randomUUID() + "\",\"faseId\":\"" + profase
                    + "\",\"status\":\"NORMAL\",\"quantidade\":" + quantidade + "}")
                    .andExpect(status().isBadRequest());
        }
        desfazerBruto(dono, lamina, "{").andExpect(status().isBadRequest());
        desfazerBruto(dono, lamina, "").andExpect(status().isBadRequest());
        desfazerBruto(dono, lamina, "{\"id\":\"" + UUID.randomUUID() + "\",\"faseId\":\"" + profase
                + "\",\"status\":\"NORMAL\\u0000\",\"quantidade\":1}").andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao(dono), post(LAMINAS + "/{id}/registros/desfazer", "nao-e-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpoDesfazer(UUID.randomUUID(), profase, "NORMAL", List.of(), 1))))
                .andExpect(status().isBadRequest());
        assertThat(quantidadeNoBanco(id)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operacao_desfazer WHERE lamina_id = ?",
                Integer.class, lamina)).isZero();
    }

    // ---------------------------------------------------------------- desfazer: idempotência (ADR 0003)

    @Test
    void repeticaoDevolveARespostaOriginalSemRemoverDeNovoMesmoDepoisDeOutrasContagens() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID primeiro = registro(dono, lamina, profase, "NORMAL", List.of(), 5);
        UUID segundo = registro(dono, lamina, profase, "NORMAL", List.of(), 5);
        UUID id = UUID.randomUUID();
        JsonNode original = json(desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 7)
                .andExpect(status().isOk()).andReturn());
        verificarAfetado(original.path("registros").get(0), segundo, 0, true);
        verificarAfetado(original.path("registros").get(1), primeiro, 3, false);

        // Depois: mais contagens, uma edição do registro afetado e a exclusão por outro desfazer.
        registro(dono, lamina, profase, "NORMAL", List.of(), 20);
        editarOk(dono, lamina, primeiro, 1, profase, "NORMAL", List.of(), 9);
        long total = resumo(dono, lamina).path("total").asLong();

        for (int i = 0; i < 3; i++) {
            JsonNode repeticao = json(desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 7)
                    .andExpect(status().isOk()).andReturn());
            assertThat(repeticao).isEqualTo(original);
        }
        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(total);
        assertThat(quantidadeNoBanco(primeiro)).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operacao_desfazer WHERE id = ?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void repeticaoVaiAntesDasChecagensDeAcessoEDeDisponibilidade() throws Exception {
        Cenario cenario = montarCenario();
        Usuario dono = cenario.pesquisador();
        UUID lamina = criarLamina(dono, cenario.laboratorio());
        UUID profase = fase("mitose", "Prófase");
        registro(dono, lamina, profase, "NORMAL", List.of(), 4);
        UUID id = UUID.randomUUID();
        JsonNode original = json(desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 4)
                .andExpect(status().isOk()).andReturn());

        // Não há mais nada a desfazer, e o laboratório foi desativado: a repetição continua 200.
        laboratorioService.desativar(cenario.laboratorio(), cenario.adminGlobal().getId());
        assertThat(json(desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 4).andExpect(status().isOk())
                .andReturn())).isEqualTo(original);
        // Um id novo com o mesmo pedido é outra operação e esbarra no acesso.
        desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 4).andExpect(status().isForbidden());
    }

    @Test
    void repeticaoComLaminaJaExcluidaEh200() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        registro(dono, lamina, profase, "NORMAL", List.of(), 4);
        UUID id = UUID.randomUUID();
        JsonNode original = json(desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 2)
                .andExpect(status().isOk()).andReturn());
        requisicaoAutenticada(sessao(dono), delete(LAMINAS + "/{id}", lamina).param("versaoEsperada", "0"))
                .andExpect(status().isNoContent());

        assertThat(json(desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 2).andExpect(status().isOk())
                .andReturn())).isEqualTo(original);
        desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 1).andExpect(status().isNotFound());
    }

    @Test
    void mesmoIdComPedidoDiferenteEh409SemAlterarNada() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID outraLamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        registro(dono, lamina, profase, "NORMAL", List.of(), 10);
        registro(dono, outraLamina, profase, "NORMAL", List.of(), 10);
        UUID id = UUID.randomUUID();
        desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 2).andExpect(status().isOk());

        desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 3).andExpect(status().isConflict());
        desfazer(dono, lamina, id, anafase, "NORMAL", List.of(), 2).andExpect(status().isConflict());
        desfazer(dono, lamina, id, anafase, "ANORMAL", List.of(ponte), 2).andExpect(status().isConflict());
        desfazer(dono, outraLamina, id, profase, "NORMAL", List.of(), 2).andExpect(status().isConflict());

        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(8);
        assertThat(resumo(dono, outraLamina).path("total").asLong()).isEqualTo(10);
    }

    @Test
    void idDeOutroDonoRespondeIgualAoDeRecursoDesconhecido() throws Exception {
        Usuario dono = criarUsuario();
        Usuario outro = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID laminaDoOutro = criarLamina(outro, null);
        UUID profase = fase("mitose", "Prófase");
        registro(dono, lamina, profase, "NORMAL", List.of(), 10);
        registro(outro, laminaDoOutro, profase, "NORMAL", List.of(), 10);
        UUID id = UUID.randomUUID();
        desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 2).andExpect(status().isOk());

        // Mesma lâmina do dono, e a lâmina do próprio outro: nos dois casos igual a um id desconhecido.
        var desconhecido = desfazer(outro, laminaDoOutro, UUID.randomUUID(), profase, "NORMAL", List.of(), 2)
                .andExpect(status().isOk()).andReturn();
        for (UUID alvo : List.of(lamina, laminaDoOutro)) {
            var resposta = desfazer(outro, alvo, id, profase, "NORMAL", List.of(), 2).andReturn();
            assertThat(resposta.getResponse().getStatus()).isEqualTo(alvo.equals(lamina) ? 404 : 404);
        }
        var paraLaminaAlheia = desfazer(outro, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 2).andReturn();
        var comIdAlheio = desfazer(outro, lamina, id, profase, "NORMAL", List.of(), 2).andReturn();
        assertThat(comIdAlheio.getResponse().getStatus()).isEqualTo(404);
        assertThat(corpoSemHorario(comIdAlheio)).isEqualTo(corpoSemHorario(paraLaminaAlheia));
        assertThat(desconhecido.getResponse().getStatus()).isEqualTo(200);
        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT lamina_id FROM operacao_desfazer WHERE id = ?",
                UUID.class, id)).isEqualTo(lamina);
    }

    // ---------------------------------------------------------------- acesso nas três rotas

    @Test
    void matrizDeAcessoNasTresRotas() throws Exception {
        Cenario cenario = montarCenario();
        List<Ator> atores = List.of(
                new Ator("dono", cenario.pesquisador(), 200, 200),
                new Ator("orientador ativo", cenario.orientador(), 200, 403),
                new Ator("coorientador", cenario.coorientador(), 200, 403),
                new Ator("orientador com vínculo encerrado", cenario.orientadorEncerrado(), 403, 403),
                new Ator("admin do laboratório", cenario.admin(), 200, 403),
                new Ator("membro comum", cenario.membro(), 403, 403),
                new Ator("ex-membro", cenario.exMembro(), 404, 404),
                new Ator("pessoa de fora", cenario.fora(), 404, 404),
                new Ator("ADMIN global de fora", cenario.adminGlobal(), 404, 404));
        verificarMatrizDeAcesso(cenario, cenario.laboratorio(), atores, false);
    }

    @Test
    void laboratorioDesativadoPermiteLerMasNinguemEscreve() throws Exception {
        Cenario cenario = montarCenario();
        List<Ator> atores = List.of(
                new Ator("dono", cenario.pesquisador(), 200, 403),
                new Ator("orientador ativo", cenario.orientador(), 200, 403),
                new Ator("coorientador", cenario.coorientador(), 200, 403),
                new Ator("admin do laboratório", cenario.admin(), 200, 403),
                new Ator("membro comum", cenario.membro(), 403, 403),
                new Ator("ex-membro", cenario.exMembro(), 404, 404),
                new Ator("pessoa de fora", cenario.fora(), 404, 404),
                new Ator("ADMIN global de fora", cenario.adminGlobal(), 404, 404));
        verificarMatrizDeAcesso(cenario, cenario.laboratorio(), atores, true);
    }

    @Test
    void laminaAvulsaEhSoDoDono() throws Exception {
        Cenario cenario = montarCenario();
        List<Ator> atores = List.of(
                new Ator("dono", cenario.pesquisador(), 200, 200),
                new Ator("admin do laboratório", cenario.admin(), 404, 404),
                new Ator("orientador", cenario.orientador(), 404, 404),
                new Ator("membro comum", cenario.membro(), 404, 404),
                new Ator("ex-membro", cenario.exMembro(), 404, 404),
                new Ator("pessoa de fora", cenario.fora(), 404, 404),
                new Ator("ADMIN global de fora", cenario.adminGlobal(), 404, 404));
        verificarMatrizDeAcesso(cenario, null, atores, false);
    }

    @Test
    void laminaExcluidaOuInexistenteEh404InclusiveParaODono() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 4);
        requisicaoAutenticada(sessao(dono), delete(LAMINAS + "/{id}", lamina).param("versaoEsperada", "0"))
                .andExpect(status().isNoContent());

        for (UUID alvo : List.of(lamina, UUID.randomUUID())) {
            requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/registros", alvo)).andExpect(status().isNotFound());
            editar(dono, alvo, id, 0, profase, "NORMAL", List.of(), 1).andExpect(status().isNotFound());
            desfazer(dono, alvo, UUID.randomUUID(), profase, "NORMAL", List.of(), 1).andExpect(status().isNotFound());
        }
        assertThat(quantidadeNoBanco(id)).isEqualTo(4);
    }

    @Test
    void semLoginEh401ESemCsrfEh403() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 4);
        String edicao = objectMapper.writeValueAsString(corpoEdicao(0L, profase, "NORMAL", List.of(), 9));
        String desfazer = objectMapper.writeValueAsString(
                corpoDesfazer(UUID.randomUUID(), profase, "NORMAL", List.of(), 1));

        mockMvc.perform(get(LAMINAS + "/{id}/registros", lamina)).andExpect(status().isUnauthorized());
        comCsrfSemLogin(patch(LAMINAS + "/{l}/registros/{r}", lamina, id).contentType(MediaType.APPLICATION_JSON)
                .content(edicao)).andExpect(status().isUnauthorized());
        comCsrfSemLogin(post(LAMINAS + "/{l}/registros/desfazer", lamina).contentType(MediaType.APPLICATION_JSON)
                .content(desfazer)).andExpect(status().isUnauthorized());
        Cookie sessao = sessao(dono);
        mockMvc.perform(patch(LAMINAS + "/{l}/registros/{r}", lamina, id).cookie(sessao)
                .contentType(MediaType.APPLICATION_JSON).content(edicao)).andExpect(status().isForbidden());
        mockMvc.perform(post(LAMINAS + "/{l}/registros/desfazer", lamina).cookie(sessao)
                .contentType(MediaType.APPLICATION_JSON).content(desfazer)).andExpect(status().isForbidden());
        assertThat(quantidadeNoBanco(id)).isEqualTo(4);
        assertThat(versaoNoBanco(id)).isZero();
    }

    // ---------------------------------------------------------------- concorrência

    @Test
    void desfazeresSimultaneosComIdsDiferentesNuncaRemovemMaisQueODisponivel() throws Exception {
        Usuario dono = criarUsuario();
        UUID profase = fase("mitose", "Prófase");

        for (int rodada = 0; rodada < 3; rodada++) {
            UUID lamina = criarLamina(dono, null);
            for (int i = 0; i < 10; i++) {
                registro(dono, lamina, profase, "NORMAL", List.of(), 1);
            }
            List<Integer> statuses = dispararSimultaneos(8, indice ->
                    desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 3)
                            .andReturn().getResponse().getStatus());

            assertQueTodos(statuses, 200, 409);
            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 200).hasSize(3);
            assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operacao_desfazer WHERE lamina_id = ?",
                    Integer.class, lamina)).isEqualTo(3);
        }
    }

    @Test
    void desfazeresSimultaneosComOMesmoIdRemovemUmaVezSo() throws Exception {
        Usuario dono = criarUsuario();
        UUID profase = fase("mitose", "Prófase");

        for (int rodada = 0; rodada < 4; rodada++) {
            UUID lamina = criarLamina(dono, null);
            for (int i = 0; i < 3; i++) {
                registro(dono, lamina, profase, "NORMAL", List.of(), 5);
            }
            UUID id = UUID.randomUUID();
            List<JsonNode> respostas = dispararSimultaneos(6, indice -> {
                var resultado = desfazer(dono, lamina, id, profase, "NORMAL", List.of(), 7).andReturn();
                assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
                return json(resultado);
            });

            assertThat(respostas).allMatch(resposta -> resposta.equals(respostas.get(0)));
            assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(8);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operacao_desfazer WHERE id = ?",
                    Integer.class, id)).isEqualTo(1);
        }
    }

    @Test
    void mesmoIdSimultaneoComPedidosDiferentesNaoDa500() throws Exception {
        Usuario dono = criarUsuario();
        UUID profase = fase("mitose", "Prófase");
        UUID lamina = criarLamina(dono, null);
        registro(dono, lamina, profase, "NORMAL", List.of(), 50);
        UUID id = UUID.randomUUID();

        List<Integer> statuses = dispararSimultaneos(6, indice ->
                desfazer(dono, lamina, id, profase, "NORMAL", List.of(), indice + 1).andReturn().getResponse().getStatus());

        assertQueTodos(statuses, 200, 409);
        assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT quantidade_desfeita FROM operacao_desfazer WHERE id = ?",
                Integer.class, id)).isEqualTo(50 - resumo(dono, lamina).path("total").asInt());
    }

    @Test
    void edicoesSimultaneasComAMesmaVersaoResultamEmUmaVencedoraEOsDemais409() throws Exception {
        Usuario dono = criarUsuario();
        UUID profase = fase("mitose", "Prófase");

        for (int rodada = 0; rodada < 4; rodada++) {
            UUID lamina = criarLamina(dono, null);
            UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 1);
            List<Integer> statuses = dispararSimultaneos(6, indice ->
                    editar(dono, lamina, id, 0, profase, "NORMAL", List.of(), indice + 2)
                            .andReturn().getResponse().getStatus());

            assertQueTodos(statuses, 200, 409);
            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(versaoNoBanco(id)).isEqualTo(1);
        }
    }

    @Test
    void desfazerSimultaneoACriarRegistroNaoDa500NemPerdeCelulas() throws Exception {
        Usuario dono = criarUsuario();
        UUID profase = fase("mitose", "Prófase");

        for (int rodada = 0; rodada < 3; rodada++) {
            UUID lamina = criarLamina(dono, null);
            registro(dono, lamina, profase, "NORMAL", List.of(), 5);
            List<Integer> statuses = dispararSimultaneos(7, indice -> indice == 0
                    ? desfazer(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 5)
                            .andReturn().getResponse().getStatus()
                    : registrar(dono, lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 1, null)
                            .andReturn().getResponse().getStatus());

            assertThat(statuses.get(0)).as("statuses %s", statuses).isEqualTo(200);
            assertQueTodos(statuses.subList(1, statuses.size()), 201);
            // 5 iniciais - 5 desfeitas + 6 criadas de 1 célula.
            assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(6);
        }
    }

    // ---------------------------------------------------------------- apoio

    private void verificarMatrizDeAcesso(Cenario cenario, UUID laboratorio, List<Ator> atores, boolean desativar)
            throws Exception {
        Usuario dono = cenario.pesquisador();
        UUID lamina = criarLamina(dono, laboratorio);
        UUID profase = fase("mitose", "Prófase");
        UUID id = registro(dono, lamina, profase, "NORMAL", List.of(), 10);
        if (desativar) {
            laboratorioService.desativar(cenario.laboratorio(), cenario.adminGlobal().getId());
        }
        for (Ator ator : atores) {
            ResultActions leitura = requisicaoAutenticada(sessao(ator.usuario()),
                    get(LAMINAS + "/{id}/registros", lamina));
            leitura.andExpect(status().is(ator.leitura()));
            if (ator.leitura() == 200) {
                assertThat(json(leitura.andReturn()).path("content").get(0).path("id").asText())
                        .as(ator.nome()).isEqualTo(id.toString());
            }
            int antes = quantidadeNoBanco(id);
            long versaoAntes = versaoNoBanco(id);
            // A edição e o desfazer de quem pode escrever mudam o registro: só o último da lista (o dono) o faz,
            // então cada ator volta o estado a 10 células ao final.
            assertThat(editar(ator.usuario(), lamina, id, versaoAntes, profase, "NORMAL", List.of(), antes + 1)
                    .andReturn().getResponse().getStatus()).as("edição de %s", ator.nome()).isEqualTo(ator.escrita());
            assertThat(desfazer(ator.usuario(), lamina, UUID.randomUUID(), profase, "NORMAL", List.of(), 1)
                    .andReturn().getResponse().getStatus()).as("desfazer de %s", ator.nome()).isEqualTo(ator.escrita());
            if (ator.escrita() == 200) {
                assertThat(quantidadeNoBanco(id)).as(ator.nome()).isEqualTo(antes);
                assertThat(versaoNoBanco(id)).as(ator.nome()).isEqualTo(versaoAntes + 2);
            } else {
                assertThat(quantidadeNoBanco(id)).as(ator.nome()).isEqualTo(antes);
                assertThat(versaoNoBanco(id)).as(ator.nome()).isEqualTo(versaoAntes);
            }
        }
    }

    private UUID registro(Usuario dono, UUID lamina, UUID fase, String status, List<UUID> tipos, int quantidade)
            throws Exception {
        UUID id = UUID.randomUUID();
        registrar(dono, lamina, id, fase, status, tipos, quantidade, null).andExpect(status().isCreated());
        // Hora oficial distinta e crescente, para a ordem do histórico e do desfazer não depender do relógio.
        jdbcTemplate.update("UPDATE registro_contagem SET registrado_em = ? WHERE id = ?",
                Timestamp.from(BASE.plusSeconds(++relogio)), id);
        return id;
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
        }
        return requisicaoAutenticada(sessao(usuario), post(LAMINAS + "/{id}/registros", lamina)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo)));
    }

    private JsonNode historico(Usuario usuario, UUID lamina, String consulta) throws Exception {
        return json(requisicaoAutenticada(sessao(usuario),
                get(LAMINAS + "/{id}/registros" + (consulta.isEmpty() ? "" : "?" + consulta), lamina))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode lamina(Usuario usuario, UUID id) throws Exception {
        return json(requisicaoAutenticada(sessao(usuario), get(LAMINAS + "/{id}", id))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode listarLaminas(Usuario usuario) throws Exception {
        return json(requisicaoAutenticada(sessao(usuario), get(LAMINAS + "?size=100"))
                .andExpect(status().isOk()).andReturn());
    }

    private long totalNaListagem(Usuario usuario, UUID id) throws Exception {
        for (JsonNode item : listarLaminas(usuario).path("content")) {
            if (item.path("id").asText().equals(id.toString())) {
                return item.path("totalCelulas").asLong();
            }
        }
        throw new AssertionError("Lâmina ausente da listagem: " + id);
    }

    private JsonNode resumo(Usuario usuario, UUID lamina) throws Exception {
        return json(requisicaoAutenticada(sessao(usuario), get(LAMINAS + "/{id}/resumo", lamina))
                .andExpect(status().isOk()).andReturn());
    }

    private static Map<String, Object> corpoEdicao(Long versao, UUID fase, String status, List<UUID> tipos,
                                                   Integer quantidade) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("versaoEsperada", versao);
        corpo.put("faseId", fase.toString());
        corpo.put("status", status);
        corpo.put("tipoIds", tipos.stream().map(UUID::toString).toList());
        corpo.put("quantidade", quantidade);
        return corpo;
    }

    private ResultActions editar(Usuario usuario, UUID lamina, UUID registro, long versao, UUID fase, String status,
                                 List<UUID> tipos, int quantidade) throws Exception {
        return patchBruto(usuario, lamina, registro,
                objectMapper.writeValueAsString(corpoEdicao(versao, fase, status, tipos, quantidade)));
    }

    private JsonNode editarOk(Usuario usuario, UUID lamina, UUID registro, long versao, UUID fase, String status,
                              List<UUID> tipos, int quantidade) throws Exception {
        return json(editar(usuario, lamina, registro, versao, fase, status, tipos, quantidade)
                .andExpect(status().isOk()).andReturn());
    }

    private ResultActions patchBruto(Usuario usuario, UUID lamina, UUID registro, String corpo) throws Exception {
        MockHttpServletRequestBuilder requisicao = patch(LAMINAS + "/{l}/registros/{r}", lamina, registro)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
        return requisicaoAutenticada(sessao(usuario), requisicao);
    }

    private static Map<String, Object> corpoDesfazer(UUID id, UUID fase, String status, List<UUID> tipos,
                                                     Integer quantidade) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", id.toString());
        corpo.put("faseId", fase.toString());
        corpo.put("status", status);
        corpo.put("tipoIds", tipos.stream().map(UUID::toString).toList());
        corpo.put("quantidade", quantidade);
        return corpo;
    }

    private ResultActions desfazer(Usuario usuario, UUID lamina, UUID id, UUID fase, String status, List<UUID> tipos,
                                   int quantidade) throws Exception {
        return desfazerBruto(usuario, lamina,
                objectMapper.writeValueAsString(corpoDesfazer(id, fase, status, tipos, quantidade)));
    }

    private ResultActions desfazerBruto(Usuario usuario, UUID lamina, String corpo) throws Exception {
        return requisicaoAutenticada(sessao(usuario), post(LAMINAS + "/{id}/registros/desfazer", lamina)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private static void verificarAfetado(JsonNode afetado, UUID registro, int restante, boolean excluido) {
        assertThat(afetado.path("id").asText()).isEqualTo(registro.toString());
        assertThat(afetado.path("quantidadeRestante").asInt()).isEqualTo(restante);
        assertThat(afetado.path("excluido").asBoolean()).isEqualTo(excluido);
    }

    private static List<String> idsDe(JsonNode pagina) {
        List<String> ids = new ArrayList<>();
        pagina.path("content").forEach(item -> ids.add(item.path("id").asText()));
        return ids;
    }

    private static List<String> ordenados(UUID... ids) {
        return java.util.Arrays.stream(ids).map(UUID::toString).sorted().toList();
    }

    private boolean excluido(UUID registro) {
        return jdbcTemplate.queryForObject("SELECT excluido_em IS NOT NULL FROM registro_contagem WHERE id = ?",
                Boolean.class, registro);
    }

    private int quantidadeNoBanco(UUID registro) {
        return jdbcTemplate.queryForObject("SELECT quantidade FROM registro_contagem WHERE id = ?",
                Integer.class, registro);
    }

    private long versaoNoBanco(UUID registro) {
        return jdbcTemplate.queryForObject("SELECT version FROM registro_contagem WHERE id = ?", Long.class, registro);
    }

    private String hashDaCriacao(UUID registro) {
        return jdbcTemplate.queryForObject("SELECT hash_conteudo_criacao FROM registro_contagem WHERE id = ?",
                String.class, registro);
    }
}
