package ru.golubyatnikov.family.calendar.bot.model.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.golubyatnikov.family.calendar.bot.model.enums.EditField;
import java.time.LocalDate;

/**
 * Контекст редактирования события.
 * Содержит информацию о редактируемом событии, чате, текущем поле и сообщении.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Data
@AllArgsConstructor
public class EditingContext {
    /**
     * Идентификатор редактируемого события
     */
    private Long eventId;
    
    /**
     * Идентификатор чата
     */
    private Long chatId;
    
    /**
     * Текущее редактируемое поле
     */
    private EditField currentField;
    
    /**
     * Идентификатор сообщения, в котором происходит редактирование.
     * Используется для обновления того же сообщения при изменениях.
     */
    private Integer messageId;
    
    /**
     * Дата, с которой началось редактирование (для возврата к списку событий на эту дату).
     * Если null, редактирование началось не из календаря.
     */
    private LocalDate sourceDate;
}
