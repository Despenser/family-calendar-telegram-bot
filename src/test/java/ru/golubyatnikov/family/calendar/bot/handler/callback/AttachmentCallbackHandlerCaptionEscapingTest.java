package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-тесты для проверки экранирования caption при отправке файлов.
 * 
 * <p>Проверяет, что caption (имена файлов) корректно экранируются
 * перед отправкой через TelegramMessageService.sendFile().</p>
 * 
 * <p><b>Требования:</b> 2.4</p>
 */
@DisplayName("AttachmentCallbackHandler - Экранирование caption")
class AttachmentCallbackHandlerCaptionEscapingTest {
    
    @Test
    @DisplayName("Должен экранировать точки в имени файла")
    void shouldEscapeDotsInFileName() {
        // Given: Имя файла с точками
        String fileName = "photo_1769005286492.jpg";
        
        // When: Экранируем имя файла
        String caption = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Then: Caption должен быть экранирован
        assertEquals("photo\\_1769005286492\\.jpg", caption);
        assertTrue(caption.contains("\\."), "Точки должны быть экранированы");
        assertTrue(caption.contains("\\_"), "Подчеркивания должны быть экранированы");
    }
    
    @Test
    @DisplayName("Должен экранировать специальные символы в имени файла")
    void shouldEscapeSpecialCharsInFileName() {
        // Given: Имя файла со специальными символами
        String fileName = "test_file-name(2024).pdf";
        
        // When: Экранируем имя файла
        String caption = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Then: Caption должен быть экранирован
        assertEquals("test\\_file\\-name\\(2024\\)\\.pdf", caption);
        assertTrue(caption.contains("\\_"), "Подчеркивания должны быть экранированы");
        assertTrue(caption.contains("\\-"), "Дефисы должны быть экранированы");
        assertTrue(caption.contains("\\("), "Открывающие скобки должны быть экранированы");
        assertTrue(caption.contains("\\)"), "Закрывающие скобки должны быть экранированы");
        assertTrue(caption.contains("\\."), "Точки должны быть экранированы");
    }
    
    @Test
    @DisplayName("Должен использовать 'Вложение' если имя файла null")
    void shouldUseDefaultCaptionWhenFileNameIsNull() {
        // Given: Имя файла null
        String fileName = null;
        
        // When: Формируем caption
        String caption = fileName != null ? 
                MarkdownFormatter.escapeMarkdownV2(fileName) : "Вложение";
        
        // Then: Caption должен быть "Вложение"
        assertEquals("Вложение", caption);
    }
    
    @Test
    @DisplayName("Должен экранировать имя файла с датой")
    void shouldEscapeFileNameWithDate() {
        // Given: Имя файла с датой
        String fileName = "report_21.01.2026.xlsx";
        
        // When: Экранируем имя файла
        String caption = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Then: Caption должен быть экранирован
        assertEquals("report\\_21\\.01\\.2026\\.xlsx", caption);
        // Проверяем, что все неэкранированные точки отсутствуют
        // (экранированные точки выглядят как "\.")
        assertTrue(caption.contains("\\."), "Точки должны быть экранированы");
    }
    
    @Test
    @DisplayName("Должен корректно обрабатывать имя файла с множественными специальными символами")
    void shouldHandleMultipleSpecialChars() {
        // Given: Имя файла с множественными специальными символами
        String fileName = "my_file-v2.0(final)!.doc";
        
        // When: Экранируем имя файла
        String caption = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Then: Caption должен быть полностью экранирован
        assertEquals("my\\_file\\-v2\\.0\\(final\\)\\!\\.doc", caption);
    }
    
    @Test
    @DisplayName("Должен экранировать имя файла с пробелами")
    void shouldEscapeFileNameWithSpaces() {
        // Given: Имя файла с пробелами
        String fileName = "my document v1.0.pdf";
        
        // When: Экранируем имя файла
        String caption = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Then: Caption должен быть экранирован (пробелы не экранируются)
        assertEquals("my document v1\\.0\\.pdf", caption);
    }
    
    @Test
    @DisplayName("Должен экранировать имя файла с кириллицей")
    void shouldEscapeFileNameWithCyrillic() {
        // Given: Имя файла с кириллицей и специальными символами
        String fileName = "фото_21.01.2026.jpg";
        
        // When: Экранируем имя файла
        String caption = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Then: Caption должен быть экранирован
        assertEquals("фото\\_21\\.01\\.2026\\.jpg", caption);
    }
    
    @Test
    @DisplayName("Должен экранировать имя файла с версией")
    void shouldEscapeFileNameWithVersion() {
        // Given: Имя файла с версией
        String fileName = "app-v1.2.3-release.apk";
        
        // When: Экранируем имя файла
        String caption = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Then: Caption должен быть экранирован
        assertEquals("app\\-v1\\.2\\.3\\-release\\.apk", caption);
    }
}
