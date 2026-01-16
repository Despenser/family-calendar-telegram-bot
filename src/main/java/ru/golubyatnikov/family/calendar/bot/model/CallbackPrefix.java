package ru.golubyatnikov.family.calendar.bot.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum для типизации callback data prefixes.
 * Устраняет магические строки и обеспечивает type-safety.
 * 
 * <p>Каждый префикс соответствует определённой функциональной области обработки callback queries.
 * Использование enum вместо строковых литералов позволяет:</p>
 * <ul>
 *   <li>Избежать опечаток в callback data</li>
 *   <li>Упростить рефакторинг</li>
 *   <li>Обеспечить автодополнение в IDE</li>
 *   <li>Централизовать все префиксы в одном месте</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@Getter
@RequiredArgsConstructor
public enum CallbackPrefix {
    
    // ===== Дата и время =====
    
    /** Выбор даты из календаря (формат: date_YYYY-MM-DD) */
    DATE("date_"),
    
    /** Навигация по календарю (формат: calendar_YYYY-MM или calendar_cancel) */
    CALENDAR("calendar_"),
    
    /** Выбор часа (формат: hour_HH) */
    HOUR("hour_"),
    
    /** Выбор времени с минутами (формат: time_HH:MM) */
    TIME("time_"),
    
    /** Возврат к выбору часа */
    TIME_BACK("time_back"),
    
    /** Отмена выбора времени */
    TIME_CANCEL("time_cancel"),
    
    // ===== События =====
    
    /** Просмотр деталей события (формат: view_event_{eventId}) */
    VIEW_EVENT("view_event_"),
    
    /** Редактирование события (формат: edit_event_{eventId}) */
    EDIT_EVENT("edit_event_"),
    
    /** Удаление события (формат: delete_event_{eventId}) */
    DELETE_EVENT("delete_event_"),
    
    /** Редактирование поля события (формат: edit_field_{field}_{eventId}) */
    EDIT_FIELD("edit_field_"),
    
    /** Выбор типа события - семейное/персональное (формат: event_type_{type}) */
    EVENT_TYPE("event_type_"),
    
    /** Пропуск описания события */
    SKIP_DESCRIPTION("skip_description"),
    
    // ===== Фильтры и корзина =====
    
    /** Фильтрация событий (формат: filter_{filterType}) */
    FILTER("filter_"),
    
    /** Действия с корзиной (формат: trash_{action}_{eventId}) */
    TRASH("trash_"),
    
    // ===== Напоминания =====
    
    /** Настройка напоминаний (формат: setup_reminders_{eventId}) */
    SETUP_REMINDERS("setup_reminders_"),
    
    /** Переключение типа напоминания (формат: toggle_reminder_{eventId}_{type}) */
    TOGGLE_REMINDER("toggle_reminder_"),
    
    /** Подтверждение создания напоминаний (формат: confirm_reminders_{eventId}) */
    CONFIRM_REMINDERS("confirm_reminders_"),
    
    /** Просмотр напоминаний (формат: view_reminders_{eventId}) */
    VIEW_REMINDERS("view_reminders_"),
    
    /** Удаление напоминания (формат: delete_reminder_{reminderId}) */
    DELETE_REMINDER("delete_reminder_"),
    
    /** Старая обработка напоминаний (deprecated) */
    REMINDER("reminder_"),
    
    // ===== Повторения =====
    
    /** Настройка повторений (формат: recurrence_{action}_{eventId}) */
    RECURRENCE("recurrence_"),
    
    /** Действия с серией повторяющихся событий (формат: series_action_{action}_{eventId}) */
    SERIES_ACTION("series_action_"),
    
    // ===== Дополнительные функции =====
    
    /** Действия с датой (формат: date_actions_{action}_{date}) */
    DATE_ACTIONS("date_actions_"),
    
    /** Прикрепление файлов (формат: attach_file_{action}_{eventId}) */
    ATTACH_FILE("attach_file_"),
    
    /** Работа с чек-листами (формат: checklist_{action}_{eventId}) */
    CHECKLIST("checklist_"),
    
    /** Работа с комментариями (формат: comment_{action}_{eventId}) */
    COMMENT("comment_"),
    
    /** Добавление заметки к завершенному событию (формат: add_completion_note_{eventId}) */
    ADD_COMPLETION_NOTE("add_completion_note_"),
    
    // ===== Создание события из текста =====
    
