package br.com.logsproductionreview.service;

import br.com.logsproductionreview.dto.LogEntryResponse;
import br.com.logsproductionreview.dto.LogSearchFilter;
import br.com.logsproductionreview.dto.LogSummaryResponse;
import br.com.logsproductionreview.dto.LogSummaryResponse.DayCount;
import br.com.logsproductionreview.dto.LogSummaryResponse.TypeCount;
import br.com.logsproductionreview.entity.LogNotification;
import br.com.logsproductionreview.repositories.LogNotificationRepository;
import br.com.logsproductionreview.service.impl.LogNotificationServiceImpl;
import br.com.logsproductionreview.service.impl.LogQueryServiceImpl;
import br.com.logsproductionreview.support.MongoContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import({LogQueryServiceImpl.class, LogNotificationServiceImpl.class})
class LogQueryServiceImplTest extends MongoContainerSupport {

    @Autowired
    private LogQueryService logQueryService;

    @Autowired
    private LogNotificationService logNotificationService;

    @Autowired
    private LogNotificationRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        // 2026-10-02T02:00Z = 01/10 23:00 em São Paulo; 2026-10-02T04:00Z = 02/10 01:00
        save("e1", "REVIEW_CREATED", "Avaliação criada", "Ana avaliou Smartphone X", "Ana", 1L, "REVIEW", "2026-09-28T15:00:00Z");
        save("e2", "REVIEW_CREATED", "Avaliação criada", "Bruno avaliou Notebook Y", "Bruno", 2L, "REVIEW", "2026-10-02T02:00:00Z");
        save("e3", "PRODUCT_UPDATED", "Produto atualizado", "Produto SMARTPHONE X editado", "Administrador", 3L, "PRODUCT", "2026-10-02T04:00:00Z");
        save(null, "LEGACY", "Avaliação criada", "mensagem antiga", "Carla", null, null, "2026-09-30T12:00:00Z");
    }

    private void save(String eventId, String type, String action, String message, String nameUser,
                      Long userId, String entityType, String occurredAt) {
        Instant at = Instant.parse(occurredAt);
        logNotificationService.saveLogNotification(LogNotification.builder()
                .eventId(eventId).type(type).action(action).message(message).nameUser(nameUser)
                .userId(userId).entityType(entityType).entityId("1")
                .occurredAt(at).receivedAt(at.plusSeconds(1)).build());
    }

    private Page<LogEntryResponse> search(LogSearchFilter filter) {
        return logQueryService.search(filter, PageRequest.of(0, 10));
    }

    private static LogSearchFilter empty() {
        return new LogSearchFilter(null, null, null, null, null, null);
    }

    @Test
    void listsAllNewestFirst() {
        Page<LogEntryResponse> page = search(empty());

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(LogEntryResponse::eventId)
                .containsExactly("e3", "e2", null, "e1");
        assertThat(page.getContent().get(0).id()).isNotBlank();
        assertThat(page.getContent().get(0).receivedAt()).isEqualTo(Instant.parse("2026-10-02T04:00:01Z"));
    }

    @Test
    void paginatesWithTotalCount() {
        Page<LogEntryResponse> page = logQueryService.search(empty(), PageRequest.of(1, 3));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(LogEntryResponse::eventId).containsExactly("e1");
    }

    @Test
    void filtersByTypeEntityTypeAndUser() {
        assertThat(search(new LogSearchFilter("REVIEW_CREATED", null, null, null, null, null)).getContent())
                .extracting(LogEntryResponse::eventId).containsExactly("e2", "e1");
        assertThat(search(new LogSearchFilter(null, "PRODUCT", null, null, null, null)).getContent())
                .extracting(LogEntryResponse::eventId).containsExactly("e3");
        assertThat(search(new LogSearchFilter(null, null, 2L, null, null, null)).getContent())
                .extracting(LogEntryResponse::eventId).containsExactly("e2");
        assertThat(search(new LogSearchFilter("REVIEW_CREATED", "REVIEW", 1L, null, null, null)).getContent())
                .extracting(LogEntryResponse::eventId).containsExactly("e1");
    }

    @Test
    void searchIsCaseInsensitiveOverActionMessageAndNameUser() {
        assertThat(search(new LogSearchFilter(null, null, null, "smartphone", null, null)).getContent())
                .extracting(LogEntryResponse::eventId).containsExactly("e3", "e1");
        assertThat(search(new LogSearchFilter(null, null, null, "carla", null, null)).getContent())
                .extracting(LogEntryResponse::type).containsExactly("LEGACY");
        assertThat(search(new LogSearchFilter(null, null, null, "PRODUTO ATUALIZADO", null, null)).getContent())
                .extracting(LogEntryResponse::eventId).containsExactly("e3");
    }

    @Test
    void searchTreatsRegexCharactersLiterally() {
        assertThat(search(new LogSearchFilter(null, null, null, ".*", null, null)).getContent()).isEmpty();
    }

    @Test
    void filtersByOccurredAtRange() {
        var filter = new LogSearchFilter(null, null, null, null,
                Instant.parse("2026-09-30T00:00:00Z"), Instant.parse("2026-10-02T03:00:00Z"));

        assertThat(search(filter).getContent()).extracting(LogEntryResponse::eventId).containsExactly("e2", null);
    }

    @Test
    void summaryGroupsByTypeAndBySaoPauloDayWithoutGaps() {
        LogSummaryResponse summary = logQueryService.summary(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2));

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.byType()).containsExactly(
                new TypeCount("REVIEW_CREATED", 2),
                new TypeCount("LEGACY", 1),
                new TypeCount("PRODUCT_UPDATED", 1));
        assertThat(summary.byDay()).containsExactly(
                new DayCount(LocalDate.of(2026, 9, 28), 1),
                new DayCount(LocalDate.of(2026, 9, 29), 0),
                new DayCount(LocalDate.of(2026, 9, 30), 1),
                new DayCount(LocalDate.of(2026, 10, 1), 1),
                new DayCount(LocalDate.of(2026, 10, 2), 1));
    }

    @Test
    void summaryOnlyCountsEventsInsideTheRange() {
        LogSummaryResponse summary = logQueryService.summary(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1));

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.byType()).containsExactly(new TypeCount("REVIEW_CREATED", 1));
        assertThat(summary.byDay()).containsExactly(new DayCount(LocalDate.of(2026, 10, 1), 1));
    }

    @Test
    void summaryOfEmptyRangeHasZeroedDays() {
        LogSummaryResponse summary = logQueryService.summary(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3));

        assertThat(summary.total()).isZero();
        assertThat(summary.byType()).isEmpty();
        assertThat(summary.byDay()).extracting(DayCount::count).containsExactly(0L, 0L, 0L);
    }

    @Test
    void duplicateEventIdIsIgnoredButLegacyEventsWithoutIdAreNot() {
        assertThat(logNotificationService.saveLogNotification(LogNotification.builder()
                .eventId("e1").type("REVIEW_CREATED").occurredAt(Instant.now()).build())).isFalse();
        assertThat(logNotificationService.saveLogNotification(LogNotification.builder()
                .type("LEGACY").action("x").occurredAt(Instant.now()).build())).isTrue();
        assertThat(logNotificationService.saveLogNotification(LogNotification.builder()
                .type("LEGACY").action("x").occurredAt(Instant.now()).build())).isTrue();

        assertThat(repository.count()).isEqualTo(6);
    }

    @Test
    void createsExpectedIndexes() {
        List<IndexInfo> indexes = mongoTemplate.indexOps(LogNotification.class).getIndexInfo();

        assertThat(indexes).extracting(IndexInfo::getName)
                .contains("eventId_unique", "occurredAt_idx", "type_idx", "userId_idx");
        IndexInfo eventId = indexes.stream().filter(i -> i.getName().equals("eventId_unique")).findFirst().orElseThrow();
        assertThat(eventId.isUnique()).isTrue();
        assertThat(eventId.isSparse()).isTrue();
    }
}
