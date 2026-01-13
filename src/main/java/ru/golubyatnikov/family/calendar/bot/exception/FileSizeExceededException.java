package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое при попытке загрузить файл, превышающий максимально допустимый размер.
 * 
 * <p>Максимальный размер файла составляет 20 МБ согласно требованиям системы.</p>
 * 
 * <p><b>Требования:</b> 20.6</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 */
public class FileSizeExceededException extends RuntimeException {
    
    /**
     * Создает новое исключение с указанным сообщением.
     * 
     * @param message сообщение об ошибке
     */
    public FileSizeExceededException(String message) {
        super(message);
    }
    
    /**
     * Создает новое исключение с указанным сообщением и причиной.
     * 
     * @param message сообщение об ошибке
     * @param cause причина исключения
     */
    public FileSizeExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
