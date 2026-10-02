package br.com.logsproductionreview.web;

import br.com.logsproductionreview.security.InternalTokenFilter;
import br.com.logsproductionreview.service.LogQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import br.com.logsproductionreview.config.ClockConfig;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem LOGS_API_TOKEN configurado a API não fica aberta: recusa tudo, inclusive um header vazio. */
@WebMvcTest(LogController.class)
@Import(ClockConfig.class)
@TestPropertySource(properties = "logs.api.token=")
class LogControllerWithoutTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogQueryService logQueryService;

    @Test
    void rejectsEverythingWhenTokenIsNotConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/logs").header(InternalTokenFilter.HEADER, ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Serviço de logs sem token interno configurado"))
                .andExpect(jsonPath("$.statusCode").value(401));

        mockMvc.perform(get("/api/v1/logs/summary").header(InternalTokenFilter.HEADER, "qualquer"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(logQueryService);
    }
}
