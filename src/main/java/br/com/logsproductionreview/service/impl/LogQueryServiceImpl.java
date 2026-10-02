package br.com.logsproductionreview.service.impl;

import br.com.logsproductionreview.config.TimeZones;
import br.com.logsproductionreview.dto.LogEntryResponse;
import br.com.logsproductionreview.dto.LogSearchFilter;
import br.com.logsproductionreview.dto.LogSummaryResponse;
import br.com.logsproductionreview.dto.LogSummaryResponse.DayCount;
import br.com.logsproductionreview.dto.LogSummaryResponse.TypeCount;
import br.com.logsproductionreview.entity.LogNotification;
import br.com.logsproductionreview.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LogQueryServiceImpl implements LogQueryService {

    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("_id"));

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<LogEntryResponse> search(LogSearchFilter filter, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        Query query = new Query(buildCriteria(filter)).with(sorted);

        List<LogEntryResponse> content = mongoTemplate.find(query, LogNotification.class).stream()
                .map(LogEntryResponse::from)
                .toList();

        return PageableExecutionUtils.getPage(content, sorted,
                () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), LogNotification.class));
    }

    @Override
    public LogSummaryResponse summary(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(TimeZones.SAO_PAULO).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(TimeZones.SAO_PAULO).toInstant();
        Criteria range = Criteria.where("occurredAt").gte(start).lt(end);

        List<TypeCount> byType = aggregateByType(range);
        long total = byType.stream().mapToLong(TypeCount::count).sum();
        return new LogSummaryResponse(total, byType, aggregateByDay(range, from, to));
    }

    static Criteria buildCriteria(LogSearchFilter filter) {
        List<Criteria> criteria = new ArrayList<>();
        if (hasText(filter.type())) {
            criteria.add(Criteria.where("type").is(filter.type().trim()));
        }
        if (hasText(filter.entityType())) {
            criteria.add(Criteria.where("entityType").is(filter.entityType().trim()));
        }
        if (filter.userId() != null) {
            criteria.add(Criteria.where("userId").is(filter.userId()));
        }
        if (hasText(filter.search())) {
            Pattern pattern = Pattern.compile(Pattern.quote(filter.search().trim()),
                    Pattern.CASE_INSENSITIVE);
            criteria.add(new Criteria().orOperator(
                    Criteria.where("action").regex(pattern),
                    Criteria.where("message").regex(pattern),
                    Criteria.where("nameUser").regex(pattern)));
        }
        if (filter.from() != null || filter.to() != null) {
            Criteria occurredAt = Criteria.where("occurredAt");
            if (filter.from() != null) {
                occurredAt.gte(filter.from());
            }
            if (filter.to() != null) {
                occurredAt.lt(filter.to());
            }
            criteria.add(occurredAt);
        }
        return criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria);
    }

    private List<TypeCount> aggregateByType(Criteria range) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(range),
                Aggregation.group("type").count().as("count"),
                Aggregation.sort(Sort.by(Sort.Order.desc("count"), Sort.Order.asc("_id"))));

        return mongoTemplate.aggregate(aggregation, LogNotification.class, Document.class)
                .getMappedResults().stream()
                .map(doc -> new TypeCount(doc.getString("_id"), ((Number) doc.get("count")).longValue()))
                .toList();
    }

    private List<DayCount> aggregateByDay(Criteria range, LocalDate from, LocalDate to) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(range),
                Aggregation.project().and(DateOperators.DateToString.dateOf("occurredAt")
                                .toString("%Y-%m-%d")
                                .withTimezone(DateOperators.Timezone.valueOf(TimeZones.SAO_PAULO.getId())))
                        .as("day"),
                Aggregation.group("day").count().as("count"));

        Map<LocalDate, Long> counts = new HashMap<>();
        mongoTemplate.aggregate(aggregation, LogNotification.class, Document.class)
                .getMappedResults()
                .forEach(doc -> counts.put(LocalDate.parse(doc.getString("_id")),
                        ((Number) doc.get("count")).longValue()));

        // um item por dia, inclusive os dias sem eventos
        List<DayCount> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.add(new DayCount(day, counts.getOrDefault(day, 0L)));
        }
        return days;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
