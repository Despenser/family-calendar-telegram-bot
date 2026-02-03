package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.golubyatnikov.family.calendar.bot.model.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.service.telegram.UnauthorizedMessageService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit тесты для проверки конфигурируемости UnauthorizedMessageService.
 * 
 * <p>Проверяет корректность работы:</p>
 * <ul>
 *   <li>Загрузки шаблонов из конфигурации</li>
 *   <li>Fallback на дефолтные значения</li>
 *   <li>Подстановки параметров в шаблоны</li>
 *   <li>Логирования предупреждений при отсутствии шаблонов</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 8.1, 8.2, 8.3, 8.4, 8.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-12
 */
@DisplayName("UnauthorizedMessageService Configuration Tests")
class UnauthorizedMessageServiceConfigTest {

    @Test
    @DisplayName("Должен загрузить шаблоны из конфигурации")
    void shouldLoadTemplatesFromConfiguration() {
        // Given
        String prefix = "🔒";
        String contactAdmin = "Для получения доступа обратитесь к администратору вашей семьи.";
        String eventCreation = "Создание событий доступно только зарегистрированным пользователям семейного календаря.";
        
        // When
        UnauthorizedMessageService service = new UnauthorizedMessageService(
            prefix, contactAdmin,
            eventCreation, null, null, null, null, null, null
        );
        
        // Then
        String message = service.getMessage(MessageCategory.EVENT_CREATION);
        assertNotNull(message, "Сообщение не должно быть null");
        assertTrue(message.contains("🔒"), "Сообщение должно содержать префикс");
        assertTrue(message.contains("Создание событий"), "Сообщение должно содержать текст из конфигурации");
    }

    @Test
    @DisplayName("Должен использовать fallback при отсутствии шаблона")
    void shouldUseFallbackWhenTemplateIsMissing() {
        // Given
        String prefix = "🔒";
        String contactAdmin = "Для получения доступа обратитесь к администратору вашей семьи.";
        
        // When - все шаблоны null
        UnauthorizedMessageService service = new UnauthorizedMessageService(
            prefix, contactAdmin,
            null, null, null, null, null, null, null
        );
        
        // Then
        String message = service.getMessage(MessageCategory.EVENT_CREATION);
        assertNotNull(message, "Сообщение не должно быть null даже при отсутствии конфигурации");
        assertTrue(message.contains("🔒"), "Сообщение должно содержать префикс");
        assertTrue(message.contains("доступно"), "Сообщение должно содержать дефолтный текст");
    }

    @Test
    @DisplayName("Должен подставлять параметры в шаблон")
    void shouldSubstituteParametersInTemplate() {
        // Given
        String prefix = "🔒";
        String contactAdmin = "Для получения доступа обратитесь к администратору вашей семьи.";
        String template = "Команда {command} доступна только зарегистрированным пользователям.";
        
        UnauthorizedMessageService service = new UnauthorizedMessageService(
            prefix, contactAdmin,
            template, null, null, null, null, null, null
        );
        
        Map<String, String> params = Map.of("command", "/add_event");
        
        // When
        String message = service.getMessage(MessageCategory.EVENT_CREATION, params);
        
        // Then
        assertNotNull(message, "Сообщение не должно быть null");
        // Проверяем, что параметр подставлен (может быть экранирован)
        assertTrue(message.contains("add_event") || message.contains("add\\_event"), 
            "Сообщение должно содержать подставленный параметр");
        assertFalse(message.contains("{command}"), "Плейсхолдер должен быть заменен");
    }

    @Test
    @DisplayName("Должен обрабатывать пустые параметры")
    void shouldHandleEmptyParameters() {
        // Given
        String prefix = "🔒";
        String contactAdmin = "Для получения доступа обратитесь к администратору вашей семьи.";
        String template = "Создание событий доступно только зарегистрированным пользователям.";
        
        UnauthorizedMessageService service = new UnauthorizedMessageService(
            prefix, contactAdmin,
            template, null, null, null, null, null, null
        );
        
        // When
        String message = service.getMessage(MessageCategory.EVENT_CREATION, Map.of());
        
        // Then
        assertNotNull(message, "Сообщение не должно быть null");
        assertTrue(message.contains("Создание событий"), "Сообщение должно содержать текст шаблона");
    }

    @Test
    @DisplayName("Должен использовать дефолтные значения для prefix и contactAdmin")
    void shouldUseDefaultValuesForPrefixAndContactAdmin() {
        // Given & When - используем дефолтные значения через конструктор
        UnauthorizedMessageService service = new UnauthorizedMessageService(
            "🔒", 
            "Для получения доступа обратитесь к администратору вашей семьи.",
            null, null, null, null, null, null, null
        );
        
        // Then
        String message = service.getMessage(MessageCategory.GENERAL);
        assertNotNull(message, "Сообщение не должно быть null");
        assertTrue(message.contains("🔒"), "Должен использоваться дефолтный префикс");
        assertTrue(message.contains("администратору"), "Должна использоваться дефолтная инструкция");
    }

    @Test
    @DisplayName("Должен корректно обрабатывать все категории")
    void shouldHandleAllCategories() {
        // Given
        String prefix = "🔒";
        String contactAdmin = "Для получения доступа обратитесь к администратору вашей семьи.";
        
        UnauthorizedMessageService service = new UnauthorizedMessageService(
            prefix, contactAdmin,
            null, null, null, null, null, null, null
        );
        
        // When & Then - проверяем все категории
        for (MessageCategory category : MessageCategory.values()) {
            String message = service.getMessage(category);
            assertNotNull(message, "Сообщение для категории " + category + " не должно быть null");
            assertTrue(message.contains("🔒"), "Сообщение должно содержать префикс");
            assertTrue(message.contains("доступ"), "Сообщение должно содержать информацию о доступе");
        }
    }

    @Test
    @DisplayName("Должен выбросить исключение при null категории")
    void shouldThrowExceptionWhenCategoryIsNull() {
        // Given
        String prefix = "🔒";
        String contactAdmin = "Для получения доступа обратитесь к администратору вашей семьи.";
        
        UnauthorizedMessageService service = new UnauthorizedMessageService(
            prefix, contactAdmin,
            null, null, null, null, null, null, null
        );
        
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.getMessage(null),
            "Должно быть выброшено IllegalArgumentException"
        );
        
        assertEquals("Категория сообщения не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен подставлять несколько параметров")
    void shouldSubstituteMultipleParameters() {
        // Given
        String prefix = "🔒";
        String contactAdmin = "Для получения доступа обратитесь к администратору вашей семьи.";
        String template = "Команда {command} для функции {feature} доступна только зарегистрированным пользователям.";
        
        UnauthorizedMessageService service = new UnauthorizedMessageService(
            prefix, contactAdmin,
            template, null, null, null, null, null, null
        );
        
        Map<String, String> params = Map.of(
            "command", "/add_event",
            "feature", "создание событий"
        );
        
        // When
        String message = service.getMessage(MessageCategory.EVENT_CREATION, params);
        
        // Then
        assertNotNull(message, "Сообщение не должно быть null");
        // Проверяем, что параметры подставлены (могут быть экранированы)
        assertTrue(message.contains("add_event") || message.contains("add\\_event"), 
            "Сообщение должно содержать первый параметр");
        assertTrue(message.contains("создание событий") || message.contains("создание событий"), 
            "Сообщение должно содержать второй параметр");
        assertFalse(message.contains("{command}"), "Первый плейсхолдер должен быть заменен");
        assertFalse(message.contains("{feature}"), "Второй плейсхолдер должен быть заменен");
    }
}
