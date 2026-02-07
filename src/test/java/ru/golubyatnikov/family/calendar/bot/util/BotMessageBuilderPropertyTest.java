package ru.golubyatnikov.family.calendar.bot.util;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based тесты для BotMessageBuilder.
 * 
 * <p>Тесты проверяют свойство корректного экранирования: для любого текста,
 * содержащего специальные символы MarkdownV2, BotMessageBuilder должен
 * корректно экранировать их в результирующем сообщении.</p>
 * 
 * <p><b>Feature: code-quality-refactoring, Property 4: BotMessageBuilder Escaping</b></p>
 * <p><b>Validates: Requirements 4.4</b></p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
class BotMessageBuilderPropertyTest {
    
    private final BotMessageBuilder messageBuilder = new BotMessageBuilder();
    
    /**
     * Специальные символы MarkdownV2, которые требуют экранирования.
     */
    private static final char[] SPECIAL_CHARS = {
        '_', '*', '[', ']', '(', ')', '~', '`', 
        '>', '#', '+', '-', '=', '|', '{', '}', 
        '.', '!'
    };
    
    /**
     * Property 4: BotMessageBuilder Escaping
     * 
     * <p>Для любого текста, содержащего специальные символы MarkdownV2,
     * BotMessageBuilder должен корректно экранировать их в результирующем сообщении.</p>
     * 
     * <p>Validates: Requirements 4.4</p>
     */
    @Property(tries = 100)
    void dateSelectedMessageEscapesSpecialChars(
            @ForAll("textWithSpecialChars") String formattedDate) {
        
        String message = messageBuilder.buildDateSelectedMessage(formattedDate);
        
        // Проверяем, что все специальные символы из входного текста экранированы
        assertSpecialCharsEscaped(message, formattedDate);
    }
    
    /**
     * Проверяет экранирование в сообщении о выборе времени.
     */
    @Property(tries = 100)
    void timeSelectedMessageEscapesSpecialChars(
            @ForAll("textWithSpecialChars") String formattedTime) {
        
        String message = messageBuilder.buildTimeSelectedMessage(formattedTime);
        
        assertSpecialCharsEscaped(message, formattedTime);
    }
    
    /**
     * Проверяет экранирование в сообщении об ошибке.
     */
    @Property(tries = 100)
    void errorMessageEscapesSpecialChars(
            @ForAll("textWithSpecialChars") String errorText) {
        
        String message = messageBuilder.buildErrorMessage(errorText);
        
        assertSpecialCharsEscaped(message, errorText);
    }
    
    /**
     * Проверяет экранирование в сообщении об ошибке с действием.
     */
    @Property(tries = 100)
    void errorMessageWithActionEscapesSpecialChars(
            @ForAll("textWithSpecialChars") String errorText,
            @ForAll("textWithSpecialChars") String actionHint) {
        
        String message = messageBuilder.buildErrorMessageWithAction(errorText, actionHint);
        
        assertSpecialCharsEscaped(message, errorText);
        assertSpecialCharsEscaped(message, actionHint);
    }
    
    /**
     * Проверяет экранирование в сообщении о прикреплении файла.
     */
    @Property(tries = 100)
    void fileAttachedMessageEscapesSpecialChars(
            @ForAll("textWithSpecialChars") String fileName,
            @ForAll @IntRange(min = 1, max = 100) int fileSizeMb) {
        
        String message = messageBuilder.buildFileAttachedMessage(fileName, fileSizeMb);
        
        assertSpecialCharsEscaped(message, fileName);
    }
    
    /**
     * Проверяет экранирование в сообщении предпросмотра события из текста.
     */
    @Property(tries = 100)
    void textEventPreviewMessageEscapesSpecialChars(
            @ForAll("textWithSpecialChars") String title,
            @ForAll("textWithSpecialChars") String date,
            @ForAll("textWithSpecialChars") String time) {
        
        String message = messageBuilder.buildTextEventPreviewMessage(title, date, time);
        
        assertSpecialCharsEscaped(message, title);
        assertSpecialCharsEscaped(message, date);
        assertSpecialCharsEscaped(message, time);
    }
    
    /**
     * Проверяет, что сообщение о выборе часа корректно форматируется.
     */
    @Property(tries = 100)
    void hourSelectedMessageFormatsCorrectly(
            @ForAll @IntRange(min = 0, max = 23) int hour) {
        
        String message = messageBuilder.buildHourSelectedMessage(hour);
        
        // Проверяем, что час отформатирован с ведущим нулём
        String expectedHour = String.format("%02d", hour);
        assertThat(message)
            .as("Сообщение должно содержать отформатированный час %s", expectedHour)
            .contains(expectedHour);
    }
    
