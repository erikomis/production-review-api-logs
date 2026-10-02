package br.com.logsproductionreview.mapper;

import br.com.logsproductionreview.entity.LogNotification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogEventMapperTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-10-02T03:10:05Z");

    private final LogEventMapper mapper = new LogEventMapper(new ObjectMapper());

    @Test
    void mapsNewEventWithAllFields() {
        String json = """
                { "eventId":"5f0c3c1e-2d7b-4b8e-9a51-0f3f6b1c2d3e", "type":"REVIEW_CREATED", "action":"Avaliação criada",
                  "message":"Usuário Teste avaliou Smartphone X com 5 estrelas", "nameUser":"Usuário Teste", "userId":2,
                  "entityType":"REVIEW", "entityId":"12", "occurredAt":"2026-10-02T03:10:00Z" }
                """;

        LogNotification log = mapper.fromJson(json, RECEIVED_AT);

        assertThat(log.getId()).isNull();
        assertThat(log.getEventId()).isEqualTo("5f0c3c1e-2d7b-4b8e-9a51-0f3f6b1c2d3e");
        assertThat(log.getType()).isEqualTo("REVIEW_CREATED");
        assertThat(log.getAction()).isEqualTo("Avaliação criada");
        assertThat(log.getMessage()).isEqualTo("Usuário Teste avaliou Smartphone X com 5 estrelas");
        assertThat(log.getNameUser()).isEqualTo("Usuário Teste");
        assertThat(log.getUserId()).isEqualTo(2L);
        assertThat(log.getEntityType()).isEqualTo("REVIEW");
        assertThat(log.getEntityId()).isEqualTo("12");
        assertThat(log.getOccurredAt()).isEqualTo(Instant.parse("2026-10-02T03:10:00Z"));
        assertThat(log.getReceivedAt()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void legacyEventBecomesLegacyTypeAndUsesReceivedAt() {
        String json = """
                {"action":"Avaliação criada","message":"Nova avaliação","nameUser":"Fulano"}
                """;

        LogNotification log = mapper.fromJson(json, RECEIVED_AT);

        assertThat(log.getType()).isEqualTo(LogNotification.LEGACY_TYPE);
        assertThat(log.getEventId()).isNull();
        assertThat(log.getOccurredAt()).isEqualTo(RECEIVED_AT);
        assertThat(log.getReceivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(log.getAction()).isEqualTo("Avaliação criada");
        assertThat(log.getNameUser()).isEqualTo("Fulano");
        assertThat(log.getUserId()).isNull();
    }

    @Test
    void legacyEventIgnoresOccurredAtEvenIfPresent() {
        String json = """
                {"action":"x","occurredAt":"2020-01-01T00:00:00Z"}
                """;

        assertThat(mapper.fromJson(json, RECEIVED_AT).getOccurredAt()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void newEventWithoutOccurredAtFallsBackToReceivedAt() {
        LogNotification log = mapper.fromJson("{\"eventId\":\"e1\",\"type\":\"USER_LOGGED_IN\"}", RECEIVED_AT);

        assertThat(log.getType()).isEqualTo("USER_LOGGED_IN");
        assertThat(log.getOccurredAt()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void acceptsOffsetDateTimeAndCoercesNumericAndStringIds() {
        String json = """
                {"eventId":"e2","type":"PRODUCT_UPDATED","userId":"7","entityId":42,
                 "occurredAt":"2026-10-01T22:00:00-03:00","unknownField":true}
                """;

        LogNotification log = mapper.fromJson(json, RECEIVED_AT);

        assertThat(log.getOccurredAt()).isEqualTo(Instant.parse("2026-10-02T01:00:00Z"));
        assertThat(log.getUserId()).isEqualTo(7L);
        assertThat(log.getEntityId()).isEqualTo("42");
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        LogNotification log = mapper.fromJson(
                "{\"eventId\":\"  \",\"type\":\" USER_SIGNED_UP \",\"entityType\":\"\"}", RECEIVED_AT);

        assertThat(log.getEventId()).isNull();
        assertThat(log.getType()).isEqualTo("USER_SIGNED_UP");
        assertThat(log.getEntityType()).isNull();
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> mapper.fromJson("not json", RECEIVED_AT))
                .isInstanceOf(InvalidLogEventException.class)
                .hasMessageContaining("JSON inválido");
    }

    @Test
    void rejectsBlankPayload() {
        assertThatThrownBy(() -> mapper.fromJson("  ", RECEIVED_AT))
                .isInstanceOf(InvalidLogEventException.class);
    }

    @Test
    void rejectsEventWithoutTypeAndWithoutLegacyFields() {
        assertThatThrownBy(() -> mapper.fromJson("{\"userId\":1}", RECEIVED_AT))
                .isInstanceOf(InvalidLogEventException.class);
    }

    @Test
    void rejectsInvalidOccurredAt() {
        assertThatThrownBy(() -> mapper.fromJson("{\"type\":\"REVIEW_CREATED\",\"occurredAt\":\"ontem\"}", RECEIVED_AT))
                .isInstanceOf(InvalidLogEventException.class)
                .hasMessageContaining("occurredAt");
    }

    @Test
    void rejectsWrongFieldType() {
        assertThatThrownBy(() -> mapper.fromJson("{\"type\":\"REVIEW_CREATED\",\"userId\":\"abc\"}", RECEIVED_AT))
                .isInstanceOf(InvalidLogEventException.class);
    }
}
