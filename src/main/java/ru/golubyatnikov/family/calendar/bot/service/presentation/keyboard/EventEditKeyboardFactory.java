package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO дублирование нужен рефакторинг
 * Фабрика для создания клавиатур редактирования событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Component
@RequiredArgsConstructor
public class EventEditKeyboardFactory {
    
    private final DateTimeFormattingService dateTimeFormattingService;
    private final KeyboardFactory keyboardFactory;

    /**
     * Создает клавиатуру со списком событий для редактирования.
     */
    public InlineKeyboardMarkup createEventListKeyboard(@NonNull List<Event> events,
                                                        LocalDate date) {

        List<InlineKeyboardRow> rows = new ArrayList<>();

        events.forEach(event -> {
            String buttonText = String.format("%s - %s",
                    dateTimeFormattingService.formatTime(event.getEventTime()),
                    event.getTitle());
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(buttonText, "edit_event_from_calendar_" + event.getId() + "_" + date.toString())
            ));
        });

        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад", "calendar_" + date)
        ));

        return keyboardFactory.createMarkup(rows);
    }
    
    /**
     * Создает клавиатуру со списком событий для удаления.
     */
    public InlineKeyboardMarkup createDeleteEventListKeyboard(@NonNull List<Event> events,
                                                              LocalDate date) {

        List<InlineKeyboardRow> rows = new ArrayList<>();

        events.forEach(event -> {
            String buttonText = String.format("%s - %s",
                    dateTimeFormattingService.formatTime(event.getEventTime()),
                    event.getTitle());
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(buttonText, "delete_event_" + event.getId())
            ));
        });
        
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад", "calendar_" + date)
        ));
        
        return keyboardFactory.createMarkup(rows);
    }
}
