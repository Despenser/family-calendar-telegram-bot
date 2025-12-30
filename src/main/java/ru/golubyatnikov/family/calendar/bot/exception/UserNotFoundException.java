package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое когда пользователь не найден в системе.
 * 
 * <p>Это исключение используется в сервисном слое когда операция требует
 * существующего пользователя, но пользователь с указанным идентификатором
 * не найден в базе данных.</p>
 * 
 * <p><b>Требования:</b> 9.1</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
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
