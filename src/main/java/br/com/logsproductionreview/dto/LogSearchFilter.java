package br.com.logsproductionreview.dto;

import java.time.Instant;

/**
 * Filtros já validados da listagem. {@code from} é inclusivo e {@code to} é exclusivo.
 */
public record LogSearchFilter(
        String type,
        String entityType,
        Long userId,
        String search,
        Instant from,
        Instant to
) {
}
