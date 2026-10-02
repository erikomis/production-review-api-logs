package br.com.logsproductionreview.web;

import br.com.logsproductionreview.config.TimeZones;
import br.com.logsproductionreview.dto.LogEntryResponse;
import br.com.logsproductionreview.dto.LogSearchFilter;
import br.com.logsproductionreview.dto.LogSummaryResponse;
import br.com.logsproductionreview.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {

    static final int DEFAULT_SIZE = 10;
    static final int MAX_SIZE = 100;
    static final int DEFAULT_SUMMARY_DAYS = 30;
    static final int MAX_SUMMARY_DAYS = 366;

    private final LogQueryService logQueryService;
    private final Clock clock;

    @GetMapping
    public Page<LogEntryResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        DateParam fromParam = DateParam.parse("from", from);
        DateParam toParam = DateParam.parse("to", to);
        if (fromParam != null && toParam != null && fromParam.asStart().isAfter(toParam.asStart())) {
            throw new InvalidParameterException("from: deve ser anterior ou igual a to");
        }

        var filter = new LogSearchFilter(type, entityType, userId, search,
                fromParam == null ? null : fromParam.asStart(),
                toParam == null ? null : toParam.asEndExclusive());

        // mesma regra de paginação da API principal: valores fora da faixa caem no padrão/limite
        int pageNumber = (page == null || page < 0) ? 0 : page;
        int pageSize = (size == null || size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return logQueryService.search(filter, PageRequest.of(pageNumber, pageSize));
    }

    /**
     * Sem {@code from}/{@code to}, resume os últimos 30 dias (incluindo hoje, no fuso de São Paulo).
     * Com só um dos dois, o outro é calculado a partir dele.
     */
    @GetMapping("/summary")
    public LogSummaryResponse summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        DateParam fromParam = DateParam.parse("from", from);
        DateParam toParam = DateParam.parse("to", to);

        LocalDate toDate = toParam != null ? toParam.date()
                : fromParam != null ? fromParam.date().plusDays(DEFAULT_SUMMARY_DAYS - 1)
                : LocalDate.now(clock.withZone(TimeZones.SAO_PAULO));
        LocalDate fromDate = fromParam != null ? fromParam.date() : toDate.minusDays(DEFAULT_SUMMARY_DAYS - 1);

        if (fromDate.isAfter(toDate)) {
            throw new InvalidParameterException("from: deve ser anterior ou igual a to");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) + 1 > MAX_SUMMARY_DAYS) {
            throw new InvalidParameterException("Intervalo máximo do resumo é de " + MAX_SUMMARY_DAYS + " dias");
        }
        return logQueryService.summary(fromDate, toDate);
    }
}
