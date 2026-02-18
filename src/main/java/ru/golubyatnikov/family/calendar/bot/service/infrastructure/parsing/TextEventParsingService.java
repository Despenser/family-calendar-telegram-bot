package ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO вероятно надо будет удалить если будет GigaChat
 * Сервис для парсинга событий из текстовых сообщений.
 * Поддерживает различные форматы ввода для быстрого создания событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Slf4j
@Component
public class TextEventParsingService {

    /**
     * Паттерн для формата "Событие: [название] Дата: [дата] Время: [время]"
     */
    private static final Pattern STRUCTURED_PATTERN = Pattern.compile(
            "(?:Событие|событие|Event|event):\\s*(.+?)\\s+(?:Дата|дата|Date|date):\\s*(.+?)\\s+(?:Время|время|Time|time):\\s*(.+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Паттерн для формата "[название] [дата] [время]"
     */
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
            "(.+?)\\s+(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})\\s+(\\d{1,2}:\\d{2})"
    );

    /**
     * Паттерн для формата "[название] завтра/сегодня в [время]"
     */
    private static final Pattern RELATIVE_DATE_PATTERN = Pattern.compile(
            "(.+?)\\s+(завтра|сегодня|today|tomorrow)\\s+(?:в|at)\\s+(\\d{1,2}:\\d{2})",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Форматы дат для парсинга
     */
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yy"),
            DateTimeFormatter.ofPattern("dd/MM/yy")
    };

    /**
     * Форматы времени для парсинга
     */
    private static final DateTimeFormatter[] TIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm")
    };

    /**
     * Результат парсинга текстового сообщения.
     */
    public record ParsedEvent(String title, LocalDate date, LocalTime time) {

        /**
         * Проверяет валидность распознанного события.
         *
         * @return true, если все поля заполнены и дата не в прошлом
         */
        public boolean isValid() {
            if (title == null || title.trim().isEmpty()) {
                return false;
            }
            if (date == null || time == null) {
                return false;
            }
            LocalDate today = LocalDate.now();
            return !date.isBefore(today);
        }

        @Override
        public @NonNull String toString() {
            return String.format("Событие: %s\nДата: %s\nВремя: %s",
                    title,
                    date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    time.format(DateTimeFormatter.ofPattern("HH:mm")));
        }
    }

    /**
     * Стратегия парсинга для конкретного формата.
     */
    private record ParsingStrategy(
            Pattern pattern,
            String formatName,
            Function<Matcher, Optional<ParsedEvent>> parser) { }

    /**
     * Парсит текстовое сообщение и извлекает информацию о событии.
     *
     * @param text текстовое сообщение от пользователя
     * @return Optional с распознанным событием или empty если не удалось распознать
     */
    public Optional<ParsedEvent> parseEvent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Optional.empty();
        }

        text = text.trim();
        // Определяем стратегии парсинга в порядке приоритета
        ParsingStrategy[] strategies = {
                new ParsingStrategy(STRUCTURED_PATTERN, "структурированный", this::parseStructuredFormat),
                new ParsingStrategy(RELATIVE_DATE_PATTERN, "относительная дата", this::parseRelativeDateFormat),
                new ParsingStrategy(SIMPLE_PATTERN, "простой", this::parseSimpleFormat)
        };

        // Пробуем каждую стратегию
        for (ParsingStrategy strategy : strategies) {
            Matcher matcher = strategy.pattern.matcher(text);
            if (matcher.find()) {
                Optional<ParsedEvent> result = strategy.parser.apply(matcher);
                if (result.isPresent()) {
                    return result;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Парсит структурированный формат "Событие: ... Дата: ... Время: ...".
     */
    private Optional<ParsedEvent> parseStructuredFormat(Matcher matcher) {
        return parseStandardFormat(matcher);
    }

    /**
     * Парсит простой формат "[название] [дата] [время]".
     */
    private Optional<ParsedEvent> parseSimpleFormat(Matcher matcher) {
        return parseStandardFormat(matcher);
    }

    /**
     * Парсит формат с относительной датой "[название] завтра/сегодня в [время]".
     */
    private Optional<ParsedEvent> parseRelativeDateFormat(@NonNull Matcher matcher) {
        String title = matcher.group(1).trim();
        String relativeDateStr = matcher.group(2).trim().toLowerCase();
        String timeStr = matcher.group(3).trim();

        LocalDate date = parseRelativeDate(relativeDateStr);
        Optional<LocalTime> time = parseTime(timeStr);

        return time.map(localTime -> new ParsedEvent(title, date, localTime));
    }

    /**
     * Универсальный метод для парсинга форматов с явной датой.
     *
     * @param matcher matcher с найденными группами
     * @return Optional с распознанным событием или empty
     */
    private Optional<ParsedEvent> parseStandardFormat(@NonNull Matcher matcher) {
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
     * Парсит относительную дату
     *
     * @param relativeDateStr строка с относительной датой
     * @return LocalDate соответствующая относительной дате
     */
    private LocalDate parseRelativeDate(@NonNull String relativeDateStr) {
        return switch (relativeDateStr) {
            case "завтра", "tomorrow" -> LocalDate.now().plusDays(1);
            default -> LocalDate.now();
        };
    }

    /**
     * Парсит строку даты в различных форматах.
     *
     * @param dateStr строка с датой
     * @return Optional с распознанной датой или empty
     */
    private Optional<LocalDate> parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return Optional.empty();
        }

        dateStr = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(dateStr, formatter);
                return Optional.of(date);

            } catch (DateTimeParseException e) {
                // Пробуем следующий формат
            }
        }

        return Optional.empty();
    }

    /**
     * Парсит строку времени в различных форматах.
     *
     * @param timeStr строка с временем
     * @return Optional с распознанным временем или empty
     */
    private Optional<LocalTime> parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return Optional.empty();
        }

        timeStr = timeStr.trim();

        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                LocalTime time = LocalTime.parse(timeStr, formatter);
                return Optional.of(time);

            } catch (DateTimeParseException e) {
                // Пробуем следующий формат
            }
        }

        return Optional.empty();
    }

    /**
     * Проверяет, может ли текст быть распознан как событие.
     *
     * @param text текстовое сообщение
     * @return true, если текст похож на описание события
     */
    public boolean looksLikeEvent(String text) {
        return parseEvent(text).isPresent();
    }
}
