package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Сервис для централизованного форматирования дат и времени.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-11
 */
@Slf4j
@Service
public class DateTimeFormattingService {

    private static final Locale RUSSIAN_LOCALE = Locale.forLanguageTag("ru");

    private final DateTimeFormatter dateFormatter;
    private final DateTimeFormatter timeFormatter;
    private final DateTimeFormatter dateTimeFormatter;
    private final DateTimeFormatter shortDateFormatter;
    private final DateTimeFormatter dateRangeFormatter;
    private final DateTimeFormatter monthFormatter;
    private final DateTimeFormatter shortDateWithoutYearFormatter;
    private final DateTimeFormatter dayOfWeekFormatter;
    private final DateTimeFormatter dateWithDayOfWeekFormatter;

    public DateTimeFormattingService() {
        this.dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        this.shortDateFormatter = DateTimeFormatter.ofPattern("dd.MM (EEEE)", RUSSIAN_LOCALE);
        this.dateRangeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        this.monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", RUSSIAN_LOCALE);
        this.shortDateWithoutYearFormatter = DateTimeFormatter.ofPattern("dd.MM", RUSSIAN_LOCALE);
        this.dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", RUSSIAN_LOCALE);
        this.dateWithDayOfWeekFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy - EEEE", RUSSIAN_LOCALE);
    }

    /**
     * Форматирует дату в формат dd.MM.yyyy
     */
    public String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(dateFormatter);
    }

    /**
     * Форматирует время в формат HH:mm
     */
    public String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(timeFormatter);
    }

    /**
     * Форматирует время в формат HH:mm
     */
    public String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(timeFormatter);
    }

    /**
     * Форматирует дату и время в формат dd.MM.yyyy HH:mm
     */
    public String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(dateTimeFormatter);
    }

    /**
     * Форматирует дату в короткий формат с днем недели: dd.MM (День недели)
     */
    public String formatShortDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(shortDateFormatter);
    }

    /**
     * Форматирует дату для диапазона: dd.MM.yyyy
     */
    public String formatDateRange(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(dateRangeFormatter);
    }

    /**
     * Форматирует месяц и год: Месяц ГГГГ
     */
    public String formatMonth(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(monthFormatter);
    }

    /**
     * Форматирует дату без года: dd.MM
     */
    public String formatShortDateWithoutYear(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(shortDateWithoutYearFormatter);
    }

    /**
     * Форматирует день недели в нижнем регистре: понедельник, вторник и т.д.
     */
    public String formatDayOfWeek(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(dayOfWeekFormatter).toLowerCase();
    }

    /**
     * Форматирует дату с днем недели: dd.MM.yyyy - День недели
     */
    public String formatDateWithDayOfWeek(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(dateWithDayOfWeekFormatter);
    }

    /**
     * Возвращает русскую локаль
     */
    public Locale getRussianLocale() {
        return RUSSIAN_LOCALE;
    }
}
