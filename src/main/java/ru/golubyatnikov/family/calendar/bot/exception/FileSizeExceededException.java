package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое при попытке загрузить файл, превышающий максимально допустимый размер.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
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
