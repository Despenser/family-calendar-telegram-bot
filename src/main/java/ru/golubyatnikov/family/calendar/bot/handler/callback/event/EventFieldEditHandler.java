package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик редактирования конкретного поля события.
 * 
 * <p>Обрабатывает выбор поля для редактирования и отображает соответствующий интерфейс.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventFieldEditHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final KeyboardService keyboardService;
    private final EventService eventService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.EDIT_FIELD;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return callbackData != null && CallbackPrefix.EDIT_FIELD.matches(callbackData);
    }
    
    @Override
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        handleEditField(callbackData, user, chatId, messageId, callbackQueryId);
    }
    
    /**
     * Обрабатывает редактирование конкретного поля события.
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
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
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
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
                return;
            }
            
            log.info("Пользователь ID={} начал редактирование поля '{}' события ID={}", 
                    userId, field, eventId);
            
            // Сохраняем messageId в контексте редактирования
            conversationStateService.startEventEditing(userId, eventId, chatId, messageId);
            
            // Устанавливаем редактируемое поле
            ConversationStateService.EditField editField = mapToEditField(field);
            if (editField != null) {
                conversationStateService.setEditingField(userId, editField);
                log.debug("Установлено состояние редактирования: userId={}, eventId={}, field={}, messageId={}", 
                         userId, eventId, editField, messageId);
            }
            
            // Формируем сообщение и клавиатуру в зависимости от поля
            String message;
            InlineKeyboardMarkup keyboard = null;
            
            switch (field) {
                case "date" -> {
                    log.debug("Выбрано поле для редактирования: DATE, userId={}", userId);
                    message = "📅 Редактирование даты\n\nВыберите новую дату из календаря:";
                    // Используем текущий месяц для начального отображения с учетом timezone пользователя
                    LocalDate now = user.getCurrentDate();
                    keyboard = keyboardService.createCalendarKeyboard(
                        now.getYear(), 
                        now.getMonthValue(), 
                        user
                    );
                }
                case "time" -> {
                    log.debug("Выбрано поле для редактирования: TIME, userId={}", userId);
                    message = "🕐 Редактирование времени\n\nВыберите новое время:";
                    
                    // Получаем событие для определения даты
                    Event event = eventService.getEventById(eventId);
                    LocalDate eventDate = event.getEventDate();
                    
                    // Показываем фильтрованный выбор часа с учетом даты события и timezone пользователя
                    keyboard = keyboardService.createFilteredHourSelectionKeyboard(eventDate, user);
                }
                case "title" -> {
                    log.debug("Выбрано поле для редактирования: TITLE, userId={}", userId);
                    message = "📝 Редактирование названия\n\nОтправьте новое название события:";
                    // Создаем клавиатуру только с кнопкой "Отменить"
                    keyboard = createCancelOnlyKeyboard(eventId);
                }
                case "description" -> {
                    log.debug("Выбрано поле для редактирования: DESCRIPTION, userId={}", userId);
                    message = "📄 Редактирование описания\n\nОтправьте новое описание события:";
                    // Создаем клавиатуру только с кнопкой "Отменить"
                    keyboard = createCancelOnlyKeyboard(eventId);
                }
                default -> {
                    log.warn("Неизвестное поле для редактирования: field='{}', userId={}", field, userId);
                    message = "❌ Неизвестное поле для редактирования";
                }
            }
            
            // Обновляем текущее сообщение вместо отправки нового
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API при редактировании поля: userId={}, callbackData='{}', error={}", 
                     userId, callbackData, e.getMessage(), e);
            throw new RuntimeException("Ошибка при редактировании поля", e);
        }
    }
    
    /**
     * Создает клавиатуру только с кнопкой "Отмена".
     * 
     * @param eventId идентификатор события для callback data
     * @return клавиатура с кнопкой "Отмена"
     */
    private InlineKeyboardMarkup createCancelOnlyKeyboard(Long eventId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData(CallbackPrefix.EDIT_CANCEL.withPayload(eventId.toString()));
        row.add(cancelButton);
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    /**
     * Преобразует строковое представление поля в EditField enum.
     * 
     * @param fieldName строковое имя поля (date, time, title, description)
     * @return соответствующий EditField или null если поле неизвестно
     */
    private ConversationStateService.EditField mapToEditField(String fieldName) {
        return switch (fieldName) {
            case "date" -> ConversationStateService.EditField.DATE;
            case "time" -> ConversationStateService.EditField.TIME;
            case "title" -> ConversationStateService.EditField.TITLE;
            case "description" -> ConversationStateService.EditField.DESCRIPTION;
            default -> null;
        };
    }
}
