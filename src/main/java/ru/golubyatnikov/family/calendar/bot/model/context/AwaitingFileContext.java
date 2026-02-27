package ru.golubyatnikov.family.calendar.bot.model.context;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Контекст ожидания файла для вложения.
 * Содержит информацию о событии, чате и сообщении со списком вложений.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Data
@AllArgsConstructor
public final class AwaitingFileContext {
    /**
     * Идентификатор события, к которому добавляется вложение
     */
    private Long eventId;
    
    /**
     * Идентификатор чата
     */
    private Long chatId;
    
    /**
     * Идентификатор сообщения со списком вложений для обновления
     */
    private Integer messageId;
    
    /**
     * Номер страницы в /my_events, с которой началось добавление вложения.
     * Если null, добавление началось не из /my_events.
     */
    private Integer myEventsPage;
}
