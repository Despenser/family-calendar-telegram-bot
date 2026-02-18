package ru.golubyatnikov.family.calendar.bot.model.context;

import ru.golubyatnikov.family.calendar.bot.model.entity.User;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Контекст для построения календарной клавиатуры.
 * Инкапсулирует всю информацию, необходимую для отображения календаря.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
public record CalendarContext(
        YearMonth yearMonth,
        YearMonth currentYearMonth,
        User user,
        boolean allowPastDates,
        Long editingEventId,
        LocalDate today,
        LocalDate firstDay,
        int year,
        int month,
        int daysInMonth
) {
    /**
     * Создает контекст календаря с вычисленными полями.
     *
     * @param yearMonth      год и месяц для отображения
     * @param user           пользователь, для которого строится календарь
     * @param allowPastDates разрешить ли выбор прошлых дат
     * @param editingEventId ID редактируемого события (null если создается новое)
     */
    public CalendarContext(YearMonth yearMonth, User user, boolean allowPastDates, Long editingEventId) {
        this(
                yearMonth,
                YearMonth.now(user.getZoneId()),
                user,
                allowPastDates,
                editingEventId,
                user.getCurrentDate(),
                yearMonth.atDay(1),
                yearMonth.getYear(),
                yearMonth.getMonthValue(),
                yearMonth.lengthOfMonth()
        );
    }

    /**
     * Проверяет, является ли календарь календарем редактирования события.
     *
     * @return true, если редактируется существующее событие
     */
    public boolean isEditingEvent() {
        return editingEventId != null;
    }

    /**
     * Проверяет, является ли календарь календарем создания нового события.
     *
     * @return true, если создается новое событие
     */
    public boolean isCreatingEvent() {
        return editingEventId == null && !allowPastDates;
    }

    /**
     * Проверяет, является ли календарь календарем просмотра событий.
     *
     * @return true, если календарь используется для просмотра
     */
    public boolean isViewingCalendar() {
        return editingEventId == null && allowPastDates;
    }
}
