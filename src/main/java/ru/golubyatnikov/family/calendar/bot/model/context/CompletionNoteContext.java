package ru.golubyatnikov.family.calendar.bot.model.context;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Контекст добавления заметки к завершенному событию.
 * Содержит информацию о событии, чате и сообщении для редактирования.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Data
@AllArgsConstructor
public final class CompletionNoteContext {
    /**
     * Идентификатор завершенного события
     */
    private Long eventId;
    
    /**
     * Идентификатор чата
     */
    private Long chatId;
    
    /**
     * Идентификатор сообщения для редактирования.
     * Используется для обновления того же сообщения на всех этапах добавления заметки.
     */
    private Integer messageId;
    
    /**
     * Номер страницы в постраничном списке /my_events.
     * Используется для возврата к списку после завершения операции.
     * Может быть null, если событие не из /my_events.
     */
    private Integer myEventsPage;
}
