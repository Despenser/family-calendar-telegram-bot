package ru.golubyatnikov.family.calendar.bot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для класса MarkdownFormatter.
 */
class MarkdownFormatterTest {

    @Test
    @DisplayName("formatMessage должен экранировать шаблон и один аргумент")
    void shouldEscapeTemplateAndSingleArgument() {
        // Given
        String template = "Дата: %s";
        String date = "12.01.2026";
        
        // When
        String result = MarkdownFormatter.formatMessage(template, date);
        
        // Then
        assertEquals("Дата: 12\\.01\\.2026", result);
    }

    @Test
    @DisplayName("formatMessage должен экранировать несколько аргументов")
    void shouldEscapeMultipleArguments() {
        // Given
        String template = "Событие: %s в %s";
        String event = "Встреча!";
        String time = "14:30";
        
        // When
        String result = MarkdownFormatter.formatMessage(template, event, time);
        
        // Then
        assertEquals("Событие: Встреча\\! в 14:30", result);
    }

    @Test
    @DisplayName("formatMessage должен выбросить исключение при null шаблоне")
    void shouldThrowExceptionWhenTemplateIsNull() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MarkdownFormatter.formatMessage(null, "arg")
        );
        
        assertTrue(exception.getMessage().contains("Шаблон сообщения не может быть null"));
    }

    @Test
    @DisplayName("formatMessage должен обрабатывать null аргумент как строку 'null'")
    void shouldHandleNullArgumentAsString() {
        // When
        String result = MarkdownFormatter.formatMessage("Шаблон: %s", (Object) null);
        
        // Then
        assertEquals("Шаблон: null", result);
    }

    @Test
    @DisplayName("formatMessage должен выбросить исключение при несоответствии количества плейсхолдеров")
    void shouldThrowExceptionWhenPlaceholderCountMismatch() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MarkdownFormatter.formatMessage("Шаблон: %s %s", "arg1")
        );
        
        assertTrue(exception.getMessage().contains("Ошибка форматирования сообщения"));
    }

    @Test
    @DisplayName("formatMessage должен обрабатывать пустой шаблон")
    void shouldHandleEmptyTemplate() {
        // When
        String result = MarkdownFormatter.formatMessage("");
        
        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("formatMessage должен обрабатывать эмодзи и Unicode символы")
    void shouldHandleEmojiAndUnicode() {
        // Given
        String template = "Привет 👋 %s!";
        String name = "Мир";
        
        // When
        String result = MarkdownFormatter.formatMessage(template, name);
        
        // Then
        assertEquals("Привет 👋 Мир\\!", result);
    }

    @Test
    @DisplayName("formatMessage должен экранировать специальные символы в шаблоне")
    void shouldEscapeSpecialCharsInTemplate() {
        // Given
        String template = "✅ Дата выбрана: %s\n\nТеперь выберите час:";
        String date = "12.01.2026";
        
        // When
        String result = MarkdownFormatter.formatMessage(template, date);
        
        // Then
        // Проверяем, что точки в дате экранированы
        assertTrue(result.contains("12\\.01\\.2026"));
        // Проверяем, что двоеточие в шаблоне экранировано
        assertTrue(result.contains("час:"));
    }

    @Test
    @DisplayName("formatMessage должен работать с числовыми аргументами")
    void shouldWorkWithNumericArguments() {
        // Given
        String template = "Найдено событий: %s";
        int count = 5;
        
        // When
        String result = MarkdownFormatter.formatMessage(template, count);
        
        // Then
        assertEquals("Найдено событий: 5", result);
    }

    @Test
    @DisplayName("escape должен экранировать все специальные символы")
    void shouldEscapeAllSpecialCharacters() {
        // Given
        String text = "_*[]()~`>#+-=|{}.!";
        
        // When
        String result = MarkdownFormatter.escape(text);
        
        // Then
        assertEquals("\\_\\*\\[\\]\\(\\)\\~\\`\\>\\#\\+\\-\\=\\|\\{\\}\\.\\!", result);
    }

    @Test
    @DisplayName("escape должен возвращать пустую строку для null")
    void shouldReturnEmptyStringForNull() {
        // When
        String result = MarkdownFormatter.escape(null);
        
        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("escape должен возвращать пустую строку для пустой строки")
    void shouldReturnEmptyStringForEmptyString() {
        // When
        String result = MarkdownFormatter.escape("");
        
        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("formatMessage должен поддерживать целочисленные плейсхолдеры %d")
    void shouldSupportIntegerPlaceholders() {
        // Given
        String template = "Найдено: %d событий";
        int count = 42;
        
        // When
        String result = MarkdownFormatter.formatMessage(template, count);
        
        // Then
        assertEquals("Найдено: 42 событий", result);
    }

    @Test
    @DisplayName("formatMessage должен поддерживать форматирование с ведущими нулями %02d")
    void shouldSupportLeadingZeroFormatting() {
        // Given
        String template = "Час: %02d:00";
        int hour = 9;
        
        // When
        String result = MarkdownFormatter.formatMessage(template, hour);
        
        // Then
        assertEquals("Час: 09:00", result);
    }

    @Test
    @DisplayName("formatMessage должен поддерживать плейсхолдеры с плавающей точкой %f")
    void shouldSupportFloatPlaceholders() {
        // Given
        String template = "Цена: %.2f руб.";
        double price = 123.456;
        
        // When
        String result = MarkdownFormatter.formatMessage(template, price);
        
        // Then
        // Точка должна быть экранирована
        assertTrue(result.contains("123\\.46"));
        assertTrue(result.contains("руб\\."));
    }

    @Test
    @DisplayName("formatMessage должен поддерживать смешанные плейсхолдеры")
    void shouldSupportMixedPlaceholders() {
        // Given
        String template = "Событие %s в %02d:%02d";
        String event = "Встреча";
        int hour = 14;
        int minute = 30;
        
        // When
        String result = MarkdownFormatter.formatMessage(template, event, hour, minute);
        
        // Then
        assertEquals("Событие Встреча в 14:30", result);
    }

    @Test
    @DisplayName("formatMessage должен экранировать точки в числах с плавающей точкой")
    void shouldEscapeDotsInFloatingPointNumbers() {
        // Given
        String template = "Значение: %f";
        double value = 3.14;
        
        // When
        String result = MarkdownFormatter.formatMessage(template, value);
        
        // Then
        // Точка должна быть экранирована
        assertTrue(result.contains("3\\.14"));
    }

    @Test
    @DisplayName("formatMessage должен выбросить исключение при несоответствии типов")
    void shouldThrowExceptionWhenTypeMismatch() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MarkdownFormatter.formatMessage("Число: %d", "строка")
        );
        
        assertTrue(exception.getMessage().contains("Ошибка форматирования сообщения"));
    }

    @Test
    @DisplayName("formatMessage должен сохранять эмодзи при форматировании чисел")
    void shouldPreserveEmojiWithNumericFormatting() {
        // Given
        String template = "✅ Час выбран: %02d:00";
        int hour = 9;
        
        // When
        String result = MarkdownFormatter.formatMessage(template, hour);
        
        // Then
        assertEquals("✅ Час выбран: 09:00", result);
        assertTrue(result.contains("✅"));
    }
}
