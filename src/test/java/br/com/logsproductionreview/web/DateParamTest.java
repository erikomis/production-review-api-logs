package br.com.logsproductionreview.web;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateParamTest {

    @Test
    void dateOnlyCoversTheWholeDayInSaoPaulo() {
        DateParam param = DateParam.parse("from", "2026-10-01");

        assertThat(param.dateOnly()).isTrue();
        assertThat(param.date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(param.asStart()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(param.asEndExclusive()).isEqualTo(Instant.parse("2026-10-02T03:00:00Z"));
    }

    @Test
    void dateTimeWithOffsetIsInclusiveToTheMillisecond() {
        DateParam param = DateParam.parse("to", "2026-10-02T03:10:00Z");

        assertThat(param.dateOnly()).isFalse();
        assertThat(param.asStart()).isEqualTo(Instant.parse("2026-10-02T03:10:00Z"));
        assertThat(param.asEndExclusive()).isEqualTo(Instant.parse("2026-10-02T03:10:00.001Z"));
        // 03:10Z = 00:10 do dia 2 em São Paulo (UTC-3)
        assertThat(param.date()).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void localDateTimeIsInterpretedInSaoPaulo() {
        assertThat(DateParam.parse("from", "2026-10-01T10:00:00").asStart())
                .isEqualTo(Instant.parse("2026-10-01T13:00:00Z"));
        assertThat(DateParam.parse("from", "2026-10-01T10:00:00-03:00").asStart())
                .isEqualTo(Instant.parse("2026-10-01T13:00:00Z"));
    }

    @Test
    void blankIsNull() {
        assertThat(DateParam.parse("from", null)).isNull();
        assertThat(DateParam.parse("from", " ")).isNull();
    }

    @Test
    void invalidValuesThrowWithParameterName() {
        for (String value : new String[]{"ontem", "2026-13-01", "01/10/2026", "2026-10-01T25:00", "2026-1-1"}) {
            assertThatThrownBy(() -> DateParam.parse("from", value))
                    .as(value)
                    .isInstanceOf(InvalidParameterException.class)
                    .hasMessageStartingWith("from:");
        }
    }
}
