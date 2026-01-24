package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-тесты для проверки корректного экранирования специальных символов MarkdownV2
 * в сообщениях о вложениях.
 * 
 * <p>Эти тесты проверяют, что метод {@link MarkdownFormatter#escapeMarkdownV2(String)}
 * корректно экранирует все специальные символы, которые могут встречаться в:</p>
 * <ul>
 *   <li>Именах файлов</li>
 *   <li>Размерах файлов (с точками)</li>
 *   <li>Датах загрузки (с точками)</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-21
 */
@DisplayName("Тесты экранирования Markdown в AttachmentCallbackHandler")
class AttachmentCallbackHandlerMarkdownEscapingTest {
    
    @Test
    @DisplayName("Экранирование имени файла со специальными символами")
    void testFileNameEscaping() {
        // Имя файла с точками и подчеркиваниями
        String fileName = "photo_1769005286492.jpg";
        String escaped = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Проверяем, что точка и подчеркивание экранированы
        assertEquals("photo\\_1769005286492\\.jpg", escaped);
        assertTrue(escaped.contains("\\."), "Точка должна быть экранирована");
        assertTrue(escaped.contains("\\_"), "Подчеркивание должно быть экранировано");
    }
    
    @Test
    @DisplayName("Экранирование имени файла с множественными точками")
    void testFileNameWithMultipleDotsEscaping() {
        String fileName = "document.backup.2026.01.21.pdf";
        String escaped = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Проверяем, что все точки экранированы
        assertEquals("document\\.backup\\.2026\\.01\\.21\\.pdf", escaped);
        
        // Подсчитываем количество экранированных точек
        long escapedDotsCount = escaped.chars().filter(ch -> ch == '\\').count();
        assertEquals(5, escapedDotsCount, "Все 5 точек должны быть экранированы");
    }
    
    @Test
    @DisplayName("Экранирование размера файла в КБ")
    void testFileSizeInKbEscaping() {
        String fileSize = "52.05 КБ";
        String escaped = MarkdownFormatter.escapeMarkdownV2(fileSize);
        
        // Проверяем, что точка экранирована
        assertEquals("52\\.05 КБ", escaped);
        assertTrue(escaped.contains("\\."), "Точка в размере файла должна быть экранирована");
    }
    
    @Test
    @DisplayName("Экранирование размера файла в МБ")
    void testFileSizeInMbEscaping() {
        String fileSize = "15.78 МБ";
        String escaped = MarkdownFormatter.escapeMarkdownV2(fileSize);
        
        // Проверяем, что точка экранирована
        assertEquals("15\\.78 МБ", escaped);
        assertTrue(escaped.contains("\\."), "Точка в размере файла должна быть экранирована");
    }
    
    @Test
    @DisplayName("Экранирование даты загрузки")
    void testUploadDateEscaping() {
        String date = "21.01.2026 14:21";
        String escaped = MarkdownFormatter.escapeMarkdownV2(date);
        
        // Проверяем, что точки экранированы (в дате 2 точки: после дня и после месяца)
        assertEquals("21\\.01\\.2026 14:21", escaped);
        
        // Подсчитываем количество экранированных точек (должно быть 2: в дате)
        long escapedDotsCount = escaped.chars().filter(ch -> ch == '\\').count();
        assertEquals(2, escapedDotsCount, "Обе точки в дате должны быть экранированы");
    }
    
    @Test
    @DisplayName("Экранирование даты с различными форматами")
    void testDifferentDateFormatsEscaping() {
        // Дата в начале года
        String date1 = "01.01.2026 00:00";
        String escaped1 = MarkdownFormatter.escapeMarkdownV2(date1);
        assertEquals("01\\.01\\.2026 00:00", escaped1);
        
        // Дата в конце года
        String date2 = "31.12.2026 23:59";
        String escaped2 = MarkdownFormatter.escapeMarkdownV2(date2);
        assertEquals("31\\.12\\.2026 23:59", escaped2);
    }
    
    @Test
    @DisplayName("Экранирование имени файла с восклицательным знаком")
    void testFileNameWithExclamationMarkEscaping() {
        String fileName = "important!.txt";
        String escaped = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Проверяем, что восклицательный знак и точка экранированы
        assertEquals("important\\!\\.txt", escaped);
        assertTrue(escaped.contains("\\!"), "Восклицательный знак должен быть экранирован");
        assertTrue(escaped.contains("\\."), "Точка должна быть экранирована");
    }
    
    @Test
    @DisplayName("Экранирование имени файла со скобками")
    void testFileNameWithParenthesesEscaping() {
        String fileName = "photo(1).jpg";
        String escaped = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Проверяем, что скобки и точка экранированы
        assertEquals("photo\\(1\\)\\.jpg", escaped);
        assertTrue(escaped.contains("\\("), "Открывающая скобка должна быть экранирована");
        assertTrue(escaped.contains("\\)"), "Закрывающая скобка должна быть экранирована");
        assertTrue(escaped.contains("\\."), "Точка должна быть экранирована");
    }
    
    @Test
    @DisplayName("Экранирование пустой строки")
    void testEmptyStringEscaping() {
        String empty = "";
        String escaped = MarkdownFormatter.escapeMarkdownV2(empty);
        
        assertEquals("", escaped, "Пустая строка должна остаться пустой");
    }
    
    @Test
    @DisplayName("Экранирование null")
    void testNullEscaping() {
        String escaped = MarkdownFormatter.escapeMarkdownV2(null);
        
        assertEquals("", escaped, "null должен преобразовываться в пустую строку");
    }
    
    @Test
    @DisplayName("Экранирование строки без специальных символов")
    void testStringWithoutSpecialCharsEscaping() {
        String text = "обычный текст";
        String escaped = MarkdownFormatter.escapeMarkdownV2(text);
        
        assertEquals("обычный текст", escaped, "Строка без специальных символов не должна изменяться");
    }
    
    @Test
    @DisplayName("Экранирование всех специальных символов MarkdownV2")
    void testAllSpecialCharsEscaping() {
        String text = "_*[]()~`>#+-=|{}.!";
        String escaped = MarkdownFormatter.escapeMarkdownV2(text);
        
        // Проверяем, что все специальные символы экранированы
        assertEquals("\\_\\*\\[\\]\\(\\)\\~\\`\\>\\#\\+\\-\\=\\|\\{\\}\\.\\!", escaped);
        
        // Проверяем, что каждый специальный символ предваряется обратным слешем
        char[] specialChars = {'_', '*', '[', ']', '(', ')', '~', '`', 
                              '>', '#', '+', '-', '=', '|', '{', '}', '.', '!'};
        
        for (char c : specialChars) {
            assertTrue(escaped.contains("\\" + c), 
                    "Символ '" + c + "' должен быть экранирован");
        }
    }
}
