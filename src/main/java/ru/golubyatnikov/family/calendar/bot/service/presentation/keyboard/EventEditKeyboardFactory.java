package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import java.time.LocalDate;
import java.util.List;

/**
 * Фабрика для создания клавиатур редактирования событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Component
@RequiredArgsConstructor
public class EventEditKeyboardFactory {
    
    private final KeyboardFactory keyboardFactory;

    /**
     * Создает клавиатуру со списком событий для редактирования.
     */
    public InlineKeyboardMarkup createEventListKeyboard(@NonNull List<Event> events,
                                                        LocalDate date) {

        List<InlineKeyboardRow> rows = keyboardFactory.createEventButtonRows(events,
                event -> CallbackPrefix.EDIT_EVENT_FROM_CALENDAR.withPayload(event.getId() + "_" + date.toString()));

        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад", CallbackPrefix.CALENDAR.withPayload(date.toString()))
        ));

        return keyboardFactory.createMarkup(rows);
    }
    
    /**
     * Создает клавиатуру со списком событий для удаления.
     */
    public InlineKeyboardMarkup createDeleteEventListKeyboard(@NonNull List<Event> events,
                                                              LocalDate date) {

        List<InlineKeyboardRow> rows = keyboardFactory.createEventButtonRows(events,
                event -> CallbackPrefix.DELETE_EVENT.withPayload(event.getId().toString()));

        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад", CallbackPrefix.CALENDAR.withPayload(date.toString()))
        ));

        return keyboardFactory.createMarkup(rows);
    }
}
