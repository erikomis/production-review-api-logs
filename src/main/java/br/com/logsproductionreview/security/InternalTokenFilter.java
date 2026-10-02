package br.com.logsproductionreview.security;

import br.com.logsproductionreview.web.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Exige o header {@code X-Internal-Token} igual a {@code LOGS_API_TOKEN} em {@code /api/v1/**}
 * (na prática, em tudo que não é {@code /actuator/health}). Sem token configurado, recusa tudo
 * (fail closed) e avisa no startup.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    static final String HEALTH_PATH = "/actuator/health";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(@Value("${logs.api.token:}") String token, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        if (token == null || token.isBlank()) {
            this.expectedToken = null;
            log.warn("LOGS_API_TOKEN não configurado: todas as requisições em /api/v1/** serão recusadas com 401");
        } else {
            this.expectedToken = token.trim().getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Só o health check fica público. Todo o resto (inclusive rotas inexistentes e variações
     * como {@code //api/v1/logs}) passa pelo token, para não depender de casar prefixos.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals(HEALTH_PATH) || path.startsWith(HEALTH_PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (expectedToken == null) {
            reject(response, "Serviço de logs sem token interno configurado");
            return;
        }
        String provided = request.getHeader(HEADER);
        // comparação em tempo constante para não vazar o token por tempo de resposta
        if (provided == null || !MessageDigest.isEqual(expectedToken, provided.trim().getBytes(StandardCharsets.UTF_8))) {
            reject(response, "Token interno ausente ou inválido");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(HttpStatus.UNAUTHORIZED, message));
    }
}
