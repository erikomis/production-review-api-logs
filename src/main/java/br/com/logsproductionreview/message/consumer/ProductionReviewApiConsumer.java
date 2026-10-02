package br.com.logsproductionreview.message.consumer;

import br.com.logsproductionreview.entity.LogNotification;
import br.com.logsproductionreview.mapper.LogEventMapper;
import br.com.logsproductionreview.service.LogNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @KafkaListener(topics = "${logs.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        LogNotification logNotification = logEventMapper.fromJson(payload, Instant.now(clock));
        if (logNotificationService.saveLogNotification(logNotification)) {
            log.debug("Log {} gravado (eventId={})", logNotification.getType(), logNotification.getEventId());
        }
    }
}
