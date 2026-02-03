package ru.golubyatnikov.family.calendar.bot.service.keyboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания inline клавиатур, связанных с напоминаниями.
 * 
 * <p>Отвечает за создание клавиатур для настройки и управления напоминаниями событий.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.3</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@Slf4j
public class ReminderInlineKeyboardFactory {

    /**
     * Создает inline клавиатуру для настройки напоминаний.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createReminderSettingsKeyboard(Long eventId) {
        log.debug("Создание inline клавиатуры настройки напоминаний для события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton morningBtn = new InlineKeyboardButton("🌅 Утром в день события");
        morningBtn.setCallbackData("reminder_morning_" + eventId);
        row1.add(morningBtn);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton eveningBtn = new InlineKeyboardButton("🌆 Вечером накануне");
        eveningBtn.setCallbackData("reminder_evening_" + eventId);
        row2.add(eveningBtn);
        rows.add(row2);
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton hourBtn = new InlineKeyboardButton("⏰ За час до события");
        hourBtn.setCallbackData("reminder_hour_" + eventId);
        row3.add(hourBtn);
        rows.add(row3);
        
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton tenMinBtn = new InlineKeyboardButton("⏱️ За 10 минут");
        tenMinBtn.setCallbackData("reminder_ten_min_" + eventId);
        row4.add(tenMinBtn);
        rows.add(row4);
        
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        InlineKeyboardButton customBtn = new InlineKeyboardButton("⚙️ Свое время");
        customBtn.setCallbackData("reminder_custom_" + eventId);
        row5.add(customBtn);
        rows.add(row5);
        
        List<InlineKeyboardButton> row6 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("reminder_cancel_" + eventId);
        row6.add(cancelBtn);
        rows.add(row6);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline клавиатура настройки напоминаний создана");
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру меню настройки повторения события.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createRecurrenceMenuKeyboard(Long eventId) {
        log.debug("Создание inline клавиатуры меню повторения для события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton dailyBtn = new InlineKeyboardButton("📆 Ежедневно");
        dailyBtn.setCallbackData("recurrence_daily_" + eventId);
        row1.add(dailyBtn);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton weeklyBtn = new InlineKeyboardButton("📅 Еженедельно");
        weeklyBtn.setCallbackData("recurrence_weekly_" + eventId);
        row2.add(weeklyBtn);
        rows.add(row2);
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton monthlyBtn = new InlineKeyboardButton("🗓️ Ежемесячно");
        monthlyBtn.setCallbackData("recurrence_monthly_" + eventId);
        row3.add(monthlyBtn);
        rows.add(row3);
        
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("recurrence_cancel_" + eventId);
        row4.add(cancelBtn);
        rows.add(row4);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline клавиатура меню повторения создана");
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для выбора действия с серией событий.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createSeriesActionKeyboard(Long eventId) {
        log.debug("Создание inline клавиатуры действия с серией для события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton singleBtn = new InlineKeyboardButton("📌 Только это событие");
        singleBtn.setCallbackData("series_action_single_" + eventId);
        row1.add(singleBtn);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton seriesBtn = new InlineKeyboardButton("📚 Всю серию");
        seriesBtn.setCallbackData("series_action_all_" + eventId);
        row2.add(seriesBtn);
        rows.add(row2);
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("series_action_cancel_" + eventId);
        row3.add(cancelBtn);
        rows.add(row3);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline клавиатура действия с серией создана");
        
        return keyboard;
    }
}
