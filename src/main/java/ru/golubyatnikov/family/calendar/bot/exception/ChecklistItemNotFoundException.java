package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое когда пункт чек-листа не найден в базе данных.
 * 
 * @author Family Calendar Bot
 * @version 1.0
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
