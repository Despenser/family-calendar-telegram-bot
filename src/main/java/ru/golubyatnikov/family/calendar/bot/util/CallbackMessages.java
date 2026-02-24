package ru.golubyatnikov.family.calendar.bot.util;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.CANCEL;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.*;

/**
 * Централизованное хранилище всплывающих сообщений для callback queries.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-04
 */
public final class CallbackMessages {

    // Константы для использования в статических полях
    private static final String SUCCESS_EMOJI = EmojiConstants.Status.SUCCESS;
    private static final String ERROR_EMOJI = EmojiConstants.Status.ERROR;

    /**
     * Приватный конструктор для предотвращения создания экземпляров.
     *
     * @throws UnsupportedOperationException всегда, так как это утилитный класс
     */
    private CallbackMessages() {
        throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
    }

    // ============ УСПЕШНЫЕ ОПЕРАЦИИ ============

    /**
     * Универсальное сообщение об успехе.
     * Используется для подтверждения успешного выполнения операции.
     */
    public static final String SUCCESS = SUCCESS_EMOJI + " Готово";

    /**
     * Подтверждение выбора.
     * Используется для подтверждения того, что выбор пользователя принят.
     */
    public static final String SELECTED = SUCCESS_EMOJI + " Выбрано";

    /**
     * Успешное создание.
     * Используется для подтверждения создания новой сущности.
     */
    public static final String CREATED = SUCCESS_EMOJI + " Создано";

    /**
     * Успешное удаление.
     * Используется для подтверждения удаления сущности.
     */
    public static final String DELETED = SUCCESS_EMOJI + " Удалено";

    /**
     * Успешное изменение.
     * Используется для подтверждения обновления сущности.
     */
    public static final String UPDATED = SUCCESS_EMOJI + " Обновлено";

    // ============ ОШИБКИ ============

    /**
     * Общая ошибка.
     * Используется, когда произошла непредвиденная ошибка.
     */
    public static final String ERROR = ERROR_EMOJI + " Произошла ошибка";

    /**
     * Ошибка доступа.
     * Используется, когда у пользователя нет прав для выполнения операции.
     */
    public static final String NO_ACCESS = ERROR_EMOJI + " Нет прав доступа";

    /**
     * Сущность не найдена.
     */
    public static final String NOT_FOUND = ERROR_EMOJI + " %s не найдено";

    /**
     * Ошибка валидации.
     */
    public static final String VALIDATION_ERROR = ERROR_EMOJI + " Ошибка: %s";

    /**
     * Неизвестное действие.
     * Используется, когда получен callback с неизвестным действием.
     */
    public static final String UNKNOWN_ACTION = ERROR_EMOJI + " Неизвестное действие";

    /**
     * Некорректный запрос.
     * Используется, когда получен некорректный callback запрос.
     */
    public static final String INVALID_REQUEST = ERROR_EMOJI + " Некорректный запрос";

    // ============ ОТМЕНЫ ============

    /**
     * Универсальная отмена.
     * Используется для подтверждения отмены операции.
     */
    public static final String CANCELLED = CANCEL + " Отменено";

    /**
     * Отмена конкретного действия.
     */
    public static final String ACTION_CANCELLED = CANCEL + " %s отменено";

    // ============ ПОДТВЕРЖДЕНИЯ ============

    /**
     * Подтверждение выбора элемента.
     */
    public static final String ITEM_SELECTED = SUCCESS_EMOJI + " %s выбрано";

    // ============ ПУСТЫЕ ОТВЕТЫ ============

    /**
     * Пустой ответ.
     */
    public static final String EMPTY = "";

    // ============ СПЕЦИФИЧНЫЕ СООБЩЕНИЯ ============

    /**
     * Для времени - слишком поздно для сегодня.
     * Используется при выборе времени, которое уже прошло сегодня.
     */
    public static final String TOO_LATE_TODAY = "Слишком поздно для сегодня";

    /**
     * Для напоминаний - событие слишком близко.
     * Используется, когда событие происходит слишком скоро для создания напоминаний.
     */
    public static final String REMINDER_TOO_SOON = INFO + " Событие уже скоро, напоминания не созданы";

    /**
     * Для выбора следующего часа.
     * Используется как подсказка при выборе времени на следующий час.
     */
    public static final String SELECT_NEXT_HOUR = "Выберите следующий час";
    
    /**
     * Для календаря - нет событий на выбранную дату.
     * Используется, когда пользователь выбирает дату без событий.
     */
    public static final String NO_EVENTS_ON_DATE = "На эту дату нет событий";
}
