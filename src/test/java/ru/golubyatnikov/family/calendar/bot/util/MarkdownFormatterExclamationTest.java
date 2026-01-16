package ru.golubyatnikov.family.calendar.bot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тест для проверки экранирования восклицательного знака в MarkdownFormatter.
 */
@DisplayName("MarkdownFormatter Exclamation Mark Tests")
class MarkdownFormatterExclamationTest {

    @Test
    @DisplayName("Должен экранировать восклицательный знак в функции escape")
    void escape_ShouldEscapeExclamationMark() {
        // Given
        String input = "Поле успешно обновлено!";
        
        // When
        String result = MarkdownFormatter.escape(input);
        
        // Then
        assertEquals("Поле успешно обновлено\\!", result);
        System.out.println("Input: " + input);
        System.out.println("Output: " + result);
    }

    @Test
    @DisplayName("Должен экранировать восклицательный знак в функции bold")
    void bold_ShouldEscapeExclamationMark() {
        // Given
        String input = "Поле успешно обновлено!";
        
        // When
        String result = MarkdownFormatter.bold(input);
        
        // Then
        assertEquals("*Поле успешно обновлено\\!*", result);
        System.out.println("Input: " + input);
        System.out.println("Output: " + result);
    }

    @Test
    @DisplayName("Должен правильно форматировать сообщение с восклицательным знаком")
    void formatMessage_ShouldEscapeExclamationMark() {
        // Given
        String template = "✅ %s";
        String boldText = MarkdownFormatter.bold("Поле успешно обновлено!");
        
        // When
        String result = MarkdownFormatter.formatMessage(template, boldText);
        
        // Then
        assertTrue(result.contains("\\!"));
        System.out.println("Template: " + template);
        System.out.println("Bold text: " + boldText);
        System.out.println("Result: " + result);
    }
}