package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.CANCEL;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.SKIP;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.BACK;

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
     * Создает inline клавиатуру с кнопкой "Отменить создание" для этапа ввода названия.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createCancelCreationKeyboard() {
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(BACK + " Назад", CallbackPrefix.TITLE_BACK.withPayload("")),
                keyboardFactory.createButton(CANCEL + " Отменить создание", CallbackPrefix.TYPE_CANCEL.withPayload(""))
            )
        );
    }
    
    /**
     * Создает inline клавиатуру с кнопкой "Пропустить" для описания события.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createSkipDescriptionKeyboard() {
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(SKIP + " Пропустить", CallbackPrefix.SKIP_DESCRIPTION.withPayload(""))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton(BACK + " Назад", CallbackPrefix.DESC_BACK_TO_TITLE.withPayload("")),
                keyboardFactory.createButton(CANCEL + " Отменить создание", CallbackPrefix.TYPE_CANCEL.withPayload(""))
            )
        );
    }
}
