package br.com.logsproductionreview.service;

import br.com.logsproductionreview.dto.LogEntryResponse;
import br.com.logsproductionreview.dto.LogSearchFilter;
import br.com.logsproductionreview.dto.LogSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface LogQueryService {

    /** Página de logs filtrada, sempre em {@code occurredAt} DESC. */
    Page<LogEntryResponse> search(LogSearchFilter filter, Pageable pageable);

    /** Totais por tipo e por dia (fuso America/Sao_Paulo) entre os dias {@code from} e {@code to}, inclusivos. */
    LogSummaryResponse summary(LocalDate from, LocalDate to);
}
