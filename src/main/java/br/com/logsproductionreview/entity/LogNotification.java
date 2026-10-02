package br.com.logsproductionreview.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Evento de auditoria gravado na coleção {@code log_notification}.
 * <p>
 * Eventos legados (só action/message/nameUser) não têm {@code eventId}; como o Spring Data
 * não grava campos nulos, o índice único esparso ignora esses documentos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = LogNotification.COLLECTION)
public class LogNotification {

    public static final String COLLECTION = "log_notification";
    public static final String LEGACY_TYPE = "LEGACY";

    @Id
    private String id;

    @Indexed(name = "eventId_unique", unique = true, sparse = true)
    private String eventId;

    @Indexed(name = "type_idx")
    private String type;

    private String action;
    private String message;
    private String nameUser;

    @Indexed(name = "userId_idx")
    private Long userId;

    private String entityType;
    private String entityId;

    @Indexed(name = "occurredAt_idx", direction = IndexDirection.DESCENDING)
    private Instant occurredAt;

    private Instant receivedAt;
}
