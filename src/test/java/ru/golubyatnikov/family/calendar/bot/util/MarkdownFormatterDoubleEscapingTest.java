package ru.golubyatnikov.family.calendar.bot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тест для проверки исправления проблемы двойного экранирования в MarkdownFormatter.
 * 
 * Проблема: использование String.format() с уже экранированным текстом из bold() 
 * приводило к двойному экранированию специальных символов.
 * 
 * Решение: использование formatMessage() вместо String.format() с функциями форматирования.
 */
@DisplayName("MarkdownFormatter Double Escaping Fix Tests")
class MarkdownFormatterDoubleEscapingTest {

    @Test
    @DisplayName("formatMessage должен правильно обрабатывать восклицательный знак")
    void formatMessage_ShouldHandleExclamationMarkCorrectly() {
        // Given
        String template = "✅ %s";
        String text = "Поле успешно обновлено!";
        
        // When
        String result = MarkdownFormatter.formatMessage(template, text);
        
        // Then
        assertEquals("✅ Поле успешно обновлено\\!", result);
        // Проверяем, что восклицательный знак экранирован только один раз
        assertFalse(result.contains("\\\\!"), "Не должно быть двойного экранирования");
        assertTrue(result.contains("\\!"), "Восклицательный знак должен быть экранирован");
        
        System.out.println("Template: " + template);
        System.out.println("Text: " + text);
        System.out.println("Result: " + result);
    }

    @Test
    @DisplayName("formatMessage должен правильно обрабатывать точки в датах")
    void formatMessage_ShouldHandleDotsInDatesCorrectly() {
        // Given
        String template = "📅 Дата: %s";
        String date = "15.01.2026";
        
        // When
        String result = MarkdownFormatter.formatMessage(template, date);
        
        // Then
        assertEquals("📅 Дата: 15\\.01\\.2026", result);
        // Проверяем, что точки экранированы только один раз
        assertFalse(result.contains("\\\\."), "Не должно быть двойного экранирования точек");
        assertTrue(result.contains("\\."), "Точки должны быть экранированы");
        
        System.out.println("Template: " + template);
        System.out.println("Date: " + date);
        System.out.println("Result: " + result);
    }

    @Test
    @DisplayName("formatMessage должен правильно обрабатывать скобки во времени")
    void formatMessage_ShouldHandleParenthesesInTimeCorrectly() {
        // Given
        String template = "🕐 Время: %s";
        String time = "14:30 (через 2 часа)";
        
        // When
        String result = MarkdownFormatter.formatMessage(template, time);
        
        // Then
        assertEquals("🕐 Время: 14:30 \\(через 2 часа\\)", result);
        // Проверяем, что скобки экранированы только один раз
        assertFalse(result.contains("\\\\(") || result.contains("\\\\)"), 
                   "Не должно быть двойного экранирования скобок");
        assertTrue(result.contains("\\(") && result.contains("\\)"), 
                  "Скобки должны быть экранированы");
        
        System.out.println("Template: " + template);
        System.out.println("Time: " + time);
        System.out.println("Result: " + result);
    }

    @Test
    @DisplayName("Демонстрация проблемы с String.format + bold (для документации)")
    void demonstrateDoubleEscapingProblem() {
        // Given - старый подход (проблемный)
        String text = "Поле успешно обновлено!";
        String boldText = MarkdownFormatter.bold(text); // уже экранирует !
        
        // When - используем String.format (проблема)
        String problematicResult = String.format("✅ %s", boldText);
        
        // Then - показываем проблему
        System.out.println("Проблемный подход:");
        System.out.println("1. bold(\"" + text + "\") = " + boldText);
        System.out.println("2. String.format(\"✅ %s\", boldText) = " + problematicResult);
        System.out.println("Результат содержит уже экранированный текст, но эмодзи не экранировано");
        
        // Правильный подход
        String correctResult = MarkdownFormatter.formatMessage("✅ %s", text);
        System.out.println("\nПравильный подход:");
        System.out.println("formatMessage(\"✅ %s\", \"" + text + "\") = " + correctResult);
        System.out.println("Весь результат правильно экранирован");
        
        // Проверяем разницу
        assertNotEquals(problematicResult, correctResult, 
                       "Результаты должны отличаться");
    }
}