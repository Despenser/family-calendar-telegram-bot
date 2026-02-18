package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Фабрика для создания клавиатур в новом API telegrambots 9.3.0.
 * Предоставляет удобные методы для создания кнопок, рядов и разметки клавиатур.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-16
 */
@Component
@RequiredArgsConstructor
public class KeyboardFactory {

    private final DateTimeFormattingService dateTimeFormattingService;

    /**
     * Создает InlineKeyboardButton с текстом и callback data
     */
    public InlineKeyboardButton createButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    /**
     * Создает InlineKeyboardRow из списка кнопок
     */
    public InlineKeyboardRow createRow(InlineKeyboardButton... buttons) {
        return new InlineKeyboardRow(Arrays.asList(buttons));
    }

    /**
     * Создает InlineKeyboardRow из списка кнопок
     */
    public InlineKeyboardRow createRow(List<InlineKeyboardButton> buttons) {
        return new InlineKeyboardRow(buttons);
    }

    /**
     * Создает InlineKeyboardMarkup из списка InlineKeyboardRow
     */
    public InlineKeyboardMarkup createMarkup(List<InlineKeyboardRow> rows) {
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Создает InlineKeyboardMarkup из varargs InlineKeyboardRow
     */
    public InlineKeyboardMarkup createMarkup(InlineKeyboardRow... rows) {
        return InlineKeyboardMarkup.builder()
                .keyboard(Arrays.asList(rows))
                .build();
    }

    /**
     * Создает список строк клавиатуры с кнопками событий.
     * Каждая кнопка содержит время и название события в формате "HH:mm - Название".
     * 
     * @param events список событий
     * @param callbackDataProvider функция для генерации callback data для каждого события
     * @return список строк клавиатуры
     */
    public List<InlineKeyboardRow> createEventButtonRows(@NonNull List<Event> events,
                                                         @NonNull Function<Event, String> callbackDataProvider) {

        List<InlineKeyboardRow> rows = new ArrayList<>();
        
        events.forEach(event -> {
            String buttonText = String.format("%s - %s",
                    dateTimeFormattingService.formatTime(event.getEventTime()),
                    event.getTitle());

            rows.add(createRow(createButton(buttonText, callbackDataProvider.apply(event))));
        });
        
        return rows;
    }
}
