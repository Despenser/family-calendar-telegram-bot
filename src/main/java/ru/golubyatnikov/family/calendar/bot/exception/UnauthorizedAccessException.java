package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое при попытке несанкционированного доступа к ресурсу.
 * 
 * <p>Это исключение используется когда пользователь пытается выполнить операцию,
 * на которую у него нет прав. Например, попытка редактировать или удалить
 * событие, созданное другим пользователем.</p>
 * 
 * <p><b>Требования:</b> 9.1, 7.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
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
