package br.com.logsproductionreview.dto;

import br.com.logsproductionreview.entity.LogNotification;

import java.time.Instant;

public record LogEntryResponse(
        String id,
        String eventId,
        String type,
        String action,
        String message,
        String nameUser,
        Long userId,
        String entityType,
        String entityId,
        Instant occurredAt,
        Instant receivedAt
) {

    public static LogEntryResponse from(LogNotification log) {
        return new LogEntryResponse(
                log.getId(),
                log.getEventId(),
                log.getType(),
                log.getAction(),
                log.getMessage(),
                log.getNameUser(),
                log.getUserId(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOccurredAt(),
                log.getReceivedAt()
        );
    }
}
