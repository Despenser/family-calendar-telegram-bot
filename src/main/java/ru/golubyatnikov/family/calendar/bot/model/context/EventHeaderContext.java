package ru.golubyatnikov.family.calendar.bot.model.context;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Контекст шапки события.
 * Содержит информацию о наличии шапки "Мои события" и количестве событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Data
@AllArgsConstructor
public class EventHeaderContext {
    /**
     * Флаг наличия шапки "Мои события"
     */
    private boolean hasMyEventsHeader;
    
    /**
     * Количество событий для формирования шапки
     */
    private int eventCount;
}
