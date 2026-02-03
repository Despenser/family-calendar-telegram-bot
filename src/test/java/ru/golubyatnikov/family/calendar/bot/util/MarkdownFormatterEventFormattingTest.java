package ru.golubyatnikov.family.calendar.bot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для проверки корректности форматирования событий с проблемными символами.
 * 
 * <p>Эти тесты проверяют, что MarkdownFormatter корректно обрабатывает
 * специальные символы, которые могут встречаться в названиях и описаниях событий.</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.3</p>
 */
class MarkdownFormatterEventFormattingTest {

    @Test
    @DisplayName("Должен корректно экранировать событие с точками в названии")
    void shouldEscapeEventWithDotsInTitle() {
        // Given - событие с точками (например, "Встреча в 14.30")
        String title = "Встреча в 14.30";
        
        // When
        String result = MarkdownFormatter.bold(title);
        
        // Then
        assertEquals("*Встреча в 14\\.30*", result);
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с восклицательными знаками")
    void shouldEscapeEventWithExclamationMarks() {
        // Given - событие с восклицательным знаком
        String title = "Важная встреча!";
        
        // When
        String result = MarkdownFormatter.bold(title);
        
        // Then
        assertEquals("*Важная встреча\\!*", result);
    }

    @Test
    @DisplayName("Должен корректно экранировать событие со скобками")
    void shouldEscapeEventWithParentheses() {
        // Given - событие со скобками
        String title = "Встреча (онлайн)";
        
        // When
        String result = MarkdownFormatter.bold(title);
        
        // Then
        assertEquals("*Встреча \\(онлайн\\)*", result);
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с дефисами и плюсами")
    void shouldEscapeEventWithDashesAndPluses() {
        // Given - событие с дефисами и плюсами
        String title = "Встреча 10-15 человек (+2 гостя)";
        
        // When
        String result = MarkdownFormatter.bold(title);
        
        // Then
        assertEquals("*Встреча 10\\-15 человек \\(\\+2 гостя\\)*", result);
    }

    @Test
    @DisplayName("Должен корректно экранировать описание с множественными специальными символами")
    void shouldEscapeDescriptionWithMultipleSpecialChars() {
        // Given - описание с различными специальными символами
        String description = "Встреча в офисе (каб. 101). Принести: документы, ручку. Важно!";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        assertTrue(result.contains("\\(каб\\. 101\\)"));
        assertTrue(result.contains("документы, ручку\\."));
        assertTrue(result.contains("Важно\\!"));
    }

    @Test
    @DisplayName("Должен корректно форматировать полное событие с датой и временем")
    void shouldFormatCompleteEventWithDateAndTime() {
        // Given - полная информация о событии
        String title = "День рождения!";
        String date = "31.12.2025";
        String time = "18:00";
        String description = "Празднование (дома)";
        
        // When - форматируем как в PlannerCommandHandler.formatEvent()
        StringBuilder formatted = new StringBuilder();
        formatted.append(MarkdownFormatter.escape("📌 ")).append(MarkdownFormatter.bold(title)).append(MarkdownFormatter.escape("\n"));
        formatted.append(MarkdownFormatter.formatMessage("📅 Дата: %s\n", date));
        formatted.append(MarkdownFormatter.formatMessage("🕐 Время: %s", time));
        
        if (description != null && !description.isBlank()) {
            formatted.append(MarkdownFormatter.formatMessage("\n📝 Описание: %s", description));
        }
        
        String result = formatted.toString();
        
        // Then - проверяем, что все специальные символы экранированы
        assertTrue(result.contains("*День рождения\\!*"));
        assertTrue(result.contains("31\\.12\\.2025"));
        assertTrue(result.contains("18:00"));
        assertTrue(result.contains("Празднование \\(дома\\)"));
        // Проверяем, что эмодзи сохранены
        assertTrue(result.contains("📌"));
        assertTrue(result.contains("📅"));
        assertTrue(result.contains("🕐"));
        assertTrue(result.contains("📝"));
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с хештегами")
    void shouldEscapeEventWithHashtags() {
        // Given - событие с хештегами
        String title = "Встреча #важно #срочно";
        
        // When
        String result = MarkdownFormatter.bold(title);
        
        // Then
        assertEquals("*Встреча \\#важно \\#срочно*", result);
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с символами равенства и вертикальной черты")
    void shouldEscapeEventWithEqualsAndPipe() {
        // Given - событие с = и |
        String description = "Формула: x = y | z > 0";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        assertEquals("Формула: x \\= y \\| z \\> 0", result);
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с фигурными скобками")
    void shouldEscapeEventWithCurlyBraces() {
        // Given - событие с фигурными скобками
        String description = "Код: {value: 100}";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        assertEquals("Код: \\{value: 100\\}", result);
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с квадратными скобками")
    void shouldEscapeEventWithSquareBrackets() {
        // Given - событие с квадратными скобками
        String title = "Встреча [важная]";
        
        // When
        String result = MarkdownFormatter.bold(title);
        
        // Then
        assertEquals("*Встреча \\[важная\\]*", result);
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с подчеркиваниями и звездочками")
    void shouldEscapeEventWithUnderscoresAndAsterisks() {
        // Given - событие с _ и *
        String description = "Файл: report_2025*.pdf";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        assertEquals("Файл: report\\_2025\\*\\.pdf", result);
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с тильдой и обратными кавычками")
    void shouldEscapeEventWithTildeAndBackticks() {
        // Given - событие с ~ и `
        String description = "Команда: `npm install` ~deprecated~";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        assertTrue(result.contains("\\`npm install\\`"));
        assertTrue(result.contains("\\~deprecated\\~"));
    }

    @Test
    @DisplayName("Должен сохранять эмодзи при экранировании")
    void shouldPreserveEmojiWhenEscaping() {
        // Given - событие с эмодзи и специальными символами
        String title = "Встреча 🎉 (важно!)";
        
        // When
        String result = MarkdownFormatter.bold(title);
        
        // Then
        assertTrue(result.contains("🎉"));
        assertTrue(result.contains("\\(важно\\!\\)"));
    }

    @Test
    @DisplayName("Должен корректно обрабатывать пустое описание события")
    void shouldHandleEmptyEventDescription() {
        // Given - пустое описание
        String description = "";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("Должен корректно обрабатывать null описание события")
    void shouldHandleNullEventDescription() {
        // Given - null описание
        String description = null;
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        assertEquals("", result);
    }

    @Test
    @DisplayName("Должен корректно форматировать событие с переносами строк")
    void shouldFormatEventWithNewlines() {
        // Given - описание с переносами строк
        String description = "Пункт 1.\nПункт 2.\nПункт 3.";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        assertTrue(result.contains("Пункт 1\\."));
        assertTrue(result.contains("Пункт 2\\."));
        assertTrue(result.contains("Пункт 3\\."));
        assertTrue(result.contains("\n"));
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с URL-подобным текстом")
    void shouldEscapeEventWithUrlLikeText() {
        // Given - описание с URL-подобным текстом
        String description = "Ссылка: https://example.com/path?param=value";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        // Проверяем, что специальные символы экранированы
        // ? не является специальным символом в MarkdownV2
        assertTrue(result.contains("https://example\\.com/path?param\\=value"));
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с email-подобным текстом")
    void shouldEscapeEventWithEmailLikeText() {
        // Given - описание с email
        String description = "Контакт: user@example.com";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        // @ не является специальным символом MarkdownV2, но точка должна быть экранирована
        assertTrue(result.contains("user@example\\.com"));
    }

    @Test
    @DisplayName("Должен корректно экранировать событие с числами и знаками препинания")
    void shouldEscapeEventWithNumbersAndPunctuation() {
        // Given - описание с числами и знаками препинания
        String description = "Цена: 1,000.50 руб. (скидка -10%)";
        
        // When
        String result = MarkdownFormatter.escape(description);
        
        // Then
        assertTrue(result.contains("1,000\\.50"));
        assertTrue(result.contains("руб\\."));
        assertTrue(result.contains("\\(скидка \\-10%\\)"));
    }
}
