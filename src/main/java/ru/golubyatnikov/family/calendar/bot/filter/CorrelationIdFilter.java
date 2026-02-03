package ru.golubyatnikov.family.calendar.bot.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Фильтр для генерации и управления correlation ID для каждого HTTP запроса.
 * Correlation ID используется для трейсинга запросов через всю систему и связывания логов.
 * 
 * Фильтр выполняет следующие действия:
 * - Генерирует уникальный correlation ID для каждого запроса
 * - Добавляет correlation ID в MDC для автоматического включения в логи
 * - Добавляет correlation ID в заголовок ответа для клиентского трейсинга
 * - Очищает MDC после обработки запроса
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Имя заголовка для correlation ID в запросе.
     */
    private static final String CORRELATION_ID_HEADER_NAME = "X-Correlation-ID";
    
    /**
     * Ключ для хранения correlation ID в MDC.
     */
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Обрабатывает каждый HTTP запрос, добавляя correlation ID.
     * 
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param filterChain цепочка фильтров
     * @throws ServletException если возникает ошибка сервлета
     * @throws IOException если возникает ошибка ввода-вывода
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Получаем correlation ID из заголовка запроса или генерируем новый
            String correlationId = extractOrGenerateCorrelationId(request);
            
            // Добавляем correlation ID в MDC для логирования
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            
            // Добавляем correlation ID в заголовок ответа
            response.setHeader(CORRELATION_ID_HEADER_NAME, correlationId);
            
            log.debug("Обработка запроса с correlation ID: {}", correlationId);
            
            // Продолжаем обработку запроса
            filterChain.doFilter(request, response);
            
        } finally {
            // Очищаем MDC после обработки запроса
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    /**
     * Извлекает correlation ID из заголовка запроса или генерирует новый.
     * 
     * @param request HTTP запрос
     * @return correlation ID
     */
    private String extractOrGenerateCorrelationId(@NonNull HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER_NAME);
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = generateCorrelationId();
            log.debug("Сгенерирован новый correlation ID: {}", correlationId);
        } else {
            log.debug("Использован correlation ID из заголовка: {}", correlationId);
        }
        
        return correlationId;
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
