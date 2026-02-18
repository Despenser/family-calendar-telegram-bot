package ru.golubyatnikov.family.calendar.bot.model.enums;

/**
 * Шаги диалога создания события.
 * Определяют текущее состояние процесса создания события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
public enum ConversationStep {
    /** Ожидание выбора даты через inline-календарь */
    WAITING_FOR_DATE,
    
    /** Ожидание выбора времени через inline-кнопки */
    WAITING_FOR_TIME,
    
    /** Ожидание выбора типа события (персональное или семейное) */
    WAITING_FOR_TYPE,
    
    /** Ожидание ввода названия события через текстовое сообщение */
    WAITING_FOR_TITLE,
    
    /** Ожидание ввода описания события (опционально) */
    WAITING_FOR_DESCRIPTION
}
