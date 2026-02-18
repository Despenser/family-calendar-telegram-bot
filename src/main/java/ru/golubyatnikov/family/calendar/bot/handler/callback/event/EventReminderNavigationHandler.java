package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderNotificationService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.domain.user.UserService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import java.time.ZoneId;

/**
 * Обработчик навигации между событием и напоминанием.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class EventReminderNavigationHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final CallbackQueryService callbackQueryService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final ReminderSchedulingService reminderSchedulingService;
    private final ReminderNotificationService reminderNotificationService;
    private final UserService userService;
    private final KeyboardFactory keyboardFactory;

    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.VIEW_EVENT_FROM_REMINDER;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        return CallbackPrefix.VIEW_EVENT_FROM_REMINDER.matches(callbackData) ||
               CallbackPrefix.BACK_TO_REMINDER.matches(callbackData);
    }
    
    @Override
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        if (CallbackPrefix.VIEW_EVENT_FROM_REMINDER.matches(context.callbackData())) {
            handleViewEventFromReminder(context);

        } else if (CallbackPrefix.BACK_TO_REMINDER.matches(context.callbackData())) {
            handleBackToReminder(context);
        }
    }

    /**
     * Record для хранения ID события и напоминания.
     */
    private record ReminderEventIds(Long eventId, Long reminderId) {}
    
    /**
     * Обрабатывает просмотр деталей события из уведомления о напоминании.
     */
    private void handleViewEventFromReminder(@NonNull CallbackQueryContext context) {
        try {
            ReminderEventIds ids = extractReminderEventIds(context);
            if (ids == null) {
                return;
            }

            Reminder reminder = reminderSchedulingService.getReminderWithEventAndUser(ids.reminderId());
            
            User recipient = userService.findById(context.getUserId())
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: userId=" + context.getUserId()));
            
            ZoneId userTimezone = getTimezone(recipient);
            
            String eventMessage = reminderNotificationService.formatReminderMessageByType(reminder, userTimezone);
            InlineKeyboardMarkup keyboard = createDetailsKeyboard(ids.eventId(), ids.reminderId());
            
            messageService.editMessageText(context.chatId(), context.messageId(), eventMessage, keyboard);
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
        } catch (EventNotFoundException e) {
            log.warn("Событие не найдено при просмотре деталей из напоминания: userId={}", context.getUserId(), e);
            answerCallbackQuerySafely(context, CallbackMessageFormatter.notFound("Событие"));

        } catch (TelegramApiException e) {
            log.warn("Ошибка Telegram API при просмотре деталей из напоминания: " +
                    "messageId={}, userId={}, error={}", context.messageId(), context.getUserId(), e.getMessage());

            answerCallbackQuerySafely(context, CallbackMessages.ERROR);

        } catch (Exception e) {
            log.error("Неожиданная ошибка при просмотре деталей из напоминания: " +
                     "userId={}, error={}", context.getUserId(), e.getMessage(), e);
                     
            answerCallbackQuerySafely(context, CallbackMessages.ERROR);
        }
    }
    
    /**
     * Обрабатывает возврат к минималистичному виду напоминания.
     */
    private void handleBackToReminder(@NonNull CallbackQueryContext context) {
        try {
            ReminderEventIds ids = extractReminderEventIds(context);
            if (ids == null) {
                return;
            }
            
            Reminder reminder = reminderSchedulingService.getReminderWithEventAndUser(ids.reminderId());
            Event event = reminder.getEvent();
            
            ZoneId creatorTimezone = getTimezone(event.getUser());
            
            String reminderMessage = reminderNotificationService.formatShortReminderMessage(reminder, creatorTimezone);
            InlineKeyboardMarkup keyboard = reminderNotificationService.createSimplifiedReminderKeyboard(event, ids.reminderId());
            
            messageService.editMessageText(context.chatId(), context.messageId(), reminderMessage, keyboard);
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
        } catch (ReminderNotFoundException e) {
            log.warn("Напоминание не найдено при возврате к напоминанию: userId={}", context.getUserId(), e);
            answerCallbackQuerySafely(context, CallbackMessageFormatter.notFound("Напоминание"));

        } catch (TelegramApiException e) {
            log.warn("Ошибка Telegram API при возврате к напоминанию: messageId={}, userId={}, error={}", 
                    context.messageId(), context.getUserId(), e.getMessage());

            answerCallbackQuerySafely(context, CallbackMessages.ERROR);

        } catch (Exception e) {
            log.error("Неожиданная ошибка при возврате к напоминанию: userId={}, error={}", 
                     context.getUserId(), e.getMessage(), e);

            answerCallbackQuerySafely(context, CallbackMessages.ERROR);
        }
    }
    
    /**
     * Извлекает ID события и напоминания из callback data.
     */
    private @Nullable ReminderEventIds extractReminderEventIds(@NonNull CallbackQueryContext context) {
        CallbackPrefix prefix = context.callbackData().startsWith("view_event_from_reminder_") 
            ? CallbackPrefix.VIEW_EVENT_FROM_REMINDER 
            : CallbackPrefix.BACK_TO_REMINDER;
            
        String payload = prefix.extractPayload(context.callbackData());
        String[] parts = payload.split("_", 2);
        
        if (parts.length != 2) {
            answerCallbackQuerySafely(context, CallbackMessages.INVALID_REQUEST);
            return null;
        }
        
        try {
            Long eventId = Long.parseLong(parts[0]);
            Long reminderId = Long.parseLong(parts[1]);
            return new ReminderEventIds(eventId, reminderId);

        } catch (NumberFormatException e) {
            log.error("Некорректный eventId или reminderId в callback data: " +
                     "eventId='{}', reminderId='{}', callbackData='{}', userId={}, error={}", 
                     parts[0], parts[1], context.callbackData(), context.getUserId(), e.getMessage());

            answerCallbackQuerySafely(context, CallbackMessages.INVALID_REQUEST);
            return null;
        }
    }
    
    /**
     * Получает timezone пользователя.
     */
    private @NonNull ZoneId getTimezone(@NonNull User user) {
        return user.getTimezone() != null 
            ? ZoneId.of(user.getTimezone()) 
            : ZoneId.of("UTC");
    }
    
    /**
     * Безопасно отвечает на callback query.
     */
    private void answerCallbackQuerySafely(CallbackQueryContext context, String message) {
        callbackQueryService.answerCallback(context, message);
    }
    
    /**
     * Создает клавиатуру для просмотра деталей события из напоминания.
     */
    private @NonNull InlineKeyboardMarkup createDetailsKeyboard(Long eventId, Long reminderId) {
        InlineKeyboardButton button = keyboardFactory.createButton(
                "🔙 Скрыть детали",
                CallbackPrefix.BACK_TO_REMINDER.withPayload(eventId + "_" + reminderId)
        );
        InlineKeyboardRow row = keyboardFactory.createRow(button);
        return keyboardFactory.createMarkup(row);
    }
}
