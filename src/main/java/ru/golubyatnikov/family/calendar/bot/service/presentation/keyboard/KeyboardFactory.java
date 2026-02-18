package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.Arrays;
import java.util.List;

/**
 * Фабрика для создания клавиатур в новом API telegrambots 9.3.0.
 * Предоставляет удобные методы для создания кнопок, рядов и разметки клавиатур.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-16
 */
@Component
public class KeyboardFactory {

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
}
