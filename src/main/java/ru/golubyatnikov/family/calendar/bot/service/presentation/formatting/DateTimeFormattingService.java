package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.config.DateTimeFormatConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
@RequiredArgsConstructor
public class DateTimeFormattingService {

    private final DateTimeFormatConfig config;

    private DateTimeFormatter dateFormatter;
    private DateTimeFormatter timeFormatter;
    private DateTimeFormatter dateTimeFormatter;
    private DateTimeFormatter shortDateFormatter;
    private DateTimeFormatter dateRangeFormatter;
    private DateTimeFormatter monthFormatter;
    private DateTimeFormatter shortDateWithoutYearFormatter;
    private DateTimeFormatter dayOfWeekFormatter;
    private DateTimeFormatter dateWithDayOfWeekFormatter;

    @Getter
    private ZoneId utc;

    @Getter
    private DateTimeFormatter[] dateParseFormatters;

    @Getter
    private DateTimeFormatter[] timeParseFormatters;

    @jakarta.annotation.PostConstruct
    public void init() {
        Locale locale = Locale.forLanguageTag(config.getLocale());
        
        this.dateFormatter = DateTimeFormatter.ofPattern(config.getDatePattern());
        this.timeFormatter = DateTimeFormatter.ofPattern(config.getTimePattern());
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(config.getDateTimePattern());
        this.shortDateFormatter = DateTimeFormatter.ofPattern(config.getShortDatePattern(), locale);
        this.dateRangeFormatter = DateTimeFormatter.ofPattern(config.getDatePattern());
        this.monthFormatter = DateTimeFormatter.ofPattern(config.getMonthPattern(), locale);
        this.shortDateWithoutYearFormatter = DateTimeFormatter.ofPattern(config.getShortDateWithoutYearPattern(), locale);
        this.dayOfWeekFormatter = DateTimeFormatter.ofPattern(config.getDayOfWeekPattern(), locale);
        this.dateWithDayOfWeekFormatter = DateTimeFormatter.ofPattern(config.getDateWithDayOfWeekPattern(), locale);

        this.dateParseFormatters = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd.MM.yy"),
                DateTimeFormatter.ofPattern("dd/MM/yy")
        };
        
        this.timeParseFormatters = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("H:mm")
        };

        this.utc = ZoneId.of("UTC");
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
}
