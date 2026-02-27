package ru.golubyatnikov.family.calendar.bot.handler.callback.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;

/**
 * Роутер callback queries для управления напоминаниями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderCallbackRouter implements CallbackHandler {
    
    private final ReminderCallbackHandler reminderCallbackHandler;
    private final CallbackDataExtractionService callbackDataExtractionService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.DISABLE_REMINDERS;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return CallbackPrefix.DISABLE_REMINDERS.matches(callbackData) ||
               CallbackPrefix.ENABLE_REMINDERS.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        // Отключение автоматических напоминаний
        if (CallbackPrefix.DISABLE_REMINDERS.matches(context.callbackData())) {
            String payload = CallbackPrefix.DISABLE_REMINDERS.extractPayload(context.callbackData());
            reminderCallbackHandler.handleDisableReminders(payload, context);
        }
        
        // Включение автоматических напоминаний
        else if (CallbackPrefix.ENABLE_REMINDERS.matches(context.callbackData())) {
            String payload = CallbackPrefix.ENABLE_REMINDERS.extractPayload(context.callbackData());
            reminderCallbackHandler.handleEnableReminders(payload, context);
        }
    }
}
