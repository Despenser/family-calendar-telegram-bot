package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое когда пункт чек-листа не найден в базе данных.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 */
public class ChecklistItemNotFoundException extends RuntimeException {
    
    /**
     * Создает исключение с сообщением об отсутствии пункта чек-листа.
     * 
     * @param itemId идентификатор пункта чек-листа
     */
    public ChecklistItemNotFoundException(Long itemId) {
        super(String.format("Пункт чек-листа с ID %d не найден", itemId));
    }
}
