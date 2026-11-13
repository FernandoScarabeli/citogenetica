package br.ufla.uuidcontract;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import br.ufla.citogenetica.shared.filter.CamposPermitidos;
import br.ufla.citogenetica.shared.filter.CriteriaCreator;
import br.ufla.citogenetica.shared.filter.FiltroInvalidoException;
import br.ufla.citogenetica.shared.persistence.UuidEntity;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Usa um contexto JPA isolado para não adicionar tabela de teste ao Flyway da aplicação. */
@SpringJUnitConfig(UuidEntityPersistenceIntegrationTest.TestPersistenceConfiguration.class)
class UuidEntityPersistenceIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private UuidEntityContractRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturingStatementInspector statementInspector;

    @Autowired
    private MutableDateTimeProvider dateTimeProvider;

    @BeforeEach
    void limparTabela() {
        jdbcTemplate.update("DELETE FROM uuid_entity_contract_test");
        statementInspector.clear();
        dateTimeProvider.set(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @AfterAll
    static void pararPostgres() {
        POSTGRES.stop();
    }

    @Test
    void idAtribuidoPersisteComoUuidNativoSemSelectAntesDoInsert() {
        UUID id = UUID.randomUUID();
        statementInspector.clear();

        repository.saveAndFlush(new UuidEntityContractFixture(id, "lâmina A"));

        String tipoId = jdbcTemplate.queryForObject("""
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND table_name = 'uuid_entity_contract_test'
                   AND column_name = 'id'
                """, String.class);
        assertEquals("uuid", tipoId);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM uuid_entity_contract_test WHERE id = ?", Integer.class, id));
        assertTrue(statementInspector.statements().stream().noneMatch(sql -> sql.stripLeading()
                .toLowerCase().startsWith("select")), statementInspector.statements().toString());
        assertTrue(statementInspector.statements().stream().anyMatch(sql -> sql.stripLeading()
                .toLowerCase().startsWith("insert")), statementInspector.statements().toString());
    }

    @Test
    void auditoriaInstantUsaTimestampComFusoEAtualizaSomenteModificadoEm() {
        Instant criadoEm = Instant.parse("2026-01-01T00:00:00Z");
        UuidEntityContractFixture salvo = repository.saveAndFlush(novaEntidade("antes"));

        String tipoCriado = tipoColuna("created_at");
        String tipoModificado = tipoColuna("last_modified_at");
        assertEquals("timestamp with time zone", tipoCriado);
        assertEquals("timestamp with time zone", tipoModificado);
        assertEquals(criadoEm, salvo.getCreatedAt());
        assertEquals(criadoEm, salvo.getLastModifiedAt());

        dateTimeProvider.set(criadoEm.plusSeconds(17));
        salvo.setConteudo("depois");
        UuidEntityContractFixture atualizado = repository.saveAndFlush(salvo);

        assertEquals(criadoEm, atualizado.getCreatedAt());
        assertEquals(criadoEm.plusSeconds(17), atualizado.getLastModifiedAt());
    }

    @Test
    void versaoIncrementaERejeitaAtualizacaoObsoleta() {
        UuidEntityContractFixture salvo = repository.saveAndFlush(novaEntidade("original"));
        assertEquals(0L, salvo.getVersion());

        UuidEntityContractFixture versaoAtual = repository.findById(salvo.getId()).orElseThrow();
        UuidEntityContractFixture versaoObsoleta = repository.findById(salvo.getId()).orElseThrow();

        versaoAtual.setConteudo("alteração atual");
        UuidEntityContractFixture atualizado = repository.saveAndFlush(versaoAtual);
        assertEquals(1L, atualizado.getVersion());

        versaoObsoleta.setConteudo("alteração obsoleta");
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> repository.saveAndFlush(versaoObsoleta));
        assertEquals("alteração atual", repository.findById(salvo.getId()).orElseThrow().getConteudo());
    }

    @Test
    void filtroInstantAceitaOperadorERejeitaFormatoInvalido() {
        Instant instanteAnterior = Instant.parse("2026-01-01T00:00:00Z");
        UuidEntityContractFixture antiga = repository.saveAndFlush(novaEntidade("anterior"));
        dateTimeProvider.set(instanteAnterior.plusSeconds(30));
        UuidEntityContractFixture recente = repository.saveAndFlush(novaEntidade("recente"));

        var parametros = new LinkedMultiValueMap<String, String>();
        parametros.add("createdAt", "gte:" + instanteAnterior);
        parametros.add("createdAt", "gt:" + instanteAnterior.plusSeconds(10));
        parametros.add("createdAt", "lte:" + instanteAnterior.plusSeconds(30));
        var campos = new CamposPermitidos(java.util.Set.of("createdAt"), java.util.Set.of());

        var encontrados = repository.findAll(CriteriaCreator.byFilterMap(parametros, campos));
        assertEquals(1, encontrados.size());
        assertEquals(recente.getId(), encontrados.get(0).getId());

        var parametrosInvalidos = new LinkedMultiValueMap<String, String>();
        parametrosInvalidos.add("createdAt", "gte:amanhã");
        assertThrows(FiltroInvalidoException.class, () -> repository.findAll(
                CriteriaCreator.byFilterMap(parametrosInvalidos, campos)));
        assertNotNull(antiga.getId());
    }

    private UuidEntityContractFixture novaEntidade(String conteudo) {
        return new UuidEntityContractFixture(UUID.randomUUID(), conteudo);
    }

    private String tipoColuna(String nomeColuna) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND table_name = 'uuid_entity_contract_test'
                   AND column_name = ?
                """, String.class, nomeColuna);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJpaAuditing(dateTimeProviderRef = "uuidDateTimeProvider")
    @EnableJpaRepositories(basePackageClasses = UuidEntityContractRepository.class,
            entityManagerFactoryRef = "uuidEntityManagerFactory",
            transactionManagerRef = "uuidTransactionManager")
    static class TestPersistenceConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(POSTGRES.getJdbcUrl());
            dataSource.setUsername(POSTGRES.getUsername());
            dataSource.setPassword(POSTGRES.getPassword());
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean uuidEntityManagerFactory(DataSource dataSource,
                                                                        CapturingStatementInspector inspector) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(UuidEntityContractFixture.class.getPackageName());
            factory.setPersistenceUnitName("uuid-contract-test");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Map<String, Object> properties = new HashMap<>();
            properties.put("hibernate.hbm2ddl.auto", "create-drop");
            properties.put("hibernate.jdbc.time_zone", "UTC");
            properties.put("hibernate.session_factory.statement_inspector", inspector);
            factory.setJpaPropertyMap(properties);
            return factory;
        }

        @Bean
        PlatformTransactionManager uuidTransactionManager(EntityManagerFactory uuidEntityManagerFactory) {
            return new JpaTransactionManager(uuidEntityManagerFactory);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        MutableDateTimeProvider uuidDateTimeProvider() {
            return new MutableDateTimeProvider();
        }

        @Bean
        CapturingStatementInspector capturingStatementInspector() {
            return new CapturingStatementInspector();
        }
    }

    static class MutableDateTimeProvider implements DateTimeProvider {
        private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public java.util.Optional<java.time.temporal.TemporalAccessor> getNow() {
            return java.util.Optional.of(now);
        }
    }

    static class CapturingStatementInspector implements StatementInspector {
        private final CopyOnWriteArrayList<String> statements = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            statements.add(sql);
            return sql;
        }

        void clear() {
            statements.clear();
        }

        java.util.List<String> statements() {
            return java.util.List.copyOf(statements);
        }
    }
}