    /** Подтверждение создания события из текста (формат: confirm_text_event:{data}) */
    CONFIRM_TEXT_EVENT("confirm_text_event:"),
    
    /** Отмена создания события из текста */
    CANCEL_TEXT_EVENT("cancel_text_event"),
    
    // ===== Специальные (игнорируемые) =====
    
    /** Игнорируемая кнопка календаря (заголовки дней недели и т.д.) */
    CALENDAR_IGNORE("calendar_ignore"),
    
    /** Игнорируемая кнопка времени */
    TIME_IGNORE("time_ignore");
    
    private final String prefix;
    
    /**
     * Проверяет, соответствует ли callback data данному префиксу.
     * 
     * <p>Для префиксов без параметров (TIME_BACK, TIME_CANCEL, SKIP_DESCRIPTION, 
     * CANCEL_TEXT_EVENT, CALENDAR_IGNORE, TIME_IGNORE) проверяется точное совпадение.
     * Для остальных префиксов проверяется, что callback data начинается с префикса.</p>
     * 
     * @param callbackData строка callback data для проверки
     * @return true если callback data соответствует данному префиксу
     */
    public boolean matches(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        // Для префиксов без параметров проверяем точное совпадение
        if (isExactMatchPrefix()) {
            return callbackData.equals(prefix);
        }
        
        return callbackData.startsWith(prefix);
    }
    
    /**
     * Извлекает payload из callback data (часть после префикса).
     * 
     * <p>Для префиксов без параметров возвращает пустую строку.</p>
     * 
     * @param callbackData строка callback data
     * @return payload (часть после префикса) или пустая строка
     * @throws IllegalArgumentException если callback data не соответствует данному префиксу
     */
    public String extractPayload(String callbackData) {
        if (!matches(callbackData)) {
            throw new IllegalArgumentException(
                String.format("Callback data '%s' не соответствует префиксу '%s'", 
                    callbackData, prefix));
        }
        
        // Для префиксов без параметров возвращаем пустую строку
        if (isExactMatchPrefix()) {
            return "";
        }
        
        return callbackData.substring(prefix.length());
    }
    
    /**
     * Создаёт callback data с данным payload.
     * 
     * <p>Для префиксов без параметров payload игнорируется и возвращается только префикс.</p>
     * 
     * @param payload данные для добавления к префиксу
     * @return callback data в формате prefix + payload
     */
    public String withPayload(String payload) {
        // Для префиксов без параметров возвращаем только префикс
        if (isExactMatchPrefix()) {
            return prefix;
        }
        
        return prefix + (payload != null ? payload : "");
    }
    
    /**
     * Находит CallbackPrefix по callback data.
     * 
     * <p>Метод перебирает все значения enum и возвращает первый подходящий префикс.
     * Порядок проверки важен: сначала проверяются более специфичные префиксы.</p>
     * 
     * @param callbackData строка callback data
     * @return соответствующий CallbackPrefix или null если не найден
     */
    public static CallbackPrefix fromCallbackData(String callbackData) {
        if (callbackData == null) {
            return null;
        }
        
        // Сначала проверяем точные совпадения (более специфичные)
        for (CallbackPrefix prefix : values()) {
            if (prefix.isExactMatchPrefix() && prefix.matches(callbackData)) {
                return prefix;
            }
        }
        
        // Затем проверяем префиксы с payload
        for (CallbackPrefix prefix : values()) {
            if (!prefix.isExactMatchPrefix() && prefix.matches(callbackData)) {
                return prefix;
            }
        }
        
        return null;
    }
    
    /**
     * Проверяет, является ли данный префикс префиксом без параметров (требует точного совпадения).
     * 
     * @return true если префикс требует точного совпадения
     */
    private boolean isExactMatchPrefix() {
        return this == TIME_BACK || 
               this == TIME_CANCEL || 
               this == SKIP_DESCRIPTION || 
               this == CANCEL_TEXT_EVENT ||
               this == CALENDAR_IGNORE ||
               this == TIME_IGNORE;
    }
    
    /**
     * Проверяет, является ли данный callback data игнорируемым.
     * 
     * @param callbackData строка callback data
     * @return true если callback data должен быть проигнорирован
     */
    public static boolean isIgnored(String callbackData) {
        return CALENDAR_IGNORE.matches(callbackData) || TIME_IGNORE.matches(callbackData);
    }
}
