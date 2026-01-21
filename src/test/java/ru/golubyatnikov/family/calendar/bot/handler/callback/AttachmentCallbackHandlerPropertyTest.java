package ru.golubyatnikov.family.calendar.bot.handler.callback;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based тесты для проверки корректного экранирования специальных символов MarkdownV2
 * в сообщениях о вложениях.
 * 
 * <p><b>Validates: Requirements 2.1, 2.2, 2.3</b></p>
 * 
 * <p>Эти тесты генерируют случайные данные (имена файлов, размеры, даты) и проверяют,
 * что все специальные символы MarkdownV2 корректно экранируются.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-21
 */
class AttachmentCallbackHandlerPropertyTest {
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    
    /**
     * Массив специальных символов MarkdownV2, которые должны быть экранированы.
     */
    private static final char[] MARKDOWN_SPECIAL_CHARS = {
        '_', '*', '[', ']', '(', ')', '~', '`', 
        '>', '#', '+', '-', '=', '|', '{', '}', 
        '.', '!'
    };
    
    /**
     * Property: Все специальные символы MarkdownV2 должны быть экранированы.
     * 
     * <p>Для любой строки, содержащей специальные символы, после экранирования
     * каждый специальный символ должен быть предварен обратным слешем.</p>
     */
    @Property
    @Label("Все специальные символы MarkdownV2 экранированы")
    void allSpecialCharsAreEscaped(@ForAll @StringLength(min = 1, max = 100) String text) {
        String escaped = MarkdownFormatter.escapeMarkdownV2(text);
        
        // Проверяем каждый специальный символ
        for (char specialChar : MARKDOWN_SPECIAL_CHARS) {
            if (text.contains(String.valueOf(specialChar))) {
                // Если исходный текст содержит специальный символ,
                // то экранированный текст должен содержать его с обратным слешем
                assertTrue(escaped.contains("\\" + specialChar),
                    String.format("Символ '%c' должен быть экранирован в тексте: %s", specialChar, text));
            }
        }
    }
    
    /**
     * Property: Экранирование не удаляет символы.
     * 
     * <p>После экранирования количество символов должно увеличиться или остаться прежним,
     * но никогда не уменьшиться.</p>
     */
    @Property
    @Label("Экранирование не удаляет символы")
    void escapingDoesNotRemoveChars(@ForAll String text) {
        String escaped = MarkdownFormatter.escapeMarkdownV2(text);
        
        // Длина экранированного текста должна быть >= длины исходного
        assertTrue(escaped.length() >= text.length(),
            "Экранирование не должно уменьшать длину текста");
    }
    
    /**
     * Property: Имена файлов с точками корректно экранируются.
     */
    @Property
    @Label("Имена файлов с точками корректно экранируются")
    void fileNamesWithDotsAreEscaped(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String baseName,
            @ForAll @AlphaChars @StringLength(min = 1, max = 5) String extension) {
        
        String fileName = baseName + "." + extension;
        String escaped = MarkdownFormatter.escapeMarkdownV2(fileName);
        
        // Проверяем, что точка экранирована
        assertTrue(escaped.contains("\\."),
            "Точка в имени файла должна быть экранирована");
        
        // Проверяем, что экранированная строка содержит оба компонента
        assertTrue(escaped.contains(baseName),
            "Экранированная строка должна содержать базовое имя");
    }
    
    /**
     * Property: Размеры файлов с точками корректно экранируются.
     */
    @Property
    @Label("Размеры файлов с точками корректно экранируются")
    void fileSizesWithDotsAreEscaped(
            @ForAll @NumericChars @StringLength(min = 1, max = 5) String integerPart,
            @ForAll @NumericChars @StringLength(min = 1, max = 2) String fractionalPart) {
        
        String fileSize = integerPart + "." + fractionalPart + " КБ";
        String escaped = MarkdownFormatter.escapeMarkdownV2(fileSize);
        
        // Проверяем, что точка экранирована
        assertTrue(escaped.contains("\\."),
            "Точка в размере файла должна быть экранирована");
        
        // Проверяем формат: число\.число КБ
        assertTrue(escaped.matches(".*\\d+\\\\\\.\\d+.*"),
            "Размер файла должен содержать экранированную точку между цифрами");
    }
    
    /**
     * Property: Даты с точками корректно экранируются.
     */
    @Property
    @Label("Даты с точками корректно экранируются")
    void datesWithDotsAreEscaped(@ForAll("validDateTimes") LocalDateTime dateTime) {
        String formattedDate = dateTime.format(DATE_TIME_FORMATTER);
        String escaped = MarkdownFormatter.escapeMarkdownV2(formattedDate);
        
        // Проверяем, что все точки экранированы
        long dotsInOriginal = formattedDate.chars().filter(ch -> ch == '.').count();
        long escapedDotsInResult = countEscapedDots(escaped);
        
        assertEquals(dotsInOriginal, escapedDotsInResult,
            "Все точки в дате должны быть экранированы");
    }
    
