package br.com.logsproductionreview.dto;

import java.time.LocalDate;
import java.util.List;

public record LogSummaryResponse(long total, List<TypeCount> byType, List<DayCount> byDay) {

    public record TypeCount(String type, long count) {
    }

    public record DayCount(LocalDate date, long count) {
    }
}
