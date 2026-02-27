package ru.golubyatnikov.family.calendar.bot.handler.callback.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderCreationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import ru.golubyatnikov.family.calendar.bot.util.TelegramExceptionUtil;
import java.util.List;

/**
 * Обработчик callback-запросов для управления напоминаниями о событиях.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderCallbackHandler {
    
    private final ReminderCreationService reminderCreationService;
    private final TelegramMessageService messageService;
    private final EventRepository eventRepository;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    
    /**
     * Обрабатывает отключение всех автоматических напоминаний для события.
     * Поддерживает контекст постраничного списка /my_events.
     */
    public void handleDisableReminders(String payload, CallbackQueryContext context) {
        try {
            // Извлекаем eventId и опциональный page из payload
            String[] parts = payload.split("_");
            Long eventId = Long.parseLong(parts[0]);
            Integer page = parts.length > 1 ? Integer.parseInt(parts[1]) : null;
            
            reminderCreationService.disableRemindersForEvent(eventId);
            answerCallbackQuery(context, CallbackMessages.SUCCESS);

            updateEventMessage(eventId, context.chatId(), context.messageId(), page);

        } catch (Exception e) {
            log.error("Ошибка при отключении напоминаний: payload={}, chatId={}, error={}, stackTrace={}", 
                    payload, context.chatId(), e.getMessage(), TelegramExceptionUtil.getStackTraceString(e), e);

            answerCallbackQuery(context, CallbackMessages.ERROR);
        }
    }
    
    /**
     * Обрабатывает включение автоматических напоминаний для события.
     * Поддерживает контекст постраничного списка /my_events.
     */
    public void handleEnableReminders(String payload, CallbackQueryContext context) {
        try {
            // Извлекаем eventId и опциональный page из payload
            String[] parts = payload.split("_");
            Long eventId = Long.parseLong(parts[0]);
            Integer page = parts.length > 1 ? Integer.parseInt(parts[1]) : null;
            
            Event event = eventRepository.findByIdWithUser(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
            
            User user = event.getUser();
            if (user == null) {
                answerCallbackQuery(context, CallbackMessageFormatter.notFound("Пользователь"));
                return;
            }
            
            List<Reminder> createdReminders = reminderCreationService.createDefaultReminders(event, user);
            
            String responseMessage = createdReminders.isEmpty() 
                ? CallbackMessages.REMINDER_TOO_SOON 
                : CallbackMessages.SUCCESS;
            
            answerCallbackQuery(context, responseMessage);

            updateEventMessage(eventId, context.chatId(), context.messageId(), page);

        } catch (Exception e) {
            log.error("Ошибка при включении напоминаний: payload={}, chatId={}, error={}, stackTrace={}", 
                    payload, context.chatId(), e.getMessage(), TelegramExceptionUtil.getStackTraceString(e), e);

            answerCallbackQuery(context, CallbackMessages.ERROR);
        }
    }
    
    /**
     * Обновляет сообщение события с новой клавиатурой.
     * Поддерживает контекст постраничного списка /my_events.
     * 
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param page номер страницы (null если не из /my_events)
     */
    private void updateEventMessage(Long eventId, Long chatId, Integer messageId, Integer page) {
        try {
            Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
            User user = event.getUser();
            
            int eventCount = eventService.getActiveEventsCount(user.getId());
            String messageText = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
            
            // Используем клавиатуру с контекстом страницы если он есть
            InlineKeyboardMarkup keyboard;
            if (page != null) {
                keyboard = keyboardService.createEventActionsKeyboardWithContext(event, user.getId(), page);

            } else {
                keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
            }
            
            messageService.editMessageText(chatId, messageId, messageText, keyboard);
            
        } catch (Exception e) {
            log.warn("Не удалось обновить сообщение события: eventId={}, messageId={}, error={}", 
                    eventId, messageId, e.getMessage());
        }
    }
    
    /**
     * Безопасно отвечает на callback query с обработкой исключений.
     */
    private void answerCallbackQuery(CallbackQueryContext context, String text) {
        try {
            messageService.answerCallbackQuery(context.callbackQueryId(), text);

        } catch (Exception e) {
            log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                    context.callbackQueryId(), e.getMessage(), TelegramExceptionUtil.getStackTraceString(e), e);
        }
    }
}
