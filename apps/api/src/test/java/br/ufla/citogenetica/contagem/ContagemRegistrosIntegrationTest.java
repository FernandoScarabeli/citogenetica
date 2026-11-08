package br.ufla.citogenetica.contagem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.ufla.citogenetica.usuario.internal.domain.Usuario;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContagemRegistrosIntegrationTest extends AbstractContagemIntegrationTest {

    private static final double EXATO = 1e-9;

    private record Ator(String nome, Usuario usuario, int post, int resumo) {
    }

    // ---------------------------------------------------------------- cálculos oficiais (casos fechados)

    @Test
    void laminaVaziaTemTodasAsFasesZeradasEIndiceZero() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);

        JsonNode resumo = resumo(dono, lamina);

        assertThat(resumo.path("laminaId").asText()).isEqualTo(lamina.toString());
        assertThat(resumo.path("total").asLong()).isZero();
        assertThat(resumo.path("anormais").asLong()).isZero();
        assertThat(resumo.path("indiceMitotico").asDouble()).isZero();
        assertThat(nomesDasFases(resumo)).containsExactly("Interfase", "Prófase", "Metáfase", "Anáfase", "Telófase");
        for (JsonNode fase : resumo.path("fases")) {
            assertThat(fase.path("normais").asLong()).isZero();
            assertThat(fase.path("anormais").asLong()).isZero();
            assertThat(fase.path("total").asLong()).isZero();
            assertThat(fase.path("percentualAnormais").asDouble()).isZero();
            assertThat(fase.path("tipos")).isEmpty();
        }
        assertThat(resumo.path("fases").get(0).path("ordem").asInt()).isEqualTo(1);
        assertThat(resumo.path("fases").get(4).path("ordem").asInt()).isEqualTo(5);
    }

    /**
     * Calculado à mão a partir de summary() em domain.ts (mitose; só a Interfase não conta como divisão):
     * Interfase 10 normais + 5 anormais = 15; Prófase 20 + 4 = 24; Metáfase 6 + 0 = 6; Anáfase 0 + 5 = 5.
     * total = 50, anormais = 14, divisão = 24 + 6 + 5 = 35, IM = 35 / 50 * 100 = 70.
     */
    @Test
    void mitoseComInterfaseForaDoIndiceMitotico() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID interfase = fase("mitose", "Interfase");
        UUID profase = fase("mitose", "Prófase");
        UUID anafase = fase("mitose", "Anáfase");
        UUID apoptoticos = tipoFixo("mitose", "Interfase", "Núcleos apoptóticos (condensados)");
        UUID stickiness = tipoFixo("mitose", "Prófase", "Stickiness");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID ponteComFragmento = tipoFixo("mitose", "Anáfase", "Ponte com fragmento");

        registrar(dono, lamina, interfase, "NORMAL", List.of(), 10).andExpect(status().isCreated());
        registrar(dono, lamina, interfase, "ANORMAL", List.of(apoptoticos), 5).andExpect(status().isCreated());
        registrar(dono, lamina, profase, "NORMAL", List.of(), 20).andExpect(status().isCreated());
        registrar(dono, lamina, profase, "ANORMAL", List.of(stickiness), 4).andExpect(status().isCreated());
        registrar(dono, lamina, fase("mitose", "Metáfase"), "NORMAL", List.of(), 6).andExpect(status().isCreated());
        registrar(dono, lamina, anafase, "ANORMAL", List.of(ponte), 3).andExpect(status().isCreated());
        registrar(dono, lamina, anafase, "ANORMAL", List.of(ponteComFragmento), 2).andExpect(status().isCreated());

        JsonNode resumo = resumo(dono, lamina);

        assertThat(resumo.path("total").asLong()).isEqualTo(50);
        assertThat(resumo.path("anormais").asLong()).isEqualTo(14);
        assertThat(resumo.path("indiceMitotico").asDouble()).isCloseTo(70.0, within(EXATO));
        // Sem arredondamento: o mesmo resultado da conta do front, bit a bit.
        assertThat(resumo.path("indiceMitotico").asDouble()).isEqualTo(35.0 / 50 * 100);
        JsonNode fases = resumo.path("fases");
        assertThat(fases).hasSize(5);
        verificarFase(fases.get(0), "Interfase", 10, 5, 15, 5.0 / 15 * 100);
        verificarFase(fases.get(1), "Prófase", 20, 4, 24, 4.0 / 24 * 100);
        verificarFase(fases.get(2), "Metáfase", 6, 0, 6, 0);
        verificarFase(fases.get(3), "Anáfase", 0, 5, 5, 100);
        verificarFase(fases.get(4), "Telófase", 0, 0, 0, 0);
        verificarTipos(fases.get(0), List.of("Núcleos apoptóticos (condensados)"), List.of(5L), List.of(5.0 / 15 * 100));
        verificarTipos(fases.get(1), List.of("Stickiness"), List.of(4L), List.of(4.0 / 24 * 100));
        assertThat(fases.get(2).path("tipos")).isEmpty();
        verificarTipos(fases.get(3), List.of("Ponte", "Ponte com fragmento"), List.of(3L, 2L), List.of(60.0, 40.0));
        assertThat(fases.get(1).path("tipos").get(0).path("tipoId").asText()).isEqualTo(stickiness.toString());
        assertThat(fases.get(0).path("faseId").asText()).isEqualTo(interfase.toString());
    }

    /**
     * Meiose: nenhuma fase se chama Interfase, então todas contam como divisão.
     * Leptóteno/Zigóteno 8 normais; Metáfase I 4 normais + 2 anormais; Tétrade 6 anormais.
     * total = 20, anormais = 8, IM = 20 / 20 * 100 = 100.
     */
    @Test
    void meioseContaTodasAsFasesComoDivisao() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null, "meiose");

        registrar(dono, lamina, fase("meiose", "Leptóteno/Zigóteno"), "NORMAL", List.of(), 8).andExpect(status().isCreated());
        registrar(dono, lamina, fase("meiose", "Metáfase I"), "NORMAL", List.of(), 4).andExpect(status().isCreated());
        registrar(dono, lamina, fase("meiose", "Metáfase I"), "ANORMAL",
                List.of(tipoFixo("meiose", "Metáfase I", "Cromossomos não orientados")), 2).andExpect(status().isCreated());
        registrar(dono, lamina, fase("meiose", "Tétrade"), "ANORMAL",
                List.of(tipoFixo("meiose", "Tétrade", "Micronúcleo")), 6).andExpect(status().isCreated());

        JsonNode resumo = resumo(dono, lamina);

        assertThat(resumo.path("fases")).hasSize(11);
        assertThat(resumo.path("total").asLong()).isEqualTo(20);
        assertThat(resumo.path("anormais").asLong()).isEqualTo(8);
        assertThat(resumo.path("indiceMitotico").asDouble()).isEqualTo(100.0);
        verificarFase(resumo.path("fases").get(3), "Metáfase I", 4, 2, 6, 2.0 / 6 * 100);
        verificarFase(resumo.path("fases").get(10), "Tétrade", 0, 6, 6, 100);
        verificarTipos(resumo.path("fases").get(3), List.of("Cromossomos não orientados"), List.of(2L),
                List.of(2.0 / 6 * 100));
    }

    @Test
    void loteContaComoUmRegistroESomaAQuantidade() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID metafase = fase("mitose", "Metáfase");

        registrar(dono, lamina, metafase, "NORMAL", List.of(), 1000).andExpect(status().isCreated());
        registrar(dono, lamina, metafase, "NORMAL", List.of(), 7).andExpect(status().isCreated());
        registrar(dono, lamina, metafase, "NORMAL", List.of(), 1).andExpect(status().isCreated());

        JsonNode resumo = resumo(dono, lamina);
        assertThat(resumo.path("total").asLong()).isEqualTo(1008);
        verificarFase(resumo.path("fases").get(2), "Metáfase", 1008, 0, 1008, 0);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM registro_contagem WHERE lamina_id = ?",
                Integer.class, lamina)).isEqualTo(3);
    }

    /**
     * Anáfase: um registro anormal de 4 células com Stickiness e Ponte, mais 4 normais.
     * normais 4, anormais 4, total 8, % anormais = 50; cada tipo conta 4 (50 %). A soma dos tipos (8) passa dos anormais.
     */
    @Test
    void registroComDoisTiposContaUmaVezEmAnormaisEUmaVezEmCadaTipo() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID anafase = fase("mitose", "Anáfase");
        UUID stickiness = tipoFixo("mitose", "Anáfase", "Stickiness");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");

        registrar(dono, lamina, anafase, "ANORMAL", List.of(ponte, stickiness), 4).andExpect(status().isCreated());
        registrar(dono, lamina, anafase, "NORMAL", List.of(), 4).andExpect(status().isCreated());

        JsonNode resumo = resumo(dono, lamina);
        JsonNode fase = resumo.path("fases").get(3);
        assertThat(resumo.path("anormais").asLong()).isEqualTo(4);
        assertThat(resumo.path("total").asLong()).isEqualTo(8);
        assertThat(resumo.path("indiceMitotico").asDouble()).isEqualTo(100.0);
        verificarFase(fase, "Anáfase", 4, 4, 8, 50);
        // Ordem estável: a do protocolo (Stickiness vem antes de Ponte).
        verificarTipos(fase, List.of("Stickiness", "Ponte"), List.of(4L, 4L), List.of(50.0, 50.0));
        long somaDosTipos = 0;
        for (JsonNode tipo : fase.path("tipos")) {
            somaDosTipos += tipo.path("contagem").asLong();
        }
        assertThat(somaDosTipos).isGreaterThan(fase.path("anormais").asLong());
    }

    /** Prófase: 3 anormais com Micronúcleo (fixo, ordem 4) e um tipo personalizado, que vem depois dos fixos. */
    @Test
    void tipoPersonalizadoApareceNoResumoDepoisDosFixos() throws Exception {
        Cenario cenario = montarCenario();
        Usuario dono = cenario.pesquisador();
        UUID lamina = criarLamina(dono, cenario.laboratorio());
        UUID profase = fase("mitose", "Prófase");
        UUID micronucleo = tipoFixo("mitose", "Prófase", "Micronúcleo");
        UUID personalizado = tipoPersonalizado(profase, dono, cenario.laboratorio(), "Meu tipo");

        registrar(dono, lamina, profase, "ANORMAL", List.of(personalizado, micronucleo), 3).andExpect(status().isCreated());
        registrar(dono, lamina, profase, "NORMAL", List.of(), 9).andExpect(status().isCreated());

        JsonNode fase = resumo(dono, lamina).path("fases").get(1);
        verificarFase(fase, "Prófase", 9, 3, 12, 3.0 / 12 * 100);
        verificarTipos(fase, List.of("Micronúcleo", "Meu tipo"), List.of(3L, 3L), List.of(25.0, 25.0));
        assertThat(fase.path("tipos").get(1).path("tipoId").asText()).isEqualTo(personalizado.toString());
    }

    @Test
    void registroExcluidoNoBancoEhIgnoradoNoResumo() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID profase = fase("mitose", "Prófase");
        UUID stickiness = tipoFixo("mitose", "Prófase", "Stickiness");
        UUID mantido = UUID.randomUUID();
        UUID excluido = UUID.randomUUID();
        registrar(dono, lamina, mantido, profase, "ANORMAL", List.of(stickiness), 5, null).andExpect(status().isCreated());
        registrar(dono, lamina, excluido, profase, "ANORMAL", List.of(stickiness), 50, null).andExpect(status().isCreated());
        registrar(dono, lamina, fase("mitose", "Metáfase"), "NORMAL", List.of(), 11).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE registro_contagem SET excluido_em = NOW() WHERE id = ?", excluido);

        JsonNode resumo = resumo(dono, lamina);

        assertThat(resumo.path("total").asLong()).isEqualTo(16);
        assertThat(resumo.path("anormais").asLong()).isEqualTo(5);
        verificarFase(resumo.path("fases").get(1), "Prófase", 0, 5, 5, 100);
        verificarTipos(resumo.path("fases").get(1), List.of("Stickiness"), List.of(5L), List.of(100.0));
        // Só 11 de 16 células estão em fases de divisão (Prófase 5 + Metáfase 11 = 16, nenhuma na Interfase).
        assertThat(resumo.path("indiceMitotico").asDouble()).isEqualTo(100.0);
    }

    @Test
    void resumoDeUmaLaminaNaoMisturaRegistrosDeOutra() throws Exception {
        Usuario dono = criarUsuario();
        UUID primeira = criarLamina(dono, null);
        UUID segunda = criarLamina(dono, null);
        registrar(dono, primeira, fase("mitose", "Telófase"), "NORMAL", List.of(), 3).andExpect(status().isCreated());
        registrar(dono, segunda, fase("mitose", "Telófase"), "NORMAL", List.of(), 40).andExpect(status().isCreated());

        assertThat(resumo(dono, primeira).path("total").asLong()).isEqualTo(3);
        assertThat(resumo(dono, segunda).path("total").asLong()).isEqualTo(40);
    }

    // ---------------------------------------------------------------- limite por registro

    @Test
    void limiteDe1000AceitaAte1000ERecusaMais() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Prófase");

        registrar(dono, lamina, fase, "NORMAL", List.of(), 1000).andExpect(status().isCreated());
        MvcResult recusado = registrar(dono, lamina, fase, "NORMAL", List.of(), 1001)
                .andExpect(status().isBadRequest()).andReturn();

        assertThat(json(recusado).path("message").asText()).contains("1000");
        assertThat(contarRegistros(lamina)).isEqualTo(1);
    }

    @Test
    void limiteAlteradoNoBancoValeNaProximaRequisicaoSemReiniciar() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Prófase");
        try {
            jdbcTemplate.update("UPDATE parametro_contagem SET limite_celulas_por_registro = 5 WHERE id = 1");
            MvcResult recusado = registrar(dono, lamina, fase, "NORMAL", List.of(), 6)
                    .andExpect(status().isBadRequest()).andReturn();
            assertThat(json(recusado).path("message").asText()).contains("5").doesNotContain("1000");
            registrar(dono, lamina, fase, "NORMAL", List.of(), 5).andExpect(status().isCreated());

            jdbcTemplate.update("UPDATE parametro_contagem SET limite_celulas_por_registro = 2000 WHERE id = 1");
            registrar(dono, lamina, fase, "NORMAL", List.of(), 2000).andExpect(status().isCreated());
            registrar(dono, lamina, fase, "NORMAL", List.of(), 2001).andExpect(status().isBadRequest());
        } finally {
            jdbcTemplate.update("UPDATE parametro_contagem SET limite_celulas_por_registro = 1000 WHERE id = 1");
        }
        registrar(dono, lamina, fase, "NORMAL", List.of(), 1001).andExpect(status().isBadRequest());
    }

    @Test
    void bancoGuardaLinhaUnicaDeParametrosEExigeLimitePositivo() {
        assertThat(jdbcTemplate.queryForObject("SELECT limite_celulas_por_registro FROM parametro_contagem WHERE id = 1",
                Integer.class)).isEqualTo(1000);
        for (String sql : List.of("INSERT INTO parametro_contagem (id, limite_celulas_por_registro) VALUES (2, 10)",
                "UPDATE parametro_contagem SET limite_celulas_por_registro = 0 WHERE id = 1",
                "UPDATE parametro_contagem SET limite_celulas_por_registro = -1 WHERE id = 1")) {
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(sql));
        }
    }

    @Test
    void bancoRecusaRegistroComDadosInvalidosESemTetoDeQuantidade() {
        Usuario dono = criarUsuario();
        UUID lamina = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO lamina (id, version, created_at, last_modified_at, dono_usuario_id, "
                + "protocolo_versao_id, nome) VALUES (?, 0, NOW(), NOW(), ?, ?, 'L')",
                lamina, dono.getId(), versaoPublicada("mitose"));
        UUID fase = fase("mitose", "Prófase");
        String hashValido = "a".repeat(64);
        String insert = "INSERT INTO registro_contagem (id, version, created_at, last_modified_at, lamina_id, fase_id, "
                + "status, quantidade, registrado_em, hash_conteudo_criacao) VALUES (?, 0, NOW(), NOW(), ?, ?, ?, ?, NOW(), ?)";

        for (Object[] invalido : new Object[][]{{"NORMAL", 0, hashValido}, {"NORMAL", -1, hashValido},
                {"talvez", 1, hashValido}, {"normal", 1, hashValido}, {"NORMAL", 1, "A".repeat(64)},
                {"NORMAL", 1, "a".repeat(63)}}) {
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(insert, UUID.randomUUID(), lamina, fase, invalido[0], invalido[1], invalido[2]));
        }
        // Sem teto no banco: o limite por registro é configurável.
        jdbcTemplate.update(insert, UUID.randomUUID(), lamina, fase, "NORMAL", 5_000_000, hashValido);
        List<String> indices = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'registro_contagem' AND indexname LIKE 'idx_%'",
                String.class);
        assertThat(indices).hasSize(1).allMatch(def -> def.contains("excluido_em IS NULL"));
    }

    // ---------------------------------------------------------------- validação

    @Test
    void corpoInvalidoResponde400SemGravarNada() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Anáfase");
        UUID tipo = tipoFixo("mitose", "Anáfase", "Ponte");
        List<Map<String, Object>> invalidos = new ArrayList<>();
        invalidos.add(com(fase, "status", null));
        invalidos.add(com(fase, "status", "normal"));
        invalidos.add(com(fase, "status", "TALVEZ"));
        invalidos.add(com(fase, "status", 1));
        invalidos.add(com(fase, "status", "NORMAL", "tipoIds", List.of(tipo.toString())));
        invalidos.add(com(fase, "status", "ANORMAL"));
        invalidos.add(com(fase, "status", "ANORMAL", "tipoIds", List.of()));
        invalidos.add(com(fase, "status", "ANORMAL", "tipoIds", List.of(tipo.toString(), tipo.toString())));
        invalidos.add(com(fase, "status", "ANORMAL", "tipoIds", List.of("nao-e-uuid")));
        invalidos.add(com(fase, "status", "ANORMAL", "tipoIds", List.of(tipo.toString().toUpperCase())));
        invalidos.add(com(fase, "status", "ANORMAL", "tipoIds", java.util.Arrays.asList(tipo.toString(), null)));
        invalidos.add(com(fase, "status", "ANORMAL", "tipoIds", "texto"));
        invalidos.add(com(fase, "status", "ANORMAL", "tipoIds", cinquentaEUm()));
        invalidos.add(com(fase, "quantidade", null));
        invalidos.add(com(fase, "quantidade", 0));
        invalidos.add(com(fase, "quantidade", -4));
        invalidos.add(com(fase, "quantidade", 1001));
        invalidos.add(com(fase, "id", "nao-e-uuid"));
        invalidos.add(com(fase, "id", null));
        invalidos.add(com(fase, "faseId", null));
        invalidos.add(com(fase, "faseId", "nao-e-uuid"));
        invalidos.add(com(fase, "fusoAparelho", "Marte/Fobos"));
        invalidos.add(com(fase, "fusoAparelho", "fuso\u0000"));
        invalidos.add(com(fase, "fusoAparelho", ""));
        invalidos.add(com(fase, "fusoAparelho", " "));
        invalidos.add(com(fase, "fusoAparelho", "UTC+99:00"));
        invalidos.add(com(fase, "fusoAparelho", "A".repeat(65)));
        invalidos.add(com(fase, "fusoAparelho", "America/" + "S".repeat(60)));
        invalidos.add(com(fase, "horaAparelho", "ontem"));
        invalidos.add(com(fase, "horaAparelho", ""));
        invalidos.add(com(fase, "horaAparelho", "2026-13-01T00:00:00Z"));
        invalidos.add(com(fase, "horaAparelho", "2026-11-08"));
        invalidos.add(com(fase, "horaAparelho", 1762560000));
        invalidos.add(com(fase, "horaAparelho", "+999999999-12-31T23:59:59Z"));
        invalidos.add(com(fase, "horaAparelho", "-999999999-01-01T00:00:00Z"));
        invalidos.add(com(fase, "horaAparelho", "1969-12-31T23:59:59Z"));

        for (Map<String, Object> corpo : invalidos) {
            assertThat(postarBruto(dono, lamina, objectMapper.writeValueAsString(corpo)).andReturn()
                    .getResponse().getStatus()).as("corpo %s", corpo).isEqualTo(400);
        }
        for (String quantidade : List.of("1.5", "2.0", "\"5\"", "2147483648", "true", "[]", "1e2")) {
            assertThat(postarBruto(dono, lamina, "{\"id\":\"" + UUID.randomUUID() + "\",\"faseId\":\"" + fase
                    + "\",\"status\":\"NORMAL\",\"quantidade\":" + quantidade + "}").andReturn().getResponse()
                    .getStatus()).as("quantidade %s", quantidade).isEqualTo(400);
        }
        postarBruto(dono, lamina, "{").andExpect(status().isBadRequest());
        postarBruto(dono, lamina, "").andExpect(status().isBadRequest());
        // \u0000 dentro do JSON, como o cliente enviaria.
        postarBruto(dono, lamina, "{\"id\":\"" + UUID.randomUUID() + "\",\"faseId\":\"" + fase
                + "\",\"status\":\"NORMAL\",\"quantidade\":1,\"fusoAparelho\":\"UTC\\u0000\"}")
                .andExpect(status().isBadRequest());
        requisicaoAutenticada(sessao(dono), post(LAMINAS + "/{id}/registros", "nao-e-uuid")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo(fase, "NORMAL", 1))))
                .andExpect(status().isBadRequest());
        assertThat(contarRegistros(lamina)).isZero();
    }

    @Test
    void mensagensDeErroSaoVerdadeirasParaOCampoRecusado() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Anáfase");

        assertThat(json(registrarCorpo(dono, lamina, com(fase, "fusoAparelho", "Marte/Fobos"))
                .andExpect(status().isBadRequest()).andReturn()).path("message").asText()).contains("fuso");
        assertThat(json(registrarCorpo(dono, lamina, com(fase, "horaAparelho", "ontem"))
                .andExpect(status().isBadRequest()).andReturn()).path("message").asText()).contains("hora");
        assertThat(json(registrarCorpo(dono, lamina, com(fase, "status", "ANORMAL"))
                .andExpect(status().isBadRequest()).andReturn()).path("message").asText()).contains("tipo");
    }

    @Test
    void horaEFusoDoAparelhoSaoGuardadosSemAlterarARegistradoEmOficial() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Telófase");

        for (String fuso : List.of("America/Sao_Paulo", "-03:00", "Z", "UTC", "GMT+2", "+05:30")) {
            Map<String, Object> corpo = corpo(fase, "NORMAL", 1);
            corpo.put("horaAparelho", "2020-01-02T03:04:05.123456Z");
            corpo.put("fusoAparelho", fuso);
            corpo.put("registradoEm", "2001-01-01T00:00:00Z");
            JsonNode criado = json(registrarCorpo(dono, lamina, corpo).andExpect(status().isCreated()).andReturn());
            assertThat(criado.path("horaAparelho").asText()).isEqualTo("2020-01-02T03:04:05.123456Z");
            assertThat(criado.path("fusoAparelho").asText()).isEqualTo(fuso);
            // O cliente não define a hora oficial: ela vem do relógio do servidor.
            assertThat(criado.path("registradoEm").asText()).doesNotStartWith("2001").doesNotStartWith("2020");
        }
        Map<String, Object> semInformativos = corpo(fase, "NORMAL", 1);
        JsonNode criado = json(registrarCorpo(dono, lamina, semInformativos).andExpect(status().isCreated()).andReturn());
        assertThat(criado.path("horaAparelho").isNull()).isTrue();
        assertThat(criado.path("fusoAparelho").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registro_contagem WHERE lamina_id = ? AND hora_aparelho IS NULL "
                        + "AND fuso_aparelho IS NULL", Integer.class, lamina)).isEqualTo(1);
        // Hora do aparelho muito diferente da oficial não muda cálculo nenhum.
        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(7);
    }

    @Test
    void respostaDeCriacaoTemTodosOsCamposELeituraNoBancoBate() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Anáfase");
        UUID stickiness = tipoFixo("mitose", "Anáfase", "Stickiness");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID id = UUID.randomUUID();

        registrar(dono, lamina, id, fase, "ANORMAL", List.of(ponte, stickiness), 12, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.versao").value(0))
                .andExpect(jsonPath("$.laminaId").value(lamina.toString()))
                .andExpect(jsonPath("$.faseId").value(fase.toString()))
                .andExpect(jsonPath("$.status").value("ANORMAL"))
                .andExpect(jsonPath("$.tipoIds[0]").value(ordenados(ponte, stickiness).get(0)))
                .andExpect(jsonPath("$.tipoIds[1]").value(ordenados(ponte, stickiness).get(1)))
                .andExpect(jsonPath("$.quantidade").value(12))
                .andExpect(jsonPath("$.registradoEm").isNotEmpty())
                .andExpect(jsonPath("$.horaAparelho").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.fusoAparelho").value(org.hamcrest.Matchers.nullValue()));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM registro_contagem_tipo WHERE registro_id = ?",
                Integer.class, id)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT hash_conteudo_criacao FROM registro_contagem WHERE id = ?",
                String.class, id)).matches("[0-9a-f]{64}");
        // A criação não aumenta a versão da lâmina.
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM lamina WHERE id = ?", Long.class, lamina)).isZero();
    }

    @Test
    void faseETiposPrecisamPertencerAoProtocoloDaLaminaComMesmaRespostaParaTodosOsCasos() throws Exception {
        Cenario cenario = montarCenario();
        Usuario dono = cenario.pesquisador();
        Usuario outro = cenario.membro();
        UUID avulsa = criarLamina(dono, null);
        UUID deLab = criarLamina(dono, cenario.laboratorio());
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID tipoDeOutraFase = tipoFixo("mitose", "Telófase", "Ponte");
        UUID tipoDeOutroProtocolo = tipoFixo("meiose", "Telófase II", "Ponte");
        UUID personalizadoDeOutro = tipoPersonalizado(anafase, outro, cenario.laboratorio(), "Do outro");
        UUID personalizadoDeOutroAvulso = tipoPersonalizado(anafase, outro, null, "Do outro avulso");
        UUID personalizadoDeOutroEscopo = tipoPersonalizado(anafase, dono, null, "Meu avulso");
        UUID personalizadoDoLab = tipoPersonalizado(anafase, dono, cenario.laboratorio(), "Meu do lab");
        UUID personalizadoOutraFase = tipoPersonalizado(fase("mitose", "Telófase"), dono, null, "Meu em outra fase");
        UUID desconhecido = UUID.randomUUID();

        MvcResult referencia = registrar(dono, avulsa, anafase, "ANORMAL", List.of(desconhecido), 1)
                .andExpect(status().isBadRequest()).andReturn();
        List<ResultActions> recusas = List.of(
                registrar(dono, avulsa, anafase, "ANORMAL", List.of(tipoDeOutraFase), 1),
                registrar(dono, avulsa, anafase, "ANORMAL", List.of(tipoDeOutroProtocolo), 1),
                registrar(dono, avulsa, anafase, "ANORMAL", List.of(personalizadoDeOutro), 1),
                registrar(dono, avulsa, anafase, "ANORMAL", List.of(personalizadoDeOutroAvulso), 1),
                registrar(dono, avulsa, anafase, "ANORMAL", List.of(personalizadoDoLab), 1),
                registrar(dono, deLab, anafase, "ANORMAL", List.of(personalizadoDeOutroEscopo), 1),
                registrar(dono, deLab, anafase, "ANORMAL", List.of(personalizadoDeOutro), 1),
                registrar(dono, avulsa, anafase, "ANORMAL", List.of(personalizadoOutraFase), 1),
                registrar(dono, avulsa, anafase, "ANORMAL", List.of(ponte, desconhecido), 1),
                registrar(dono, avulsa, fase("meiose", "Telófase II"), "ANORMAL", List.of(), 1),
                registrar(dono, avulsa, desconhecido, "NORMAL", List.of(), 1));
        for (ResultActions recusa : recusas) {
            MvcResult resposta = recusa.andReturn();
            if (resposta.getResponse().getStatus() == 400 && !json(resposta).path("message").asText().contains("tipo")) {
                assertThat(corpoSemHorario(resposta)).isEqualTo(corpoSemHorario(referencia));
            }
        }
        // Fase de outro protocolo, normal ou anormal, e fase desconhecida: mesma resposta que tipo desconhecido.
        MvcResult faseAlheia = registrar(dono, avulsa, fase("meiose", "Telófase II"), "NORMAL", List.of(), 1)
                .andExpect(status().isBadRequest()).andReturn();
        MvcResult faseDesconhecida = registrar(dono, avulsa, desconhecido, "NORMAL", List.of(), 1)
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(corpoSemHorario(faseAlheia)).isEqualTo(corpoSemHorario(referencia));
        assertThat(corpoSemHorario(faseDesconhecida)).isEqualTo(corpoSemHorario(referencia));
        for (UUID invalido : List.of(tipoDeOutraFase, tipoDeOutroProtocolo, personalizadoDeOutro,
                personalizadoDeOutroAvulso, personalizadoDoLab, personalizadoOutraFase)) {
            assertThat(corpoSemHorario(registrar(dono, avulsa, anafase, "ANORMAL", List.of(invalido), 1)
                    .andExpect(status().isBadRequest()).andReturn())).isEqualTo(corpoSemHorario(referencia));
        }
        for (UUID invalido : List.of(personalizadoDeOutroEscopo, personalizadoDeOutro, personalizadoDeOutroAvulso)) {
            assertThat(corpoSemHorario(registrar(dono, deLab, anafase, "ANORMAL", List.of(invalido), 1)
                    .andExpect(status().isBadRequest()).andReturn())).isEqualTo(corpoSemHorario(referencia));
        }
        assertThat(contarRegistros(avulsa) + contarRegistros(deLab)).isZero();

        // Os válidos: fixo, personalizado do dono no mesmo escopo.
        registrar(dono, avulsa, anafase, "ANORMAL", List.of(ponte, personalizadoDeOutroEscopo), 1)
                .andExpect(status().isCreated());
        registrar(dono, deLab, anafase, "ANORMAL", List.of(ponte, personalizadoDoLab), 1)
                .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- acesso

    @Test
    void matrizDeAcessoNaCriacaoENoResumo() throws Exception {
        Cenario cenario = montarCenario();
        UUID lamina = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        UUID fase = fase("mitose", "Prófase");
        registrar(cenario.pesquisador(), lamina, fase, "NORMAL", List.of(), 2).andExpect(status().isCreated());

        List<Ator> atores = List.of(
                new Ator("dono", cenario.pesquisador(), 201, 200),
                new Ator("orientador ativo", cenario.orientador(), 403, 200),
                new Ator("coorientador", cenario.coorientador(), 403, 200),
                new Ator("orientador com vínculo encerrado", cenario.orientadorEncerrado(), 403, 403),
                new Ator("admin do laboratório", cenario.admin(), 403, 200),
                new Ator("membro comum", cenario.membro(), 403, 403),
                new Ator("ex-membro", cenario.exMembro(), 404, 404),
                new Ator("pessoa de fora", cenario.fora(), 404, 404),
                new Ator("ADMIN global de fora", cenario.adminGlobal(), 404, 404));
        verificarMatriz(atores, lamina, fase);
    }

    @Test
    void laboratorioDesativadoPermiteLerMasNinguemRegistra() throws Exception {
        Cenario cenario = montarCenario();
        UUID lamina = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        UUID fase = fase("mitose", "Prófase");
        registrar(cenario.pesquisador(), lamina, fase, "NORMAL", List.of(), 2).andExpect(status().isCreated());
        laboratorioService.desativar(cenario.laboratorio(), cenario.adminGlobal().getId());

        List<Ator> atores = List.of(
                new Ator("dono", cenario.pesquisador(), 403, 200),
                new Ator("orientador ativo", cenario.orientador(), 403, 200),
                new Ator("coorientador", cenario.coorientador(), 403, 200),
                new Ator("admin do laboratório", cenario.admin(), 403, 200),
                new Ator("membro comum", cenario.membro(), 403, 403),
                new Ator("ex-membro", cenario.exMembro(), 404, 404),
                new Ator("pessoa de fora", cenario.fora(), 404, 404),
                new Ator("ADMIN global de fora", cenario.adminGlobal(), 404, 404));
        verificarMatriz(atores, lamina, fase);
    }

    @Test
    void laminaAvulsaEhSoDoDonoParaRegistrarELerOResumo() throws Exception {
        Cenario cenario = montarCenario();
        UUID lamina = criarLamina(cenario.pesquisador(), null);
        UUID fase = fase("mitose", "Prófase");

        for (Usuario outro : List.of(cenario.admin(), cenario.orientador(), cenario.membro(), cenario.fora(),
                cenario.adminGlobal(), cenario.exMembro())) {
            registrar(outro, lamina, fase, "NORMAL", List.of(), 1).andExpect(status().isNotFound());
            requisicaoAutenticada(sessao(outro), get(LAMINAS + "/{id}/resumo", lamina)).andExpect(status().isNotFound());
        }
        assertThat(contarRegistros(lamina)).isZero();
        registrar(cenario.pesquisador(), lamina, fase, "NORMAL", List.of(), 1).andExpect(status().isCreated());
        assertThat(resumo(cenario.pesquisador(), lamina).path("total").asLong()).isEqualTo(1);
    }

    @Test
    void laminaExcluidaOuInexistenteEh404InclusiveParaODono() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Prófase");
        registrar(dono, lamina, fase, "NORMAL", List.of(), 1).andExpect(status().isCreated());
        requisicaoAutenticada(sessao(dono), delete(LAMINAS + "/{id}", lamina).param("versaoEsperada", "0"))
                .andExpect(status().isNoContent());

        registrar(dono, lamina, fase, "NORMAL", List.of(), 1).andExpect(status().isNotFound());
        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/resumo", lamina)).andExpect(status().isNotFound());
        registrar(dono, UUID.randomUUID(), fase, "NORMAL", List.of(), 1).andExpect(status().isNotFound());
        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/resumo", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        requisicaoAutenticada(sessao(dono), get(LAMINAS + "/{id}/resumo", "nao-e-uuid"))
                .andExpect(status().isBadRequest());
        assertThat(contarRegistros(lamina)).isEqualTo(1);
    }

    @Test
    void semLoginEh401ESemCsrfEh403() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        String corpo = objectMapper.writeValueAsString(corpo(fase("mitose", "Prófase"), "NORMAL", 1));

        mockMvc.perform(get(LAMINAS + "/{id}/resumo", lamina)).andExpect(status().isUnauthorized());
        comCsrfSemLogin(post(LAMINAS + "/{id}/registros", lamina).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(LAMINAS + "/{id}/registros", lamina).cookie(sessao(dono))
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isForbidden());
        assertThat(contarRegistros(lamina)).isZero();
    }

    // ---------------------------------------------------------------- idempotência (ADR 0003)

    @Test
    void repeticaoComMesmoIdDonoEHashEh200SemEfeito() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID stickiness = tipoFixo("mitose", "Anáfase", "Stickiness");
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpo(fase, "ANORMAL", 4);
        corpo.put("id", id.toString());
        corpo.put("tipoIds", List.of(ponte.toString(), stickiness.toString()));
        corpo.put("horaAparelho", "2026-11-08T12:30:00Z");
        corpo.put("fusoAparelho", "America/Sao_Paulo");
        MvcResult primeira = registrarCorpo(dono, lamina, corpo).andExpect(status().isCreated()).andReturn();

        MvcResult repetida = registrarCorpo(dono, lamina, corpo).andExpect(status().isOk()).andReturn();
        // A ordem dos tipos não muda o pedido.
        Map<String, Object> outraOrdem = new LinkedHashMap<>(corpo);
        outraOrdem.put("tipoIds", List.of(stickiness.toString(), ponte.toString()));
        MvcResult outraOrdemResposta = registrarCorpo(dono, lamina, outraOrdem).andExpect(status().isOk()).andReturn();

        assertThat(json(repetida)).isEqualTo(json(primeira));
        assertThat(json(outraOrdemResposta)).isEqualTo(json(primeira));
        assertThat(contarRegistros(lamina)).isEqualTo(1);
        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(4);
    }

    @Test
    void mesmoIdEDonoComPedidoDiferenteEh409SemAlterarNada() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID outraLamina = criarLamina(dono, null);
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        UUID stickiness = tipoFixo("mitose", "Anáfase", "Stickiness");
        UUID id = UUID.randomUUID();
        Map<String, Object> base = corpo(anafase, "ANORMAL", 4);
        base.put("id", id.toString());
        base.put("tipoIds", List.of(ponte.toString()));
        base.put("horaAparelho", "2026-11-08T12:30:00Z");
        base.put("fusoAparelho", "UTC");
        registrarCorpo(dono, lamina, base).andExpect(status().isCreated());

        Map<String, Map<String, Object>> diferentes = new LinkedHashMap<>();
        diferentes.put("quantidade", alterado(base, "quantidade", 5));
        diferentes.put("fase", alterado(base, "faseId", fase("mitose", "Telófase").toString()));
        diferentes.put("tipos", alterado(base, "tipoIds", List.of(stickiness.toString())));
        diferentes.put("mais tipos", alterado(base, "tipoIds", List.of(ponte.toString(), stickiness.toString())));
        diferentes.put("situação", alterado(alterado(base, "status", "NORMAL"), "tipoIds", List.of()));
        diferentes.put("hora", alterado(base, "horaAparelho", "2026-11-08T12:30:01Z"));
        diferentes.put("sem hora", alterado(base, "horaAparelho", null));
        diferentes.put("fuso", alterado(base, "fusoAparelho", "America/Sao_Paulo"));
        diferentes.put("sem fuso", alterado(base, "fusoAparelho", null));
        for (Map.Entry<String, Map<String, Object>> diferente : diferentes.entrySet()) {
            assertThat(registrarCorpo(dono, lamina, diferente.getValue()).andReturn().getResponse().getStatus())
                    .as(diferente.getKey()).isEqualTo(diferente.getKey().equals("fase") ? 409 : 409);
        }
        // O mesmo pedido em outra lâmina do mesmo dono também é outro pedido.
        registrarCorpo(dono, outraLamina, base).andExpect(status().isConflict());
        assertThat(contarRegistros(lamina)).isEqualTo(1);
        assertThat(contarRegistros(outraLamina)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT quantidade FROM registro_contagem WHERE id = ?",
                Integer.class, id)).isEqualTo(4);
    }

    @Test
    void idDeOutroDonoRespondeIgualAoDeRecursoDesconhecidoSemSobrescrever() throws Exception {
        Cenario cenario = montarCenario();
        UUID lamina = criarLamina(cenario.pesquisador(), null);
        UUID laminaDoInvasor = criarLamina(cenario.membro(), null);
        UUID laminaDeLab = criarLamina(cenario.pesquisador(), cenario.laboratorio());
        UUID fase = fase("mitose", "Prófase");
        UUID id = UUID.randomUUID();
        UUID idDeLab = UUID.randomUUID();
        registrar(cenario.pesquisador(), lamina, id, fase, "NORMAL", List.of(), 3, null).andExpect(status().isCreated());
        registrar(cenario.pesquisador(), laminaDeLab, idDeLab, fase, "NORMAL", List.of(), 3, null)
                .andExpect(status().isCreated());
        MvcResult desconhecido = registrar(cenario.membro(), UUID.randomUUID(), UUID.randomUUID(), fase, "NORMAL",
                List.of(), 3, null).andExpect(status().isNotFound()).andReturn();

        for (Usuario invasor : List.of(cenario.membro(), cenario.admin(), cenario.adminGlobal(), cenario.fora())) {
            UUID propria = invasor.equals(cenario.membro()) ? laminaDoInvasor : criarLamina(invasor, null);
            for (UUID idAlheio : List.of(id, idDeLab)) {
                for (int quantidade : List.of(3, 99)) {
                    // Pedido idêntico, pedido diferente, na lâmina do dono e na do próprio invasor.
                    for (UUID alvo : List.of(lamina, laminaDeLab, propria)) {
                        MvcResult resposta = registrar(invasor, alvo, idAlheio, fase, "NORMAL", List.of(), quantidade, null)
                                .andExpect(status().isNotFound()).andReturn();
                        assertThat(corpoSemHorario(resposta)).isEqualTo(corpoSemHorario(desconhecido));
                    }
                }
            }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT quantidade FROM registro_contagem WHERE id = ?",
                Integer.class, id)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM registro_contagem WHERE lamina_id IN (?, ?)",
                Integer.class, lamina, laminaDeLab)).isEqualTo(2);
    }

    @Test
    void repeticaoDeRegistroExcluidoNaoRessuscitaEDevolveOEstadoAtual() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Prófase");
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpo(fase, "NORMAL", 8);
        corpo.put("id", id.toString());
        registrarCorpo(dono, lamina, corpo).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE registro_contagem SET excluido_em = NOW() WHERE id = ?", id);
        Object excluidoAntes = jdbcTemplate.queryForObject("SELECT excluido_em FROM registro_contagem WHERE id = ?",
                java.sql.Timestamp.class, id);

        registrarCorpo(dono, lamina, corpo).andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(8));

        assertThat(jdbcTemplate.queryForObject("SELECT excluido_em FROM registro_contagem WHERE id = ?",
                java.sql.Timestamp.class, id)).isEqualTo(excluidoAntes);
        assertThat(resumo(dono, lamina).path("total").asLong()).isZero();
        assertThat(contarRegistros(lamina)).isEqualTo(1);
    }

    @Test
    void repeticaoContinuaReconhecidaDepoisDeAQuantidadeMudarNoBanco() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID fase = fase("mitose", "Prófase");
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpo(fase, "NORMAL", 8);
        corpo.put("id", id.toString());
        registrarCorpo(dono, lamina, corpo).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE registro_contagem SET quantidade = 3, version = version + 1 WHERE id = ?", id);

        registrarCorpo(dono, lamina, corpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(3)).andExpect(jsonPath("$.versao").value(1));
        // Quem tenta de novo com a quantidade atual do banco faz outro pedido: conflito.
        registrarCorpo(dono, lamina, alterado(corpo, "quantidade", 3)).andExpect(status().isConflict());
        assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(3);
    }

    @Test
    void repeticaoVaiAntesDasChecagensDeAcessoDeLaminaELimite() throws Exception {
        Cenario cenario = montarCenario();
        UUID lamina = criarLamina(cenario.membro(), cenario.laboratorio());
        UUID fase = fase("mitose", "Prófase");
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpo(fase, "NORMAL", 1000);
        corpo.put("id", id.toString());
        registrarCorpo(cenario.membro(), lamina, corpo).andExpect(status().isCreated());

        try {
            jdbcTemplate.update("UPDATE parametro_contagem SET limite_celulas_por_registro = 10 WHERE id = 1");
            registrarCorpo(cenario.membro(), lamina, corpo).andExpect(status().isOk());
        } finally {
            jdbcTemplate.update("UPDATE parametro_contagem SET limite_celulas_por_registro = 1000 WHERE id = 1");
        }
        laboratorioService.sair(cenario.laboratorio(), cenario.membro().getId());
        registrarCorpo(cenario.membro(), lamina, corpo).andExpect(status().isOk());
        requisicaoAutenticada(sessao(cenario.membro()), get(LAMINAS + "/{id}/resumo", lamina))
                .andExpect(status().isNotFound());
        assertThat(contarRegistros(lamina)).isEqualTo(1);
    }

    @Test
    void repeticaoComLaminaJaExcluidaEh200() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID id = UUID.randomUUID();
        Map<String, Object> corpo = corpo(fase("mitose", "Prófase"), "NORMAL", 2);
        corpo.put("id", id.toString());
        registrarCorpo(dono, lamina, corpo).andExpect(status().isCreated());
        requisicaoAutenticada(sessao(dono), delete(LAMINAS + "/{id}", lamina).param("versaoEsperada", "0"))
                .andExpect(status().isNoContent());

        registrarCorpo(dono, lamina, corpo).andExpect(status().isOk());
        assertThat(contarRegistros(lamina)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- concorrência

    @Test
    void postsSimultaneosComOMesmoIdResultamEmUmaCriacaoEDemaisRepeticoes() throws Exception {
        Usuario dono = criarUsuario();
        UUID fase = fase("mitose", "Prófase");
        int disputantes = 6;

        for (int rodada = 0; rodada < 5; rodada++) {
            UUID lamina = criarLamina(dono, null);
            UUID id = UUID.randomUUID();
            List<Integer> statuses = dispararSimultaneos(disputantes, indice ->
                    registrar(dono, lamina, id, fase, "NORMAL", List.of(), 5, null).andReturn().getResponse().getStatus());

            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 200).hasSize(disputantes - 1);
            assertThat(contarRegistros(lamina)).isEqualTo(1);
            assertThat(resumo(dono, lamina).path("total").asLong()).isEqualTo(5);
        }
    }

    @Test
    void postsSimultaneosComOMesmoIdEPedidosDiferentesNaoDao500() throws Exception {
        Usuario dono = criarUsuario();
        UUID fase = fase("mitose", "Prófase");

        for (int rodada = 0; rodada < 4; rodada++) {
            UUID lamina = criarLamina(dono, null);
            UUID id = UUID.randomUUID();
            List<Integer> statuses = dispararSimultaneos(6, indice ->
                    registrar(dono, lamina, id, fase, "NORMAL", List.of(), indice + 1, null).andReturn().getResponse().getStatus());

            assertThat(statuses).as("statuses %s", statuses).filteredOn(s -> s == 201).hasSize(1);
            assertQueTodos(statuses, 201, 409);
            assertThat(contarRegistros(lamina)).isEqualTo(1);
        }
    }

    @Test
    void idsDiferentesSimultaneosSomamCertoNoResumo() throws Exception {
        Usuario dono = criarUsuario();
        UUID lamina = criarLamina(dono, null);
        UUID anafase = fase("mitose", "Anáfase");
        UUID ponte = tipoFixo("mitose", "Anáfase", "Ponte");
        int registros = 12;

        List<Integer> statuses = dispararSimultaneos(registros, indice -> {
            boolean anormal = indice % 2 == 0;
            return registrar(dono, lamina, UUID.randomUUID(), anafase, anormal ? "ANORMAL" : "NORMAL",
                    anormal ? List.of(ponte) : List.of(), indice + 1, null).andReturn().getResponse().getStatus();
        });

        assertThat(statuses).allMatch(s -> s == 201);
        // Pares (anormais): 1 + 3 + 5 + 7 + 9 + 11 = 36. Ímpares (normais): 2 + 4 + 6 + 8 + 10 + 12 = 42.
        JsonNode resumo = resumo(dono, lamina);
        assertThat(resumo.path("total").asLong()).isEqualTo(78);
        assertThat(resumo.path("anormais").asLong()).isEqualTo(36);
        verificarFase(resumo.path("fases").get(3), "Anáfase", 42, 36, 78, 36.0 / 78 * 100);
        verificarTipos(resumo.path("fases").get(3), List.of("Ponte"), List.of(36L), List.of(36.0 / 78 * 100));
        assertThat(contarRegistros(lamina)).isEqualTo(registros);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM lamina WHERE id = ?", Long.class, lamina)).isZero();
    }

    @Test
    void registroSimultaneoAExclusaoDaLaminaNaoDa500ENaoCriaRegistroDepoisDela() throws Exception {
        Usuario dono = criarUsuario();
        UUID fase = fase("mitose", "Prófase");

        for (int rodada = 0; rodada < 6; rodada++) {
            UUID lamina = criarLamina(dono, null);
            registrar(dono, lamina, fase, "NORMAL", List.of(), 1).andExpect(status().isCreated());
            List<Integer> statuses = dispararSimultaneos(6, indice -> {
                if (indice == 0) {
                    return requisicaoAutenticada(sessao(dono), delete(LAMINAS + "/{id}", lamina)
                            .param("versaoEsperada", "0")).andReturn().getResponse().getStatus();
                }
                return registrar(dono, lamina, fase, "NORMAL", List.of(), 1).andReturn().getResponse().getStatus();
            });

            assertThat(statuses.get(0)).as("statuses %s", statuses).isEqualTo(204);
            assertQueTodos(statuses.subList(1, statuses.size()), 201, 404);
            long criados = statuses.subList(1, statuses.size()).stream().filter(s -> s == 201).count();
            assertThat(contarRegistros(lamina)).isEqualTo(1 + criados);
            // Depois da exclusão, nada entra.
            registrar(dono, lamina, fase, "NORMAL", List.of(), 1).andExpect(status().isNotFound());
            assertThat(contarRegistros(lamina)).isEqualTo(1 + criados);
        }
    }

    // ---------------------------------------------------------------- apoio

    private void verificarMatriz(List<Ator> atores, UUID lamina, UUID fase) throws Exception {
        for (Ator ator : atores) {
            UUID id = UUID.randomUUID();
            int antes = contarRegistros(lamina);
            registrar(ator.usuario(), lamina, id, fase, "NORMAL", List.of(), 1, null)
                    .andExpect(status().is(ator.post()));
            assertThat(contarRegistros(lamina) - antes).as("POST de %s", ator.nome())
                    .isEqualTo(ator.post() == 201 ? 1 : 0);
            ResultActions leitura = requisicaoAutenticada(sessao(ator.usuario()), get(LAMINAS + "/{id}/resumo", lamina));
            leitura.andExpect(status().is(ator.resumo()));
            if (ator.resumo() == 200) {
                leitura.andExpect(jsonPath("$.laminaId").value(lamina.toString()));
            }
        }
    }

    private JsonNode resumo(Usuario usuario, UUID lamina) throws Exception {
        return json(requisicaoAutenticada(sessao(usuario), get(LAMINAS + "/{id}/resumo", lamina))
                .andExpect(status().isOk()).andReturn());
    }

    private static List<String> nomesDasFases(JsonNode resumo) {
        List<String> nomes = new ArrayList<>();
        resumo.path("fases").forEach(fase -> nomes.add(fase.path("nome").asText()));
        return nomes;
    }

    private static void verificarFase(JsonNode fase, String nome, long normais, long anormais, long total,
                                      double percentualAnormais) {
        assertThat(fase.path("nome").asText()).isEqualTo(nome);
        assertThat(fase.path("normais").asLong()).as(nome).isEqualTo(normais);
        assertThat(fase.path("anormais").asLong()).as(nome).isEqualTo(anormais);
        assertThat(fase.path("total").asLong()).as(nome).isEqualTo(total);
        assertThat(fase.path("percentualAnormais").asDouble()).as(nome).isEqualTo(percentualAnormais);
    }

    private static void verificarTipos(JsonNode fase, List<String> nomes, List<Long> contagens,
                                       List<Double> percentuais) {
        JsonNode tipos = fase.path("tipos");
        assertThat(tipos).hasSize(nomes.size());
        for (int i = 0; i < nomes.size(); i++) {
            assertThat(tipos.get(i).path("nome").asText()).isEqualTo(nomes.get(i));
            assertThat(tipos.get(i).path("contagem").asLong()).isEqualTo(contagens.get(i));
            assertThat(tipos.get(i).path("percentual").asDouble()).isEqualTo(percentuais.get(i));
            assertThat(tipos.get(i).path("tipoId").asText()).isNotEmpty();
        }
    }

    private int contarRegistros(UUID lamina) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM registro_contagem WHERE lamina_id = ?",
                Integer.class, lamina);
    }

    private static List<String> ordenados(UUID... ids) {
        return java.util.Arrays.stream(ids).map(UUID::toString).sorted().toList();
    }

    private static List<String> cinquentaEUm() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            ids.add(UUID.randomUUID().toString());
        }
        return ids;
    }

    private static Map<String, Object> alterado(Map<String, Object> base, String campo, Object valor) {
        Map<String, Object> copia = new LinkedHashMap<>(base);
        copia.put(campo, valor);
        return copia;
    }

    private Map<String, Object> corpo(UUID fase, String status, int quantidade) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", UUID.randomUUID().toString());
        corpo.put("faseId", fase.toString());
        corpo.put("status", status);
        corpo.put("quantidade", quantidade);
        return corpo;
    }

    /** Corpo válido de um registro NORMAL de 1 célula com o campo (e opcionalmente outro) trocado. */
    private Map<String, Object> com(UUID fase, Object... camposEValores) {
        Map<String, Object> corpo = corpo(fase, "NORMAL", 1);
        for (int i = 0; i < camposEValores.length; i += 2) {
            corpo.put((String) camposEValores[i], camposEValores[i + 1]);
        }
        return corpo;
    }

    private ResultActions registrar(Usuario usuario, UUID lamina, UUID fase, String status, List<UUID> tipos,
                                    int quantidade) throws Exception {
        return registrar(usuario, lamina, UUID.randomUUID(), fase, status, tipos, quantidade, null);
    }

    private ResultActions registrar(Usuario usuario, UUID lamina, UUID id, UUID fase, String status, List<UUID> tipos,
                                    int quantidade, String horaAparelho) throws Exception {
        Map<String, Object> corpo = corpo(fase, status, quantidade);
        corpo.put("id", id.toString());
        corpo.put("tipoIds", tipos.stream().map(UUID::toString).toList());
        if (horaAparelho != null) {
            corpo.put("horaAparelho", horaAparelho);
        }
        return registrarCorpo(usuario, lamina, corpo);
    }

    private ResultActions registrarCorpo(Usuario usuario, UUID lamina, Map<String, Object> corpo) throws Exception {
        return postarBruto(usuario, lamina, objectMapper.writeValueAsString(corpo));
    }

    private ResultActions postarBruto(Usuario usuario, UUID lamina, String corpo) throws Exception {
        Cookie sessao = sessao(usuario);
        return requisicaoAutenticada(sessao, post(LAMINAS + "/{id}/registros", lamina)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }
}
