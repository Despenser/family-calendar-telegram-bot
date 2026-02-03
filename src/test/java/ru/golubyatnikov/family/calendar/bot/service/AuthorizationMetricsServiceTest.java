package ru.golubyatnikov.family.calendar.bot.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.golubyatnikov.family.calendar.bot.model.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.service.authorization.AuthorizationMetricsService;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты для AuthorizationMetricsService.
 * 
 * <p>Проверяет корректность записи метрик авторизации и обработки
 * неавторизованных пользователей.</p>
 */
@DisplayName("AuthorizationMetricsService Tests")
class AuthorizationMetricsServiceTest {
    
    private MeterRegistry meterRegistry;
    private AuthorizationMetricsService metricsService;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new AuthorizationMetricsService(meterRegistry);
    }
    
    @Test
    @DisplayName("Должен записывать попытку доступа неавторизованного пользователя")
    void shouldRecordUnauthorizedAccessAttempt() {
        // Given
        String commandName = "/add_event";
        
        // When
        metricsService.recordUnauthorizedAccessAttempt(commandName);
        
        // Then
        Counter counter = meterRegistry.find("unauthorized_access_attempts_total")
                .tag("command", commandName)
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Должен записывать несколько попыток доступа")
    void shouldRecordMultipleUnauthorizedAccessAttempts() {
        // Given
        String commandName = "/add_event";
        
        // When
        metricsService.recordUnauthorizedAccessAttempt(commandName);
        metricsService.recordUnauthorizedAccessAttempt(commandName);
        metricsService.recordUnauthorizedAccessAttempt(commandName);
        
        // Then
        double count = metricsService.getUnauthorizedAccessCount(commandName);
        assertThat(count).isEqualTo(3.0);
    }
    
    @Test
    @DisplayName("Должен записывать отправку сообщения об ограничении доступа")
    void shouldRecordMessageSent() {
        // Given
        MessageCategory category = MessageCategory.EVENT_CREATION;
        
        // When
        metricsService.recordMessageSent(category);
        
        // Then
        Counter counter = meterRegistry.find("unauthorized_messages_sent_total")
                .tag("category", category.name())
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Должен записывать время проверки авторизации")
    void shouldRecordAuthorizationCheckDuration() {
        // Given
        long durationNanos = TimeUnit.MILLISECONDS.toNanos(10);
        boolean isAuthorized = false;
        
        // When
        metricsService.recordAuthorizationCheckDuration(durationNanos, isAuthorized);
        
        // Then
        Timer timer = meterRegistry.find("authorization_check_duration_seconds")
                .tag("result", "unauthorized")
                .timer();
        
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(durationNanos);
    }
    
    @Test
    @DisplayName("Должен записывать ошибку отправки сообщения")
    void shouldRecordMessageSendError() {
        // Given
        String errorType = "telegram_api_error";
        
        // When
        metricsService.recordMessageSendError(errorType);
        
        // Then
        Counter counter = meterRegistry.find("message_send_errors_total")
                .tag("error_type", errorType)
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Должен обрабатывать пустое имя команды")
    void shouldHandleEmptyCommandName() {
        // When
        metricsService.recordUnauthorizedAccessAttempt("");
        
        // Then
        double count = metricsService.getUnauthorizedAccessCount("");
        assertThat(count).isEqualTo(0.0);
    }
    
    @Test
    @DisplayName("Должен обрабатывать null категорию")
    void shouldHandleNullCategory() {
        // When
        metricsService.recordMessageSent(null);
        
        // Then
        double count = metricsService.getMessagesSentCount(null);
        assertThat(count).isEqualTo(0.0);
    }
    
    @Test
    @DisplayName("Должен обрабатывать отрицательную длительность")
    void shouldHandleNegativeDuration() {
        // When
        metricsService.recordAuthorizationCheckDuration(-100, false);
        
        // Then
        Timer timer = meterRegistry.find("authorization_check_duration_seconds")
                .tag("result", "unauthorized")
                .timer();
        
        assertThat(timer).isNull();
    }
    
    @Test
    @DisplayName("Должен записывать разные типы ошибок")
    void shouldRecordDifferentErrorTypes() {
        // When
        metricsService.recordMessageSendError("telegram_api_error");
        metricsService.recordMessageSendError("network_error");
        metricsService.recordMessageSendError("telegram_api_error");
        
        // Then
        double telegramErrors = metricsService.getMessageErrorsCount("telegram_api_error");
        double networkErrors = metricsService.getMessageErrorsCount("network_error");
        
        assertThat(telegramErrors).isEqualTo(2.0);
        assertThat(networkErrors).isEqualTo(1.0);
    }
    
    @Test
    @DisplayName("Должен различать авторизованных и неавторизованных пользователей в метриках времени")
    void shouldDistinguishAuthorizedAndUnauthorizedInDurationMetrics() {
        // Given
        long duration1 = TimeUnit.MILLISECONDS.toNanos(5);
        long duration2 = TimeUnit.MILLISECONDS.toNanos(10);
        
        // When
        metricsService.recordAuthorizationCheckDuration(duration1, true);
        metricsService.recordAuthorizationCheckDuration(duration2, false);
        
        // Then
        Timer authorizedTimer = meterRegistry.find("authorization_check_duration_seconds")
                .tag("result", "authorized")
                .timer();
        
        Timer unauthorizedTimer = meterRegistry.find("authorization_check_duration_seconds")
                .tag("result", "unauthorized")
                .timer();
        
        assertThat(authorizedTimer).isNotNull();
        assertThat(authorizedTimer.count()).isEqualTo(1);
        
        assertThat(unauthorizedTimer).isNotNull();
        assertThat(unauthorizedTimer.count()).isEqualTo(1);
    }
}
