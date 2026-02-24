package ru.golubyatnikov.family.calendar.bot.service.domain.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.ReminderMessageFormattingService;
import java.time.ZoneId;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.EYE;

/**
 * Отправитель напоминаний.
 * Отвечает за отправку уведомлений пользователям.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReminderSender {
    
    private final TelegramMessageService telegramMessageService;
    private final ReminderConfigurationService reminderConfigurationService;
    private final ReminderMessageFormattingService messageFormatter;
    private final KeyboardFactory keyboardFactory;
    private final DateTimeFormattingService dateTimeFormattingService;
    
    /**
     * Отправляет уведомление о напоминании.
     * 
     * @param reminder напоминание для отправки
     * @throws TelegramApiException если не удалось отправить уведомление
     */
    public void sendNotification(@NonNull Reminder reminder) throws TelegramApiException {
        Event event = reminder.getEvent();
        if (event.getIsPersonal()) {
            sendToUser(reminder, event.getUser());

        } else {
            sendToFamily(reminder, event);
        }
    }
    
    /**
     * Создает клавиатуру для уведомления о напоминании.
     * 
     * @param event событие
     * @param reminderId идентификатор напоминания
     *
     * @return inline-клавиатура
     */
    public InlineKeyboardMarkup createKeyboard(@NonNull Event event, Long reminderId) {
        InlineKeyboardButton button = keyboardFactory.createButton(EYE + " Посмотреть детали",
                CallbackPrefix.VIEW_EVENT_FROM_REMINDER.withPayload(event.getId() + "_" + reminderId)
        );
        InlineKeyboardRow row = keyboardFactory.createRow(button);
        return keyboardFactory.createMarkup(row);
    }
    
    private void sendToUser(@NonNull Reminder reminder, @NonNull User user) throws TelegramApiException {
        try {
            ZoneId timezone = reminderConfigurationService.getUserTimezone(user);
            sendMessage(reminder, user, timezone);

        } catch (Exception e) {
            handleSendError(e, reminder, user);
        }
    }
    
    private void sendToFamily(Reminder reminder, @NonNull Event event) {
        if (event.getFamily() == null || event.getFamily().getMembers() == null) {
            return;
        }

        event.getFamily().getMembers().forEach(member -> {
            try {
                ZoneId timezone = reminderConfigurationService.getUserTimezone(member);
                sendMessage(reminder, member, timezone);

            } catch (Exception e) {
                log.error("Ошибка отправки напоминания члену семьи ID {}: {}", member.getId(), e.getMessage(), e);
            }
        });
    }
    
    private void sendMessage(Reminder reminder, @NonNull User user, ZoneId timezone) throws TelegramApiException {
        String message = messageFormatter.formatShortMessage(reminder, timezone);
        var keyboard = createKeyboard(reminder.getEvent(), reminder.getId());
        telegramMessageService.sendMessageWithInlineKeyboard(user.getTelegramId(), message, keyboard);
    }
    
    private void handleSendError(Exception e, Reminder reminder, @NonNull User user) throws TelegramApiException {
        log.error("Ошибка отправки напоминания пользователю ID {}: {}", 
                 user.getId(), e.getMessage(), e);
        
        if (e instanceof TelegramApiException) {
            throw (TelegramApiException) e;
        }

        try {
            sendMessage(reminder, user, dateTimeFormattingService.getUtc());

        } catch (Exception fallbackError) {
            log.error("Критическая ошибка отправки с UTC для пользователя ID {}: {}", 
                     user.getId(), fallbackError.getMessage(), fallbackError);

            if (fallbackError instanceof TelegramApiException) {
                throw (TelegramApiException) fallbackError;
            }

            throw new RuntimeException(fallbackError);
        }
    }
}
