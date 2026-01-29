package ru.golubyatnikov.family.calendar.bot.util;

/**
 * Утилиты для форматирования всплывающих сообщений callback queries.
 * <p>
 * Этот класс предоставляет методы для форматирования сообщений с параметрами,
 * используя шаблоны из {@link CallbackMessages}.
 * <p>
 * Все методы корректно обрабатывают граничные случаи (null, пустые строки).
 */
public final class CallbackMessageFormatter {

    private CallbackMessageFormatter() {
        // Utility class - предотвращаем создание экземпляров
    }

    /**
     * Форматирует сообщение "не найдено" для конкретной сущности.
     * <p>
     * Использует шаблон {@link CallbackMessages#NOT_FOUND}.
     *
     * @param entityName название сущности (например, "Событие", "Вложение", "Напоминание")
     * @return отформатированное сообщение в формате "❌ {entityName} не найдено"
     * @see CallbackMessages#NOT_FOUND
     */
    public static String notFound(String entityName) {
        return String.format(CallbackMessages.NOT_FOUND, entityName);
    }

    /**
     * Форматирует сообщение об ошибке валидации.
     * <p>
     * Использует шаблон {@link CallbackMessages#VALIDATION_ERROR}.
     *
     * @param reason причина ошибки валидации (например, "некорректные данные", "не указан ID")
     * @return отформатированное сообщение в формате "❌ Ошибка: {reason}"
     * @see CallbackMessages#VALIDATION_ERROR
     */
    public static String validationError(String reason) {
        return String.format(CallbackMessages.VALIDATION_ERROR, reason);
    }

    /**
     * Форматирует сообщение об отмене действия.
     * <p>
     * Использует шаблон {@link CallbackMessages#ACTION_CANCELLED}.
     *
     * @param action название действия (например, "Создание", "Редактирование", "Удаление")
     * @return отформатированное сообщение в формате "🚫 {action} отменено"
     * @see CallbackMessages#ACTION_CANCELLED
     */
    public static String actionCancelled(String action) {
        return String.format(CallbackMessages.ACTION_CANCELLED, action);
    }

    /**
     * Форматирует информационную подсказку.
     * <p>
     * Использует шаблон {@link CallbackMessages#HINT}.
     *
     * @param hint текст подсказки (например, "Добавьте время события")
     * @return отформатированное сообщение в формате "ℹ️ {hint}"
     * @see CallbackMessages#HINT
     */
    public static String hint(String hint) {
        return String.format(CallbackMessages.HINT, hint);
    }

    /**
     * Форматирует запрос выбора.
     * <p>
     * Использует шаблон {@link CallbackMessages#SELECT_PROMPT}.
     *
     * @param item что нужно выбрать (например, "тип события", "дату", "время")
     * @return отформатированное сообщение в формате "Выберите {item}"
     * @see CallbackMessages#SELECT_PROMPT
     */
    public static String selectPrompt(String item) {
        return String.format(CallbackMessages.SELECT_PROMPT, item);
    }

    /**
     * Форматирует подтверждение выбора элемента.
     * <p>
     * Использует шаблон {@link CallbackMessages#ITEM_SELECTED}.
     *
     * @param item что было выбрано (например, "Дата", "Час", "Время")
     * @return отформатированное сообщение в формате "✅ {item} выбрано"
     * @see CallbackMessages#ITEM_SELECTED
     */
    public static String itemSelected(String item) {
        return String.format(CallbackMessages.ITEM_SELECTED, item);
    }
}
