package ru.golubyatnikov.family.calendar.bot.service.keyboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания inline клавиатур навигации.
 * 
 * <p>Отвечает за создание клавиатур для навигации между экранами и разделами бота.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.3</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@Slf4j
public class NavigationInlineKeyboardFactory {

    /**
     * Создает inline клавиатуру с кнопкой "Пропустить" для описания события.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createSkipDescriptionKeyboard() {
        log.debug("Создание inline клавиатуры с кнопкой 'Пропустить'");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton skipBtn = new InlineKeyboardButton("⏭️ Пропустить");
        skipBtn.setCallbackData("skip_description");
        row1.add(skipBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }
}
