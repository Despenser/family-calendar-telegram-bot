package ru.golubyatnikov.family.calendar.bot.model.enums;

/**
 * Тип действия с событием для истории изменений.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-10
 */
public enum ActionType {
    /**
     * Событие создано
     */
    CREATED,
    
    /**
     * Событие обновлено
     */
    UPDATED,
    
    /**
     * Событие удалено
     */
    DELETED,
    
    /**
     * Событие восстановлено из корзины
     */
    RESTORED
}
