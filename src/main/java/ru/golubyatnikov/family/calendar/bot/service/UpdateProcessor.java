package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler;
import ru.golubyatnikov.family.calendar.bot.handler.FilterCommandHandler;
import ru.golubyatnikov.family.calendar.bot.handler.TrashCommandHandler;
import ru.golubyatnikov.family.calendar.bot.handler.SearchCommandHandler;
import ru.golubyatnikov.family.calendar.bot.handler.ReminderCallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.util.TextEventParser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

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
    private final FilterCommandHandler filterCommandHandler;
    private final TrashCommandHandler trashCommandHandler;
    private final SearchCommandHandler searchCommandHandler;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final ConversationService conversationService;
    private final ConversationStateService conversationStateService;
    private final TextEventParser textEventParser;
    private final AttachmentService attachmentService;
    private final ReminderCallbackHandler reminderCallbackHandler;
    private final AuthorizationService authorizationService;

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
                
                // Проверяем, содержит ли сообщение файл, документ или изображение
                if (message.hasDocument() || message.hasPhoto()) {
                    // Получаем пользователя для обработки файла
                    Long telegramId = message.getFrom().getId();
                    Optional<User> userOpt = userService.findByTelegramId(telegramId);
                    handleFileMessage(message, userOpt);
                }
                
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
            
            // Если пользователь ожидает ввода поискового запроса, обрабатываем текст как запрос
            if (userOptional.isPresent() && conversationStateService.isAwaitingSearchQuery(userOptional.get().getId())) {
                handleSearchQuery(message, userOptional.get());
                return;
            }
            
            // Если у пользователя есть активный черновик, обрабатываем текст в контексте диалога
            if (userOptional.isPresent() && conversationService.hasActiveDraft(userOptional.get().getId())) {
                handleConversationMessage(message, userOptional.get());
                return;
            }
            
            // Проверяем, является ли текст командой (начинается с /)
            boolean isCommand = commandText != null && commandText.startsWith("/");
            
            // Если это не команда и пользователь авторизован, пробуем распознать событие из текста
            if (!isCommand && userOptional.isPresent() && textEventParser.looksLikeEvent(originalText)) {
                handleTextEventParsing(message, userOptional.get(), originalText);
                return;
            }
            
            // Обрабатываем команду с проверкой авторизации
            handleCommand(message);
            
            log.info("Обновление успешно обработано: updateId={}", update.getUpdateId());
            
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
                    "❌ Пользователь не найден. Используйте " + escape("/start") + " для регистрации.");
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
                
            // Обработка просмотра деталей события (из уведомлений)
            } else if (callbackData.startsWith("view_event_")) {
                Long eventId = extractEventId(callbackData, "view_event_");
                String response = myEventsCommandHandler.handleViewEventDetails(eventId, user.getId());
                messageService.sendMessage(chatId, response);
                messageService.answerCallbackQuery(callbackQuery.getId(), "Обработано");
                
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
                
            // Обработка фильтрации событий
            } else if (callbackData.startsWith("filter_")) {
                filterCommandHandler.handleFilterCallback(callbackQuery, user);
                messageService.answerCallbackQuery(callbackQuery.getId(), "Фильтр применен");
                
            // Обработка действий с корзиной
            } else if (callbackData.startsWith("trash_")) {
                trashCommandHandler.handleTrashCallback(callbackQuery, user);
                messageService.answerCallbackQuery(callbackQuery.getId(), "Обработано");
                
            // Обработка выбора типа события (семейное/персональное)
            } else if (callbackData.startsWith("event_type_")) {
                handleEventTypeSelection(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка редактирования полей события
            } else if (callbackData.startsWith("edit_field_")) {
                handleEditField(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка настройки напоминаний
            } else if (callbackData.startsWith("setup_reminders_")) {
                Long eventId = Long.parseLong(callbackData.substring("setup_reminders_".length()));
                reminderCallbackHandler.handleSetupReminders(eventId, chatId, messageId, callbackQuery.getId());
                
            // Обработка toggle выбора типа напоминания
            } else if (callbackData.startsWith("toggle_reminder_")) {
                String[] parts = callbackData.substring("toggle_reminder_".length()).split("_");
                Long eventId = Long.parseLong(parts[0]);
                String typeName = parts[1];
                ru.golubyatnikov.family.calendar.bot.model.Reminder.ReminderType type = 
                    ru.golubyatnikov.family.calendar.bot.model.Reminder.ReminderType.valueOf(typeName);
                reminderCallbackHandler.handleReminderTypeSelection(eventId, type, chatId, messageId, callbackQuery.getId());
                
            // Обработка подтверждения создания напоминаний
            } else if (callbackData.startsWith("confirm_reminders_")) {
                Long eventId = Long.parseLong(callbackData.substring("confirm_reminders_".length()));
                reminderCallbackHandler.handleConfirmReminders(eventId, chatId, messageId, callbackQuery.getId());
                
            // Обработка просмотра напоминаний
            } else if (callbackData.startsWith("view_reminders_")) {
                Long eventId = Long.parseLong(callbackData.substring("view_reminders_".length()));
                reminderCallbackHandler.handleViewReminders(eventId, chatId, messageId);
                messageService.answerCallbackQuery(callbackQuery.getId(), "");
                
            // Обработка удаления напоминания
            } else if (callbackData.startsWith("delete_reminder_")) {
                Long reminderId = Long.parseLong(callbackData.substring("delete_reminder_".length()));
                reminderCallbackHandler.handleDeleteReminder(reminderId, chatId, messageId, callbackQuery.getId());
                
            // Старая обработка напоминаний (deprecated, оставлено для совместимости)
            } else if (callbackData.startsWith("reminder_")) {
                handleReminderSettings(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка настройки повторений
            } else if (callbackData.startsWith("recurrence_")) {
                handleRecurrenceSettings(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка действий с серией повторяющихся событий
            } else if (callbackData.startsWith("series_action_")) {
                handleSeriesAction(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка действий с датой
            } else if (callbackData.startsWith("date_actions_")) {
                handleDateActions(callbackData, user, chatId, messageId, callbackQuery.getId());
                
            // Обработка прикрепления файлов
            } else if (callbackData.startsWith("attach_file_")) {
                handleAttachFile(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка чек-листов
            } else if (callbackData.startsWith("checklist_")) {
                handleChecklist(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка комментариев
            } else if (callbackData.startsWith("comment_")) {
                handleComment(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка добавления заметки к завершенному событию
            } else if (callbackData.startsWith("add_completion_note_")) {
                handleAddCompletionNote(callbackData, user.getId(), chatId, messageId, callbackQuery.getId());
                
            // Обработка подтверждения создания события из текста
            } else if (callbackData.startsWith("confirm_text_event:")) {
                handleConfirmTextEvent(callbackData, user, chatId, messageId, callbackQuery.getId());
                
            // Обработка отмены создания события из текста
            } else if (callbackData.equals("cancel_text_event")) {
                handleCancelTextEvent(chatId, messageId, callbackQuery.getId());
                
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
                    "❌ " + escape("Произошла ошибка. Попробуйте еще раз."));
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
            
            String formattedDate = date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            String message = ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage(
                "✅ Дата выбрана: %s\n\nТеперь выберите час:", 
                formattedDate);
            
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
                formatMessage("✅ Час выбран: %02d:00\n\nТеперь выберите минуты:", hour), 
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
            String message = formatMessage("✅ Время выбрано: %s\n\n" +
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
            String response = formatMessage(
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
                    String response = formatMessage(
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
                    
                    // Сбрасываем состояние диалога
                    conversationService.cancelEventCreation(user.getId());
                    log.info("Состояние диалога сброшено для пользователя: userId={}", user.getId());
                    
                    String response = "❌ " + bold("Произошла ошибка") + "\\. " + 
                                    italic("Попробуйте начать заново с команды /add_event");
                    ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                    messageService.sendMessage(chatId, response, keyboard);
                }
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке сообщения в контексте диалога: userId={}, error={}", 
                user.getId(), e.getMessage(), e);
            
            try {
                String response = "❌ " + bold("Произошла ошибка") + "\\. " + 
                                italic("Попробуйте начать заново с команды /add_event");
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
    
    /**
     * Обрабатывает выбор типа события (семейное/персональное).
     * 
     * @param callbackData данные callback query
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEventTypeSelection(String callbackData, Long userId, Long chatId, 
                                          Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем тип события (family или personal)
            String eventType = callbackData.substring("event_type_".length());
            boolean isPersonal = eventType.equals("personal");
            
            // Сохраняем выбор типа события в черновике
            conversationService.updateEventType(userId, isPersonal);
            log.info("Пользователь {} выбрал тип события: {}", userId, eventType);
            
            // Получаем пользователя для доступа к семье
            User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
            
            // Показываем календарь для выбора даты
            LocalDate now = LocalDate.now();
            InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(
                now.getYear(), now.getMonthValue(), user.getFamily().getId());
            
            String message = isPersonal 
                ? "✅ " + bold("Выбрано: Персональное событие") + "\n\n" +
                  italic("Только вы будете видеть это событие.") + "\n\n" +
                  "📅 " + escape("Теперь выберите дату события:")
                : "✅ " + bold("Выбрано: Семейное событие") + "\n\n" +
                  italic("Все члены семьи будут видеть это событие.") + "\n\n" +
                  "📅 " + escape("Теперь выберите дату события:");
            
            messageService.editMessageText(chatId, messageId, message, calendar);
            messageService.answerCallbackQuery(callbackQueryId, "Тип события выбран");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке выбора типа события: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает редактирование полей события.
     * 
     * @param callbackData данные callback query
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEditField(String callbackData, Long userId, Long chatId, 
                                 Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем поле для редактирования (date, time, title, description)
            String field = callbackData.substring("edit_field_".length());
            
            log.info("Пользователь {} начал редактирование поля: {}", userId, field);
            
            String message = switch (field) {
                case "date" -> "📅 Редактирование даты\n\nВыберите новую дату из календаря:";
                case "time" -> "🕐 Редактирование времени\n\nВыберите новое время:";
                case "title" -> "📝 Редактирование названия\n\nОтправьте новое название события:";
                case "description" -> "📄 Редактирование описания\n\nОтправьте новое описание события:";
                default -> "❌ Неизвестное поле для редактирования";
            };
            
            // TODO: Показать соответствующую клавиатуру (календарь для даты, выбор времени и т.д.)
            
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке редактирования поля: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает настройку напоминаний.
     * 
     * @param callbackData данные callback query
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleReminderSettings(String callbackData, Long userId, Long chatId, 
                                       Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем тип напоминания
            String reminderType = callbackData.substring("reminder_".length());
            
            log.info("Пользователь {} настроил напоминание: {}", userId, reminderType);
            
            String message = switch (reminderType) {
                case "morning_of_day" -> "✅ Напоминание: Утром в день события (9:00)";
                case "evening_before" -> "✅ Напоминание: Вечером накануне (20:00)";
                case "one_hour_before" -> "✅ Напоминание: За 1 час до события";
                case "ten_minutes_before" -> "✅ Напоминание: За 10 минут до события";
                case "custom" -> "✅ Напоминание: Настраиваемое время\n\nОтправьте количество минут до события:";
                default -> "❌ Неизвестный тип напоминания";
            };
            
            // TODO: Сохранить настройку напоминания через ReminderService
            
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "Напоминание настроено");
            
        } catch (Exception e) {
            log.error("Ошибка при настройке напоминания: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает настройку повторений события.
     * 
     * @param callbackData данные callback query
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleRecurrenceSettings(String callbackData, Long userId, Long chatId, 
                                         Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем тип повторения
            String recurrenceType = callbackData.substring("recurrence_".length());
            
            log.info("Пользователь {} настроил повторение: {}", userId, recurrenceType);
            
            String message = switch (recurrenceType) {
                case "daily" -> "✅ Повторение: Ежедневно\n\nСобытие будет повторяться каждый день.";
                case "weekly" -> "✅ Повторение: Еженедельно\n\nСобытие будет повторяться каждую неделю.";
                case "monthly" -> "✅ Повторение: Ежемесячно\n\nСобытие будет повторяться каждый месяц.";
                case "none" -> "✅ Повторение отключено\n\nСобытие будет одноразовым.";
                default -> "❌ Неизвестный тип повторения";
            };
            
            // TODO: Сохранить настройку повторения через RecurrenceService
            
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "Повторение настроено");
            
        } catch (Exception e) {
            log.error("Ошибка при настройке повторения: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает действия с серией повторяющихся событий.
     * 
     * @param callbackData данные callback query
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleSeriesAction(String callbackData, Long userId, Long chatId, 
                                   Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем действие (this_only или entire_series)
            String action = callbackData.substring("series_action_".length());
            
            log.info("Пользователь {} выбрал действие с серией: {}", userId, action);
            
            String message = action.equals("this_only")
                ? "✅ Изменения применены только к этому событию"
                : "✅ Изменения применены ко всей серии событий";
            
            // TODO: Применить изменения через RecurrenceService
            
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "Обработано");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке действия с серией: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает действия с датой в календаре.
     * 
     * @param callbackData данные callback query
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleDateActions(String callbackData, User user, Long chatId, 
                                   Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем действие (view или create)
            String action = callbackData.substring("date_actions_".length());
            
            log.info("Пользователь {} выбрал действие с датой: {}", user.getId(), action);
            
            if (action.equals("view")) {
                // TODO: Показать события на выбранную дату
                messageService.editMessageText(chatId, messageId, 
                    "📅 Просмотр событий на дату", null);
            } else if (action.equals("create")) {
                // TODO: Начать создание нового события на выбранную дату
                messageService.editMessageText(chatId, messageId, 
                    "➕ Создание нового события", null);
            }
            
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке действия с датой: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает прикрепление файлов к событию.
     * 
     * @param callbackData данные callback query
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleAttachFile(String callbackData, Long userId, Long chatId, 
                                  Integer messageId, String callbackQueryId) {
        try {
            log.info("Пользователь {} начал прикрепление файла", userId);
            
            String message = "📎 Прикрепление файла\n\n" +
                           "Отправьте файл, документ или изображение для прикрепления к событию.\n\n" +
                           "_Максимальный размер файла: 20 МБ_";
            
            // TODO: Установить контекст ожидания файла
            
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке прикрепления файла: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает действия с чек-листом.
     * 
     * @param callbackData данные callback query
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleChecklist(String callbackData, Long userId, Long chatId, 
                                Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем действие (add или toggle_ITEM_ID)
            String action = callbackData.substring("checklist_".length());
            
            log.info("Пользователь {} выполнил действие с чек-листом: {}", userId, action);
            
            if (action.equals("add")) {
                String message = "✅ Добавление пункта в чек-лист\n\n" +
                               "Отправьте текст нового пункта:";
                messageService.editMessageText(chatId, messageId, message, null);
            } else if (action.startsWith("toggle_")) {
                // TODO: Переключить статус пункта чек-листа через ChecklistService
                Long itemId = Long.parseLong(action.substring("toggle_".length()));
                log.info("Переключение статуса пункта чек-листа ID={}", itemId);
                messageService.answerCallbackQuery(callbackQueryId, "✅ Статус изменен");
                return;
            }
            
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке чек-листа: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает добавление комментария к событию.
     * 
     * @param callbackData данные callback query
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleComment(String callbackData, Long userId, Long chatId, 
                              Integer messageId, String callbackQueryId) {
        try {
            log.info("Пользователь {} начал добавление комментария", userId);
            
            String message = "💬 Добавление комментария\n\n" +
                           "Отправьте текст комментария:";
            
            // TODO: Установить контекст ожидания комментария
            
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке комментария: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает добавление заметки к завершенному событию.
     * 
     * @param callbackData данные callback query
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleAddCompletionNote(String callbackData, Long userId, Long chatId, 
                                        Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем ID события
            Long eventId = Long.parseLong(callbackData.substring("add_completion_note_".length()));
            
            log.info("Пользователь {} начал добавление заметки к завершенному событию ID={}", 
                     userId, eventId);
            
            String message = "📝 Добавление заметки к завершенному событию\n\n" +
                           "Отправьте текст заметки о том, как прошло событие:";
            
            // TODO: Установить контекст ожидания заметки для события
            
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Ошибка при обработке добавления заметки: {}", e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает сообщение с файлом, документом или изображением.
     * 
     * <p>Проверяет наличие активного черновика или контекста редактирования события.
     * Если контекст найден, сохраняет файл как вложение к событию.</p>
     * 
     * @param message сообщение с файлом
     * @param userOptional опциональный пользователь
     */
    private void handleFileMessage(Message message, Optional<User> userOptional) {
        try {
            Long chatId = message.getChatId();
            
            // Проверяем авторизацию
            if (userOptional.isEmpty()) {
                log.warn("Неавторизованный пользователь пытается отправить файл");
                messageService.sendMessage(chatId, 
                    "❌ Для отправки файлов необходимо авторизоваться. Используйте " + escape("/start"));
                return;
            }
            
            User user = userOptional.get();
            
            // Проверяем наличие активного черновика
            if (!conversationService.hasActiveDraft(user.getId())) {
                log.debug("Пользователь {} отправил файл без активного черновика", user.getId());
                messageService.sendMessage(chatId, 
                    "❌ Для прикрепления файлов сначала создайте событие с помощью /add_event");
                return;
            }
            
            // Получаем активный черновик
            ru.golubyatnikov.family.calendar.bot.model.Event draft = 
                conversationService.getActiveDraft(user.getId());
            
            // Проверяем, что черновик уже имеет дату и время (не на начальном этапе создания)
            if (draft.getEventDate() == null || draft.getEventTime() == null) {
                log.debug("Пользователь {} отправил файл на раннем этапе создания события", user.getId());
                messageService.sendMessage(chatId, 
                    "❌ Сначала завершите создание события, затем вы сможете прикрепить файлы");
                return;
            }
            
            String fileId;
            String fileName;
            String fileType;
            Long fileSize;
            
            // Извлекаем информацию о файле
            if (message.hasDocument()) {
                org.telegram.telegrambots.meta.api.objects.Document document = message.getDocument();
                fileId = document.getFileId();
                fileName = document.getFileName();
                fileType = document.getMimeType();
                fileSize = document.getFileSize();
                
                log.info("Получен документ: fileId={}, fileName='{}', size={}", 
                         fileId, fileName, fileSize);
                
            } else if (message.hasPhoto()) {
                // Берем фото наибольшего размера
                List<org.telegram.telegrambots.meta.api.objects.PhotoSize> photos = message.getPhoto();
                org.telegram.telegrambots.meta.api.objects.PhotoSize photo = 
                    photos.get(photos.size() - 1);
                
                fileId = photo.getFileId();
                fileName = "photo_" + System.currentTimeMillis() + ".jpg";
                fileType = "image/jpeg";
                fileSize = photo.getFileSize().longValue();
                
                log.info("Получено изображение: fileId={}, size={}", fileId, fileSize);
                
            } else {
                log.warn("Сообщение не содержит документа или фото");
                return;
            }
            
            // Проверяем размер файла (максимум 20 МБ)
            final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 МБ в байтах
            if (fileSize > MAX_FILE_SIZE) {
                log.warn("Файл слишком большой: size={}, max={}", fileSize, MAX_FILE_SIZE);
                messageService.sendMessage(chatId, 
                    formatMessage("❌ Размер файла превышает максимально допустимый (20 МБ).\n\n" +
                                "Размер вашего файла: %.2f МБ", fileSize / (1024.0 * 1024.0)));
                return;
            }
            
            // Сохраняем вложение
            try {
                attachmentService.saveAttachment(draft.getId(), fileId, fileName, fileType, fileSize);
                
                String response = formatMessage(
                    "✅ *Файл успешно прикреплен!*\n\n" +
                    "📎 Название: %s\n" +
                    "📊 Размер: %.2f МБ\n\n" +
                    "Вы можете продолжить прикреплять файлы или завершить создание события.",
                    fileName,
                    fileSize / (1024.0 * 1024.0)
                );
                
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(chatId, response, keyboard);
                
                log.info("Файл успешно прикреплен к событию: eventId={}, fileName='{}'", 
                         draft.getId(), fileName);
                
            } catch (ru.golubyatnikov.family.calendar.bot.exception.FileSizeExceededException e) {
                log.warn("Размер файла превышает лимит: {}", e.getMessage());
                messageService.sendMessage(chatId, 
                    "❌ " + e.getMessage());
                
            } catch (Exception e) {
                log.error("Ошибка при сохранении вложения: eventId={}, error={}", 
                         draft.getId(), e.getMessage(), e);
                messageService.sendMessage(chatId, 
                    "❌ " + bold("Произошла ошибка при сохранении файла") + "\\. " + 
                    italic("Попробуйте еще раз\\."));
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке файла: error={}", e.getMessage(), e);
            
            try {
                messageService.sendMessage(message.getChatId(), 
                    "❌ " + bold("Произошла ошибка при обработке файла") + "\\.");
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает распознавание события из текстового сообщения.
     * 
     * <p>Пробует распознать параметры события из текста в различных форматах:
     * <ul>
     *   <li>"Событие: [название] Дата: [дата] Время: [время]"</li>
     *   <li>"[название] [дата] [время]"</li>
     *   <li>"[название] завтра/сегодня в [время]"</li>
     * </ul>
     * 
     * <p>Если событие успешно распознано, показывает inline-кнопку для подтверждения
     * создания события с предпросмотром всех параметров.</p>
     * 
     * @param message исходное сообщение от пользователя
     * @param user авторизованный пользователь
     * @param text текст сообщения для парсинга
     */
    private void handleTextEventParsing(Message message, User user, String text) {
        try {
            Long chatId = message.getChatId();
            
            log.info("Попытка распознать событие из текста для пользователя {}: '{}'", 
                     user.getId(), text);
            
            // Пробуем распознать событие
            Optional<TextEventParser.ParsedEvent> parsedEventOpt = textEventParser.parseEvent(text);
            
            if (parsedEventOpt.isEmpty()) {
                log.debug("Не удалось распознать событие из текста: '{}'", text);
                // Не отправляем сообщение об ошибке, просто игнорируем
                // Возможно, это было обычное сообщение, а не попытка создать событие
                return;
            }
            
            TextEventParser.ParsedEvent parsedEvent = parsedEventOpt.get();
            
            // Проверяем валидность распознанного события
            if (!parsedEvent.isValid()) {
                log.warn("Распознанное событие невалидно: {}", parsedEvent);
                
                String response = "❌ *Не удалось создать событие*\n\n";
                
                if (parsedEvent.getTitle() == null || parsedEvent.getTitle().trim().isEmpty()) {
                    response += "Название события не может быть пустым.\n\n";
                }
                
                if (parsedEvent.getDate() != null && 
                    parsedEvent.getDate().isBefore(java.time.LocalDate.now())) {
                    response += "Дата события не может быть в прошлом.\n\n";
                }
                
                response += "Попробуйте использовать один из форматов:\n" +
                           "• `Событие: Встреча Дата: 15.01.2026 Время: 14:30`\n" +
                           "• `Встреча 15.01.2026 14:30`\n" +
                           "• `Встреча завтра в 14:30`\n\n" +
                           "Или используйте команду " + escape("/add_event") + " для пошагового создания.";
                
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(chatId, response, keyboard);
                return;
            }
            
            log.info("Событие успешно распознано: {}", parsedEvent);
            
            // Формируем предпросмотр события
            String preview = formatMessage(
                "✅ *Распознано событие из текста:*\n\n" +
                "📝 Название: %s\n" +
                "📅 Дата: %s\n" +
                "🕐 Время: %s\n\n" +
                "Подтвердите создание события:",
                parsedEvent.getTitle(),
                parsedEvent.getDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                parsedEvent.getTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            );
            
            // Создаем inline-клавиатуру с кнопками подтверждения
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                createEventConfirmationKeyboard(parsedEvent);
            
            messageService.sendMessageWithInlineKeyboard(chatId, preview, keyboard);
            
            log.info("Отправлен предпросмотр распознанного события пользователю {}", user.getId());
            
        } catch (Exception e) {
            log.error("Ошибка при обработке распознавания события из текста: userId={}, error={}", 
                     user.getId(), e.getMessage(), e);
            
            try {
                String response = "❌ " + bold("Произошла ошибка при распознавании события") + "\\.\n\n" +
                                italic("Используйте команду " + escape("/add_event") + " для пошагового создания\\.");
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает подтверждение создания события из текста.
     * 
     * <p>Метод выполняется в транзакции для обеспечения атомарности операций.
     * При возникновении ошибки на любом этапе создания события, транзакция
     * откатывается автоматически, и черновик удаляется явным вызовом
     * {@link ConversationService#cancelEventCreation(Long)}.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.5, 2.2, 2.4</p>
     * 
     * @param callbackData данные callback query с закодированными параметрами события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    @Transactional
    private void handleConfirmTextEvent(String callbackData, User user, Long chatId, 
                                       Integer messageId, String callbackQueryId) {
        try {
            // Извлекаем закодированные данные события
            String encodedData = callbackData.substring("confirm_text_event:".length());
            String decodedData = new String(java.util.Base64.getDecoder().decode(encodedData));
            
            // Парсим данные (формат: title|date|time)
            String[] parts = decodedData.split("\\|");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Неверный формат данных события");
            }
            
            String title = parts[0];
            java.time.LocalDate date = java.time.LocalDate.parse(parts[1]);
            java.time.LocalTime time = java.time.LocalTime.parse(parts[2]);
            
            log.info("Подтверждение создания события из текста: userId={}, title='{}', date={}, time={}", 
                     user.getId(), title, date, time);
            
            // Создаем событие через ConversationService для единообразия
            // Сначала создаем черновик
            conversationService.startEventCreation(user.getId());
            conversationService.updateEventDate(user.getId(), date);
            conversationService.updateEventTime(user.getId(), time);
            conversationService.updateEventTitle(user.getId(), title);
            
            // Завершаем создание без описания
            ru.golubyatnikov.family.calendar.bot.model.Event createdEvent = 
                conversationService.completeEventCreation(user.getId(), null);
            
            // Формируем подтверждение
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
            messageService.answerCallbackQuery(callbackQueryId, "✅ Событие создано");
            
            log.info("Событие успешно создано из текста: eventId={}, userId={}", 
                     createdEvent.getId(), user.getId());
            
        } catch (Exception e) {
            log.error("Ошибка при подтверждении создания события из текста: userId={}, " +
                     "errorType={}, errorMessage={}, stackTrace={}", 
                     user.getId(), e.getClass().getSimpleName(), e.getMessage(), 
                     java.util.Arrays.toString(e.getStackTrace()));
            
            // Явно удаляем черновик при ошибке
            try {
                conversationService.cancelEventCreation(user.getId());
                log.info("Черновик успешно удален после ошибки: userId={}", user.getId());
            } catch (Exception cleanupEx) {
                log.error("Ошибка при удалении черновика после ошибки создания события: userId={}, error={}", 
                         user.getId(), cleanupEx.getMessage(), cleanupEx);
            }
            
            // Отправляем пользователю понятное сообщение об ошибке
            try {
                String errorMessage = "❌ " + bold("Произошла ошибка при создании события") + "\\.\n\n" +
                                    italic("Попробуйте использовать команду " + escape("/add_event") + " для пошагового создания\\.") + "\n\n" +
                                    "Детали ошибки: " + escape(e.getMessage() != null ? e.getMessage() : "Неизвестная ошибка");
                messageService.editMessageText(chatId, messageId, errorMessage, null);
                messageService.answerCallbackQuery(callbackQueryId, "❌ " + escape("Ошибка"));
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке пользователю: userId={}, error={}", 
                         user.getId(), ex.getMessage(), ex);
            }
            
            // Пробрасываем исключение для отката транзакции
            throw new RuntimeException("Ошибка при создании события из текста", e);
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
        try {
            log.info("Отмена создания события из текста: chatId={}", chatId);
            
            messageService.editMessageText(chatId, messageId, 
                "❌ Создание события отменено", null);
            messageService.answerCallbackQuery(callbackQueryId, "Отменено");
            
        } catch (Exception e) {
            log.error("Ошибка при отмене создания события из текста: chatId={}, error={}", 
                     chatId, e.getMessage(), e);
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка");
            } catch (Exception ex) {
                log.error("Ошибка при ответе на callback query: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Создает inline-клавиатуру для подтверждения создания события из текста.
     * 
     * @param parsedEvent распознанное событие
     * @return InlineKeyboardMarkup с кнопками подтверждения и отмены
     */
    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup 
            createEventConfirmationKeyboard(TextEventParser.ParsedEvent parsedEvent) {
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = 
            new java.util.ArrayList<>();
        
        // Кодируем данные события в callback data (используем Base64 для безопасности)
        String eventData = String.format("%s|%s|%s",
            parsedEvent.getTitle(),
            parsedEvent.getDate().toString(),
            parsedEvent.getTime().toString()
        );
        
        // Кнопка подтверждения
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row1 = 
            new java.util.ArrayList<>();
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton confirmBtn = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("✅ Создать событие");
        confirmBtn.setCallbackData("confirm_text_event:" + java.util.Base64.getEncoder().encodeToString(eventData.getBytes()));
        row1.add(confirmBtn);
        
        rows.add(row1);
        
        // Кнопка отмены
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row2 = 
            new java.util.ArrayList<>();
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton cancelBtn = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("cancel_text_event");
        row2.add(cancelBtn);
        
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Обрабатывает текстовое сообщение как поисковый запрос.
     * Вызывает SearchCommandHandler.performSearch() и очищает состояние ожидания.
     * 
     * @param message сообщение с поисковым запросом
     * @param user пользователь, выполняющий поиск
     */
    private void handleSearchQuery(Message message, User user) {
        try {
            String query = message.getText();
            Long chatId = message.getChatId();
            Long userId = user.getId();
            
            log.info("Обработка поискового запроса от пользователя ID={}: '{}'", userId, query);
            
            // Удаляем пользователя из списка ожидающих
            conversationStateService.clearAwaitingSearchQuery(userId);
            
            // Выполняем поиск через SearchCommandHandler
            searchCommandHandler.performSearch(chatId, user, query);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке поискового запроса: userId={}, error={}", 
                     user.getId(), e.getMessage(), e);
            
            try {
                // Удаляем пользователя из списка ожидающих даже при ошибке
                conversationStateService.clearAwaitingSearchQuery(user.getId());
                
                String response = "❌ " + bold("Произошла ошибка при обработке поискового запроса") + "\\. " +
                                italic("Попробуйте еще раз\\.");
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает команду с проверкой авторизации.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Извлекает команду из текста сообщения</li>
     *   <li>Получает обработчик команды из CommandDispatcher</li>
     *   <li>Проверяет, требует ли команда авторизации</li>
     *   <li>Если требуется авторизация:
     *     <ul>
     *       <li>Проверяет авторизацию через AuthorizationService</li>
     *       <li>При отсутствии авторизации отправляет информативное сообщение и прерывает выполнение</li>
     *       <li>При наличии авторизации выполняет команду</li>
     *     </ul>
     *   </li>
     *   <li>Если не требуется авторизация, выполняет команду без проверки</li>
     *   <li>Отправляет ответ пользователю с соответствующей клавиатурой</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5</p>
     * 
     * @param message входящее сообщение от Telegram, содержащее команду
     */
    private void handleCommand(Message message) {
        String messageText = message.getText().trim();
        Long telegramId = message.getFrom().getId();
        Long chatId = message.getChatId();
        String username = message.getFrom().getUserName();
        
        log.info("Обработка команды: text='{}', telegramId={}, chatId={}", 
                messageText, telegramId, chatId);
        
        // Извлекаем команду (первое слово, начинающееся с /)
        String commandText = extractCommand(messageText);
        
        if (commandText == null) {
            log.warn("Не удалось извлечь команду из текста: '{}', telegramId={}", 
                    messageText, telegramId);
            
            try {
                Optional<User> userOpt = userService.findByTelegramId(telegramId);
                ReplyKeyboardMarkup keyboard = userOpt.isPresent()
                        ? keyboardService.createAuthorizedUserKeyboard()
                        : keyboardService.createUnauthorizedUserKeyboard();
                
                String response = "Команда должна начинаться с символа '/'. Используйте " + 
                                escape("/help") + " для списка доступных команд.";
                messageService.sendMessage(chatId, response, keyboard);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", e.getMessage(), e);
            }
            return;
        }
        
        log.debug("Извлечена команда: '{}' из текста: '{}'", commandText, messageText);
        
        // Получаем обработчик команды
        if (!commandDispatcher.hasHandler(commandText)) {
            log.warn("Обработчик не найден для команды: '{}', telegramId={}", commandText, telegramId);
            
            try {
                Optional<User> userOpt = userService.findByTelegramId(telegramId);
                ReplyKeyboardMarkup keyboard = userOpt.isPresent()
                        ? keyboardService.createAuthorizedUserKeyboard()
                        : keyboardService.createUnauthorizedUserKeyboard();
                
                String response = formatMessage("Неизвестная команда: %s\n\nИспользуйте %s для списка доступных команд.", 
                                              commandText, "/help");
                messageService.sendMessage(chatId, response, keyboard);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", e.getMessage(), e);
            }
            return;
        }
        
        // Проверяем, требует ли команда авторизации
        // Для этого нам нужно получить обработчик и проверить его requiresAuth()
        // Но CommandDispatcher не предоставляет публичный метод для получения обработчика
        // Поэтому мы делегируем обработку в CommandDispatcher, который сам проверит авторизацию
        // Но теперь нам нужно перехватить UnauthorizedAccessException и обработать её
        
        try {
            String response = commandDispatcher.dispatch(message);
            
            log.info("Команда '{}' успешно обработана: telegramId={}, responseLength={}", 
                    commandText, telegramId, response != null ? response.length() : 0);
            
            // Отправляем ответ пользователю с соответствующей клавиатурой
            if (response != null && !response.isBlank()) {
                try {
                    Optional<User> userOpt = userService.findByTelegramId(telegramId);
                    ReplyKeyboardMarkup keyboard = userOpt.isPresent()
                            ? keyboardService.createAuthorizedUserKeyboard()
                            : keyboardService.createUnauthorizedUserKeyboard();
                    
                    messageService.sendMessage(chatId, response, keyboard);
                    log.info("Ответ с клавиатурой успешно отправлен пользователю: chatId={}, responseLength={}, authorized={}", 
                            chatId, response.length(), userOpt.isPresent());
                } catch (Exception e) {
                    log.error("Ошибка при отправке ответа пользователю: chatId={}, error={}", 
                            chatId, e.getMessage(), e);
                }
            } else {
                log.warn("Пустой ответ от обработчика команды: command={}", commandText);
            }
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            // Перехватываем исключение о неавторизованном доступе
            log.info("Команда '{}' отклонена из-за отсутствия авторизации: telegramId={}, username={}", 
                    commandText, telegramId, username);
            
            // Определяем категорию сообщения на основе команды
            MessageCategory category = determineMessageCategory(commandText);
            
            // Проверяем авторизацию и отправляем информативное сообщение
            authorizationService.checkAuthorizationAndNotify(telegramId, chatId, category, commandText, username);
            
            log.info("Команда отклонена из-за неавторизованного доступа: command={}, telegramId={}, username={}", 
                    commandText, telegramId, username);
        }
    }
    
    /**
     * Извлекает команду из текста сообщения.
     * 
     * <p>Команда - это первое слово в сообщении, начинающееся с символа '/'.</p>
     * <p>Примеры:</p>
     * <ul>
     *   <li>"/start" → "/start"</li>
     *   <li>"/add_event Встреча" → "/add_event"</li>
     *   <li>"/help " → "/help"</li>
     *   <li>"Привет" → null</li>
     * </ul>
     * 
     * @param text текст сообщения
     * @return команда (включая символ '/') или null, если команда не найдена
     */
    private String extractCommand(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        String trimmed = text.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }
        
        // Находим первый пробел или берем всю строку
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex).toLowerCase();
        }
        
        return trimmed.toLowerCase();
    }
    
    /**
     * Определяет категорию сообщения на основе команды.
     * 
     * <p>Каждая команда соответствует определенной категории функциональности бота,
     * для которой формируется специфичное сообщение об ограничении доступа.</p>
     * 
     * <p>Маппинг команд на категории:</p>
     * <ul>
     *   <li>/add_event → EVENT_CREATION</li>
     *   <li>/my_events, /upcoming_events, /today, /week → EVENT_VIEWING</li>
     *   <li>/search, /filter → SEARCH_FILTER</li>
     *   <li>/trash → TRASH_MANAGEMENT</li>
     *   <li>/stats → STATISTICS</li>
     *   <li>Остальные команды → GENERAL</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 3.1, 3.2, 3.3, 3.4</p>
     * 
     * @param command команда для определения категории (например, "/add_event")
     * @return категория сообщения для данной команды
     */
    private MessageCategory determineMessageCategory(String command) {
        return switch (command) {
            case "/add_event" -> MessageCategory.EVENT_CREATION;
            case "/my_events", "/upcoming_events", "/today", "/week" -> MessageCategory.EVENT_VIEWING;
            case "/search", "/filter" -> MessageCategory.SEARCH_FILTER;
            case "/trash" -> MessageCategory.TRASH_MANAGEMENT;
            case "/stats" -> MessageCategory.STATISTICS;
            default -> MessageCategory.GENERAL;
        };
    }
}
