package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import java.time.LocalDate;

/**
 * Фасад для компоновки сложных клавиатур (календари, выбор времени).
 * Делегирует создание клавиатур специализированным компонентам.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeyboardLayoutService {

    private final CalendarKeyboardBuilder calendarKeyboardBuilder;
    private final TimeKeyboardBuilder timeKeyboardBuilder;

    /**
     * Создает inline-календарь для выбора даты события с учетом таймзоны пользователя.
     * 
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     *
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если month не в диапазоне 1-12
     */
    public InlineKeyboardMarkup createCalendarKeyboard(int year,
                                                       int month,
                                                       User user) {

        return calendarKeyboardBuilder.createForNewEvent(year, month, user);
    }

    /**
     * Создает inline-календарь для выбора даты события с учетом таймзоны пользователя.
     * 
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     * @param editingEventId ID редактируемого события (null для создания нового)
     *
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если month не в диапазоне 1-12
     */
    public InlineKeyboardMarkup createCalendarKeyboard(int year,
                                                       int month,
                                                       User user,
                                                       Long editingEventId) {

        return calendarKeyboardBuilder.createForEventEdit(year, month, user, editingEventId);
    }

    /**
     * Создает inline-календарь для просмотра событий с возможностью навигации в прошлое.
     * 
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     *
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если month не в диапазоне 1-12
     */
    public InlineKeyboardMarkup createViewCalendarKeyboard(int year,
                                                           int month,
                                                           User user) {

        return calendarKeyboardBuilder.createForEventView(year, month, user);
    }

    /**
     * Создает inline-клавиатуру для выбора часа с фильтрацией прошедших часов.
     * 
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createFilteredHourSelectionKeyboard(LocalDate selectedDate,
                                                                    User user) {

        return timeKeyboardBuilder.createFilteredHourSelection(selectedDate, user, null);
    }

    /**
     * Создает inline-клавиатуру для выбора часа с фильтрацией прошедших часов.
     * 
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @param editingEventId ID редактируемого события (null для создания нового)
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createFilteredHourSelectionKeyboard(LocalDate selectedDate,
                                                                    User user,
                                                                    Long editingEventId) {

        return timeKeyboardBuilder.createFilteredHourSelection(selectedDate, user, editingEventId);
    }

    /**
     * Создает inline-клавиатуру для выбора минут с фильтрацией прошедших минут.
     * 
     * @param selectedHour выбранный час (0-23)
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @param editingEventId ID редактируемого события (null для создания нового)
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createFilteredMinuteSelectionKeyboard(int selectedHour,
                                                                      LocalDate selectedDate,
                                                                      User user,
                                                                      Long editingEventId) {

        return timeKeyboardBuilder.createFilteredMinuteSelection(selectedHour, selectedDate, user, editingEventId);
    }
}
