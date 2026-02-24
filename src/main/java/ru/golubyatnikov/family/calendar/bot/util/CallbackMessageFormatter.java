package ru.golubyatnikov.family.calendar.bot.util;

import org.springframework.lang.NonNull;


/**
 * Утилиты для форматирования всплывающих сообщений callback queries.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
public final class CallbackMessageFormatter {

    private CallbackMessageFormatter() {
         throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
    }

    /**
     * Форматирует сообщение "не найдено" для конкретной сущности.
     *
     * @param entityName название сущности (например, "Событие", "Вложение", "Напоминание")
     * @return отформатированное сообщение в формате "{ERROR} {entityName} не найдено"
     */
    public static @NonNull String notFound(String entityName) {
        return String.format(CallbackMessages.NOT_FOUND, entityName);
    }

    /**
     * Форматирует сообщение об ошибке валидации.
     *
     * @param reason причина ошибки валидации (например, "некорректные данные", "не указан ID")
     * @return отформатированное сообщение в формате "{ERROR} Ошибка: {reason}"
     */
    public static @NonNull String validationError(String reason) {
        return String.format(CallbackMessages.VALIDATION_ERROR, reason);
    }

    /**
     * Форматирует сообщение об отмене действия.
     *
     * @param action название действия (например, "Создание", "Редактирование", "Удаление")
     * @return отформатированное сообщение в формате "{CANCEL} {action} отменено"
     */
    public static @NonNull String actionCancelled(String action) {
        return String.format(CallbackMessages.ACTION_CANCELLED, action);
    }

    /**
     * Форматирует подтверждение выбора элемента.
     *
     * @param item что было выбрано (например, "Дата", "Час", "Время")
     * @return отформатированное сообщение в формате "{SUCCESS} {item} выбрано"
     */
    public static @NonNull String itemSelected(String item) {
        return String.format(CallbackMessages.ITEM_SELECTED, item);
    }
}
