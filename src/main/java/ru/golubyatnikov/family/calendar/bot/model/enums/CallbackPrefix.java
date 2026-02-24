package ru.golubyatnikov.family.calendar.bot.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;

/**
 * Enum для типизации callback data prefixes.
 *
 * @author Golubyatnikov Aleksey
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
    
    /** Возврат к календарю выбора даты */
    TIME_TO_CALENDAR("time_to_calendar"),
    
    /** Отмена создания события */
    TIME_CANCEL("time_cancel"),
    
    /** Возврат к выбору времени (с этапа выбора типа) */
    TYPE_BACK_TO_TIME("type_back_to_time"),
    
    /** Возврат к выбору типа события (с этапа ввода названия) */
    TITLE_BACK("title_back"),
    
    /** Возврат к вводу названия (с этапа ввода описания) */
    DESC_BACK_TO_TITLE("desc_back_to_title"),
    
    /** Отмена создания на этапе выбора типа */
    TYPE_CANCEL("type_cancel"),
    
    // ===== События =====
    
    /** Просмотр деталей события (формат: view_event_{eventId}) */
    VIEW_EVENT("view_event_"),
    
    /** Просмотр деталей события из напоминания (формат: view_event_from_reminder_{eventId}_{reminderId}) */
    VIEW_EVENT_FROM_REMINDER("view_event_from_reminder_"),
    
    /** Редактирование события (формат: edit_event_{eventId}) */
    EDIT_EVENT("edit_event_"),
    
    /** Возврат к меню выбора поля редактирования (формат: edit_back_{eventId}) */
    EDIT_BACK("edit_back_"),
    
    /** Отмена редактирования события (формат: edit_cancel_{eventId}) */
    EDIT_CANCEL("edit_cancel_"),
    
    /** Удаление события (формат: delete_event_{eventId}) */
    DELETE_EVENT("delete_event_"),
    
    /** Завершение события (формат: complete_event_{eventId}) */
    COMPLETE_EVENT("complete_event_"),
    
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
    
    /** Отключение автоматических напоминаний (формат: disable_reminders_{eventId}) */
    DISABLE_REMINDERS("disable_reminders_"),
    
    /** Включение автоматических напоминаний (формат: enable_reminders_{eventId}) */
    ENABLE_REMINDERS("enable_reminders_"),
    
    /** Старая обработка напоминаний (deprecated) */
    REMINDER("reminder_"),
    
    /** Возврат к минималистичному виду напоминания (формат: back_to_reminder_{eventId}_{reminderId}) */
    BACK_TO_REMINDER("back_to_reminder_"),
    
    // ===== Дополнительные функции =====
    
    /** Действия с датой (формат: date_actions_{action}_{date}) */
    DATE_ACTIONS("date_actions_"),
    
    /** Прикрепление файлов (формат: attach_file_{action}_{eventId}) */
    ATTACH_FILE("attach_file_"),
    
    /** Работа с комментариями (формат: comment_{action}_{eventId}) */
    COMMENT("comment_"),
    
    /** Добавление заметки к завершенному событию (формат: add_completion_note_{eventId}) */
    ADD_COMPLETION_NOTE("add_completion_note_"),
    
    /** Пропуск добавления заметки к завершенному событию */
    SKIP_COMPLETION_NOTE("skip_completion_note"),
    
    /** Повторение события с новой датой и временем (формат: repeat_event_{eventId}) */
    REPEAT_EVENT("repeat_event_"),
    
    // ===== Навигация по календарю =====
    
    /** Возврат к календарю с указанием года и месяца (формат: back_to_calendar_YYYY-MM) */
    BACK_TO_CALENDAR("back_to_calendar_"),
    
    /** Создание события на конкретную дату (формат: create_event_on_date_YYYY-MM-DD) */
    CREATE_EVENT_ON_DATE("create_event_on_date_"),
    
    /** Просмотр событий на конкретную дату (формат: view_events_on_date_YYYY-MM-DD) */
    VIEW_EVENTS_ON_DATE("view_events_on_date_"),
    
    /** Редактирование события из календаря (формат: edit_event_from_calendar_{eventId}_{date}) */
    EDIT_EVENT_FROM_CALENDAR("edit_event_from_calendar_"),
    
    /** Редактирование своих событий на дату (формат: edit_my_events_on_date_YYYY-MM-DD) */
    EDIT_MY_EVENTS_ON_DATE("edit_my_events_on_date_"),
    
    /** Удаление своих событий на дату (формат: delete_my_events_on_date_YYYY-MM-DD) */
    DELETE_MY_EVENTS_ON_DATE("delete_my_events_on_date_"),
    
    /** Отмена навигации по календарю */
    CALENDAR_CANCEL("calendar_cancel"),
    
    /** Восстановление события из корзины (формат: trash_restore_{eventId}) */
    TRASH_RESTORE("trash_restore_"),
    
    /** Окончательное удаление события из корзины (формат: trash_delete_{eventId}) */
    TRASH_DELETE("trash_delete_"),
    
    // ===== Поиск =====
    
    /** Повторный поиск событий (формат: search_again:) */
    SEARCH_AGAIN("search_again:"),
    
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
     * @param callbackData строка callback data
     *
     * @return payload (часть после префикса) или пустая строка
     * @throws IllegalArgumentException если callback data не соответствует данному префиксу
     */
    public @NonNull String extractPayload(String callbackData) {
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
     * @return true, если префикс требует точного совпадения
     */
    private boolean isExactMatchPrefix() {
        return this == TIME_BACK || 
               this == TIME_TO_CALENDAR ||
               this == TIME_CANCEL || 
               this == TYPE_BACK_TO_TIME ||
               this == TITLE_BACK ||
               this == DESC_BACK_TO_TITLE ||
               this == TYPE_CANCEL ||
               this == SKIP_DESCRIPTION || 
               this == SKIP_COMPLETION_NOTE ||
               this == CANCEL_TEXT_EVENT ||
               this == CALENDAR_CANCEL ||
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
