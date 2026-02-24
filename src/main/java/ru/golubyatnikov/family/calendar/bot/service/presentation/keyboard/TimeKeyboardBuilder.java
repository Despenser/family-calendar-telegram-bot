package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.config.TimeSelectionConfig;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Компонент для построения клавиатур выбора времени.
 * Отвечает за создание inline-клавиатур для выбора часов и минут.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TimeKeyboardBuilder {

    private static final String TIME_IGNORE = "time_ignore";
    private static final String TIME_TO_CALENDAR = "time_to_calendar";
    private static final String TIME_CANCEL = "time_cancel";
    private static final String TIME_BACK = "time_back";
    private static final String HOUR_PREFIX = "hour_";
    private static final String TIME_PREFIX = "time_";

    private final TimeAvailabilityService timeAvailabilityService;
    private final KeyboardFactory keyboardFactory;
    private final TimeSelectionConfig timeSelectionConfig;

    /**
     * Создает клавиатуру выбора часа с фильтрацией прошедших часов.
     * 
     * @param selectedDate выбранная дата
     * @param user пользователь
     * @param editingEventId ID редактируемого события (null для создания нового)
     * 
     * @return клавиатура выбора часа
     */
    public InlineKeyboardMarkup createFilteredHourSelection(LocalDate selectedDate, 
                                                            User user, 
                                                            Long editingEventId) {

        validateParameters(selectedDate, user);
        
        List<Integer> availableHours = timeAvailabilityService.getAvailableHours(selectedDate, user);
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(createHeaderRow("Выберите час:"));
        
        if (!availableHours.isEmpty()) {
            rows.addAll(createHourButtons(availableHours));
        }
        
        rows.add(createBackRow(editingEventId));
        
        return buildKeyboard(rows);
    }

    /**
     * Создает клавиатуру выбора минут с фильтрацией прошедших интервалов.
     * 
     * @param selectedHour выбранный час
     * @param selectedDate выбранная дата
     * @param user пользователь
     * @param editingEventId ID редактируемого события (null для создания нового)
     * 
     * @return клавиатура выбора минут
     */
    public InlineKeyboardMarkup createFilteredMinuteSelection(int selectedHour,
                                                              LocalDate selectedDate,
                                                              User user,
                                                              Long editingEventId) {

        validateHour(selectedHour);
        validateParameters(selectedDate, user);
        
        List<Integer> availableMinutes = timeAvailabilityService.getAvailableMinutes(
            selectedHour, selectedDate, user);
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(createHeaderRow(String.format("Выберите минуты (час: %02d):", selectedHour)));
        
        if (!availableMinutes.isEmpty()) {
            rows.add(createMinuteButtons(selectedHour, availableMinutes));
        }
        
        rows.add(createNavigationRow(editingEventId == null));
        
        return buildKeyboard(rows);
    }

    private InlineKeyboardRow createHeaderRow(String text) {
        return keyboardFactory.createRow(
            keyboardFactory.createButton(text, TIME_IGNORE)
        );
    }

    private @NonNull List<InlineKeyboardRow> createHourButtons(@NonNull List<Integer> hours) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        
        for (int i = 0; i < hours.size(); i++) {
            int hour = hours.get(i);
            currentRow.add(keyboardFactory.createButton(
                String.format("%02d:00", hour),
                HOUR_PREFIX + String.format("%02d", hour)
            ));
            
            if ((i + 1) % timeSelectionConfig.getHoursPerRow() == 0 || i == hours.size() - 1) {
                rows.add(keyboardFactory.createRow(new ArrayList<>(currentRow)));
                currentRow.clear();
            }
        }
        
        return rows;
    }

    private InlineKeyboardRow createMinuteButtons(int selectedHour, @NonNull List<Integer> minutes) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();

        for (int minute : minutes) {
            buttons.add(keyboardFactory.createButton(
                String.format("%02d:%02d", selectedHour, minute),
                TIME_PREFIX + String.format("%02d:%02d", selectedHour, minute)
            ));
        }

        return keyboardFactory.createRow(buttons);
    }

    private InlineKeyboardRow createBackRow(Long editingEventId) {
        if (editingEventId != null) {
            // При редактировании - только кнопка "Назад" к меню редактирования
            String callbackData = CallbackPrefix.EDIT_BACK.withPayload(editingEventId.toString());
            return keyboardFactory.createRow(
                keyboardFactory.createButton("🔙 Назад", callbackData)
            );

        } else {
            // При создании нового события - всегда две кнопки: "Назад к календарю" и "Отменить создание"
            return keyboardFactory.createRow(
                keyboardFactory.createButton("🔙 Назад", TIME_TO_CALENDAR),
                keyboardFactory.createButton("✖️ Отменить создание", TIME_CANCEL)
            );
        }
    }

    private InlineKeyboardRow createNavigationRow(boolean isCreatingNewEvent) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        
        buttons.add(keyboardFactory.createButton("🔙 Назад", TIME_BACK));
        
        // При создании нового события всегда показываем кнопку "Отменить создание"
        if (isCreatingNewEvent) {
            buttons.add(keyboardFactory.createButton("✖️ Отменить создание", TIME_CANCEL));
        }
        
        return keyboardFactory.createRow(buttons);
    }

    private InlineKeyboardMarkup buildKeyboard(@NonNull List<InlineKeyboardRow> rows) {
        return keyboardFactory.createMarkup(rows);
    }

    private void validateHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Переданные часы должны быть в диапазоне от 0 до 23");
        }
    }

    private void validateParameters(LocalDate date, User user) {
        if (date == null) {
            throw new IllegalArgumentException("Выбранная дата не должна быть null");
        }
        if (user == null) {
            throw new IllegalArgumentException("Пользователь не должен быть null");
        }
    }
}
