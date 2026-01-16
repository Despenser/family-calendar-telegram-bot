package ru.golubyatnikov.family.calendar.bot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Специальный тест для проверки работы formatMessage с %02d плейсхолдером
 * в контексте метода UpdateProcessor.handleHourSelection().
 * 
 * Этот тест проверяет требование 1.2: поддержка форматирования с ведущими нулями.
 */
class MarkdownFormatterHourSelectionTest {

    @Test
    @DisplayName("formatMessage должен корректно форматировать сообщение выбора часа с %02d")
    void shouldFormatHourSelectionMessageCorrectly() {
        // Given - точно такой же вызов как в UpdateProcessor.handleHourSelection()
        String template = "✅ Час выбран: %02d:00\n\nТеперь выберите минуты:";
        int hour = 9;
        
        // When
        String result = formatMessage(template, hour);
        
        // Then
        // Проверяем что час отформатирован с ведущим нулём
        assertTrue(result.contains("09:00"), 
            "Час должен быть отформатирован с ведущим нулём: " + result);
        
        // Проверяем что эмодзи сохранён
        assertTrue(result.contains("✅"), 
            "Эмодзи должен быть сохранён: " + result);
        
        // Проверяем что двоеточие НЕ экранировано (это не специальный символ MarkdownV2)
        assertTrue(result.contains("09:00"), 
            "Двоеточие не должно быть экранировано: " + result);
        
        // Проверяем полное сообщение
        String expected = "✅ Час выбран: 09:00\n\nТеперь выберите минуты:";
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("formatMessage должен работать для всех часов от 0 до 23")
    void shouldFormatAllHoursCorrectly() {
        String template = "✅ Час выбран: %02d:00\n\nТеперь выберите минуты:";
        
        // Проверяем часы с ведущим нулём (0-9)
        for (int hour = 0; hour <= 9; hour++) {
            String result = formatMessage(template, hour);
            String expectedHour = String.format("%02d", hour);
            assertTrue(result.contains(expectedHour + ":00"), 
                "Час " + hour + " должен быть отформатирован как " + expectedHour + ":00");
        }
        
        // Проверяем часы без ведущего нуля (10-23)
        for (int hour = 10; hour <= 23; hour++) {
            String result = formatMessage(template, hour);
            assertTrue(result.contains(hour + ":00"), 
                "Час " + hour + " должен быть отформатирован как " + hour + ":00");
        }
    }

    @Test
    @DisplayName("formatMessage должен корректно обрабатывать граничные случаи")
    void shouldHandleEdgeCases() {
        String template = "✅ Час выбран: %02d:00\n\nТеперь выберите минуты:";
        
        // Час 0 (полночь)
        String result0 = formatMessage(template, 0);
        assertTrue(result0.contains("00:00"), "Час 0 должен быть 00:00");
        
        // Час 23 (последний час дня)
        String result23 = formatMessage(template, 23);
        assertTrue(result23.contains("23:00"), "Час 23 должен быть 23:00");
        
        // Час 12 (полдень)
        String result12 = formatMessage(template, 12);
        assertTrue(result12.contains("12:00"), "Час 12 должен быть 12:00");
    }

    @Test
    @DisplayName("formatMessage не должен выбрасывать исключение для корректного вызова")
    void shouldNotThrowExceptionForValidCall() {
        // Given
        String template = "✅ Час выбран: %02d:00\n\nТеперь выберите минуты:";
        int hour = 14;
        
        // When & Then - не должно быть исключения
        assertDoesNotThrow(() -> {
            String result = formatMessage(template, hour);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        });
    }
}
