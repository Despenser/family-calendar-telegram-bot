package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.Optional;

/**
 * Сервис для асинхронной обработки обновлений от Telegram Bot API.
 * 
 * <p>UpdateProcessor является центральным компонентом обработки входящих webhook обновлений.
 * Он отвечает за:</p>
 * <ul>
 *   <li>Асинхронную обработку обновлений для быстрого ответа Telegram API</li>
 *   <li>Извлечение сообщений из обновлений</li>
 *   <li>Делегирование обработки команд в CommandDispatcher</li>
 *   <li>Логирование процесса обработки для мониторинга и отладки</li>
 * </ul>
 * 
 * <p>Асинхронная обработка позволяет быстро возвращать HTTP 200 OK в webhook контроллере,
 * что критично для соблюдения 60-секундного таймаута Telegram API.</p>
 * 
 * <p><b>Архитектурный паттерн:</b> Async Processing + Delegation</p>
 * <p><b>Требования:</b> 8.2</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * @RestController
 * public class TelegramWebhookController {
 *     private final UpdateProcessor updateProcessor;
 *     
 *     @PostMapping("/webhook/{token}")
 *     public ResponseEntity<Void> onUpdate(@RequestBody Update update) {
 *         updateProcessor.processUpdate(update);
 *         return ResponseEntity.ok().build();
 *     }
 * }
 * }</pre>
 * 
 * @see CommandDispatcher
 * @see UserService
 * @see Update
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateProcessor {

    private final CommandDispatcher commandDispatcher;
    private final UserService userService;
    private final MyEventsCommandHandler myEventsCommandHandler;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final ConversationService conversationService;

    /**
     * Асинхронно обрабатывает входящее обновление от Telegram Bot API.
     * 
     * <p>Этот метод выполняется в отдельном потоке благодаря аннотации @Async,
     * что позволяет webhook контроллеру быстро вернуть ответ Telegram API.</p>
     * 
     * <p>Процесс обработки:</p>
     * <ol>
     *   <li>Проверка наличия сообщения в обновлении</li>
     *   <li>Извлечение объекта Message</li>
     *   <li>Делегирование обработки в CommandDispatcher</li>
     *   <li>Логирование результата обработки</li>
     * </ol>
     * 
     * <p>Если обновление не содержит сообщения (например, это callback query или
     * другой тип обновления), метод логирует это и завершает обработку.</p>
     * 
     * <p>Все исключения перехватываются и логируются, чтобы не прерывать
     * обработку других обновлений.</p>
     * 
     * @param update объект Update от Telegram, содержащий информацию о событии
     * @throws IllegalArgumentException если update равен null
     * @see Update
     * @see Message
     * @see CommandDispatcher#dispatch(Message)
     */
    @Async
    public void processUpdate(Update update) {
        if (update == null) {
            log.error("Получено null обновление для обработки");
            throw new IllegalArgumentException("Update не может быть null");
        }
        
        log.info("Начало асинхронной обработки обновления: updateId={}", update.getUpdateId());
        
        try {
            // Проверяем, содержит ли обновление callback query
            if (update.hasCallbackQuery()) {
                log.debug("Обновление содержит callback query: updateId={}", update.getUpdateId());
                processCallbackQuery(update.getCallbackQuery());
                return;
            }
            
            // Проверяем, содержит ли обновление сообщение
            if (!update.hasMessage()) {
                log.debug("Обновление не содержит сообщения: updateId={}, hasCallbackQuery={}, hasEditedMessage={}", 
                        update.getUpdateId(), 
                        update.hasCallbackQuery(), 
                        update.hasEditedMessage());
                
                // TODO: В будущем здесь можно добавить обработку других типов обновлений
                // (edited messages и т.д.)
                log.info("Обновление пропущено (не содержит сообщения): updateId={}", update.getUpdateId());
                return;
            }
            
            // Извлекаем сообщение из обновления
            Message message = update.getMessage();
            
            if (message == null) {
                log.warn("Обновление помечено как hasMessage=true, но message=null: updateId={}", 
                        update.getUpdateId());
                return;
            }
            
            // Проверяем наличие текста в сообщении
            String originalText = message.getText();
            if (originalText == null || originalText.isBlank()) {
                log.debug("Сообщение не содержит текста: updateId={}, messageId={}", 
                        update.getUpdateId(), message.getMessageId());
                return;
            }
            
            log.debug("Извлечено сообщение из обновления: updateId={}, messageId={}, chatId={}, from={}", 
                    update.getUpdateId(), 
                    message.getMessageId(), 
                    message.getChatId(),
                    message.getFrom() != null ? message.getFrom().getId() : null);
            
            // Преобразуем текст кнопки в команду, если это кнопка
            String commandText = keyboardService.buttonTextToCommand(originalText);
            
            // Если текст был преобразован, создаем новое сообщение с командой
            if (!originalText.equals(commandText)) {
                log.debug("Текст кнопки '{}' преобразован в команду '{}'", originalText, commandText);
                message.setText(commandText);
            }
            
            // Проверяем авторизацию пользователя
            Long telegramId = message.getFrom().getId();
            Optional<User> userOptional = userService.findByTelegramId(telegramId);
            
            // Если у пользователя есть активный черновик, обрабатываем текст в контексте диалога
            if (userOptional.isPresent() && conversationService.hasActiveDraft(userOptional.get().getId())) {
                handleConversationMessage(message, userOptional.get());
                return;
            }
            
            // Делегируем обработку команды в CommandDispatcher
            // CommandDispatcher сам проверит авторизацию через UserService
            String response = commandDispatcher.dispatch(message);
            
            log.info("Обновление успешно обработано: updateId={}, responseLength={}", 
                    update.getUpdateId(), 
                    response != null ? response.length() : 0);
            
            // Отправляем ответ пользователю с соответствующей клавиатурой
            if (response != null && !response.isBlank()) {
                try {
                    // Выбираем клавиатуру в зависимости от статуса авторизации
                    ReplyKeyboardMarkup keyboard = userOptional.isPresent()
                            ? keyboardService.createAuthorizedUserKeyboard()
                            : keyboardService.createUnauthorizedUserKeyboard();
                    
                    messageService.sendMessage(message.getChatId(), response, keyboard);
                    log.info("Ответ с клавиатурой успешно отправлен пользователю: chatId={}, responseLength={}, authorized={}", 
                            message.getChatId(), response.length(), userOptional.isPresent());
                } catch (Exception e) {
                    log.error("Ошибка при отправке ответа пользователю: chatId={}, error={}", 
                            message.getChatId(), e.getMessage(), e);
                }
            } else {
                log.warn("Пустой ответ от обработчика команды: updateId={}", update.getUpdateId());
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке обновления: updateId={}, error={}", 
                    update.getUpdateId(), e.getMessage(), e);
            
            // Не пробрасываем исключение дальше, чтобы не прерывать обработку других обновлений
            // В production можно добавить отправку сообщения об ошибке пользователю
        }
    }

    /**
     * Обрабатывает callback query от inline кнопок.
     * 
     * <p>Callback queries возникают, когда пользователь нажимает на inline кнопку.
     * Данные кнопки (callback data) содержат информацию о действии, которое нужно выполнить.</p>
     * 
     * <p>Поддерживаемые callback data:</p>
     * <ul>
     *   <li>date_YYYY-MM-DD - выбор даты из календаря</li>
     *   <li>calendar_YYYY-MM - навигация по месяцам календаря</li>
     *   <li>calendar_cancel - отмена выбора даты</li>
     *   <li>hour_HH - выбор часа</li>
     *   <li>time_HH:MM - выбор времени (час и минуты)</li>
     *   <li>time_back - возврат к выбору часа</li>
     *   <li>time_cancel - отмена выбора времени</li>
     *   <li>edit_event_{eventId} - редактирование события</li>
     *   <li>delete_event_{eventId} - удаление события</li>
     *   <li>confirm_delete_{eventId} - подтверждение удаления</li>
     * </ul>
     * 
     * @param callbackQuery объект CallbackQuery от Telegram
     */
    private void processCallbackQuery(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            log.warn("Получен null callback query");
            return;
        }
        
        String callbackData = callbackQuery.getData();
        Long telegramId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        
        log.info("Обработка callback query: data='{}', telegramId={}", callbackData, telegramId);
        
        try {
            // Игнорируем неактивные кнопки
            if (callbackData.equals("calendar_ignore") || callbackData.equals("time_ignore")) {
                messageService.answerCallbackQuery(callbackQuery.getId(), "");
                return;
            }
            
            // Проверяем авторизацию пользователя
            Optional<User> userOptional = userService.findByTelegramId(telegramId);
            if (userOptional.isEmpty()) {
                log.warn("Пользователь с telegramId={} не найден при обработке callback", telegramId);
                messageService.answerCallbackQuery(callbackQuery.getId(), 
                    "❌ Пользователь не найден. Используйте /start для регистрации.");
                return;
            }
            
            User user = userOptional.get();
            
            // Обработка выбора даты из календаря
            if (callbackData.startsWith("date_")) {
                handleDateSelection(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка навигации по календарю
            } else if (callbackData.startsWith("calendar_")) {
                handleCalendarNavigation(callbackData, user, chatId, messageId, callbackQuery.getId());
                
            // Обработка выбора часа
            } else if (callbackData.startsWith("hour_")) {
                handleHourSelection(callbackData, chatId, messageId, callbackQuery.getId());
                
            // Обработка выбора времени (час:минуты)
            } else if (callbackData.startsWith("time_") && callbackData.contains(":")) {
                handleTimeSelection(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка возврата к выбору часа
            } else if (callbackData.equals("time_back")) {
                handleTimeBack(chatId, messageId, callbackQuery.getId());
                
            // Обработка отмены выбора времени
            } else if (callbackData.equals("time_cancel")) {
                handleTimeCancel(user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка пропуска описания события
            } else if (callbackData.equals("skip_description")) {
                handleSkipDescription(user.getId(), chatId, callbackQuery.getId());
                
            // Обработка редактирования события
            } else if (callbackData.startsWith("edit_event_")) {
                Long eventId = extractEventId(callbackData, "edit_event_");
                String response = myEventsCommandHandler.handleEditCallback(eventId, user.getId());
                messageService.sendMessage(chatId, response);
                messageService.answerCallbackQuery(callbackQuery.getId(), "Обработано");
                
            // Обработка удаления события
            } else if (callbackData.startsWith("delete_event_")) {
                Long eventId = extractEventId(callbackData, "delete_event_");
                String response = myEventsCommandHandler.handleDeleteCallback(eventId, user.getId());
                messageService.sendMessage(chatId, response);
                messageService.answerCallbackQuery(callbackQuery.getId(), "Обработано");
                
            } else {
                log.warn("Неизвестный callback data: '{}'", callbackData);
                messageService.answerCallbackQuery(callbackQuery.getId(), "❌ Неизвестная команда");
            }
            
            log.info("Callback query успешно обработан: data='{}'", callbackData);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке callback query: data='{}', error={}", 
                    callbackData, e.getMessage(), e);
            
            try {
                messageService.answerCallbackQuery(callbackQuery.getId(), 
                    "❌ Произошла ошибка. Попробуйте еще раз.");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: error={}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Обрабатывает выбор даты из календаря.
     * Обновляет черновик события и показывает выбор часа.
     */
    private void handleDateSelection(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем дату из callback data (формат: date_YYYY-MM-DD)
            String dateStr = callbackData.substring(5); // Убираем "date_"
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
            
            // Обновляем черновик с выбранной датой
            conversationService.updateEventDate(userId, date);
            
            // Показываем выбор часа
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                keyboardService.createHourSelectionKeyboard();
            
            String message = String.format("✅ Дата выбрана: %s\n\nТеперь выберите час:", 
                date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Дата выбрана");
            
            log.info("Дата выбрана для пользователя {}: {}", userId, date);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке выбора даты: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка при выборе даты");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Обрабатывает навигацию по календарю (переключение месяцев).
     */
    private void handleCalendarNavigation(String callbackData, User user, Long chatId, 
                                          Integer messageId, String callbackQueryId) {
        try {
            if (callbackData.equals("calendar_cancel")) {
                messageService.editMessageText(chatId, messageId, 
                    "❌ Создание события отменено", null);
                messageService.answerCallbackQuery(callbackQueryId, "Отменено");
                return;
            }
            
            // Извлекаем год и месяц из callback data (формат: calendar_YYYY-MM)
            String dateStr = callbackData.substring(9); // Убираем "calendar_"
            String[] parts = dateStr.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            
            // Показываем календарь для выбранного месяца с событиями семьи
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                keyboardService.createCalendarKeyboard(year, month, user.getFamily().getId());
            
            messageService.editMessageText(chatId, messageId, 
                "📅 Выберите дату события:", keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Ошибка при навигации по календарю: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка навигации");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Обрабатывает выбор часа.
     * Показывает выбор минут для выбранного часа.
     */
    private void handleHourSelection(String callbackData, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем час из callback data (формат: hour_HH)
            String hourStr = callbackData.substring(5); // Убираем "hour_"
            int hour = Integer.parseInt(hourStr);
            
            // Показываем выбор минут
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                keyboardService.createMinuteSelectionKeyboard(hour);
            
            messageService.editMessageText(chatId, messageId, 
                String.format("✅ Час выбран: %02d:00\n\nТеперь выберите минуты:", hour), 
                keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Час выбран");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке выбора часа: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка при выборе часа");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Обрабатывает выбор времени (час и минуты).
     * Обновляет черновик и запрашивает название события.
     */
    private void handleTimeSelection(String callbackData, Long userId, Long chatId, 
                                     Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем время из callback data (формат: time_HH:MM)
            String timeStr = callbackData.substring(5); // Убираем "time_"
            java.time.LocalTime time = java.time.LocalTime.parse(timeStr);
            
            // Обновляем черновик с выбранным временем
            conversationService.updateEventTime(userId, time);
            
            // Запрашиваем название события
            String message = String.format("✅ Время выбрано: %s\n\n" +
                "Теперь отправьте название события:", 
                time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "Время выбрано");
            
            log.info("Время выбрано для пользователя {}: {}", userId, time);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке выбора времени: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка при выборе времени");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Обрабатывает возврат к выбору часа.
     */
    private void handleTimeBack(Long chatId, Integer messageId, String callbackQueryId) {
        try {
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                keyboardService.createHourSelectionKeyboard();
            
            messageService.editMessageText(chatId, messageId, 
                "🕐 Выберите час:", keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к выбору часа: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Обрабатывает отмену выбора времени.
     * Удаляет черновик события.
     */
    private void handleTimeCancel(Long userId, Long chatId, Integer messageId, String callbackQueryId) {
        try {
            conversationService.cancelEventCreation(userId);
            
            messageService.editMessageText(chatId, messageId, 
                "❌ Создание события отменено", null);
            messageService.answerCallbackQuery(callbackQueryId, "Отменено");
            
            log.info("Создание события отменено пользователем {}", userId);
            
        } catch (Exception e) {
            log.error("Ошибка при отмене создания события: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Обрабатывает пропуск описания события.
     * Завершает создание события без описания.
     */
    private void handleSkipDescription(Long userId, Long chatId, String callbackQueryId) {
        try {
            ru.golubyatnikov.family.calendar.bot.model.Event completedEvent = 
                conversationService.completeEventCreation(userId, null);
            
            // Формируем подтверждение
            String response = String.format(
                "✅ *Событие успешно создано!*\n\n" +
                "📅 Дата: %s\n" +
                "🕐 Время: %s\n" +
                "📝 Название: %s",
                completedEvent.getFormattedDate(),
                completedEvent.getFormattedTime(),
                completedEvent.getTitle()
            );
            
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Событие создано");
            
            log.info("Событие успешно создано без описания: eventId={}, userId={}", 
                completedEvent.getId(), userId);
            
        } catch (Exception e) {
            log.error("Ошибка при пропуске описания события: userId={}, error={}", 
                userId, e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Обрабатывает текстовое сообщение в контексте активного диалога создания события.
     * Определяет текущий шаг диалога и обрабатывает сообщение соответственно.
     */
    private void handleConversationMessage(Message message, User user) {
        try {
            ru.golubyatnikov.family.calendar.bot.model.Event draft = 
                conversationService.getActiveDraft(user.getId());
            
            ConversationService.ConversationStep step = conversationService.getCurrentStep(draft);
            String text = message.getText();
            Long chatId = message.getChatId();
            
            log.info("Обработка сообщения в контексте диалога: userId={}, step={}, text='{}'", 
                user.getId(), step, text);
            
            switch (step) {
                case WAITING_FOR_TITLE -> {
                    // Пользователь отправил название события
                    conversationService.updateEventTitle(user.getId(), text);
                    
                    // Запрашиваем описание с inline-кнопкой "Пропустить"
                    String response = "✅ Название сохранено: " + text + "\n\n" +
                        "Теперь отправьте описание события или нажмите кнопку 'Пропустить':";
                    
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup skipKeyboard = 
                        keyboardService.createSkipDescriptionKeyboard();
                    
                    messageService.sendMessageWithInlineKeyboard(chatId, response, skipKeyboard);
                    
                    log.info("Название события сохранено для пользователя {}", user.getId());
                }
                
                case WAITING_FOR_DESCRIPTION -> {
                    // Пользователь отправил описание или пропустил его
                    String description = text.equalsIgnoreCase("пропустить") ? null : text;
                    
                    ru.golubyatnikov.family.calendar.bot.model.Event completedEvent = 
                        conversationService.completeEventCreation(user.getId(), description);
                    
                    // Формируем подтверждение
                    String response = String.format(
                        "✅ *Событие успешно создано!*\n\n" +
                        "📅 Дата: %s\n" +
                        "🕐 Время: %s\n" +
                        "📝 Название: %s\n" +
                        "%s",
                        completedEvent.getFormattedDate(),
                        completedEvent.getFormattedTime(),
                        completedEvent.getTitle(),
                        description != null ? "📄 Описание: " + description : ""
                    );
                    
                    ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                    messageService.sendMessage(chatId, response, keyboard);
                    
                    log.info("Событие успешно создано: eventId={}, userId={}", 
                        completedEvent.getId(), user.getId());
                }
                
                default -> {
                    log.warn("Неожиданный шаг диалога: {}", step);
                    String response = "❌ Произошла ошибка. Попробуйте начать заново с команды /add_event";
                    ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                    messageService.sendMessage(chatId, response, keyboard);
                }
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке сообщения в контексте диалога: userId={}, error={}", 
                user.getId(), e.getMessage(), e);
            
            try {
                String response = "❌ Произошла ошибка. Попробуйте начать заново с команды /add_event";
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * Извлекает ID события из callback data.
     * 
     * <p>Callback data имеет формат: "{prefix}_{eventId}"
     * Например: "edit_event_123" или "delete_event_456"</p>
     * 
     * @param callbackData строка с callback data
     * @param prefix префикс для удаления
     * @return ID события
     * @throws NumberFormatException если ID не является числом
     */
    private Long extractEventId(String callbackData, String prefix) {
        String eventIdStr = callbackData.substring(prefix.length());
        return Long.parseLong(eventIdStr);
    }
}