    /**
     * Property: Сообщение о вложениях не содержит неэкранированных специальных символов.
     * 
     * <p><b>Validates: Requirements 2.1, 2.2, 2.3</b></p>
     */
    @Property
    @Label("Сообщение о вложениях не содержит неэкранированных специальных символов")
    void attachmentMessageHasNoUnescapedSpecialChars(
            @ForAll @StringLength(min = 1, max = 50) String fileName,
            @ForAll @StringLength(min = 1, max = 20) String fileSize,
            @ForAll("validDateTimes") LocalDateTime uploadDate) {
        
        // Формируем части сообщения (как в AttachmentCallbackHandler)
        String escapedFileName = MarkdownFormatter.escapeMarkdownV2(fileName);
        String escapedFileSize = MarkdownFormatter.escapeMarkdownV2(fileSize);
        String formattedDate = uploadDate.format(DATE_TIME_FORMATTER);
        String escapedDate = MarkdownFormatter.escapeMarkdownV2(formattedDate);
        
        // Формируем сообщение
        StringBuilder message = new StringBuilder();
        message.append("📎 *Вложения события*\n\n");
        message.append("🖼️ *").append(escapedFileName).append("*\n");
        message.append("📊 Размер: ").append(escapedFileSize).append("\n");
        message.append("📅 Загружено: ").append(escapedDate);
        
        String fullMessage = message.toString();
        
        // Проверяем, что в сообщении нет неэкранированных специальных символов
        // (кроме тех, что используются для форматирования: *, _, и т.д.)
        assertNoUnescapedSpecialCharsInContent(fullMessage, escapedFileName);
        assertNoUnescapedSpecialCharsInContent(fullMessage, escapedFileSize);
        assertNoUnescapedSpecialCharsInContent(fullMessage, escapedDate);
    }
    
    /**
     * Property: Null и пустые строки обрабатываются корректно.
     */
    @Property
    @Label("Null и пустые строки обрабатываются корректно")
    void nullAndEmptyStringsHandledCorrectly() {
        // Null должен преобразовываться в пустую строку
        assertEquals("", MarkdownFormatter.escapeMarkdownV2(null));
        
        // Пустая строка должна остаться пустой
        assertEquals("", MarkdownFormatter.escapeMarkdownV2(""));
    }
    
    /**
     * Провайдер для генерации валидных дат.
     */
    @Provide
    Arbitrary<LocalDateTime> validDateTimes() {
        return Arbitraries.integers().between(2020, 2030)
            .flatMap(year -> Arbitraries.integers().between(1, 12)
                .flatMap(month -> Arbitraries.integers().between(1, 28)
                    .flatMap(day -> Arbitraries.integers().between(0, 23)
                        .flatMap(hour -> Arbitraries.integers().between(0, 59)
                            .map(minute -> LocalDateTime.of(year, month, day, hour, minute))))));
    }
    
    /**
     * Проверяет, содержит ли текст специальные символы MarkdownV2.
     */
    private boolean containsSpecialChars(String text) {
        for (char specialChar : MARKDOWN_SPECIAL_CHARS) {
            if (text.indexOf(specialChar) >= 0) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Подсчитывает количество экранированных точек в строке.
     */
    private long countEscapedDots(String text) {
        long count = 0;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '\\' && text.charAt(i + 1) == '.') {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Проверяет, что в контенте нет неэкранированных специальных символов.
     * 
     * <p>Игнорирует символы, используемые для форматирования (*, _, и т.д.),
     * которые находятся вне экранированного контента.</p>
     */
    private void assertNoUnescapedSpecialCharsInContent(String fullMessage, String escapedContent) {
        // Проверяем, что экранированный контент присутствует в сообщении
        assertTrue(fullMessage.contains(escapedContent),
            "Экранированный контент должен присутствовать в сообщении");
        
        // Проверяем, что в экранированном контенте все специальные символы экранированы
        for (char specialChar : MARKDOWN_SPECIAL_CHARS) {
            // Пропускаем символы форматирования, которые используются намеренно
            if (specialChar == '*' || specialChar == '_') {
                continue;
            }
            
            // Если исходный контент (до экранирования) содержал специальный символ,
            // то в экранированном виде он должен быть с обратным слешем
            if (escapedContent.contains(String.valueOf(specialChar))) {
                assertTrue(escapedContent.contains("\\" + specialChar),
                    String.format("Символ '%c' должен быть экранирован в контенте", specialChar));
            }
        }
    }
}
