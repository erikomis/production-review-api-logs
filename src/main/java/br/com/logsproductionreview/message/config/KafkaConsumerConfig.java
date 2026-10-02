package br.com.logsproductionreview.message.config;

import br.com.logsproductionreview.mapper.InvalidLogEventException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * O consumidor, o produtor (usado só para a DLT) e os deserializadores vêm do
 * {@code application.properties} via auto-configuração do Spring Boot. Aqui ficam só o
 * tratamento de erro e a declaração dos tópicos.
 * <p>
 * O Boot liga o {@link DefaultErrorHandler} declarado como bean à factory padrão do
 * {@code @KafkaListener}; com o ack em modo BATCH (padrão) o offset é confirmado pelo
 * container depois do processamento ou depois de a mensagem ir para a DLT.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public NewTopic logsTopic(@Value("${logs.kafka.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic logsDeadLetterTopic(@Value("${logs.kafka.topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaOperations<Object, Object> kafkaTemplate,
            @Value("${logs.kafka.retry.attempts:2}") long retryAttempts,
            @Value("${logs.kafka.retry.interval-ms:1000}") long retryIntervalMs
    ) {
        // partição -1: o Kafka escolhe a partição da DLT, então ela não precisa ter
        // o mesmo número de partições do tópico original
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));

        var handler = new DefaultErrorHandler((record, ex) -> {
            log.warn("Mensagem enviada para {}.DLT (offset {}): {}",
                    record.topic(), record.offset(), rootMessage(ex));
            recoverer.accept(record, ex);
        }, new FixedBackOff(retryIntervalMs, retryAttempts));

        // mensagem malformada não melhora com retry: vai direto para a DLT
        handler.addNotRetryableExceptions(InvalidLogEventException.class);
        return handler;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
