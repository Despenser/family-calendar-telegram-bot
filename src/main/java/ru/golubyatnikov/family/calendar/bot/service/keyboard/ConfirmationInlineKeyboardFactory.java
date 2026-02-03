package ru.golubyatnikov.family.calendar.bot.service.keyboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания inline клавиатур подтверждений.
 * 
 * <p>Отвечает за создание клавиатур для подтверждения различных действий пользователя.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.3</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@Slf4j
public class ConfirmationInlineKeyboardFactory {

    // Пока нет специфичных методов подтверждения, которые не относятся к событиям или вложениям
    // Этот класс создан для будущего расширения и соблюдения архитектуры
}
