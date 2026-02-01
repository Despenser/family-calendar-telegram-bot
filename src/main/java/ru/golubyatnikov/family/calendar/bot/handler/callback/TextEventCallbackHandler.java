package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик callback queries для создания событий из текста.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>confirm_text_event: - подтверждение создания события из текста</li>
 *   <li>cancel_text_event - отмена создания события из текста</li>
 * </ul>
 * 
 * <p><b>Важно:</b> Транзакционная логика отделена от вызовов Telegram API
 * для предотвращения блокировки соединений БД при внешних вызовах.</p>
 * 
 * <p><b>Требования:</b> 1.3, 2.5, 7.1, 7.2, 7.3, 7.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TextEventCallbackHandler implements CallbackHandler {
    
    private final ConversationService conversationService;
    private final TelegramMessageService messageService;
    private final BotMessageBuilder messageBuilder;
    private final ru.golubyatnikov.family.calendar.bot.service.EventService eventService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.CONFIRM_TEXT_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.CONFIRM_TEXT_EVENT.matches(callbackData) ||
               CallbackPrefix.CANCEL_TEXT_EVENT.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback создания события из текста: data='{}', userId={}", 
                callbackData, user.getId());
        
        if (CallbackPrefix.CONFIRM_TEXT_EVENT.matches(callbackData)) {
            handleConfirmTextEvent(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.CANCEL_TEXT_EVENT.matches(callbackData)) {
            handleCancelTextEvent(chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает подтверждение создания события из текста.
     * 
     * <p>Метод разделен на две части:</p>
     * <ol>
     *   <li>Транзакционная часть - создание события в БД</li>
     *   <li>Не-транзакционная часть - отправка сообщений через Telegram API</li>
     * </ol>
     * 
     * <p>Это соответствует требованию 7.1-7.5 о разделении транзакций и внешних вызовов.</p>
     * 
     * @param callbackData данные callback с закодированными параметрами события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleConfirmTextEvent(String callbackData, User user, Long chatId, 
                                       Integer messageId, String callbackQueryId) {
        Event createdEvent = null;
        String errorMessage = null;
        
        try {
            // Извлекаем закодированные данные события
            String encodedData = CallbackPrefix.CONFIRM_TEXT_EVENT.extractPayload(callbackData);
            String decodedData = new String(Base64.getDecoder().decode(encodedData));
            
            // Парсим данные (формат: title|date|time)
            String[] parts = decodedData.split("\\|");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Неверный формат данных события");
            }
            
            String title = parts[0];
            LocalDate date = LocalDate.parse(parts[1]);
            LocalTime time = LocalTime.parse(parts[2]);
            
            log.debug("Подтверждение создания события из текста: userId={}, title='{}', date={}, time={}", 
                     user.getId(), title, date, time);
            
            // Создаем событие в транзакции (без Telegram API вызовов)
            createdEvent = createEventInTransaction(user.getId(), title, date, time);
            
            log.info("Событие успешно создано из текста: eventId={}, userId={}", 
                     createdEvent.getId(), user.getId());
            
        } catch (Exception e) {
            log.error("Ошибка при подтверждении создания события из текста: userId={}, " +
                     "errorType={}, errorMessage={}", 
                     user.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
            
            // Явно удаляем черновик при ошибке
            cleanupDraftOnError(user.getId());
            
            errorMessage = e.getMessage() != null ? e.getMessage() : "Неизвестная ошибка";
        }
        
        // Отправляем сообщения через Telegram API (вне транзакции)
        sendTelegramResponse(createdEvent, errorMessage, chatId, messageId, callbackQueryId);
    }
    
    /**
     * Создает событие в транзакции.
     * 
     * <p>Этот метод выполняется в отдельной транзакции и НЕ содержит
     * вызовов внешних API (Telegram). Это соответствует требованию 7.1.</p>
     * 
     * @param userId идентификатор пользователя
     * @param title название события
     * @param date дата события
     * @param time время события
     * @return созданное событие
     */
    @Transactional
    public Event createEventInTransaction(Long userId, String title, LocalDate date, LocalTime time) {
        // Создаем событие через ConversationService для единообразия
        conversationService.startEventCreation(userId);
        conversationService.updateEventDate(userId, date);
        conversationService.updateEventTime(userId, time);
        conversationService.updateEventTitle(userId, title);
        
        // Завершаем создание без описания
        return conversationService.completeEventCreation(userId, null);
    }
    
    /**
     * Очищает черновик при ошибке создания события.
     * 
     * @param userId идентификатор пользователя
     */
    private void cleanupDraftOnError(Long userId) {
        try {
            conversationService.cancelEventCreation(userId);
            log.info("Черновик успешно удален после ошибки: userId={}", userId);
        } catch (Exception cleanupEx) {
            log.error("Ошибка при удалении черновика после ошибки создания события: userId={}, error={}", 
                     userId, cleanupEx.getMessage(), cleanupEx);
        }
    }
    
    /**
     * Отправляет ответ через Telegram API.
     * 
     * <p>Этот метод выполняется ВНЕ транзакции, что соответствует требованию 7.3.
     * Если ошибка происходит при вызове Telegram API, транзакция уже закоммичена.</p>
     * 
     * @param createdEvent созданное событие (null если была ошибка)
     * @param errorMessage сообщение об ошибке (null если успех)
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void sendTelegramResponse(Event createdEvent, String errorMessage, 
                                      Long chatId, Integer messageId, String callbackQueryId) {
        try {
            if (createdEvent != null) {
                // Успешное создание - отправляем сообщение о событии и сохраняем messageId
                try {
                    eventService.sendOrUpdateEventMessage(createdEvent, chatId);
                    log.debug("Сообщение о созданном событии отправлено и messageId сохранён: eventId={}", 
                            createdEvent.getId());
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.CREATED);
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    log.error("Ошибка при отправке сообщения о созданном событии: eventId={}, error={}", 
                            createdEvent.getId(), e.getMessage());
                    // Отправляем простое подтверждающее сообщение как fallback
                    String response = formatMessage(
                        "✅ *Событие успешно создано!*\n\n" +
                        "📅 Дата: %s\n" +
                        "🕐 Время: %s\n" +
                        "📝 Название: %s",
                        createdEvent.getFormattedDate(),
                        createdEvent.getFormattedTime(),
                        createdEvent.getTitle()
                    );
                    messageService.editMessageText(chatId, messageId, response, null);
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.CREATED);
                }
            } else {
                // Ошибка создания
                String response = "❌ " + bold("Произошла ошибка при создании события") + "\\.\n\n" +
                                italic("Попробуйте использовать команду /add_event для пошагового создания.") + "\n\n" +
                                "Детали ошибки: " + escape(errorMessage);
                
                messageService.editMessageText(chatId, messageId, response, null);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            }
        } catch (Exception ex) {
            // Логируем ошибку Telegram API, но не откатываем транзакцию (она уже закоммичена)
            log.error("Ошибка при отправке сообщения через Telegram API: chatId={}, error={}", 
                     chatId, ex.getMessage(), ex);
        }
    }
    
    /**
     * Обрабатывает отмену создания события из текста.
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleCancelTextEvent(Long chatId, Integer messageId, String callbackQueryId) {
        log.info("Отмена создания события из текста: chatId={}", chatId);
        
        String message = messageBuilder.buildEventCancelledMessage();
        try {
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.CANCELLED);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при отмене создания события из текста: chatId={}, error={}", 
                     chatId, e.getMessage());
            throw new RuntimeException("Ошибка при отмене создания события", e);
        }
    }
}
