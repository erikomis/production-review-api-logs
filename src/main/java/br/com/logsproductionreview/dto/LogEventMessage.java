package br.com.logsproductionreview.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload recebido do tópico {@code production-review-api}.
 * Campos desconhecidos são ignorados para que a API possa evoluir o evento sem quebrar o consumidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LogEventMessage(
        String eventId,
        String type,
        String action,
        String message,
        String nameUser,
        Long userId,
        String entityType,
        String entityId,
        String occurredAt
) {
}
