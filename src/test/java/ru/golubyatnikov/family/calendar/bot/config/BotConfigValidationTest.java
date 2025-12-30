package ru.golubyatnikov.family.calendar.bot.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit тесты для валидации конфигурации BotConfig.
 * 
 * <p>Проверяет, что при отсутствии обязательных параметров
 * (token, username, webhookUrl) выбрасываются соответствующие
 * исключения валидации.</p>
 * 
 * <p>Validates: Requirements 2.2, 2.5</p>
 */
class BotConfigValidationTest {

    private Validator validator;
    private BotConfig botConfig;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        botConfig = new BotConfig();
    }

    /**
     * Проверяет, что при отсутствии токена бота выбрасывается исключение валидации.
     * 
     * <p>Validates: Requirements 2.2</p>
     */
    @Test
    @DisplayName("Должно выбросить исключение валидации при отсутствии токена бота")
    void shouldFailValidationWhenTokenIsNull() {
        // Given - конфигурация без токена
        botConfig.setToken(null);
        botConfig.setUsername("TestBot");
        botConfig.setWebhookUrl("https://test.example.com/webhook");

        // When - выполняем валидацию
        Set<ConstraintViolation<BotConfig>> violations = validator.validate(botConfig);

        // Then - должна быть ошибка валидации для поля token
        assertFalse(violations.isEmpty(), 
            "Должна быть хотя бы одна ошибка валидации");
        
        boolean hasTokenViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("token"));
        
        assertTrue(hasTokenViolation, 
            "Должна быть ошибка валидации для поля 'token'");
        
        ConstraintViolation<BotConfig> tokenViolation = violations.stream()
            .filter(v -> v.getPropertyPath().toString().equals("token"))
            .findFirst()
            .orElseThrow();
        
        assertTrue(tokenViolation.getMessage().contains("не может быть пустым"),
            "Сообщение об ошибке должно содержать информацию о пустом токене");
    }

    /**
     * Проверяет, что при пустом токене бота выбрасывается исключение валидации.
     * 
     * <p>Validates: Requirements 2.2</p>
     */
    @Test
    @DisplayName("Должно выбросить исключение валидации при пустом токене бота")
    void shouldFailValidationWhenTokenIsEmpty() {
        // Given - конфигурация с пустым токеном
        botConfig.setToken("");
        botConfig.setUsername("TestBot");
        botConfig.setWebhookUrl("https://test.example.com/webhook");

        // When - выполняем валидацию
        Set<ConstraintViolation<BotConfig>> violations = validator.validate(botConfig);

        // Then - должна быть ошибка валидации для поля token
        assertFalse(violations.isEmpty(), 
            "Должна быть хотя бы одна ошибка валидации");
        
        boolean hasTokenViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("token"));
        
        assertTrue(hasTokenViolation, 
            "Должна быть ошибка валидации для поля 'token'");
    }

    /**
     * Проверяет, что при токене из пробелов выбрасывается исключение валидации.
     * 
     * <p>Validates: Requirements 2.2</p>
     */
    @Test
    @DisplayName("Должно выбросить исключение валидации при токене из пробелов")
    void shouldFailValidationWhenTokenIsBlank() {
        // Given - конфигурация с токеном из пробелов
        botConfig.setToken("   ");
        botConfig.setUsername("TestBot");
        botConfig.setWebhookUrl("https://test.example.com/webhook");

        // When - выполняем валидацию
        Set<ConstraintViolation<BotConfig>> violations = validator.validate(botConfig);

        // Then - должна быть ошибка валидации для поля token
        assertFalse(violations.isEmpty(), 
            "Должна быть хотя бы одна ошибка валидации");
        
        boolean hasTokenViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("token"));
        
        assertTrue(hasTokenViolation, 
            "Должна быть ошибка валидации для поля 'token'");
    }

    /**
     * Проверяет, что при отсутствии webhook URL выбрасывается исключение валидации.
     * 
     * <p>Validates: Requirements 2.5</p>
     */
    @Test
    @DisplayName("Должно выбросить исключение валидации при отсутствии webhook URL")
    void shouldFailValidationWhenWebhookUrlIsNull() {
        // Given - конфигурация без webhook URL
        botConfig.setToken("test-token-123");
        botConfig.setUsername("TestBot");
        botConfig.setWebhookUrl(null);

        // When - выполняем валидацию
        Set<ConstraintViolation<BotConfig>> violations = validator.validate(botConfig);

        // Then - должна быть ошибка валидации для поля webhookUrl
        assertFalse(violations.isEmpty(), 
            "Должна быть хотя бы одна ошибка валидации");
        
        boolean hasWebhookViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("webhookUrl"));
        
        assertTrue(hasWebhookViolation, 
            "Должна быть ошибка валидации для поля 'webhookUrl'");
        
        ConstraintViolation<BotConfig> webhookViolation = violations.stream()
            .filter(v -> v.getPropertyPath().toString().equals("webhookUrl"))
            .findFirst()
            .orElseThrow();
        
        assertTrue(webhookViolation.getMessage().contains("не может быть пустым"),
            "Сообщение об ошибке должно содержать информацию о пустом webhook URL");
    }

    /**
     * Проверяет, что при пустом webhook URL выбрасывается исключение валидации.
     * 
     * <p>Validates: Requirements 2.5</p>
     */
    @Test
    @DisplayName("Должно выбросить исключение валидации при пустом webhook URL")
    void shouldFailValidationWhenWebhookUrlIsEmpty() {
        // Given - конфигурация с пустым webhook URL
        botConfig.setToken("test-token-123");
        botConfig.setUsername("TestBot");
        botConfig.setWebhookUrl("");

        // When - выполняем валидацию
        Set<ConstraintViolation<BotConfig>> violations = validator.validate(botConfig);

        // Then - должна быть ошибка валидации для поля webhookUrl
        assertFalse(violations.isEmpty(), 
            "Должна быть хотя бы одна ошибка валидации");
        
        boolean hasWebhookViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("webhookUrl"));
        
        assertTrue(hasWebhookViolation, 
            "Должна быть ошибка валидации для поля 'webhookUrl'");
    }

    /**
     * Проверяет, что при webhook URL из пробелов выбрасывается исключение валидации.
     * 
     * <p>Validates: Requirements 2.5</p>
     */
    @Test
    @DisplayName("Должно выбросить исключение валидации при webhook URL из пробелов")
    void shouldFailValidationWhenWebhookUrlIsBlank() {
        // Given - конфигурация с webhook URL из пробелов
        botConfig.setToken("test-token-123");
        botConfig.setUsername("TestBot");
        botConfig.setWebhookUrl("   ");

        // When - выполняем валидацию
        Set<ConstraintViolation<BotConfig>> violations = validator.validate(botConfig);

        // Then - должна быть ошибка валидации для поля webhookUrl
        assertFalse(violations.isEmpty(), 
            "Должна быть хотя бы одна ошибка валидации");
        
        boolean hasWebhookViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("webhookUrl"));
        
        assertTrue(hasWebhookViolation, 
            "Должна быть ошибка валидации для поля 'webhookUrl'");
    }

    /**
     * Проверяет, что при отсутствии username выбрасывается исключение валидации.
     * 
     * <p>Validates: Requirements 2.2</p>
     */
    @Test
    @DisplayName("Должно выбросить исключение валидации при отсутствии username")
    void shouldFailValidationWhenUsernameIsNull() {
        // Given - конфигурация без username
        botConfig.setToken("test-token-123");
        botConfig.setUsername(null);
        botConfig.setWebhookUrl("https://test.example.com/webhook");

        // When - выполняем валидацию
        Set<ConstraintViolation<BotConfig>> violations = validator.validate(botConfig);

        // Then - должна быть ошибка валидации для поля username
        assertFalse(violations.isEmpty(), 
            "Должна быть хотя бы одна ошибка валидации");
        
        boolean hasUsernameViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("username"));
        
        assertTrue(hasUsernameViolation, 
            "Должна быть ошибка валидации для поля 'username'");
    }

    /**
     * Проверяет, что при валидной конфигурации нет ошибок валидации.
     */
    @Test
    @DisplayName("Не должно быть ошибок валидации при корректной конфигурации")
    void shouldPassValidationWhenAllFieldsAreValid() {
        // Given - полностью валидная конфигурация
        botConfig.setToken("valid-token-123");
        botConfig.setUsername("ValidBot");
        botConfig.setWebhookUrl("https://valid.example.com/webhook");

        // When - выполняем валидацию
        Set<ConstraintViolation<BotConfig>> violations = validator.validate(botConfig);

        // Then - не должно быть ошибок валидации
        assertTrue(violations.isEmpty(), 
            "Не должно быть ошибок валидации для корректной конфигурации");
    }
}
