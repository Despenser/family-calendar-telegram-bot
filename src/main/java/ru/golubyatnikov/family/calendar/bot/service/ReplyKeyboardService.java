package ru.golubyatnikov.family.calendar.bot.service;

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
 * <p>ReplyKeyboardService предоставляет методы для создания клавиатур с кнопками команд,
 * которые отображаются в нижней части экрана пользователя.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание клавиатуры для авторизованных пользователей с полным набором команд</li>
 *   <li>Создание клавиатуры для неавторизованных пользователей с ограниченным набором команд</li>
 *   <li>Преобразование текста кнопки в соответствующую команду для обработки</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Service
@Slf4j
public class ReplyKeyboardService {

    // Константы для текста кнопок
    private static final String BTN_START = "🚀 Начать";
    private static final String BTN_UPCOMING = "📋 Планы";
    private static final String BTN_ADD = "➕ Добавить";
    private static final String BTN_MY = "📝 Мои события";
    private static final String BTN_HELP = "❓ Помощь";
    private static final String BTN_TODAY = "📅 Сегодня";
    private static final String BTN_WEEK = "📆 Неделя";
    private static final String BTN_TRASH = "🗑️ Корзина";
    private static final String BTN_STATS = "📊 Статистика";
    private static final String BTN_SEARCH = "🔍 Поиск";
    private static final String BTN_FILTER = "🎯 Фильтр";

    /**
     * Создает клавиатуру для авторизованного пользователя с полным набором команд.
     * 
     * <p>Клавиатура содержит следующие кнопки:</p>
     * <ul>
     *   <li>📝 Мои события - просмотр и управление своими событиями</li>
     *   <li>➕ Добавить - создание нового события</li>
     *   <li>📅 Сегодня - события на текущий день</li>
     *   <li>📆 Неделя - события на текущую неделю</li>
     *   <li>📋 Планы - просмотр событий на ближайшие 30 дней</li>
     *   <li>🔍 Поиск - поиск событий</li>
     *   <li>🎯 Фильтр - фильтрация событий</li>
     *   <li>📊 Статистика - статистика по событиям</li>
     *   <li>🗑️ Корзина - просмотр удаленных событий</li>
     *   <li>❓ Помощь - справка по командам</li>
     * </ul>
     * 
     * @return настроенная ReplyKeyboardMarkup для авторизованного пользователя
     */
    public ReplyKeyboardMarkup createAuthorizedUserKeyboard() {
        log.debug("Создание клавиатуры для авторизованного пользователя");
        
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        
        // Ряд 1: Мои события | Добавить
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BTN_MY));
        row1.add(new KeyboardButton(BTN_ADD));
        rows.add(row1);
        
        // Ряд 2: Сегодня | Неделя | Предстоящие
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(BTN_TODAY));
        row2.add(new KeyboardButton(BTN_WEEK));
        row2.add(new KeyboardButton(BTN_UPCOMING));
        rows.add(row2);
        
        // Ряд 3: Поиск | Фильтр | Статистика
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton(BTN_SEARCH));
        row3.add(new KeyboardButton(BTN_FILTER));
        row3.add(new KeyboardButton(BTN_STATS));
        rows.add(row3);
        
        // Ряд 4: Корзина | Помощь
        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton(BTN_TRASH));
        row4.add(new KeyboardButton(BTN_HELP));
        rows.add(row4);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Клавиатура для авторизованного пользователя создана: {} кнопок в {} рядах", 
                countButtons(rows), rows.size());
        
        return keyboard;
    }

    /**
     * Создает клавиатуру для неавторизованного пользователя с ограниченным набором команд.
     * 
     * <p>Клавиатура содержит следующие кнопки:</p>
     * <ul>
     *   <li>🚀 Начать - регистрация/авторизация в системе</li>
     *   <li>❓ Помощь - справка по командам</li>
     * </ul>
     * 
     * @return настроенная ReplyKeyboardMarkup для неавторизованного пользователя
     */
    public ReplyKeyboardMarkup createUnauthorizedUserKeyboard() {
        log.debug("Создание клавиатуры для неавторизованного пользователя");
        
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        
        // Единственная строка: Начать | Помощь
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(BTN_START));
        row.add(new KeyboardButton(BTN_HELP));
        rows.add(row);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Клавиатура для неавторизованного пользователя создана: {} кнопок в {} рядах", 
                countButtons(rows), rows.size());
        
        return keyboard;
    }

    /**
     * Преобразует текст кнопки в соответствующую команду для обработки.
     * 
     * <p>Поддерживаемые преобразования:</p>
     * <ul>
     *   <li>"🚀 Начать" → "/start"</li>
     *   <li>"📋 Планы" → "/upcoming_events"</li>
     *   <li>"➕ Добавить" → "/add_event"</li>
     *   <li>"📝 Мои события" → "/my_events"</li>
     *   <li>"❓ Помощь" → "/help"</li>
     *   <li>"📅 Сегодня" → "/today"</li>
     *   <li>"📆 Неделя" → "/week"</li>
     *   <li>"🗑️ Корзина" → "/trash"</li>
     *   <li>"📊 Статистика" → "/stats"</li>
     *   <li>"🔍 Поиск" → "/search"</li>
     *   <li>"🎯 Фильтр" → "/filter"</li>
     * </ul>
     * 
     * @param buttonText текст кнопки для преобразования
     * @return соответствующая команда или исходный текст
     * @throws IllegalArgumentException если buttonText равен null
     */
    public String buttonTextToCommand(String buttonText) {
        if (buttonText == null) {
            log.error("Попытка преобразовать null buttonText в команду");
            throw new IllegalArgumentException("ButtonText не может быть null");
        }
        
        log.debug("Преобразование текста кнопки в команду: '{}'", buttonText);
        
        String command = switch (buttonText) {
            case BTN_START -> "/start";
            case BTN_UPCOMING -> "/upcoming_events";
            case BTN_ADD -> "/add_event";
            case BTN_MY -> "/my_events";
            case BTN_HELP -> "/help";
            case BTN_TODAY -> "/today";
            case BTN_WEEK -> "/week";
            case BTN_TRASH -> "/trash";
            case BTN_STATS -> "/stats";
            case BTN_SEARCH -> "/search";
            case BTN_FILTER -> "/filter";
            default -> buttonText;
        };
        
        if (!command.equals(buttonText)) {
            log.debug("Текст кнопки '{}' преобразован в команду '{}'", buttonText, command);
        } else {
            log.debug("Текст '{}' не является кнопкой, возвращен без изменений", buttonText);
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
