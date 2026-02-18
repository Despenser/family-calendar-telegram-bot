package ru.golubyatnikov.family.calendar.bot.model.context;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Контекст поискового запроса.
 * Содержит информацию о чате и сообщении для редактирования при поиске.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Data
@AllArgsConstructor
public class SearchQueryContext {
    /**
     * Идентификатор чата
     */
    private Long chatId;
    
    /**
     * Идентификатор сообщения для редактирования
     */
    private Integer messageId;
}
