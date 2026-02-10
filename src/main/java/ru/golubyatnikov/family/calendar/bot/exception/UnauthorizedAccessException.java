package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое при попытке несанкционированного доступа к ресурсу.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
public class UnauthorizedAccessException extends RuntimeException {
    
    /**
     * Создает новое исключение с сообщением об ошибке.
     * 
     * @param message сообщение об ошибке
     */
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
