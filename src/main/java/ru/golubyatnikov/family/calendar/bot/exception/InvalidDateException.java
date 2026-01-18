package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое при попытке создать или обновить событие с некорректной датой.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 */
public class InvalidDateException extends RuntimeException {
    
    /**
     * Создает новое исключение с сообщением об ошибке.
     * 
     * @param message сообщение об ошибке
     */
    public InvalidDateException(String message) {
        super(message);
    }
}
