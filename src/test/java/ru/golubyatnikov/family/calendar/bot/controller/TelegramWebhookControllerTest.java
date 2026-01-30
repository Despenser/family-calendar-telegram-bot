package ru.golubyatnikov.family.calendar.bot.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.golubyatnikov.family.calendar.bot.service.UpdateProcessor;
import ru.golubyatnikov.family.calendar.bot.service.WebhookSecurityService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для {@link TelegramWebhookController}.
 * Проверяет валидацию secret token и обработку webhook запросов.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-31
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramWebhookController Unit Tests")
class TelegramWebhookControllerTest {

    @Mock
    private UpdateProcessor updateProcessor;

    @Mock
    private WebhookSecurityService webhookSecurityService;

    private TelegramWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new TelegramWebhookController(updateProcessor, webhookSecurityService);
    }

    @Test
    @DisplayName("Должен успешно обработать запрос с валидным secret token")
    void shouldProcessRequestWithValidSecretToken() {
        // Given
        String validSecretToken = "valid-secret-token";
        Update update = new Update();
        update.setUpdateId(12345);

        when(webhookSecurityService.validateSecretToken(validSecretToken)).thenReturn(true);

        // When
        ResponseEntity<Void> response = controller.onUpdateReceived(validSecretToken, update);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(webhookSecurityService, times(1)).validateSecretToken(validSecretToken);
        verify(updateProcessor, times(1)).processUpdate(update);
    }

    @Test
    @DisplayName("Должен вернуть 401 при невалидном secret token")
    void shouldReturn401WithInvalidSecretToken() {
        // Given
        String invalidSecretToken = "invalid-secret-token";
        Update update = new Update();
        update.setUpdateId(12345);

        when(webhookSecurityService.validateSecretToken(invalidSecretToken)).thenReturn(false);

        // When
        ResponseEntity<Void> response = controller.onUpdateReceived(invalidSecretToken, update);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(webhookSecurityService, times(1)).validateSecretToken(invalidSecretToken);
        verify(updateProcessor, never()).processUpdate(any());
    }

    @Test
    @DisplayName("Должен вернуть 401 при отсутствии secret token")
    void shouldReturn401WithMissingSecretToken() {
        // Given
        Update update = new Update();
        update.setUpdateId(12345);

        when(webhookSecurityService.validateSecretToken(null)).thenReturn(false);

        // When
        ResponseEntity<Void> response = controller.onUpdateReceived(null, update);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(webhookSecurityService, times(1)).validateSecretToken(null);
        verify(updateProcessor, never()).processUpdate(any());
    }

    @Test
    @DisplayName("Должен вернуть 401 при пустом secret token")
    void shouldReturn401WithEmptySecretToken() {
        // Given
        String emptySecretToken = "";
        Update update = new Update();
        update.setUpdateId(12345);

        when(webhookSecurityService.validateSecretToken(emptySecretToken)).thenReturn(false);

        // When
        ResponseEntity<Void> response = controller.onUpdateReceived(emptySecretToken, update);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(webhookSecurityService, times(1)).validateSecretToken(emptySecretToken);
        verify(updateProcessor, never()).processUpdate(any());
    }
}
