package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое, когда пользователь не найден в системе.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
public class UserNotFoundException extends RuntimeException {
    
    /**
     * Создает новое исключение с сообщением об ошибке.
     * 
     * @param message сообщение об ошибке
     */
    public UserNotFoundException(String message) {
        super(message);
    }
    
    /**
     * Создает новое исключение для указанного идентификатора пользователя.
     * 
     * @param userId идентификатор пользователя, который не был найден
     */
    public UserNotFoundException(Long userId) {
        super("Пользователь с ID " + userId + " не найден");
    }
}
