package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое, когда напоминание не найдено в базе данных.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-28
 */
public class ReminderNotFoundException extends RuntimeException {
    
    /**
     * Создает исключение с сообщением об отсутствии напоминания.
     * 
     * @param reminderId идентификатор напоминания
     */
    public ReminderNotFoundException(Long reminderId) {
        super(String.format("Напоминание с ID %d не найдено", reminderId));
    }
}
