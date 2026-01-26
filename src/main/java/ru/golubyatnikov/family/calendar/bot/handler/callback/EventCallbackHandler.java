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
               CallbackPrefix.EDIT_CANCEL.matches(callbackData) ||
               CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData) ||
               CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData);
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
        } else if (CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData)) {
            handleAddCompletionNote(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData)) {
            handleSkipCompletionNote(user.getId(), chatId, messageId, callbackQueryId);
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
     * Обрабатывает завершение события с переупорядочиванием списка.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Извлекает eventId из callback data</li>
     *   <li>Вызывает EventService.completeEventWithReordering() для завершения события с переупорядочиванием</li>
     *   <li>Сохраняет контекст с обновлённым messageId из completedEvent для последующего редактирования</li>
     *   <li>Отвечает на callback query</li>
     * </ol>
     * 
     * <p>EventService.completeEventWithReordering():</p>
     * <ul>
     *   <li>Завершает событие (статус → COMPLETED)</li>
     *   <li>Проверяет позицию события в списке</li>
     *   <li>Если событие не последнее - переупорядочивает список "Мои события":</li>
     *   <ul>
     *     <li>Удаляет все сообщения активных событий из чата</li>
     *     <li>Формирует новый порядок: активные события + завершённое</li>
     *     <li>Отправляет события заново с обновлённой шапкой</li>
     *     <li>Сохраняет новые messageId для всех событий</li>
     *   </ul>
     *   <li>Отправляет завершённое событие с предложением добавить заметку</li>
     * </ul>
     * 
     * <p>Переупорядочивание обеспечивает, что завершённое событие отображается внизу списка,
     * а все активные события остаются выше. Это позволяет пользователю комфортно добавлять
     * заметку о завершении, видя контекст оставшихся активных событий.</p>
     * 
     * <p>Все ошибки обрабатываются через аннотацию @HandleCallbackErrors.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 2.1</p>
     * 
     * @param callbackData данные callback (формат: complete_event_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения, из которого был вызван callback (не используется)
     * @param callbackQueryId идентификатор callback query
     */
    private void handleCompleteEvent(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        Long eventId = extractEventId(callbackData, CallbackPrefix.COMPLETE_EVENT);
        
        log.debug("Начало обработки завершения события с переупорядочиванием: eventId={}, userId={}", 
                 eventId, userId);
        
        try {
            // Завершаем событие с переупорядочиванием списка
            // Метод автоматически переупорядочивает список, если событие не последнее,
            // и отправляет завершённое событие с предложением добавить заметку
            Event completedEvent = eventService.completeEventWithReordering(eventId, userId);
            
            log.info("Событие ID={} успешно завершено с переупорядочиванием пользователем ID={}", 
                    eventId, userId);
            
            // Сохраняем контекст для добавления заметки
            // Используем обновлённый messageId из completedEvent после переупорядочивания
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
            messageService.answerCallbackQuery(callbackQueryId, "");
            
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
            log.error("Ошибка при завершении события с переупорядочиванием: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Добавить заметку" к завершенному событию.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Извлекает eventId из callback data</li>
     *   <li>Редактирует текущее сообщение с просьбой ввести текст заметки</li>
     *   <li>Устанавливает состояние ожидания заметки с messageId</li>
     *   <li>Отвечает на callback query</li>
     * </ol>
     * 
     * <p>При ошибке редактирования сообщения используется fallback на отправку нового сообщения.</p>
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
                
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
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
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при обработке добавления заметки: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            
            // Очищаем контекст при критической ошибке
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            throw new RuntimeException("Ошибка при обработке добавления заметки", e);
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Пропустить" при добавлении заметки к завершенному событию.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает контекст для доступа к eventId и messageId</li>
     *   <li>Редактирует сообщение с финальной карточкой события (без заметки)</li>
     *   <li>Очищает контекст ожидания заметки</li>
     *   <li>Обновляет шапку /my_events после завершения процесса</li>
     *   <li>Отвечает на callback query</li>
     * </ol>
     * 
     * <p><b>Важно:</b> Обновление шапки /my_events происходит ПОСЛЕ отображения карточки события.
     * Это гарантирует правильную последовательность сообщений:</p>
     * <ol>
     *   <li>Карточка завершенного события (без заметки)</li>
     *   <li>Сообщение "У вас пока нет созданных событий" (если список активных событий пуст)</li>
     * </ol>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Контекст не найден - отправляется сообщение об истечении времени ожидания</li>
     *   <li>Событие не найдено - отправляется сообщение об ошибке</li>
     *   <li>Ошибка редактирования сообщения - используется fallback на отправку нового сообщения</li>
     * </ul>
     * 
     * <p><b>Реализуемые требования:</b></p>
     * <ul>
     *   <li><b>2.3:</b> Обновление шапки /my_events после пропуска заметки</li>
     *   <li><b>3.3:</b> Очистка контекста после пропуска заметки</li>
     * </ul>
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
            ru.golubyatnikov.family.calendar.bot.service.ConversationStateService.CompletionNoteContext context = 
                conversationStateService.getCompletionNoteContext(userId);
            
            if (context == null) {
                log.warn("Контекст добавления заметки не найден для пользователя ID={}", userId);
                
                // Очищаем состояние на всякий случай
                conversationStateService.clearAwaitingCompletionNote(userId);
                
                // Отправляем сообщение об ошибке
                String errorMessage = formatMessage("❌ Время ожидания истекло. Попробуйте снова.");
                messageService.sendMessage(chatId, errorMessage);
                messageService.answerCallbackQuery(callbackQueryId, "");
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
                
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                // Fallback: если редактирование не удалось, отправляем новое сообщение
                log.warn("Ошибка редактирования сообщения при пропуске заметки, используем fallback: eventId={}, messageId={}, error={}", 
                        eventId, targetMessageId, e.getMessage());
                
                messageService.sendMessage(chatId, eventMessage);
            }
            
            // Очищаем контекст
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            log.info("Контекст очищен после пропуска заметки: userId={}, eventId={}", userId, eventId);
            
            // Обновляем шапку /my_events ПОСЛЕ отображения карточки события
            // Это обеспечивает правильную последовательность сообщений:
            // 1. Карточка завершенного события (без заметки)
            // 2. Сообщение "У вас пока нет созданных событий" (если список активных событий пуст)
            // Требования: 2.3
            eventService.updateMyEventsHeaderAfterRemoval(userId);
            
            log.info("Шапка /my_events обновлена после пропуска заметки к событию ID={}: userId={}", 
                    eventId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.error("Событие не найдено при пропуске заметки: userId={}", userId, e);
            
            // Очищаем контекст при ошибке
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            // Отправляем сообщение об ошибке
            try {
                String errorMessage = formatMessage("❌ Событие не найдено.");
                messageService.sendMessage(chatId, errorMessage);
                messageService.answerCallbackQuery(callbackQueryId, "");
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                log.error("Ошибка отправки сообщения об ошибке: userId={}", userId, ex);
            }
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при пропуске заметки: userId={}, error={}", 
                     userId, e.getMessage(), e);
            
            // Очищаем контекст при критической ошибке
            conversationStateService.clearAwaitingCompletionNote(userId);
            
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
