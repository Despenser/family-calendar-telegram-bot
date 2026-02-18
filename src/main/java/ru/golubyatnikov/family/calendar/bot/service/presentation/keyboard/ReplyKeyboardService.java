package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для создания обычных клавиатур (ReplyKeyboardMarkup) в Telegram.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@Slf4j
public class ReplyKeyboardService {

    // Константы для текста кнопок
    private static final String BTN_START = "🚀 Начать";
    private static final String BTN_MONTH = "🗓️ Месяц";
    private static final String BTN_ADD = "➕ Добавить";
    private static final String BTN_MY = "📝 Мои события";
    private static final String BTN_HELP = "❓ Помощь";
    private static final String BTN_TODAY = "📅 Сегодня";
    private static final String BTN_WEEK = "📆 Неделя";
    private static final String BTN_TRASH = "🗑️ Корзина";
    private static final String BTN_STATS = "📊 Статистика";
    private static final String BTN_SEARCH = "🔍 Поиск";
    private static final String BTN_FILTER = "🎯 Фильтр";
    private static final String BTN_CALENDAR = "📅 Календарь";

    /**
     * Создает клавиатуру для авторизованного пользователя с полным набором команд.
     *
     * @return настроенная ReplyKeyboardMarkup для авторизованного пользователя
     */
    public ReplyKeyboardMarkup createAuthorizedUserKeyboard() {
        List<KeyboardRow> rows = new ArrayList<>();
        
        // Ряд 1: Календарь
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BTN_CALENDAR));
        rows.add(row1);
        
        // Ряд 2: Мои события | Добавить
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(BTN_MY));
        row2.add(new KeyboardButton(BTN_ADD));
        rows.add(row2);
        
        // Ряд 3: Сегодня | Неделя | Месяц
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton(BTN_TODAY));
        row3.add(new KeyboardButton(BTN_WEEK));
        row3.add(new KeyboardButton(BTN_MONTH));
        rows.add(row3);
        
        // Ряд 4: Поиск | Фильтр | Статистика
        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton(BTN_SEARCH));
        row4.add(new KeyboardButton(BTN_FILTER));
        row4.add(new KeyboardButton(BTN_STATS));
        rows.add(row4);
        
        // Ряд 5: Корзина | Помощь
        KeyboardRow row5 = new KeyboardRow();
        row5.add(new KeyboardButton(BTN_TRASH));
        row5.add(new KeyboardButton(BTN_HELP));
        rows.add(row5);
        
        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboard(rows)
                .resizeKeyboard(true)
                .build();
        
        return keyboard;
    }

    /**
     * Создает клавиатуру для неавторизованного пользователя с ограниченным набором команд.
     *
     * @return настроенная ReplyKeyboardMarkup для неавторизованного пользователя
     */
    public ReplyKeyboardMarkup createUnauthorizedUserKeyboard() {
        List<KeyboardRow> rows = new ArrayList<>();
        
        // Единственная строка: Начать | Помощь
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(BTN_START));
        row.add(new KeyboardButton(BTN_HELP));
        rows.add(row);
        
        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboard(rows)
                .resizeKeyboard(true)
                .build();
        
        return keyboard;
    }

    /**
     * Преобразует текст кнопки в соответствующую команду для обработки.
     *
     * @param buttonText текст кнопки для преобразования
     *
     * @return соответствующая команда или исходный текст
     * @throws IllegalArgumentException если buttonText равен null
     */
    public String buttonTextToCommand(String buttonText) {
        if (buttonText == null) {
            throw new IllegalArgumentException("ButtonText не может быть null");
        }
        
        String command = switch (buttonText) {
            case BTN_START -> "/start";
            case BTN_MONTH -> "/month";
            case BTN_ADD -> "/add_event";
            case BTN_MY -> "/my_events";
            case BTN_HELP -> "/help";
            case BTN_TODAY -> "/today";
            case BTN_WEEK -> "/week";
            case BTN_TRASH -> "/trash";
            case BTN_STATS -> "/stats";
            case BTN_SEARCH -> "/search";
            case BTN_FILTER -> "/filter";
            case BTN_CALENDAR -> "/calendar";
            default -> buttonText;
        };
        
        if (!command.equals(buttonText)) {
            } else {
            }
        
        return command;
    }

    /**
     * Подсчитывает общее количество кнопок в списке рядов клавиатуры.
     * 
     * @param rows список рядов клавиатуры
     * @return общее количество кнопок
     */
    private int countButtons(List<KeyboardRow> rows) {
        if (rows == null) {
            return 0;
        }
        
        return rows.stream()
                .mapToInt(row -> row != null ? row.size() : 0)
                .sum();
    }
}
