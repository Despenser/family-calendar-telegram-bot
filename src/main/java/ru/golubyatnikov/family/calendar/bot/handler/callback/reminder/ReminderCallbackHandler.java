package ru.golubyatnikov.family.calendar.bot.handler.callback.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
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
     */
    public void handleDisableReminders(Long eventId, CallbackQueryContext context) {
        log.debug("Отключение автоматических напоминаний для события ID={}", eventId);
        
        try {
            reminderCreationService.disableRemindersForEvent(eventId);
            answerCallbackQuery(context, CallbackMessages.SUCCESS);

            updateEventMessage(eventId, context.chatId(), context.messageId());
            log.info("Автоматические напоминания отключены для события ID={}", eventId);
            
        } catch (Exception e) {
            log.error("Ошибка при отключении напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                    eventId, context.chatId(), e.getMessage(), TelegramExceptionUtil.getStackTraceString(e), e);

            answerCallbackQuery(context, CallbackMessages.ERROR);
        }
    }
    
    /**
     * Обрабатывает включение автоматических напоминаний для события.
     */
    public void handleEnableReminders(Long eventId, CallbackQueryContext context) {
        log.debug("Включение автоматических напоминаний для события ID={}", eventId);
        
        try {
            Event event = eventRepository.findByIdWithUser(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
            
            User user = event.getUser();
            if (user == null) {
                log.error("User is null для события ID {}", eventId);
                answerCallbackQuery(context, CallbackMessageFormatter.notFound("Пользователь"));
                return;
            }
            
            List<Reminder> createdReminders = reminderCreationService.createDefaultReminders(event, user);
            
            String responseMessage = createdReminders.isEmpty() 
                ? CallbackMessages.REMINDER_TOO_SOON 
                : CallbackMessages.SUCCESS;
            
            answerCallbackQuery(context, responseMessage);

            updateEventMessage(eventId, context.chatId(), context.messageId());
            log.info("Автоматические напоминания включены для события ID={}, создано напоминаний: {}", 
                    eventId, createdReminders.size());
            
        } catch (LazyInitializationException e) {
            log.error("LazyInitializationException при включении напоминаний: eventId={}, chatId={}, error={}", 
                    eventId, context.chatId(), e.getMessage(), e);

            answerCallbackQuery(context, CallbackMessages.ERROR);

        } catch (Exception e) {
            log.error("Ошибка при включении напоминаний: eventId={}, chatId={}, error={}, stackTrace={}", 
                    eventId, context.chatId(), e.getMessage(), TelegramExceptionUtil.getStackTraceString(e), e);

            answerCallbackQuery(context, CallbackMessages.ERROR);
        }
    }
    
    /**
     * Обновляет сообщение события с новой клавиатурой.
     * 
     * @param eventId идентификатор события
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     */
    private void updateEventMessage(Long eventId, Long chatId, Integer messageId) {
        try {
            Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
            User user = event.getUser();
            
            int eventCount = eventService.getActiveEventsCount(user.getId());
            String messageText = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
            
            messageService.editMessageText(chatId, messageId, messageText, keyboard);
            
            log.debug("Сообщение события обновлено: eventId={}, messageId={}", eventId, messageId);

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
