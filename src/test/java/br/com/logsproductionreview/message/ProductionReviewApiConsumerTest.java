package br.com.logsproductionreview.message;

import br.com.logsproductionreview.entity.LogNotification;
import br.com.logsproductionreview.repositories.LogNotificationRepository;
import br.com.logsproductionreview.support.MongoContainerSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Fluxo completo: Kafka embarcado → consumidor → MongoDB (Testcontainers), incluindo
 * idempotência por eventId, formato legado e envio de mensagens inválidas para a DLT.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "logs.kafka.retry.attempts=0",
        "logs.api.token=test-token"
})
@EmbeddedKafka(partitions = 1, topics = {ProductionReviewApiConsumerTest.TOPIC, ProductionReviewApiConsumerTest.DLT})
class ProductionReviewApiConsumerTest extends MongoContainerSupport {

    static final String TOPIC = "production-review-api";
    static final String DLT = TOPIC + ".DLT";

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private LogNotificationRepository repository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private MeterRegistry meterRegistry;

    private double eventsCounter(String type, String result) {
        var counter = meterRegistry.find("reviewstore.logs.events").tag("type", type).tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-reader-" + System.nanoTime(), "true", broker);
        props.put("auto.offset.reset", "earliest");
        dltConsumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(dltConsumer, DLT);
    }

    @AfterEach
    void tearDown() {
        dltConsumer.close();
    }

    private void send(String payload) {
        kafkaTemplate.send(TOPIC, payload).join();
    }

    private void awaitCount(long expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(repository.count()).isEqualTo(expected));
    }

    @Test
    void storesNewEventWithAllFields() {
        send("""
                { "eventId":"it-1", "type":"REVIEW_CREATED", "action":"Avaliação criada",
                  "message":"Usuário Teste avaliou Smartphone X com 5 estrelas", "nameUser":"Usuário Teste", "userId":2,
                  "entityType":"REVIEW", "entityId":"12", "occurredAt":"2026-10-02T03:10:00Z" }
                """);

        awaitCount(1);
        LogNotification log = repository.findAll().get(0);
        assertThat(log.getEventId()).isEqualTo("it-1");
        assertThat(log.getType()).isEqualTo("REVIEW_CREATED");
        assertThat(log.getUserId()).isEqualTo(2L);
        assertThat(log.getEntityType()).isEqualTo("REVIEW");
        assertThat(log.getEntityId()).isEqualTo("12");
        assertThat(log.getOccurredAt()).isEqualTo(Instant.parse("2026-10-02T03:10:00Z"));
        assertThat(log.getReceivedAt()).isNotNull();
    }

    @Test
    void ignoresDuplicateEventId() {
        String event = "{\"eventId\":\"it-dup\",\"type\":\"USER_LOGGED_IN\",\"action\":\"Login\",\"occurredAt\":\"2026-10-02T03:10:00Z\"}";
        send(event);
        send(event);
        send("{\"eventId\":\"it-after-dup\",\"type\":\"USER_LOGGED_IN\",\"action\":\"Login\"}");

        // o terceiro evento só é consumido depois dos dois primeiros (mesma partição)
        awaitCount(2);
        assertThat(repository.findAll()).extracting(LogNotification::getEventId)
                .containsExactlyInAnyOrder("it-dup", "it-after-dup");
        // métricas: 2 gravados e 1 duplicata descartada
        assertThat(eventsCounter("USER_LOGGED_IN", "stored")).isGreaterThanOrEqualTo(2.0);
        assertThat(eventsCounter("USER_LOGGED_IN", "duplicate")).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void acceptsLegacyMessage() {
        send("{\"action\":\"Avaliação criada\",\"message\":\"Nova avaliação\",\"nameUser\":\"Fulano\"}");

        awaitCount(1);
        LogNotification log = repository.findAll().get(0);
        assertThat(log.getType()).isEqualTo("LEGACY");
        assertThat(log.getEventId()).isNull();
        assertThat(log.getOccurredAt()).isEqualTo(log.getReceivedAt());
    }

    @Test
    void invalidMessageGoesToDltAndDoesNotBlockTheConsumer() {
        send("isto não é json");
        send("{\"eventId\":\"it-ok\",\"type\":\"PRODUCT_CREATED\",\"action\":\"Produto criado\"}");

        awaitCount(1);
        assertThat(repository.findAll().get(0).getEventId()).isEqualTo("it-ok");

        List<ConsumerRecord<String, String>> dead = await().atMost(Duration.ofSeconds(20))
                .until(() -> {
                    List<ConsumerRecord<String, String>> records = new java.util.ArrayList<>();
                    KafkaTestUtils.getRecords(dltConsumer, Duration.ofMillis(500)).forEach(records::add);
                    return records;
                }, records -> !records.isEmpty());
        ConsumerRecord<String, String> deadLetter = dead.stream()
                .filter(r -> "isto não é json".equals(r.value())).findFirst().orElseThrow();
        assertThat(deadLetter.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
        assertThat(eventsCounter("INVALID", "dead_letter")).isGreaterThanOrEqualTo(1.0);
    }
}
