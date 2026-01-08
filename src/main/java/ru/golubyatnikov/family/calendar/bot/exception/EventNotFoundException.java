package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое когда событие не найдено в системе.
 *
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
public class EventNotFoundException extends RuntimeException {
    
    /**
     * Создает новое исключение с сообщением об ошибке.
     * 
     * @param message сообщение об ошибке
     */
    public EventNotFoundException(String message) {
        super(message);
    }
    
    /**
     * Создает новое исключение для указанного идентификатора события.
     * 
     * @param eventId идентификатор события, которое не было найдено
     */
    public EventNotFoundException(Long eventId) {
        super("Событие с ID " + eventId + " не найдено");
    }
}
