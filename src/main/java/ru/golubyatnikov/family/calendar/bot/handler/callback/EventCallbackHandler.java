package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик callback queries для операций с событиями.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>view_event_ - просмотр деталей события</li>
 *   <li>edit_event_ - редактирование события</li>
 *   <li>delete_event_ - удаление события</li>
 *   <li>complete_event_ - завершение события</li>
 *   <li>edit_field_ - редактирование конкретного поля события</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.2, 1.3, 2.1, 2.2, 2.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.1.0
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
    private final ru.golubyatnikov.family.calendar.bot.service.EventService eventService;
    private final ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder botMessageBuilder;
    
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
               CallbackPrefix.EDIT_FIELD.matches(callbackData) ||
               CallbackPrefix.COMPLETE_EVENT.matches(callbackData) ||
               CallbackPrefix.EDIT_CANCEL.matches(callbackData);
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
            handleEditEvent(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.DELETE_EVENT.matches(callbackData)) {
            handleDeleteEvent(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.EDIT_FIELD.matches(callbackData)) {
            handleEditField(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.COMPLETE_EVENT.matches(callbackData)) {
            handleCompleteEvent(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.EDIT_CANCEL.matches(callbackData)) {
            handleEditCancel(callbackData, user.getId(), chatId, callbackQueryId);
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
            var event = eventService.getEventById(eventId);
            
            // Проверяем права доступа
            if (!event.getUser().getId().equals(userId)) {
                log.warn("Пользователь ID={} не имеет прав для редактирования события ID={}", 
                        userId, eventId);
                messageService.answerCallbackQuery(callbackQueryId, 
                    "У вас нет прав для редактирования этого события");
                return;
            }
            
            // ИЗМЕНЕНИЕ: Сохраняем messageId в контексте редактирования
            conversationStateService.startEventEditing(userId, eventId, chatId, messageId);
            
            // Формируем сообщение с текущими данными события и клавиатурой выбора поля
            String message = buildEditFieldSelectionMessage(event);
            InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId);
            
            // ИЗМЕНЕНИЕ: Обновляем ТЕКУЩЕЕ сообщение вместо отправки нового
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
            log.debug("Начато редактирование события ID={} в сообщении ID={} пользователем ID={}", 
                     eventId, messageId, userId);
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при редактировании события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при редактировании события", e);
        }
    }
    
    /**
     * Формирует сообщение с текущими данными события для выбора поля редактирования.
     * 
     * @param event событие для редактирования
     * @return отформатированное сообщение
     */
    private String buildEditFieldSelectionMessage(ru.golubyatnikov.family.calendar.bot.model.Event event) {
        StringBuilder message = new StringBuilder();
        message.append("📝 ").append(bold("Редактирование события")).append("\n\n");
        message.append(botMessageBuilder.buildEventMessage(event));
        message.append("\n\n").append("Выберите поле для редактирования:");
        return message.toString();
    }
    
    /**
     * Обрабатывает удаление события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Вызывает eventService.deleteEvent() для удаления события</li>
     *   <li>Отвечает на callback query с текстом "Событие удалено"</li>
     * </ol>
     * 
     * <p>EventService.deleteEvent() автоматически:</p>
     * <ul>
     *   <li>Удаляет сообщение события из чата</li>
     *   <li>Обновляет статус события на DELETED</li>
     *   <li>Сбрасывает messageId и isMyEventsHeader</li>
     *   <li>Вызывает updateMyEventsHeaderAfterRemoval для обновления шапки</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.4</p>
     * 
     * @param callbackData данные callback (формат: delete_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения, из которого был вызван callback
     * @param callbackQueryId идентификатор callback query
     */
    private void handleDeleteEvent(String callbackData, Long userId, Long chatId, 
                                   Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.DELETE_EVENT);
        
        log.info("Удаление события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Удаляем событие (перемещаем в корзину)
            // EventService автоматически удалит сообщение и обновит шапку /my_events
            eventService.deleteEvent(eventId, userId);
            
            // Отвечаем на callback query с подтверждением
            messageService.answerCallbackQuery(callbackQueryId, "Событие удалено");
            
            log.debug("Событие ID={} успешно удалено пользователем ID={}", eventId, userId);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Событие не найдено: eventId={}, userId={}", eventId, userId, e);
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Нет прав на удаление события: eventId={}, userId={}", eventId, userId, e);
        } catch (Exception e) {
            // Обработка других ошибок без отправки сообщений
            log.error("Ошибка при удалении события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
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
            
            // ИЗМЕНЕНИЕ: Сохраняем messageId в контексте редактирования
            conversationStateService.startEventEditing(userId, eventId, chatId, messageId);
            
            // Устанавливаем редактируемое поле
            ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.EditField editField = mapToEditField(field);
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
                    // Добавляем кнопку "Отменить"
                    keyboard = addCancelButton(keyboard, eventId);
                }
                case "time" -> {
                    log.debug("Выбрано поле для редактирования: TIME, userId={}", userId);
                    message = "🕐 Редактирование времени\n\nВыберите новое время:";
                    // Показываем выбор часа
                    keyboard = keyboardService.createHourSelectionKeyboard();
                    // Добавляем кнопку "Отменить"
                    keyboard = addCancelButton(keyboard, eventId);
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
            
            // ИЗМЕНЕНИЕ: Обновляем ТЕКУЩЕЕ сообщение вместо отправки нового
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при редактировании поля: userId={}, callbackData='{}', error={}", 
                     userId, callbackData, e.getMessage(), e);
            throw new RuntimeException("Ошибка при редактировании поля", e);
        }
    }
    
    /**
     * Добавляет кнопку "Отменить" к существующей клавиатуре.
     * 
     * <p>Если клавиатура null, создает новую клавиатуру только с кнопкой "Отменить".</p>
     * 
     * @param keyboard существующая клавиатура или null
     * @param eventId идентификатор события для callback data
     * @return клавиатура с добавленной кнопкой "Отменить"
     */
    private InlineKeyboardMarkup addCancelButton(InlineKeyboardMarkup keyboard, Long eventId) {
        if (keyboard == null) {
            return createCancelOnlyKeyboard(eventId);
        }
        
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = 
            new java.util.ArrayList<>(keyboard.getKeyboard());
        
        // Добавляем кнопку "Отменить" в последнюю строку
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> cancelRow = 
            new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton cancelButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        cancelButton.setText("❌ Отменить");
        cancelButton.setCallbackData(CallbackPrefix.EDIT_CANCEL.withPayload(eventId.toString()));
        cancelRow.add(cancelButton);
        rows.add(cancelRow);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Создает клавиатуру только с кнопкой "Отменить".
     * 
     * <p>Используется для режимов ожидания текстового ввода (название, описание).</p>
     * 
     * @param eventId идентификатор события для callback data
     * @return клавиатура с кнопкой "Отменить"
     */
    private InlineKeyboardMarkup createCancelOnlyKeyboard(Long eventId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
            new java.util.ArrayList<>();
        
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = 
            new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton cancelButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        cancelButton.setText("❌ Отменить");
        cancelButton.setCallbackData(CallbackPrefix.EDIT_CANCEL.withPayload(eventId.toString()));
        row.add(cancelButton);
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        return markup;
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
    
    /**
     * Обрабатывает завершение события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Извлекает eventId из callback data</li>
     *   <li>Вызывает EventService.completeEvent() для завершения события</li>
     *   <li>Отвечает на callback query</li>
     * </ol>
     * 
     * <p>EventService.completeEvent() автоматически:</p>
     * <ul>
     *   <li>Удаляет сообщение события из чата</li>
     *   <li>Изменяет статус события на COMPLETED</li>
     *   <li>Сбрасывает messageId и isMyEventsHeader</li>
     *   <li>Вызывает updateMyEventsHeaderAfterRemoval для обновления шапки</li>
     * </ul>
     * 
     * <p>Все ошибки обрабатываются через аннотацию @HandleCallbackErrors.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.4</p>
     * 
     * @param callbackData данные callback (формат: complete_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения, из которого был вызван callback
     * @param callbackQueryId идентификатор callback query
     */
    private void handleCompleteEvent(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.COMPLETE_EVENT);
        
        log.debug("Начало обработки завершения события: eventId={}, userId={}", eventId, userId);
        
        try {
            // Завершаем событие
            // EventService автоматически удалит сообщение и обновит шапку /my_events
            eventService.completeEvent(eventId, userId);
            
            log.info("Событие ID={} успешно завершено вручную пользователем ID={}", 
                    eventId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, "Событие завершено");
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Событие не найдено: eventId={}, userId={}", eventId, userId, e);
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Нет прав на завершение события: eventId={}, userId={}", eventId, userId, e);
        } catch (IllegalStateException e) {
            // Обработка ошибок без отправки сообщений
            log.error("Неверное состояние события: eventId={}, userId={}", eventId, userId, e);
        } catch (Exception e) {
            // Обработка других ошибок без отправки сообщений
            log.error("Ошибка при завершении события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
        }
    }
    
    /**
     * Создает клавиатуру с кнопками "Добавить заметку" и "Пропустить" для завершенного события.
     * 
     * @param eventId идентификатор завершенного события
     * @return клавиатура с кнопками добавления заметки и пропуска
     */
    private InlineKeyboardMarkup createCompletionNoteKeyboard(Long eventId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = 
            new java.util.ArrayList<>();
        
        // Первая строка: кнопка "Добавить заметку"
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row1 = 
            new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton addNoteButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        addNoteButton.setText("📝 Добавить заметку");
        addNoteButton.setCallbackData(
            CallbackPrefix.ADD_COMPLETION_NOTE.withPayload(eventId.toString())
        );
        row1.add(addNoteButton);
        keyboard.add(row1);
        
        // Вторая строка: кнопка "Пропустить"
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = 
            new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton skipButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        skipButton.setText("⏭️ Пропустить");
        skipButton.setCallbackData(CallbackPrefix.SKIP_COMPLETION_NOTE.withPayload(""));
        row2.add(skipButton);
        keyboard.add(row2);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    /**
     * Обрабатывает нажатие кнопки "Добавить заметку" к завершенному событию.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Извлекает eventId из callback data</li>
     *   <li>Устанавливает состояние ожидания заметки в ConversationStateService</li>
     *   <li>Отправляет сообщение с просьбой ввести текст заметки</li>
     *   <li>Отвечает на callback query</li>
     * </ol>
     * 
     * @param callbackData данные callback (формат: add_completion_note_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param callbackQueryId идентификатор callback query
     */
    private void handleAddCompletionNote(String callbackData, Long userId, Long chatId, 
                                        String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.ADD_COMPLETION_NOTE);
        
        log.debug("Начало обработки добавления заметки к событию: eventId={}, userId={}", 
                 eventId, userId);
        
        try {
            // Устанавливаем состояние ожидания заметки
            conversationStateService.setAwaitingCompletionNote(userId, eventId, chatId);
            
            log.info("Пользователь ID={} переведен в режим ожидания заметки для события ID={}", 
                    userId, eventId);
            
            // Отправляем сообщение с просьбой ввести заметку
            String message = formatMessage(
                "📝 Напишите заметку о том, как прошло событие.\n\n" +
                "Например, что было сделано, какие были результаты или впечатления."
            );
            
            messageService.sendMessage(chatId, message);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, "Ожидаю текст заметки");
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при обработке добавления заметки: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при обработке добавления заметки", e);
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Пропустить" при добавлении заметки к завершенному событию.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Отправляет подтверждающее сообщение о том, что событие завершено без заметки</li>
     *   <li>Отвечает на callback query</li>
     * </ol>
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param callbackQueryId идентификатор callback query
     */
    private void handleSkipCompletionNote(Long userId, Long chatId, String callbackQueryId) {
        log.debug("Пользователь ID={} пропустил добавление заметки к завершенному событию", userId);
        
        try {
            // Отправляем подтверждающее сообщение
            String message = formatMessage(
                "✅ Событие завершено без заметки."
            );
            
            messageService.sendMessage(chatId, message);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, "Заметка пропущена");
            
            log.info("Пользователь ID={} успешно пропустил добавление заметки", userId);
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при пропуске заметки: userId={}, error={}", 
                     userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при пропуске заметки", e);
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
            var event = eventService.getEventById(eventId);
            
            if (messageId != null) {
                // ИЗМЕНЕНИЕ: Обновляем то же сообщение, возвращая его к отображению события
                // Используем buildEventMessageWithHeader для сохранения шапки, если это первое событие
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
            messageService.answerCallbackQuery(callbackQueryId, "Редактирование отменено");
            
            log.info("Редактирование события ID={} успешно отменено пользователем ID={}", eventId, userId);
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при отмене редактирования: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            throw new RuntimeException("Ошибка при отмене редактирования события", e);
        }
    }
}
