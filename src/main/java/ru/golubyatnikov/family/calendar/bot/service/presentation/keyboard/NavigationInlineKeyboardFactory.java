package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

/**
 * Фабрика для создания inline клавиатур навигации.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NavigationInlineKeyboardFactory {

    private final KeyboardFactory keyboardFactory;

    /**
     * Создает inline клавиатуру с кнопкой "Пропустить" для описания события.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createSkipDescriptionKeyboard() {
        log.debug("Создание inline клавиатуры с кнопкой 'Пропустить'");
        InlineKeyboardButton button = keyboardFactory.createButton("⏭️ Пропустить", "skip_description");
        InlineKeyboardRow row = keyboardFactory.createRow(button);
        return keyboardFactory.createMarkup(row);
    }
}
