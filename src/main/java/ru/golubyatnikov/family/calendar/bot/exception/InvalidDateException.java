package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое при попытке создать или обновить событие с некорректной датой.
 * 
 * <p>Это исключение используется для валидации дат событий. Например, когда
 * пользователь пытается создать событие с датой в прошлом.</p>
 * 
 * <p><b>Требования:</b> 9.1, 4.2</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
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
