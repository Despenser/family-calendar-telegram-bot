package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.enums.ReminderType;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderConfigurationService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.EventType.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Reminders.ENABLED;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Time.CLOCK;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Сервис форматирования сообщений для напоминаний.
 * Отвечает за создание текстов уведомлений о напоминаниях.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReminderMessageFormattingService {
    
    private final ReminderConfigurationService reminderConfigurationService;
    private final DateTimeFormattingService dateTimeFormattingService;
    
    /**
     * Форматирует короткую версию уведомления о напоминании.
     * 
     * @param reminder напоминание
     * @param recipientTimezone часовой пояс получателя
     *
     * @return короткое отформатированное сообщение
     */
    public String formatShortMessage(Reminder reminder, ZoneId recipientTimezone) {
        try {
            return buildShortMessage(reminder, recipientTimezone);

        } catch (Exception e) {
            return handleFormattingError(e, reminder, recipientTimezone, this::buildShortMessage);
        }
    }

    /**
     * Форматирует полную версию уведомления о напоминании.
     *
     * @param reminder напоминание
     * @param recipientTimezone часовой пояс получателя
     *
     * @return полное отформатированное сообщение
     */
    public String formatFullMessage(Reminder reminder, ZoneId recipientTimezone) {
        try {
            return buildFullMessage(reminder, recipientTimezone);

        } catch (Exception e) {
            return handleFormattingError(e, reminder, recipientTimezone, this::buildFullMessage);
        }
    }
    
    private @NonNull String buildShortMessage(@NonNull Reminder reminder, ZoneId timezone) {
        Event event = reminder.getEvent();
        ZonedDateTime eventTime = createEventZonedDateTime(event, timezone);
        String formattedTime = dateTimeFormattingService.formatTime(eventTime.toLocalTime());
        
        String prefix = getReminderPrefix(reminder.getReminderType(), formattedTime);
        return prefix + bold(event.getTitle());
    }

    private @NonNull String buildFullMessage(@NonNull Reminder reminder, ZoneId recipientTimezone) {
        Event event = reminder.getEvent();
        ZoneId creatorTimezone = reminderConfigurationService.getUserTimezone(event.getUser());

        ZonedDateTime eventInCreatorTZ = createEventZonedDateTime(event, creatorTimezone);
        ZonedDateTime eventInRecipientTZ = eventInCreatorTZ.withZoneSameInstant(recipientTimezone);

        return buildFullMessageContent(reminder, event, eventInRecipientTZ);
    }
    
    private @NonNull String buildFullMessageContent(Reminder reminder,
                                                    @NonNull
                                                    Event event,
                                                    @NonNull ZonedDateTime eventTime) {

        StringBuilder message = new StringBuilder();
        
        message.append(ENABLED + " ").append(bold("Напоминание о событии")).append("\n\n");
        message.append(formatMessage(TITLE + " Событие: %s\n", event.getTitle()));
        message.append(formatMessage(DATE + " Дата: %s\n", dateTimeFormattingService.formatDate(eventTime.toLocalDate())));
        message.append(formatMessage(TIME + " Время: %s\n", dateTimeFormattingService.formatTime(eventTime.toLocalTime())));
        
        appendEventType(message, event);
        appendDescription(message, event);
        appendReminderType(message, reminder);
        
        return message.toString();
    }
    
    private @NonNull String getReminderPrefix(@NonNull ReminderType type, String formattedTime) {
        return switch (type) {
            case EVENING_BEFORE -> MOON + " " + bold("Напоминание: завтра в " + formattedTime + " у вас событие - ");
            case ONE_HOUR_BEFORE -> LIGHTNING + " " + bold("Напоминание: через 1 час начнется событие - ");
            case FIFTEEN_MINUTES_BEFORE -> FIRE + " " + bold("Напоминание: через 15 минут начнется событие - ");
        };
    }
    
    private @NonNull ZonedDateTime createEventZonedDateTime(@NonNull Event event, ZoneId timezone) {
        return ZonedDateTime.of(event.getEventDate(), event.getEventTime(), timezone);
    }
    
    private void appendEventType(StringBuilder message, @NonNull Event event) {
        if (event.getIsPersonal()) {
            message.append(PERSONAL + " Тип: Персональное\n");
        } else {
            message.append(FAMILY + " Тип: Семейное\n");
        }
    }
    
    private void appendDescription(StringBuilder message, @NonNull Event event) {
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            message.append(formatMessage(DESCRIPTION + " Описание: %s\n", event.getDescription()));
        }
    }
    
    private void appendReminderType(@NonNull StringBuilder message, @NonNull Reminder reminder) {
        String reminderTypeText = getReminderTypeText(reminder.getReminderType());
        message.append(formatMessage("\n" + CLOCK + " Напоминание: %s", reminderTypeText));
    }
    
    private @NonNull String getReminderTypeText(@NonNull ReminderType type) {
        return switch (type) {
            case EVENING_BEFORE -> "накануне вечером";
            case ONE_HOUR_BEFORE -> "за 1 час до события";
            case FIFTEEN_MINUTES_BEFORE -> "за 15 минут до события";
        };
    }
    
    private String handleFormattingError(Exception e, 
                                        @NonNull Reminder reminder,
                                        ZoneId timezone,
                                        MessageBuilder builder) {

        log.error("Ошибка форматирования напоминания ID {} в timezone {}: {}", 
                 reminder.getId(), timezone, e.getMessage(), e);
        
        if (dateTimeFormattingService.getUtc().equals(timezone)) {
            return ENABLED + " " + bold("Напоминание о событии - " + reminder.getEvent().getTitle());
        }
        
        return builder.build(reminder, dateTimeFormattingService.getUtc());
    }
    
    @FunctionalInterface
    private interface MessageBuilder {
        String build(Reminder reminder, ZoneId timezone);
    }
}
