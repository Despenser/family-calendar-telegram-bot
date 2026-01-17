package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
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
    private final ru.golubyatnikov.family.calendar.bot.service.ConversationStateService conversationStateService;
    private final ru.golubyatnikov.family.calendar.bot.service.KeyboardService keyboardService;
    
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
            handleEditField(callbackData, user, chatId, messageId, callbackQueryId);
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
     * <p>Извлекает имя поля и ID события из callback data формата edit_field_{field}_{eventId},
     * устанавливает состояние редактирования и отправляет соответствующее сообщение пользователю.</p>
     * 
     * @param callbackData данные callback (формат: edit_field_{field}_{eventId})
     * @param user объект пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditField(String callbackData, User user, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
        Long userId = user.getId();
        try {
            // Извлекаем payload после префикса edit_field_
            String payload = CallbackPrefix.EDIT_FIELD.extractPayload(callbackData);
            
            log.debug("Извлечен payload из callback data: payload='{}', userId={}", payload, userId);
            
            // Разделяем payload на поле и eventId
            String[] parts = payload.split("_", 2);
            
            // Валидация формата
            if (parts.length != 2) {
                log.error("Некорректный формат callback data: ожидается 2 части, получено {}. " +
                         "CallbackData='{}', userId={}", parts.length, callbackData, userId);
                messageService.editMessageText(chatId, messageId, 
                    "❌ Произошла ошибка при обработке запроса", null);
                messageService.answerCallbackQuery(callbackQueryId, "");
                return;
            }
            
            String field = parts[0];
            Long eventId;
            
            // Парсинг eventId с обработкой NumberFormatException
            try {
                eventId = Long.parseLong(parts[1]);
                log.debug("Успешно извлечены данные: field='{}', eventId={}, userId={}", 
                         field, eventId, userId);
            } catch (NumberFormatException e) {
                log.error("Некорректный eventId в callback data: eventId='{}', callbackData='{}', " +
                         "userId={}, error={}", parts[1], callbackData, userId, e.getMessage());
                messageService.editMessageText(chatId, messageId, 
                    "❌ Произошла ошибка при обработке запроса", null);
                messageService.answerCallbackQuery(callbackQueryId, "");
                return;
            }
            
            log.info("Пользователь ID={} начал редактирование поля '{}' события ID={}", 
                    userId, field, eventId);
            
            // Устанавливаем состояние редактирования
            ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField editField = mapToEditField(field);
            if (editField != null) {
                conversationStateService.startEventEditing(userId, eventId, chatId);
                conversationStateService.setEditingField(userId, editField);
                log.debug("Установлено состояние редактирования: userId={}, eventId={}, field={}", 
                         userId, eventId, editField);
            }
            
            // Формируем сообщение и клавиатуру в зависимости от поля
            String message;
            InlineKeyboardMarkup keyboard = null;
            
            switch (field) {
                case "date" -> {
                    log.debug("Выбрано поле для редактирования: DATE, userId={}", userId);
                    message = "📅 Редактирование даты\n\nВыберите новую дату из календаря:";
                    // Получаем ID семьи пользователя для отображения событий в календаре
                    // Используем текущий месяц для начального отображения
                    java.time.LocalDate now = java.time.LocalDate.now();
                    Long familyId = user.getFamily() != null ? user.getFamily().getId() : null;
                    if (familyId != null) {
                        keyboard = keyboardService.createCalendarKeyboard(
                            now.getYear(), 
                            now.getMonthValue(), 
                            familyId
                        );
                    }
                }
                case "time" -> {
                    log.debug("Выбрано поле для редактирования: TIME, userId={}", userId);
                    message = "🕐 Редактирование времени\n\nВыберите новое время:";
                    // Показываем выбор часа
                    keyboard = keyboardService.createHourSelectionKeyboard();
                }
                case "title" -> {
                    log.debug("Выбрано поле для редактирования: TITLE, userId={}", userId);
                    message = "📝 Редактирование названия\n\nОтправьте новое название события:";
                }
                case "description" -> {
                    log.debug("Выбрано поле для редактирования: DESCRIPTION, userId={}", userId);
                    message = "📄 Редактирование описания\n\nОтправьте новое описание события:";
                }
                default -> {
                    log.warn("Неизвестное поле для редактирования: field='{}', userId={}", field, userId);
                    message = "❌ Неизвестное поле для редактирования";
                }
            }
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при редактировании поля: userId={}, callbackData='{}', error={}", 
                     userId, callbackData, e.getMessage(), e);
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
    
    /**
     * Преобразует строковое представление поля в EditField enum.
     * 
     * <p>Используется для маппинга строковых значений полей из callback data
     * в типизированный enum для установки состояния редактирования.</p>
     * 
     * @param fieldName строковое имя поля (date, time, title, description)
     * @return соответствующий EditField или null если поле неизвестно
     */
    private ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField mapToEditField(String fieldName) {
        return switch (fieldName) {
            case "date" -> ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField.DATE;
            case "time" -> ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField.TIME;
            case "title" -> ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField.TITLE;
            case "description" -> ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField.DESCRIPTION;
            default -> null;
        };
    }
}
