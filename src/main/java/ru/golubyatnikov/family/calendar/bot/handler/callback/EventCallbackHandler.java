package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

/**
 * Обработчик callback queries для операций с событиями.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>view_event_ - просмотр деталей события</li>
 *   <li>edit_event_ - редактирование события</li>
 *   <li>delete_event_ - удаление события</li>
 *   <li>edit_field_ - редактирование конкретного поля события</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.3, 2.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCallbackHandler implements CallbackHandler {
    
    private final MyEventsCommandHandler myEventsCommandHandler;
    private final TelegramMessageService messageService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.VIEW_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.VIEW_EVENT.matches(callbackData) ||
               CallbackPrefix.EDIT_EVENT.matches(callbackData) ||
               CallbackPrefix.DELETE_EVENT.matches(callbackData) ||
               CallbackPrefix.EDIT_FIELD.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback для события: data='{}', userId={}", 
                callbackData, user.getId());
        
        if (CallbackPrefix.VIEW_EVENT.matches(callbackData)) {
            handleViewEvent(callbackData, user.getId(), chatId, callbackQueryId);
        } else if (CallbackPrefix.EDIT_EVENT.matches(callbackData)) {
            handleEditEvent(callbackData, user.getId(), chatId, callbackQueryId);
        } else if (CallbackPrefix.DELETE_EVENT.matches(callbackData)) {
            handleDeleteEvent(callbackData, user.getId(), chatId, callbackQueryId);
        } else if (CallbackPrefix.EDIT_FIELD.matches(callbackData)) {
            handleEditField(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает просмотр деталей события.
     * 
     * @param callbackData данные callback (формат: view_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param callbackQueryId идентификатор callback query
     */
    private void handleViewEvent(String callbackData, Long userId, Long chatId, 
                                 String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.VIEW_EVENT);
        
        log.info("Просмотр деталей события ID={} пользователем ID={}", eventId, userId);
        
        try {
            String response = myEventsCommandHandler.handleViewEventDetails(eventId, userId);
            messageService.sendMessage(chatId, response);
            messageService.answerCallbackQuery(callbackQueryId, "Обработано");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при просмотре события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage());
            throw new RuntimeException("Ошибка при просмотре события", e);
        }
    }
    
    /**
     * Обрабатывает редактирование события.
     * 
     * @param callbackData данные callback (формат: edit_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditEvent(String callbackData, Long userId, Long chatId, 
                                 String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.EDIT_EVENT);
        
        log.info("Редактирование события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Сообщение уже отправлено внутри handleEditCallback с клавиатурой
            myEventsCommandHandler.handleEditCallback(eventId, userId, chatId);
            messageService.answerCallbackQuery(callbackQueryId, "Обработано");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при редактировании события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage());
            throw new RuntimeException("Ошибка при редактировании события", e);
        }
    }
    
    /**
     * Обрабатывает удаление события.
     * 
     * @param callbackData данные callback (формат: delete_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param callbackQueryId идентификатор callback query
     */
    private void handleDeleteEvent(String callbackData, Long userId, Long chatId, 
                                   String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.DELETE_EVENT);
        
        log.info("Удаление события ID={} пользователем ID={}", eventId, userId);
        
        try {
            String response = myEventsCommandHandler.handleDeleteCallback(eventId, userId);
            messageService.sendMessage(chatId, response);
            messageService.answerCallbackQuery(callbackQueryId, "Обработано");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при удалении события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage());
            throw new RuntimeException("Ошибка при удалении события", e);
        }
    }
    
    /**
     * Обрабатывает редактирование конкретного поля события.
     * 
     * @param callbackData данные callback (формат: edit_field_{field}_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditField(String callbackData, Long userId, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
        // Извлекаем поле для редактирования (date, time, title, description)
        String payload = CallbackPrefix.EDIT_FIELD.extractPayload(callbackData);
        
        log.info("Пользователь {} начал редактирование поля: {}", userId, payload);
        
        String message = switch (payload) {
            case "date" -> "📅 Редактирование даты\n\nВыберите новую дату из календаря:";
            case "time" -> "🕐 Редактирование времени\n\nВыберите новое время:";
            case "title" -> "📝 Редактирование названия\n\nОтправьте новое название события:";
            case "description" -> "📄 Редактирование описания\n\nОтправьте новое описание события:";
            default -> "❌ Неизвестное поле для редактирования";
        };
        
        // TODO: Показать соответствующую клавиатуру (календарь для даты, выбор времени и т.д.)
        
        try {
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при редактировании поля: userId={}, field={}, error={}", 
                     userId, payload, e.getMessage());
            throw new RuntimeException("Ошибка при редактировании поля", e);
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
