package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;

/**
 * Обработчик редактирования события.
 * 
 * <p>Обрабатывает начало редактирования события и отмену редактирования.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventEditHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final KeyboardService keyboardService;
    private final EventService eventService;
    private final BotMessageBuilder botMessageBuilder;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.EDIT_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        return CallbackPrefix.EDIT_EVENT.matches(callbackData) ||
               CallbackPrefix.EDIT_CANCEL.matches(callbackData);
    }
    
    @Override
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        if (CallbackPrefix.EDIT_EVENT.matches(callbackData)) {
            handleEditEvent(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.EDIT_CANCEL.matches(callbackData)) {
            handleEditCancel(callbackData, user.getId(), chatId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает редактирование события.
     * 
     * <p>Обновляет текущее сообщение, показывая меню выбора поля для редактирования.</p>
     * 
     * @param callbackData данные callback (формат: edit_event_{eventId})
     * @param user объект пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditEvent(String callbackData, User user, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
        Long userId = user.getId();
        Long eventId = extractEventId(callbackData, CallbackPrefix.EDIT_EVENT);
        
        log.info("Редактирование события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Получаем событие и проверяем права доступа
            Event event = eventService.getEventById(eventId);
            
            // Проверяем права доступа
            if (!event.getUser().getId().equals(userId)) {
                log.warn("Пользователь ID={} не имеет прав для редактирования события ID={}", 
                        userId, eventId);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.NO_ACCESS);
                return;
            }
            
            // Сохраняем messageId в контексте редактирования
            conversationStateService.startEventEditing(userId, eventId, chatId, messageId);
            
            // Формируем сообщение с текущими данными события и клавиатурой выбора поля
            String message = buildEditFieldSelectionMessage(event);
            InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId);
            
            // Обновляем текущее сообщение вместо отправки нового
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
            log.debug("Начато редактирование события ID={} в сообщении ID={} пользователем ID={}", 
                     eventId, messageId, userId);
            
        } catch (TelegramApiException e) {
            log.error("Ошибка при редактировании события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при редактировании события", e);
        }
    }
    
    /**
     * Обрабатывает отмену редактирования события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает messageId из EditingContext</li>
     *   <li>Очищает состояние редактирования</li>
     *   <li>Получает событие для отображения</li>
     *   <li>Обновляет то же сообщение через editMessageText с полной информацией о событии</li>
     *   <li>Если messageId не найден, использует fallback на sendOrUpdateEventMessage</li>
     * </ol>
     * 
     * @param callbackData данные callback (формат: edit_cancel_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditCancel(String callbackData, Long userId, Long chatId, String callbackQueryId) {
        String eventIdStr = CallbackPrefix.EDIT_CANCEL.extractPayload(callbackData);
        Long eventId = Long.parseLong(eventIdStr);
        
        log.info("Отмена редактирования события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Получаем messageId из контекста редактирования
            Integer messageId = conversationStateService.getEditingMessageId(userId);
            
            // Очищаем состояние редактирования
            conversationStateService.clearEventEditing(userId);
            
            // Получаем событие для отображения
            Event event = eventService.getEventById(eventId);
            
            if (messageId != null) {
                // Обновляем то же сообщение, возвращая его к отображению события
                int eventCount = eventService.getActiveEventsCount(event.getUser().getId());
                String eventMessage = botMessageBuilder.buildEventMessageWithHeader(event, eventCount);
                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
                messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
                
                log.debug("Сообщение обновлено при отмене редактирования: eventId={}, messageId={}", 
                         eventId, messageId);
            } else {
                // Fallback: если messageId не найден, отправляем новое сообщение
                log.warn("MessageId не найден в контексте редактирования, используем sendOrUpdateEventMessage: eventId={}, userId={}", 
                        eventId, userId);
                eventService.sendOrUpdateEventMessage(event, chatId);
            }
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.actionCancelled("Редактирование"));
            
            log.info("Редактирование события ID={} успешно отменено пользователем ID={}", eventId, userId);
            
        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API при отмене редактирования: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при отмене редактирования события", e);
        }
    }
    
    /**
     * Формирует сообщение с текущими данными события для выбора поля редактирования.
     * 
     * @param event событие для редактирования
     * @return отформатированное сообщение
     */
    private String buildEditFieldSelectionMessage(Event event) {
        StringBuilder message = new StringBuilder();
        message.append("📝 ").append(bold("Редактирование события")).append("\n\n");
        message.append(botMessageBuilder.buildEventMessage(event));
        message.append("\n\n").append("Выберите поле для редактирования:");
        return message.toString();
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
