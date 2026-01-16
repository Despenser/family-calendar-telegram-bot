package ru.golubyatnikov.family.calendar.bot.model;

import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based тесты для CallbackPrefix enum.
 * 
 * <p>Тесты проверяют свойство консистентности: для любого payload,
 * созданный через withPayload() callback data должен корректно
 * распознаваться методом matches() и extractPayload() должен
 * возвращать исходный payload.</p>
 * 
 * <p><b>Feature: code-quality-refactoring, Property 2: CallbackPrefix Matching Consistency</b></p>
 * <p><b>Validates: Requirements 3.2</b></p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
class CallbackPrefixPropertyTest {
    
    /**
     * Property 2: CallbackPrefix Matching Consistency
     * 
     * <p>Для любого callback data, созданного через CallbackPrefix.withPayload(payload),
     * метод matches() того же префикса должен возвращать true,
     * и extractPayload() должен возвращать исходный payload.</p>
     * 
     * <p>Validates: Requirements 3.2</p>
     */
    @Property(tries = 100)
    void callbackPrefixRoundTrip(
            @ForAll @StringLength(min = 1, max = 50) String payload) {
        
        // Проверяем все префиксы с payload (не exact match)
        for (CallbackPrefix prefix : CallbackPrefix.values()) {
            // Пропускаем префиксы без параметров (exact match)
            if (isExactMatchPrefix(prefix)) {
                continue;
            }
            
            // Создаём callback data с payload
            String callbackData = prefix.withPayload(payload);
            
            // Проверяем, что matches() возвращает true
            assertThat(prefix.matches(callbackData))
                .as("Prefix %s должен соответствовать callback data '%s'", 
                    prefix.name(), callbackData)
                .isTrue();
            
            // Проверяем, что extractPayload() возвращает исходный payload
            assertThat(prefix.extractPayload(callbackData))
                .as("extractPayload() для prefix %s и callback data '%s' должен вернуть '%s'",
                    prefix.name(), callbackData, payload)
                .isEqualTo(payload);
            
            // Проверяем, что fromCallbackData() находит правильный префикс
            CallbackPrefix found = CallbackPrefix.fromCallbackData(callbackData);
            assertThat(found)
                .as("fromCallbackData('%s') должен найти префикс", callbackData)
                .isNotNull();
            assertThat(found.matches(callbackData))
                .as("Найденный префикс %s должен соответствовать callback data '%s'",
                    found.name(), callbackData)
                .isTrue();
        }
    }
    
    /**
     * Проверяет, что exact match префиксы корректно работают.
     * 
     * <p>Для префиксов без параметров (TIME_BACK, TIME_CANCEL и т.д.)
     * matches() должен возвращать true только при точном совпадении.</p>
     */
    @Property(tries = 100)
    void exactMatchPrefixesWorkCorrectly(
            @ForAll @StringLength(min = 1, max = 20) String suffix) {
        
        CallbackPrefix[] exactMatchPrefixes = {
            CallbackPrefix.TIME_BACK,
            CallbackPrefix.TIME_CANCEL,
            CallbackPrefix.SKIP_DESCRIPTION,
            CallbackPrefix.CANCEL_TEXT_EVENT,
            CallbackPrefix.CALENDAR_IGNORE,
            CallbackPrefix.TIME_IGNORE
        };
        
        for (CallbackPrefix prefix : exactMatchPrefixes) {
            // Точное совпадение должно работать
            assertThat(prefix.matches(prefix.getPrefix()))
                .as("Exact match prefix %s должен соответствовать своему значению '%s'",
                    prefix.name(), prefix.getPrefix())
                .isTrue();
            
            // С суффиксом не должно совпадать
            String withSuffix = prefix.getPrefix() + suffix;
            assertThat(prefix.matches(withSuffix))
                .as("Exact match prefix %s НЕ должен соответствовать '%s'",
                    prefix.name(), withSuffix)
                .isFalse();
            
            // withPayload() должен возвращать только префикс (игнорировать payload)
            assertThat(prefix.withPayload(suffix))
                .as("withPayload() для exact match prefix %s должен вернуть только префикс",
                    prefix.name())
                .isEqualTo(prefix.getPrefix());
            
            // extractPayload() должен возвращать пустую строку
            assertThat(prefix.extractPayload(prefix.getPrefix()))
                .as("extractPayload() для exact match prefix %s должен вернуть пустую строку",
                    prefix.name())
                .isEmpty();
        }
    }
    
    /**
     * Проверяет, что null callback data обрабатывается корректно.
     */
    @Example
    void nullCallbackDataHandledCorrectly() {
        for (CallbackPrefix prefix : CallbackPrefix.values()) {
            assertThat(prefix.matches(null))
                .as("matches(null) для prefix %s должен вернуть false", prefix.name())
                .isFalse();
        }
        
        assertThat(CallbackPrefix.fromCallbackData(null))
            .as("fromCallbackData(null) должен вернуть null")
            .isNull();
    }
    
    /**
     * Проверяет, что extractPayload() выбрасывает исключение для несоответствующего callback data.
     */
    @Property(tries = 100)
    void extractPayloadThrowsForMismatchedData(
            @ForAll @StringLength(min = 1, max = 30) String randomData) {
        
        // Берём префикс DATE и проверяем с данными, которые не начинаются с "date_"
        CallbackPrefix prefix = CallbackPrefix.DATE;
        
        // Если данные случайно начинаются с "date_", пропускаем
        if (randomData.startsWith(prefix.getPrefix())) {
            return;
        }
        
        assertThatThrownBy(() -> prefix.extractPayload(randomData))
            .as("extractPayload('%s') для prefix DATE должен выбросить исключение", randomData)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("не соответствует префиксу");
    }
    
    /**
     * Проверяет, что isIgnored() корректно определяет игнорируемые callback data.
     */
    @Example
    void isIgnoredWorksCorrectly() {
        assertThat(CallbackPrefix.isIgnored("calendar_ignore")).isTrue();
        assertThat(CallbackPrefix.isIgnored("time_ignore")).isTrue();
        assertThat(CallbackPrefix.isIgnored("date_2026-01-16")).isFalse();
        assertThat(CallbackPrefix.isIgnored("view_event_123")).isFalse();
        assertThat(CallbackPrefix.isIgnored(null)).isFalse();
    }
    
    /**
     * Проверяет, что withPayload(null) обрабатывается корректно.
     */
    @Example
    void withPayloadNullHandledCorrectly() {
        // Для префиксов с payload
        assertThat(CallbackPrefix.DATE.withPayload(null))
            .isEqualTo("date_");
        
        // Для exact match префиксов
        assertThat(CallbackPrefix.TIME_BACK.withPayload(null))
            .isEqualTo("time_back");
    }
    
    /**
     * Вспомогательный метод для определения exact match префиксов.
     */
    private boolean isExactMatchPrefix(CallbackPrefix prefix) {
        return prefix == CallbackPrefix.TIME_BACK ||
               prefix == CallbackPrefix.TIME_CANCEL ||
               prefix == CallbackPrefix.SKIP_DESCRIPTION ||
               prefix == CallbackPrefix.CANCEL_TEXT_EVENT ||
               prefix == CallbackPrefix.CALENDAR_IGNORE ||
               prefix == CallbackPrefix.TIME_IGNORE;
    }
}
