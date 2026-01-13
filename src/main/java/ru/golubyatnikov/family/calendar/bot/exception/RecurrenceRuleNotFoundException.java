package ru.golubyatnikov.family.calendar.bot.exception;

/**
 * Исключение, выбрасываемое когда правило повторения не найдено в базе данных.
 * 
 * @author Family Calendar Bot
 * @version 1.0
 */
public class RecurrenceRuleNotFoundException extends RuntimeException {
    
    /**
     * Создает исключение с сообщением об отсутствии правила повторения.
     * 
     * @param seriesId идентификатор серии событий
     */
    public RecurrenceRuleNotFoundException(String seriesId) {
        super(String.format("Правило повторения для серии %s не найдено", seriesId));
    }
}
