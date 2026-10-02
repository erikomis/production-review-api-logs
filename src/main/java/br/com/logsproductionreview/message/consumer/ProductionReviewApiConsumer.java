package br.com.logsproductionreview.message.consumer;

import br.com.logsproductionreview.entity.LogNotification;
import br.com.logsproductionreview.mapper.LogEventMapper;
import br.com.logsproductionreview.service.LogNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Consome os eventos de auditoria da API. Erros sobem para o {@code DefaultErrorHandler}
 * (ver {@code KafkaConsumerConfig}), que manda a mensagem para a DLT sem travar a partição.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionReviewApiConsumer {

    private final LogEventMapper logEventMapper;
    private final LogNotificationService logNotificationService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "${logs.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        LogNotification logNotification = logEventMapper.fromJson(payload, Instant.now(clock));
        boolean stored = logNotificationService.saveLogNotification(logNotification);
        if (stored) {
            log.debug("Log {} gravado (eventId={})", logNotification.getType(), logNotification.getEventId());
        }
        // reviewstore_logs_events_total{type, result=stored|duplicate}
        meterRegistry.counter("reviewstore.logs.events",
                "type", logNotification.getType(), "result", stored ? "stored" : "duplicate").increment();
    }
}