    /**
     * Проверяет, что сообщение о выборе даты с LocalDate корректно форматируется.
     */
    @Property(tries = 100)
    void dateSelectedMessageWithLocalDateFormatsCorrectly(
            @ForAll("validDate") LocalDate date) {
        
        String message = messageBuilder.buildDateSelectedMessage(date);
        
        // Проверяем, что дата присутствует в сообщении
        String expectedDay = String.format("%02d", date.getDayOfMonth());
        String expectedMonth = String.format("%02d", date.getMonthValue());
        
        assertThat(message)
            .as("Сообщение должно содержать день %s", expectedDay)
            .contains(expectedDay);
        assertThat(message)
            .as("Сообщение должно содержать месяц %s", expectedMonth)
            .contains(expectedMonth);
    }
    
    /**
     * Проверяет, что сообщение о выборе времени с LocalTime корректно форматируется.
     */
    @Property(tries = 100)
    void timeSelectedMessageWithLocalTimeFormatsCorrectly(
            @ForAll("validTime") LocalTime time) {
        
        String message = messageBuilder.buildTimeSelectedMessage(time);
        
        // Проверяем, что время присутствует в сообщении
        String expectedHour = String.format("%02d", time.getHour());
        String expectedMinute = String.format("%02d", time.getMinute());
        
        assertThat(message)
            .as("Сообщение должно содержать час %s", expectedHour)
            .contains(expectedHour);
        assertThat(message)
            .as("Сообщение должно содержать минуты %s", expectedMinute)
            .contains(expectedMinute);
    }
    
    /**
     * Проверяет, что сообщение о типе события содержит правильный текст.
     */
    @Property(tries = 100)
    void eventTypeSelectedMessageContainsCorrectText(
            @ForAll boolean isPersonal) {
        
        String message = messageBuilder.buildEventTypeSelectedMessage(isPersonal);
        
        if (isPersonal) {
            assertThat(message)
                .as("Сообщение для персонального события должно содержать 'Персональное'")
                .contains("Персональное");
        } else {
            assertThat(message)
                .as("Сообщение для семейного события должно содержать 'Семейное'")
                .contains("Семейное");
        }
    }
    
    /**
     * Проверяет, что сообщение выбора даты с шапкой содержит заголовок создания события.
     */
    @Property(tries = 10)
    void selectDateMessageWithHeaderContainsCreationHeader() {
        String message = messageBuilder.buildSelectDateMessageWithHeader();
        
        assertThat(message)
            .as("Сообщение должно содержать заголовок 'Создание нового события'")
            .contains("Создание нового события");
        
        assertThat(message)
            .as("Сообщение должно содержать текст 'Выберите дату события'")
            .contains("Выберите дату события");
    }
    
    /**
     * Проверяет, что сообщение выбора даты без шапки не содержит заголовок создания.
     */
    @Property(tries = 10)
    void selectDateMessageWithoutHeaderDoesNotContainCreationHeader() {
        String message = messageBuilder.buildSelectDateMessage();
        
        assertThat(message)
            .as("Сообщение не должно содержать заголовок 'Создание нового события'")
            .doesNotContain("Создание нового события");
        
        assertThat(message)
            .as("Сообщение должно содержать текст 'Выберите дату события'")
            .contains("Выберите дату события");
    }
    
    /**
     * Провайдер текста со специальными символами.
     */
    @Provide
    Arbitrary<String> textWithSpecialChars() {
        return Arbitraries.strings()
            .withChars(SPECIAL_CHARS)
            .ofMinLength(1)
            .ofMaxLength(50)
            .map(specialPart -> "Test" + specialPart + "Text");
    }
    
    /**
     * Провайдер валидных дат.
     */
    @Provide
    Arbitrary<LocalDate> validDate() {
        return Arbitraries.integers()
            .between(2020, 2030)
            .flatMap(year -> Arbitraries.integers()
                .between(1, 12)
                .flatMap(month -> Arbitraries.integers()
                    .between(1, 28) // Используем 28 для простоты
                    .map(day -> LocalDate.of(year, month, day))));
    }
    
    /**
     * Провайдер валидного времени.
     */
    @Provide
    Arbitrary<LocalTime> validTime() {
        return Arbitraries.integers()
            .between(0, 23)
            .flatMap(hour -> Arbitraries.integers()
                .between(0, 59)
                .map(minute -> LocalTime.of(hour, minute)));
    }
    
    /**
     * Проверяет, что все специальные символы из исходного текста экранированы в сообщении.
     */
    private void assertSpecialCharsEscaped(String message, String originalText) {
        for (char special : SPECIAL_CHARS) {
            if (originalText.contains(String.valueOf(special))) {
                // Специальный символ должен быть экранирован (предшествовать \)
                String escaped = "\\" + special;
                assertThat(message)
                    .as("Символ '%c' должен быть экранирован как '%s' в сообщении", special, escaped)
                    .contains(escaped);
            }
        }
    }
}
