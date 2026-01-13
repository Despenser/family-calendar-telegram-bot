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
    @DisplayName("formatMessage должен выбросить исключение при null аргументе")
    void shouldThrowExceptionWhenArgumentIsNull() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MarkdownFormatter.formatMessage("Шаблон: %s", (Object) null)
        );
        
        assertTrue(exception.getMessage().contains("Аргумент с индексом 0 не может быть null"));
    }

    @Test
    @DisplayName("formatMessage должен выбросить исключение при несоответствии количества плейсхолдеров")
    void shouldThrowExceptionWhenPlaceholderCountMismatch() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MarkdownFormatter.formatMessage("Шаблон: %s %s", "arg1")
        );
        
        assertTrue(exception.getMessage().contains("количество плейсхолдеров"));
        assertTrue(exception.getMessage().contains("не совпадает"));
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
}
