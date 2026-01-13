package ru.golubyatnikov.family.calendar.bot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для парсинга событий из текстовых сообщений.
 * Поддерживает различные форматы ввода для быстрого создания событий.
 * 
 * <p>Примеры поддерживаемых форматов:
 * <ul>
 *   <li>"Событие: Встреча Дата: 15.01.2026 Время: 14:30"</li>
 *   <li>"Встреча 15.01.2026 14:30"</li>
 *   <li>"Встреча завтра в 14:30"</li>
 *   <li>"Встреча сегодня в 14:30"</li>
 * </ul>
 * 
 * @author Kiro AI Assistant
 * @since 1.0
 */
@Slf4j
@Component
public class TextEventParser {

    // Паттерн для формата "Событие: [название] Дата: [дата] Время: [время]"
    private static final Pattern STRUCTURED_PATTERN = Pattern.compile(
            "(?:Событие|событие|Event|event):\\s*(.+?)\\s+(?:Дата|дата|Date|date):\\s*(.+?)\\s+(?:Время|время|Time|time):\\s*(.+)",
            Pattern.CASE_INSENSITIVE
    );

    // Паттерн для формата "[название] [дата] [время]"
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
            "(.+?)\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})\\s+(\\d{1,2}:\\d{2})"
    );

    // Паттерн для формата "[название] завтра/сегодня в [время]"
    private static final Pattern RELATIVE_DATE_PATTERN = Pattern.compile(
            "(.+?)\\s+(завтра|сегодня|today|tomorrow)\\s+(?:в|at)\\s+(\\d{1,2}:\\d{2})",
            Pattern.CASE_INSENSITIVE
    );

    // Форматы дат для парсинга
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yy"),
            DateTimeFormatter.ofPattern("dd/MM/yy")
    };

    // Форматы времени для парсинга
    private static final DateTimeFormatter[] TIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm")
    };

    /**
     * Результат парсинга текстового сообщения.
     */
    public static class ParsedEvent {
        private final String title;
        private final LocalDate date;
        private final LocalTime time;

        public ParsedEvent(String title, LocalDate date, LocalTime time) {
            this.title = title;
            this.date = date;
            this.time = time;
        }

        public String getTitle() {
            return title;
        }

        public LocalDate getDate() {
            return date;
        }

        public LocalTime getTime() {
            return time;
        }

        /**
         * Проверяет валидность распознанного события.
         * 
         * @return true если все поля заполнены и дата не в прошлом
         */
        public boolean isValid() {
            if (title == null || title.trim().isEmpty()) {
                return false;
            }
            if (date == null || time == null) {
                return false;
            }
            // Проверяем, что дата не в прошлом
            LocalDate today = LocalDate.now();
            return !date.isBefore(today);
        }

        @Override
        public String toString() {
            return String.format("Событие: %s\nДата: %s\nВремя: %s",
                    title,
                    date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    time.format(DateTimeFormatter.ofPattern("HH:mm")));
        }
    }

    /**
     * Парсит текстовое сообщение и извлекает информацию о событии.
     * 
     * @param text текстовое сообщение от пользователя
     * @return Optional с распознанным событием или empty если не удалось распознать
     */
    public Optional<ParsedEvent> parseEvent(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("Пустой текст для парсинга");
            return Optional.empty();
        }

        text = text.trim();
        log.debug("Парсинг текста: {}", text);

        // Пробуем структурированный формат
        Optional<ParsedEvent> result = tryStructuredFormat(text);
        if (result.isPresent()) {
            log.info("Распознано событие в структурированном формате: {}", result.get());
            return result;
        }

        // Пробуем формат с относительной датой
        result = tryRelativeDateFormat(text);
        if (result.isPresent()) {
            log.info("Распознано событие с относительной датой: {}", result.get());
            return result;
        }

        // Пробуем простой формат
        result = trySimpleFormat(text);
        if (result.isPresent()) {
            log.info("Распознано событие в простом формате: {}", result.get());
            return result;
        }

        log.debug("Не удалось распознать событие из текста: {}", text);
        return Optional.empty();
    }

    /**
     * Пробует распознать структурированный формат "Событие: ... Дата: ... Время: ...".
     */
    private Optional<ParsedEvent> tryStructuredFormat(String text) {
        Matcher matcher = STRUCTURED_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String title = matcher.group(1).trim();
        String dateStr = matcher.group(2).trim();
        String timeStr = matcher.group(3).trim();

        Optional<LocalDate> date = parseDate(dateStr);
        Optional<LocalTime> time = parseTime(timeStr);

        if (date.isPresent() && time.isPresent()) {
            return Optional.of(new ParsedEvent(title, date.get(), time.get()));
        }

        return Optional.empty();
    }

    /**
     * Пробует распознать простой формат "[название] [дата] [время]".
     */
    private Optional<ParsedEvent> trySimpleFormat(String text) {
        Matcher matcher = SIMPLE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String title = matcher.group(1).trim();
        String dateStr = matcher.group(2).trim();
        String timeStr = matcher.group(3).trim();

        Optional<LocalDate> date = parseDate(dateStr);
        Optional<LocalTime> time = parseTime(timeStr);

        if (date.isPresent() && time.isPresent()) {
            return Optional.of(new ParsedEvent(title, date.get(), time.get()));
        }

        return Optional.empty();
    }

    /**
     * Пробует распознать формат с относительной датой "[название] завтра/сегодня в [время]".
     */
    private Optional<ParsedEvent> tryRelativeDateFormat(String text) {
        Matcher matcher = RELATIVE_DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String title = matcher.group(1).trim();
        String relativeDateStr = matcher.group(2).trim().toLowerCase();
        String timeStr = matcher.group(3).trim();

        LocalDate date;
        if (relativeDateStr.equals("сегодня") || relativeDateStr.equals("today")) {
            date = LocalDate.now();
        } else if (relativeDateStr.equals("завтра") || relativeDateStr.equals("tomorrow")) {
            date = LocalDate.now().plusDays(1);
        } else {
            return Optional.empty();
        }

        Optional<LocalTime> time = parseTime(timeStr);

        if (time.isPresent()) {
            return Optional.of(new ParsedEvent(title, date, time.get()));
        }

        return Optional.empty();
    }

    /**
     * Парсит строку даты в различных форматах.
     * 
     * @param dateStr строка с датой
     * @return Optional с распознанной датой или empty
     */
    public Optional<LocalDate> parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return Optional.empty();
        }

        dateStr = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(dateStr, formatter);
                log.debug("Дата успешно распознана: {} -> {}", dateStr, date);
                return Optional.of(date);
            } catch (DateTimeParseException e) {
                // Пробуем следующий формат
            }
        }

        log.debug("Не удалось распознать дату: {}", dateStr);
        return Optional.empty();
    }

    /**
     * Парсит строку времени в различных форматах.
     * 
     * @param timeStr строка с временем
     * @return Optional с распознанным временем или empty
     */
    public Optional<LocalTime> parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return Optional.empty();
        }

        timeStr = timeStr.trim();

        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                LocalTime time = LocalTime.parse(timeStr, formatter);
                log.debug("Время успешно распознано: {} -> {}", timeStr, time);
                return Optional.of(time);
            } catch (DateTimeParseException e) {
                // Пробуем следующий формат
            }
        }

        log.debug("Не удалось распознать время: {}", timeStr);
        return Optional.empty();
    }

    /**
     * Проверяет, может ли текст быть распознан как событие.
     * 
     * @param text текстовое сообщение
     * @return true если текст похож на описание события
     */
    public boolean looksLikeEvent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        text = text.trim();

        // Проверяем наличие ключевых слов
        return STRUCTURED_PATTERN.matcher(text).find() ||
               SIMPLE_PATTERN.matcher(text).find() ||
               RELATIVE_DATE_PATTERN.matcher(text).find();
    }
}
