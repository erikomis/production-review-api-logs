package br.com.logsproductionreview.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * MongoDB real (mesma versão do ambiente local) via Testcontainers.
 * <p>
 * Escolhido no lugar do flapdoodle embedded porque roda o binário oficial em container, sem baixar
 * executáveis por sistema operacional, e testa exatamente o que roda em produção: índice único
 * esparso, {@code $dateToString} com fuso horário e regex case-insensitive.
 * <p>
 * Sem Docker disponível, as classes que herdam daqui são puladas (não falham).
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class MongoContainerSupport {

    @Container
    protected static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("logs-test"));
    }
}
