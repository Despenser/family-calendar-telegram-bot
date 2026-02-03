package ru.golubyatnikov.family.calendar.bot.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для {@link CorrelationIdFilter}.
 * Проверяет генерацию correlation ID, добавление в MDC и заголовки ответа.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldGenerateCorrelationIdWhenNotProvidedInRequest() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldUseProvidedCorrelationIdFromRequest() throws ServletException, IOException {
        // Given
        String providedCorrelationId = "test-correlation-id-123";
        when(request.getHeader("X-Correlation-ID")).thenReturn(providedCorrelationId);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(response).setHeader("X-Correlation-ID", providedCorrelationId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAddCorrelationIdToMDC() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        // When
        doAnswer(invocation -> {
            // Проверяем, что correlation ID добавлен в MDC во время обработки запроса
            String correlationId = MDC.get("correlationId");
            assertThat(correlationId).isNotNull();
            assertThat(correlationId).isNotEmpty();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        // Then
        // После обработки MDC должен быть очищен
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldClearMDCAfterProcessing() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("test-id");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldClearMDCEvenWhenExceptionOccurs() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("test-id");
        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(request, response);

        // When/Then
        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (ServletException e) {
            // Ожидаемое исключение
        }

        // MDC должен быть очищен даже при исключении
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldGenerateUniqueCorrelationIds() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        String[] correlationIds = new String[2];

        // When
        doAnswer(invocation -> {
            correlationIds[0] = MDC.get("correlationId");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        // Второй запрос
        doAnswer(invocation -> {
            correlationIds[1] = MDC.get("correlationId");
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(correlationIds[0]).isNotNull();
        assertThat(correlationIds[1]).isNotNull();
        assertThat(correlationIds[0]).isNotEqualTo(correlationIds[1]);
    }

    @Test
    void shouldHandleEmptyCorrelationIdHeader() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        // Должен сгенерировать новый correlation ID для пустого заголовка
        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldHandleWhitespaceCorrelationIdHeader() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("   ");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        // Должен сгенерировать новый correlation ID для заголовка с пробелами
        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(filterChain).doFilter(request, response);
    }
}
