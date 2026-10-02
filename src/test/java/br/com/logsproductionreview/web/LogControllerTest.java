package br.com.logsproductionreview.web;

import br.com.logsproductionreview.dto.LogEntryResponse;
import br.com.logsproductionreview.dto.LogSearchFilter;
import br.com.logsproductionreview.dto.LogSummaryResponse;
import br.com.logsproductionreview.dto.LogSummaryResponse.DayCount;
import br.com.logsproductionreview.dto.LogSummaryResponse.TypeCount;
import br.com.logsproductionreview.security.InternalTokenFilter;
import br.com.logsproductionreview.service.LogQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogController.class)
@TestPropertySource(properties = "logs.api.token=test-token")
class LogControllerTest {

    private static final String TOKEN = "test-token";

    @TestConfiguration
    static class FixedClock {
        // 2026-10-02T02:00Z ainda é dia 01/10 em São Paulo
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-02T02:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogQueryService logQueryService;

    private static LogEntryResponse entry() {
        return new LogEntryResponse("66fc0000000000000000000a", "e1", "REVIEW_CREATED", "Avaliação criada",
                "Usuário Teste avaliou Smartphone X com 5 estrelas", "Usuário Teste", 2L, "REVIEW", "12",
                Instant.parse("2026-10-02T03:10:00Z"), Instant.parse("2026-10-02T03:10:01Z"));
    }

    @Test
    void returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token interno ausente ou inválido"))
                .andExpect(jsonPath("$.httpStatus").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.statusCode").value(401));
        verifyNoInteractions(logQueryService);
    }

    @Test
    void returns401WithWrongToken() throws Exception {
        mockMvc.perform(get("/api/v1/logs/summary").header(InternalTokenFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(logQueryService);
    }

    @Test
    void returnsPageInSameFormatAsMainApi() throws Exception {
        when(logQueryService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(entry()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/logs").header(InternalTokenFilter.HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("66fc0000000000000000000a"))
                .andExpect(jsonPath("$.content[0].eventId").value("e1"))
                .andExpect(jsonPath("$.content[0].type").value("REVIEW_CREATED"))
                .andExpect(jsonPath("$.content[0].userId").value(2))
                .andExpect(jsonPath("$.content[0].entityId").value("12"))
                .andExpect(jsonPath("$.content[0].occurredAt").value("2026-10-02T03:10:00Z"))
                .andExpect(jsonPath("$.content[0].receivedAt").value("2026-10-02T03:10:01Z"))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test
    void passesFiltersAndDateRangeToService() throws Exception {
        when(logQueryService.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/logs").header(InternalTokenFilter.HEADER, TOKEN)
                        .param("page", "2").param("size", "500")
                        .param("type", "REVIEW_CREATED").param("entityType", "REVIEW")
                        .param("userId", "2").param("search", "smart")
                        .param("from", "2026-10-01").param("to", "2026-10-02T10:00:00Z"))
                .andExpect(status().isOk());

        var filter = ArgumentCaptor.forClass(LogSearchFilter.class);
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(logQueryService).search(filter.capture(), pageable.capture());

        assertThat(filter.getValue()).isEqualTo(new LogSearchFilter("REVIEW_CREATED", "REVIEW", 2L, "smart",
                Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-10-02T10:00:00.001Z")));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void dateOnlyToIncludesTheWholeDay() throws Exception {
        when(logQueryService.search(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/logs").header(InternalTokenFilter.HEADER, TOKEN)
                        .param("page", "-1").param("to", "2026-10-01"))
                .andExpect(status().isOk());

        var filter = ArgumentCaptor.forClass(LogSearchFilter.class);
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(logQueryService).search(filter.capture(), pageable.capture());
        assertThat(filter.getValue().from()).isNull();
        assertThat(filter.getValue().to()).isEqualTo(Instant.parse("2026-10-02T03:00:00Z"));
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void returns400ForInvalidDate() throws Exception {
        mockMvc.perform(get("/api/v1/logs").header(InternalTokenFilter.HEADER, TOKEN).param("from", "ontem"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("from: data inválida")))
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.statusCode").value(400));
        verifyNoInteractions(logQueryService);
    }

    @Test
    void returns400WhenFromIsAfterTo() throws Exception {
        mockMvc.perform(get("/api/v1/logs").header(InternalTokenFilter.HEADER, TOKEN)
                        .param("from", "2026-10-03").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void returns400ForNonNumericUserId() throws Exception {
        mockMvc.perform(get("/api/v1/logs").header(InternalTokenFilter.HEADER, TOKEN).param("userId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userId: valor inválido 'abc'"));
    }

    @Test
    void summaryDefaultsToLast30DaysInSaoPaulo() throws Exception {
        LocalDate to = LocalDate.of(2026, 10, 1);
        LocalDate from = LocalDate.of(2026, 9, 2);
        when(logQueryService.summary(from, to)).thenReturn(new LogSummaryResponse(3,
                List.of(new TypeCount("REVIEW_CREATED", 2), new TypeCount("LEGACY", 1)),
                List.of(new DayCount(from, 0), new DayCount(to, 3))));

        mockMvc.perform(get("/api/v1/logs/summary").header(InternalTokenFilter.HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.byType[0].type").value("REVIEW_CREATED"))
                .andExpect(jsonPath("$.byType[0].count").value(2))
                .andExpect(jsonPath("$.byDay[0].date").value("2026-09-02"))
                .andExpect(jsonPath("$.byDay[1].count").value(3));
    }

    @Test
    void summaryUsesGivenRange() throws Exception {
        when(logQueryService.summary(any(), any())).thenReturn(new LogSummaryResponse(0, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/logs/summary").header(InternalTokenFilter.HEADER, TOKEN)
                        .param("from", "2026-09-01").param("to", "2026-10-02T02:00:00Z"))
                .andExpect(status().isOk());

        // 02:00Z do dia 2 ainda é dia 1 em São Paulo
        verify(logQueryService).summary(eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 10, 1)));
    }

    @Test
    void summaryRejectsInvalidDateAndTooLongRange() throws Exception {
        mockMvc.perform(get("/api/v1/logs/summary").header(InternalTokenFilter.HEADER, TOKEN).param("to", "2026-02-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("to: data inválida")));

        mockMvc.perform(get("/api/v1/logs/summary").header(InternalTokenFilter.HEADER, TOKEN)
                        .param("from", "2024-01-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"));

        verifyNoInteractions(logQueryService);
    }
}
