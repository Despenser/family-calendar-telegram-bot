package ru.golubyatnikov.family.calendar.bot.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.golubyatnikov.family.calendar.bot.config.HttpHeadersConfig;
import ru.golubyatnikov.family.calendar.bot.util.CorrelationIdUtil;
import java.io.IOException;
import java.util.UUID;

/**
 * Фильтр для генерации и управления correlation ID для каждого HTTP запроса.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final HttpHeadersConfig httpHeadersConfig;

    /**
     * Обрабатывает каждый HTTP запрос, добавляя correlation ID.
     * 
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param filterChain цепочка фильтров
     *
     * @throws ServletException если возникает ошибка сервлета
     * @throws IOException если возникает ошибка ввода-вывода
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String correlationId = extractOrGenerateCorrelationId(request);
            MDC.put(CorrelationIdUtil.CORRELATION_ID_KEY, correlationId);
            response.setHeader(httpHeadersConfig.getCorrelationIdHeader(), correlationId);

            filterChain.doFilter(request, response);
            
        } finally {
            MDC.remove(CorrelationIdUtil.CORRELATION_ID_KEY);
        }
    }

    /**
     * Извлекает correlation ID из заголовка запроса или генерирует новый.
     * 
     * @param request HTTP запрос
     * @return correlation ID
     */
    private String extractOrGenerateCorrelationId(@NonNull HttpServletRequest request) {
        String correlationId = request.getHeader(httpHeadersConfig.getCorrelationIdHeader());
        return (correlationId == null || correlationId.trim().isEmpty()) 
            ? generateCorrelationId() 
            : correlationId;
    }

    /**
     * Генерирует уникальный correlation ID на основе UUID.
     * 
     * @return уникальный correlation ID
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
