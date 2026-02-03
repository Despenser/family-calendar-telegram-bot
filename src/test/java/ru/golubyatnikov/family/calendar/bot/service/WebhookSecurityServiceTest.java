package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.golubyatnikov.family.calendar.bot.service.authorization.WebhookSecurityService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit тесты для {@link WebhookSecurityService}.
 * Проверяет генерацию и валидацию secret token.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-31
 */
@DisplayName("WebhookSecurityService Unit Tests")
class WebhookSecurityServiceTest {

    private WebhookSecurityService service;

    @BeforeEach
    void setUp() {
        service = new WebhookSecurityService();
    }

    @Test
    @DisplayName("Должен генерировать непустой secret token")
    void shouldGenerateNonEmptySecretToken() {
        // When
        String token = service.generateSecretToken();

        // Then
        assertNotNull(token, "Secret token не должен быть null");
        assertFalse(token.isEmpty(), "Secret token не должен быть пустым");
    }

    @Test
    @DisplayName("Должен генерировать уникальные secret tokens")
    void shouldGenerateUniqueSecretTokens() {
        // When
        String token1 = service.generateSecretToken();
        String token2 = service.generateSecretToken();

        // Then
        assertNotEquals(token1, token2, "Каждый вызов должен генерировать уникальный токен");
    }

    @Test
    @DisplayName("Должен валидировать корректный secret token")
    void shouldValidateCorrectSecretToken() {
        // Given
        String token = service.generateSecretToken();

        // When
        boolean isValid = service.validateSecretToken(token);

        // Then
        assertTrue(isValid, "Сгенерированный токен должен быть валидным");
    }

    @Test
    @DisplayName("Должен отклонять невалидный secret token")
    void shouldRejectInvalidSecretToken() {
        // Given
        service.generateSecretToken();
        String invalidToken = "invalid-token";

        // When
        boolean isValid = service.validateSecretToken(invalidToken);

        // Then
        assertFalse(isValid, "Невалидный токен должен быть отклонен");
    }

    @Test
    @DisplayName("Должен отклонять null secret token")
    void shouldRejectNullSecretToken() {
        // Given
        service.generateSecretToken();

        // When
        boolean isValid = service.validateSecretToken(null);

        // Then
        assertFalse(isValid, "Null токен должен быть отклонен");
    }

    @Test
    @DisplayName("Должен отклонять пустой secret token")
    void shouldRejectEmptySecretToken() {
        // Given
        service.generateSecretToken();

        // When
        boolean isValid = service.validateSecretToken("");

        // Then
        assertFalse(isValid, "Пустой токен должен быть отклонен");
    }

    @Test
    @DisplayName("Должен сохранять и возвращать secret token")
    void shouldStoreAndReturnSecretToken() {
        // Given
        String token = "test-secret-token";

        // When
        service.storeSecretToken(token);
        String storedToken = service.getSecretToken();

        // Then
        assertEquals(token, storedToken, "Сохраненный токен должен совпадать с исходным");
    }

    @Test
    @DisplayName("Должен валидировать сохраненный secret token")
    void shouldValidateStoredSecretToken() {
        // Given
        String token = "test-secret-token";
        service.storeSecretToken(token);

        // When
        boolean isValid = service.validateSecretToken(token);

        // Then
        assertTrue(isValid, "Сохраненный токен должен быть валидным");
    }

    @Test
    @DisplayName("Должен возвращать false при валидации без инициализации токена")
    void shouldReturnFalseWhenValidatingWithoutInitialization() {
        // When
        boolean isValid = service.validateSecretToken("any-token");

        // Then
        assertFalse(isValid, "Валидация должна вернуть false если токен не инициализирован");
    }

    @Test
    @DisplayName("Должен генерировать токен достаточной длины")
    void shouldGenerateTokenWithSufficientLength() {
        // When
        String token = service.generateSecretToken();

        // Then
        assertTrue(token.length() >= 64, 
                "Secret token должен быть достаточно длинным для безопасности");
    }
}
