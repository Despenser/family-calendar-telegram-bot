package ru.golubyatnikov.family.calendar.bot.model.enums;

/**
 * Состояния процесса парсинга события через AI.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-27
 */
public enum EventParsingState {
    /**
     * Начальное состояние - ожидание первого сообщения
     */
    INITIAL,
    
    /**
     * Ожидание уточнения данных от пользователя
     */
    AWAITING_CLARIFICATION,
    
    /**
     * Данные собраны, ожидание подтверждения создания события
     */
    AWAITING_CONFIRMATION,
    
    /**
     * Процесс завершен
     */
    COMPLETED,
    
    /**
     * Процесс отменен пользователем
     */
    CANCELLED
}
