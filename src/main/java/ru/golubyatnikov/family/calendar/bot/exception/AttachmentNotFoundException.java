package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое при попытке получить несуществующее вложение.
 * 
 * @author Family Calendar Bot
 * @version 1.0
 */
public class AttachmentNotFoundException extends RuntimeException {
    
    /**
     * Создает новое исключение для указанного идентификатора вложения.
     * 
     * @param attachmentId идентификатор вложения, которое не найдено
     */
    public AttachmentNotFoundException(Long attachmentId) {
        super("Вложение с ID " + attachmentId + " не найдено");
    }
    
    /**
     * Создает новое исключение с указанным сообщением.
     * 
     * @param message сообщение об ошибке
     */
    public AttachmentNotFoundException(String message) {
        super(message);
    }
}
