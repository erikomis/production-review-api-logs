package br.com.logsproductionreview.web;

import br.com.logsproductionreview.config.TimeZones;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Parâmetro {@code from}/{@code to} em ISO-8601, aceito como data ({@code 2026-10-01}),
 * data-hora com offset ({@code 2026-10-01T10:00:00Z}, {@code 2026-10-01T10:00:00-03:00})
 * ou data-hora local ({@code 2026-10-01T10:00:00}, interpretada em America/Sao_Paulo).
 * <p>
 * Uma data sem hora vale pelo dia inteiro: como {@code from} começa às 00:00 e como
 * {@code to} vai até o fim do dia.
 */
public record DateParam(Instant instant, LocalDate date, boolean dateOnly) {

    public static DateParam parse(String name, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            if (value.length() == 10) {
                LocalDate date = LocalDate.parse(value);
                return new DateParam(date.atStartOfDay(TimeZones.SAO_PAULO).toInstant(), date, true);
            }
            Instant instant = hasOffset(value)
                    ? OffsetDateTime.parse(value).toInstant()
                    : LocalDateTime.parse(value).atZone(TimeZones.SAO_PAULO).toInstant();
            return new DateParam(instant, instant.atZone(TimeZones.SAO_PAULO).toLocalDate(), false);
        } catch (DateTimeParseException e) {
            throw new InvalidParameterException(
                    "%s: data inválida '%s'; use ISO-8601 (ex.: 2026-10-01 ou 2026-10-01T10:00:00Z)".formatted(name, value));
        }
    }

    /** Início inclusivo do intervalo. */
    public Instant asStart() {
        return instant;
    }

    /**
     * Fim exclusivo do intervalo: o início do dia seguinte para datas; para data-hora, o próximo
     * milissegundo (o MongoDB guarda datas com precisão de ms), o que torna o instante informado inclusivo.
     */
    public Instant asEndExclusive() {
        return dateOnly
                ? date.plusDays(1).atStartOfDay(TimeZones.SAO_PAULO).toInstant()
                : instant.truncatedTo(ChronoUnit.MILLIS).plusMillis(1);
    }

    private static boolean hasOffset(String value) {
        int timeStart = value.indexOf('T');
        if (timeStart < 0) {
            return false;
        }
        String time = value.substring(timeStart);
        return time.endsWith("Z") || time.endsWith("z") || time.contains("+") || time.contains("-");
    }
}
