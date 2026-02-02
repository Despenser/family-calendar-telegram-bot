package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.EventNotificationService;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Обработчик завершения события.
 * 
 * <p>Обрабатывает завершение события с переупорядочиванием списка,
 * добавление заметки о завершении и пропуск заметки.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 2.1, 2.2, 2.3, 3.3</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCompletionHandler implements CallbackHandler {
    
    private final EventService eventService;
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final EventNotificationService eventNotificationService;
    private final BotMessageBuilder botMessageBuilder;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.COMPLETE_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        return CallbackPrefix.COMPLETE_EVENT.matches(callbackData) ||
               CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData) ||
               CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData);
    }
    
    @Override
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        Long userId = user.getId();
        
        if (CallbackPrefix.COMPLETE_EVENT.matches(callbackData)) {
            handleCompleteEvent(callbackData, userId, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData)) {
            handleAddCompletionNote(callbackData, userId, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData)) {
            handleSkipCompletionNote(userId, chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает завершение события с переупорядочиванием списка.
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 2.1</p>
     * 
     * @param callbackData данные callback (формат: complete_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleCompleteEvent(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.COMPLETE_EVENT);
        
        log.debug("Начало обработки завершения события с переупорядочиванием: eventId={}, userId={}", 
                 eventId, userId);
        
        try {
            // Завершаем событие с переупорядочиванием списка
            Event completedEvent = eventService.completeEventWithReordering(eventId, userId);
            
            log.info("Событие ID={} успешно завершено с переупорядочиванием пользователем ID={}", 
                    eventId, userId);
            
            // Сохраняем контекст для добавления заметки
            Integer updatedMessageId = completedEvent.getMessageId() != null 
                ? completedEvent.getMessageId().intValue() 
                : null;
            
            conversationStateService.setAwaitingCompletionNote(
                userId, 
                eventId, 
                chatId, 
                updatedMessageId
            );
            
            log.debug("Контекст сохранён для добавления заметки: eventId={}, messageId={}, userId={}", 
                     eventId, updatedMessageId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (EventNotFoundException e) {
            log.error("Событие не найдено: eventId={}, userId={}", eventId, userId, e);
        } catch (UnauthorizedAccessException e) {
            log.error("Нет прав на завершение события: eventId={}, userId={}", eventId, userId, e);
        } catch (IllegalStateException e) {
            log.error("Неверное состояние события: eventId={}, userId={}", eventId, userId, e);
        } catch (Exception e) {
            log.error("Ошибка при завершении события с переупорядочиванием: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Добавить заметку" к завершенному событию.
     * 
     * <p><b>Требования:</b> 1.2, 2.2</p>
     * 
     * @param callbackData данные callback (формат: add_completion_note_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query
     */
    private void handleAddCompletionNote(String callbackData, Long userId, Long chatId, 
                                        Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.ADD_COMPLETION_NOTE);
        
        log.debug("Начало обработки добавления заметки к событию: eventId={}, userId={}, messageId={}", 
                 eventId, userId, messageId);
        
        try {
            // Формируем сообщение с просьбой ввести заметку
            String message = formatMessage(
                "📝 Напишите заметку о том, как прошло событие.\n\n" +
                "Например, что было сделано, какие были результаты или впечатления."
            );
            
            try {
                // Пытаемся отредактировать текущее сообщение
                messageService.editMessageText(chatId, messageId, message, null);
                
                // Устанавливаем состояние ожидания заметки с messageId
                conversationStateService.setAwaitingCompletionNote(userId, eventId, chatId, messageId);
                
                log.info("Пользователь ID={} переведен в режим ожидания заметки для события ID={}, messageId={}", 
                        userId, eventId, messageId);
                
            } catch (TelegramApiException e) {
                // Fallback: если редактирование не удалось, отправляем новое сообщение
                log.warn("Ошибка редактирования сообщения при добавлении заметки, используем fallback: eventId={}, messageId={}, error={}", 
                        eventId, messageId, e.getMessage());
                
                messageService.sendMessage(chatId, message);
                
                // Устанавливаем состояние ожидания заметки без messageId
                conversationStateService.setAwaitingCompletionNote(userId, eventId, chatId, null);
                
                log.info("Пользователь ID={} переведен в режим ожидания заметки для события ID={} (fallback без messageId)", 
                        userId, eventId);
            }
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API при обработке добавления заметки: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            
            // Очищаем контекст при критической ошибке
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            throw new RuntimeException("Ошибка при обработке добавления заметки", e);
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Пропустить" при добавлении заметки.
     * 
     * <p><b>Требования:</b> 2.3, 3.3</p>
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query
     */
    private void handleSkipCompletionNote(Long userId, Long chatId, Integer messageId, 
                                         String callbackQueryId) {
        log.info("Пользователь ID={} пропустил добавление заметки к завершенному событию", userId);
        
        try {
            // Получаем контекст для доступа к eventId
            ConversationStateService.CompletionNoteContext context = 
                conversationStateService.getCompletionNoteContext(userId);
            
            if (context == null) {
                log.warn("Контекст добавления заметки не найден для пользователя ID={}", userId);
                
                // Очищаем состояние на всякий случай
                conversationStateService.clearAwaitingCompletionNote(userId);
                
                // Отправляем сообщение об ошибке
                String errorMessage = formatMessage("❌ Время ожидания истекло. Попробуйте снова.");
                messageService.sendMessage(chatId, errorMessage);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
                return;
            }
            
            Long eventId = context.getEventId();
            Integer contextMessageId = context.getMessageId();
            
            log.debug("Получен контекст для пропуска заметки: eventId={}, messageId={}, userId={}", 
                     eventId, contextMessageId, userId);
            
            // Получаем событие
            Event event = eventService.getEventById(eventId);
            
            // Формируем финальное сообщение с карточкой события
            String eventMessage = botMessageBuilder.buildCompletedEventMessage(event);
            
            // Используем messageId из контекста, если он есть, иначе из callback query
            Integer targetMessageId = contextMessageId != null ? contextMessageId : messageId;
            
            try {
                // Пытаемся отредактировать сообщение
                if (targetMessageId != null) {
                    messageService.editMessageText(chatId, targetMessageId, eventMessage, null);
                    
                    log.info("Сообщение отредактировано при пропуске заметки: eventId={}, messageId={}, userId={}", 
                            eventId, targetMessageId, userId);
                } else {
                    // Fallback: если messageId отсутствует, отправляем новое сообщение
                    log.warn("MessageId отсутствует при пропуске заметки, отправляем новое сообщение: eventId={}, userId={}", 
                            eventId, userId);
                    messageService.sendMessage(chatId, eventMessage);
                }
                
            } catch (TelegramApiException e) {
                // Fallback: если редактирование не удалось, отправляем новое сообщение
                log.warn("Ошибка редактирования сообщения при пропуске заметки, используем fallback: eventId={}, messageId={}, error={}", 
                        eventId, targetMessageId, e.getMessage());
                
                messageService.sendMessage(chatId, eventMessage);
            }
            
            // Очищаем контекст
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            log.info("Контекст очищен после пропуска заметки: userId={}, eventId={}", userId, eventId);
            
            // Обновляем шапку /my_events после отображения карточки события
            eventNotificationService.updateMyEventsHeaderAfterRemoval(userId);
            
            log.info("Шапка /my_events обновлена после пропуска заметки к событию ID={}: userId={}", 
                    eventId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (EventNotFoundException e) {
            log.error("Событие не найдено при пропуске заметки: userId={}", userId, e);
            
            // Очищаем контекст при ошибке
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            // Отправляем сообщение об ошибке
            try {
                String errorMessage = formatMessage("❌ Событие не найдено.");
                messageService.sendMessage(chatId, errorMessage);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            } catch (TelegramApiException ex) {
                log.error("Ошибка отправки сообщения об ошибке: userId={}", userId, ex);
            }
            
        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API при пропуске заметки: userId={}, error={}", 
                     userId, e.getMessage(), e);
            
            // Очищаем контекст при критической ошибке
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            throw new RuntimeException("Ошибка при пропуске заметки", e);
        }
    }
    
    /**
     * Извлекает ID события из callback data.
     * 
     * @param callbackData строка callback data
     * @param prefix префикс для извлечения payload
     * @return ID события
     */
    private Long extractEventId(String callbackData, CallbackPrefix prefix) {
        String payload = prefix.extractPayload(callbackData);
        return Long.parseLong(payload);
    }
}
