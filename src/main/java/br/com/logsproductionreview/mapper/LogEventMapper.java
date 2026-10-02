package br.com.logsproductionreview.mapper;

import br.com.logsproductionreview.dto.LogEventMessage;
import br.com.logsproductionreview.entity.LogNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Converte o JSON publicado pela API em {@link LogNotification}.
 * <ul>
 *   <li>Evento novo: tem {@code type}; {@code occurredAt} vem do evento (ou {@code receivedAt} se ausente).</li>
 *   <li>Evento legado: sem {@code type}; vira {@code type="LEGACY"} e {@code occurredAt=receivedAt}.</li>
 * </ul>
 */
@Component
public class LogEventMapper {

    private final ObjectMapper objectMapper;

    public LogEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LogNotification fromJson(String payload, Instant receivedAt) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidLogEventException("Mensagem vazia");
        }
        LogEventMessage event;
        try {
            event = objectMapper.readValue(payload, LogEventMessage.class);
        } catch (JsonProcessingException e) {
            throw new InvalidLogEventException("JSON inválido: " + e.getOriginalMessage(), e);
        }
        if (event == null) {
            throw new InvalidLogEventException("Mensagem nula");
        }
        return toEntity(event, receivedAt);
    }

    public LogNotification toEntity(LogEventMessage event, Instant receivedAt) {
        boolean legacy = isBlank(event.type());
        if (legacy && isBlank(event.action()) && isBlank(event.message()) && isBlank(event.nameUser())) {
            throw new InvalidLogEventException("Evento sem type e sem action/message/nameUser");
        }

        Instant occurredAt = legacy || isBlank(event.occurredAt())
                ? receivedAt
                : parseInstant(event.occurredAt());

        return LogNotification.builder()
                .eventId(trimToNull(event.eventId()))
                .type(legacy ? LogNotification.LEGACY_TYPE : event.type().trim())
                .action(event.action())
                .message(event.message())
                .nameUser(event.nameUser())
                .userId(event.userId())
                .entityType(trimToNull(event.entityType()))
                .entityId(trimToNull(event.entityId()))
                .occurredAt(occurredAt)
                .receivedAt(receivedAt)
                .build();
    }

    private static Instant parseInstant(String value) {
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidLogEventException("occurredAt inválido: " + value, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
