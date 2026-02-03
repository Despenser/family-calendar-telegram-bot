package ru.golubyatnikov.family.calendar.bot.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест для проверки структурированного логирования с correlation ID.
 * Проверяет, что correlation ID корректно добавляется в MDC и включается в логи.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
class StructuredLoggingIntegrationTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(StructuredLoggingIntegrationTest.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        logger.detachAppender(listAppender);
    }

    @Test
    void shouldIncludeCorrelationIdInLogs() {
        // Given
        String correlationId = "test-correlation-id-123";
        MDC.put("correlationId", correlationId);

        // When
        logger.info("Test message with correlation ID");

        // Then
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logEvent = listAppender.list.get(0);
        assertThat(logEvent.getMDCPropertyMap()).containsEntry("correlationId", correlationId);
        assertThat(logEvent.getMessage()).isEqualTo("Test message with correlation ID");
    }

    @Test
    void shouldHandleMissingCorrelationId() {
        // Given - correlation ID не установлен

        // When
        logger.info("Test message without correlation ID");

        // Then
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logEvent = listAppender.list.get(0);
        assertThat(logEvent.getMDCPropertyMap()).doesNotContainKey("correlationId");
    }

    @Test
    void shouldIncludeMultipleMdcKeys() {
        // Given
        MDC.put("correlationId", "test-id");
        MDC.put("userId", "user-123");
        MDC.put("eventId", "event-456");

        // When
        logger.info("Test message with multiple MDC keys");

        // Then
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logEvent = listAppender.list.get(0);
        assertThat(logEvent.getMDCPropertyMap())
                .containsEntry("correlationId", "test-id")
                .containsEntry("userId", "user-123")
                .containsEntry("eventId", "event-456");
    }

    @Test
    void shouldClearMdcBetweenRequests() {
        // Given
        MDC.put("correlationId", "first-request");
        logger.info("First request");

        // When
        MDC.clear();
        MDC.put("correlationId", "second-request");
        logger.info("Second request");

        // Then
        assertThat(listAppender.list).hasSize(2);
        assertThat(listAppender.list.get(0).getMDCPropertyMap())
                .containsEntry("correlationId", "first-request");
        assertThat(listAppender.list.get(1).getMDCPropertyMap())
                .containsEntry("correlationId", "second-request");
    }

    @Test
    void shouldPreserveCorrelationIdAcrossMultipleLogs() {
        // Given
        String correlationId = "persistent-id";
        MDC.put("correlationId", correlationId);

        // When
        logger.info("First log");
        logger.warn("Second log");
        logger.error("Third log");

        // Then
        assertThat(listAppender.list).hasSize(3);
        listAppender.list.forEach(event ->
                assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", correlationId)
        );
    }
}
