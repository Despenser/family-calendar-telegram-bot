package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.handler.SearchCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.TextEventParser;

import java.util.ArrayList;
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
 *   <li>Делегирование обработки callback queries в CallbackQueryDispatcher</li>
 *   <li>Логирование процесса обработки для мониторинга и отладки</li>
 * </ul>
 * 
 * <p>Асинхронная обработка позволяет быстро возвращать HTTP 200 OK в webhook контроллере,
 * что критично для соблюдения 60-секундного таймаута Telegram API.</p>
 * 
 * <p><b>Архитектурный паттерн:</b> Async Processing + Delegation</p>
 * <p><b>Требования:</b> 1.1, 1.5, 8.2</p>
 * 
 * @see CommandDispatcher
 * @see CallbackQueryDispatcher
 * @see UserService
 * @see Update
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2025-12-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateProcessor {

    private final CommandDispatcher commandDispatcher;
    private final CallbackQueryDispatcher callbackQueryDispatcher;
    private final UserService userService;
    private final SearchCommandHandler searchCommandHandler;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final ConversationService conversationService;
    private final ConversationStateService conversationStateService;
    private final TextEventParser textEventParser;
    private final AttachmentService attachmentService;
    private final AuthorizationService authorizationService;
    private final EventService eventService;
    private final BotMessageBuilder botMessageBuilder;

    /**
     * Асинхронно обрабатывает входящее обновление от Telegram Bot API.
     * 
     * <p>Этот метод выполняется в отдельном потоке благодаря аннотации @Async,
     * что позволяет webhook контроллеру быстро вернуть ответ Telegram API.</p>
     * 
     * <p>Процесс обработки:</p>
     * <ol>
     *   <li>Проверка наличия callback query - делегирование в CallbackQueryDispatcher</li>
     *   <li>Проверка наличия сообщения в обновлении</li>
     *   <li>Извлечение объекта Message</li>
     *   <li>Делегирование обработки в CommandDispatcher</li>
     *   <li>Логирование результата обработки</li>
     * </ol>
     * 
     * @param update объект Update от Telegram, содержащий информацию о событии
     * @throws IllegalArgumentException если update равен null
     */
    @Async
    public void processUpdate(Update update) {
        if (update == null) {
            log.error("Получено null обновление для обработки");
            throw new IllegalArgumentException("Update не может быть null");
        }
        
        log.debug("Начало асинхронной обработки обновления: updateId={}", update.getUpdateId());
        
        try {
            // Делегируем обработку callback query в CallbackQueryDispatcher
            if (update.hasCallbackQuery()) {
                log.debug("Обновление содержит callback query: updateId={}", update.getUpdateId());
                callbackQueryDispatcher.dispatch(update.getCallbackQuery());
                return;
            }
            
            // Проверяем, содержит ли обновление сообщение
            if (!update.hasMessage()) {
                log.debug("Обновление не содержит сообщения: updateId={}, hasEditedMessage={}", 
                        update.getUpdateId(), update.hasEditedMessage());
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
            
            // Логируем состояния пользователя для диагностики
            if (userOptional.isPresent()) {
                Long userId = userOptional.get().getId();
                log.debug("Проверка состояний пользователя: userId={}, telegramId={}, " +
                         "awaitingCompletionNote={}, awaitingSearchQuery={}, editingEvent={}, hasActiveDraft={}",
                         userId, telegramId,
                         conversationStateService.isAwaitingCompletionNote(userId),
                         conversationStateService.isAwaitingSearchQuery(userId),
                         conversationStateService.isEditingEvent(userId),
                         conversationService.hasActiveDraft(userId));
            }
            
            // Если пользователь редактирует событие, обрабатываем текст как редактирование
            // ВАЖНО: Эта проверка должна быть первой, чтобы редактирование имело приоритет
            if (userOptional.isPresent() && conversationStateService.isEditingEvent(userOptional.get().getId())) {
                handleEventEditing(message, userOptional.get());
                return;
            }
            
            // Если пользователь ожидает загрузки файла, отправляем подсказку
            if (userOptional.isPresent() && conversationStateService.isAwaitingFile(userOptional.get().getId())) {
                Long chatId = message.getChatId();
                String hintMessage = formatMessage(
                    "📎 Пожалуйста, отправьте файл \\(документ, фото, видео или аудио\\)\n\n" +
                    "_Для отмены нажмите кнопку 'Отмена' в списке вложений_"
                );
                messageService.sendMessage(chatId, hintMessage);
                log.debug("Отправлена подсказка пользователю в режиме ожидания файла: userId={}", 
                        userOptional.get().getId());
                return;
            }
            
            // Если пользователь ожидает ввода заметки к завершенному событию, обрабатываем текст как заметку
            if (userOptional.isPresent() && conversationStateService.isAwaitingCompletionNote(userOptional.get().getId())) {
                handleCompletionNote(message, userOptional.get(), originalText);
                return;
            }
            
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
            
            log.debug("Обновление успешно обработано: updateId={}", update.getUpdateId());
            
        } catch (Exception e) {
            log.error("Ошибка при обработке обновления: updateId={}, error={}", 
                    update.getUpdateId(), e.getMessage(), e);
        }
    }


    /**
     * Обрабатывает сообщение в контексте активного диалога создания события.
     * 
     * <p>Этот метод реализует улучшенный процесс создания события, где весь диалог
     * происходит в одном сообщении бота, а сообщения пользователя удаляются из чата.
     * Это делает чат чистым и удобным для пользователя.</p>
     * 
     * <p><b>Основные операции:</b></p>
     * <ul>
     *   <li><b>Удаление сообщений пользователя:</b> После получения текстового сообщения
     *       от пользователя (название или описание события), система удаляет это сообщение
     *       из чата через {@link TelegramMessageService#deleteMessageSilently(Long, Integer)}.
     *       Удаление происходит "тихо" - ошибки логируются, но не прерывают процесс.</li>
     *   
     *   <li><b>Сохранение данных:</b> Введенные пользователем данные сохраняются в черновике
     *       события через {@link ConversationService#updateEventTitle(Long, String)} или
     *       {@link ConversationService#completeEventCreation(Long, String)}.</li>
     *   
     *   <li><b>Обновление сообщения создания:</b> Единое сообщение бота (messageId которого
     *       сохранен в черновике) обновляется через {@link TelegramMessageService#editMessageText}
     *       с актуальной информацией о прогрессе создания события. При ошибке обновления
     *       отправляется новое сообщение (fallback).</li>
     * </ul>
     * 
     * <p><b>Шаги диалога:</b></p>
     * <ul>
     *   <li><b>WAITING_FOR_TITLE:</b> Пользователь отправляет название события.
     *       Сообщение удаляется, название сохраняется, сообщение создания обновляется
     *       с запросом описания.</li>
     *   
     *   <li><b>WAITING_FOR_DESCRIPTION:</b> Пользователь отправляет описание события
     *       или пропускает этот шаг. Сообщение удаляется, событие завершается и переходит
     *       в статус ACTIVE, сообщение создания обновляется с финальной карточкой события.</li>
     * </ul>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Ошибки удаления сообщений пользователя логируются, но не прерывают процесс</li>
     *   <li>При ошибке обновления сообщения создания отправляется новое сообщение</li>
     *   <li>Если creationMessageId отсутствует в черновике, отправляется новое сообщение</li>
     * </ul>
     * 
     * <p><b>Реализуемые требования:</b></p>
     * <ul>
     *   <li><b>1.1:</b> Удаление сообщения пользователя с названием события</li>
     *   <li><b>1.2:</b> Сохранение названия в черновике после удаления сообщения</li>
     *   <li><b>1.3:</b> Обновление сообщения создания с подтверждением сохранения названия</li>
     *   <li><b>2.1:</b> Удаление сообщения пользователя с описанием события</li>
     *   <li><b>2.2:</b> Сохранение описания в черновике после удаления сообщения</li>
     *   <li><b>2.3:</b> Завершение создания события после ввода описания</li>
     *   <li><b>4.2:</b> Использование сохраненного messageId для обновления при вводе названия</li>
     *   <li><b>4.3:</b> Использование сохраненного messageId для обновления при вводе описания</li>
     * </ul>
     * 
     * @param message сообщение от пользователя с названием или описанием события
     * @param user авторизованный пользователь, создающий событие
     * 
     * @see ConversationService#getActiveDraft(Long)
     * @see ConversationService#getCurrentStep(ru.golubyatnikov.family.calendar.bot.model.Event)
     * @see ConversationService#updateEventTitle(Long, String)
     * @see ConversationService#completeEventCreation(Long, String)
     * @see TelegramMessageService#deleteMessageSilently(Long, Integer)
     * @see TelegramMessageService#editMessageText(Long, Integer, String, InlineKeyboardMarkup)
     */
    private void handleConversationMessage(Message message, User user) {
        try {
            ru.golubyatnikov.family.calendar.bot.model.Event draft = 
                conversationService.getActiveDraft(user.getId());
            
            ConversationService.ConversationStep step = conversationService.getCurrentStep(draft);
            String text = message.getText();
            Long chatId = message.getChatId();
            Long telegramId = user.getTelegramId();
            Integer userMessageId = message.getMessageId();
            Long creationMessageId = draft.getMessageId();
            
            log.debug("Обработка сообщения в контексте диалога: userId={}, telegramId={}, step={}, creationMessageId={}", 
                user.getId(), telegramId, step, creationMessageId);
            
            switch (step) {
                case WAITING_FOR_TITLE -> {
                    // 1. Удаляем сообщение пользователя
                    messageService.deleteMessageSilently(chatId, userMessageId);
                    log.debug("Сообщение пользователя с названием удалено: chatId={}, messageId={}, userId={}", 
                            chatId, userMessageId, user.getId());
                    
                    // 2. Сохраняем название в черновике
                    conversationService.updateEventTitle(user.getId(), text);
                    log.debug("Название события сохранено: userId={}, title='{}'", user.getId(), text);
                    
                    // 3. Обновляем сообщение создания
                    if (creationMessageId != null) {
                        try {
                            String response = bold("📋 Создание нового события") + "\n\n" +
                                "✅ Название: " + escape(text) + "\n\n" +
                                "Теперь отправьте описание события или нажмите кнопку 'Пропустить':";
                            
                            InlineKeyboardMarkup skipKeyboard = keyboardService.createSkipDescriptionKeyboard();
                            messageService.editMessageText(chatId, creationMessageId.intValue(), response, skipKeyboard);
                            
                            log.debug("Сообщение создания обновлено с названием: chatId={}, messageId={}, userId={}", 
                                    chatId, creationMessageId, user.getId());
                            
                        } catch (TelegramApiException e) {
                            log.warn("Не удалось обновить сообщение создания, отправка нового: chatId={}, messageId={}, error={}", 
                                    chatId, creationMessageId, e.getMessage());
                            
                            // Fallback: отправляем новое сообщение
                            String response = bold("📋 Создание нового события") + "\n\n" +
                                "✅ Название: " + escape(text) + "\n\n" +
                                "Теперь отправьте описание события или нажмите кнопку 'Пропустить':";
                            
                            InlineKeyboardMarkup skipKeyboard = keyboardService.createSkipDescriptionKeyboard();
                            messageService.sendMessageWithInlineKeyboard(chatId, response, skipKeyboard);
                        }
                    } else {
                        log.warn("creationMessageId отсутствует в черновике, отправка нового сообщения: userId={}", 
                                user.getId());
                        
                        // Fallback: отправляем новое сообщение
                        String response = bold("📋 Создание нового события") + "\n\n" +
                            "✅ Название: " + escape(text) + "\n\n" +
                            "Теперь отправьте описание события или нажмите кнопку 'Пропустить':";
                        
                        InlineKeyboardMarkup skipKeyboard = keyboardService.createSkipDescriptionKeyboard();
                        messageService.sendMessageWithInlineKeyboard(chatId, response, skipKeyboard);
                    }
                }
                
                case WAITING_FOR_DESCRIPTION -> {
                    // 1. Удаляем сообщение пользователя
                    messageService.deleteMessageSilently(chatId, userMessageId);
                    log.debug("Сообщение пользователя с описанием удалено: chatId={}, messageId={}, userId={}", 
                            chatId, userMessageId, user.getId());
                    
                    // 2. Обрабатываем текст описания (включая "пропустить")
                    String description = text.equalsIgnoreCase("пропустить") ? null : text;
                    
                    // 3. Завершаем создание события
                    ru.golubyatnikov.family.calendar.bot.model.Event completedEvent = 
                        conversationService.completeEventCreation(user.getId(), description);
                    log.debug("Создание события завершено: eventId={}, userId={}", 
                            completedEvent.getId(), user.getId());
                    
                    // 4. Обновляем сообщение создания с финальной карточкой события
                    if (creationMessageId != null) {
                        try {
                            // Формируем финальную карточку события
                            int eventCount = eventService.getActiveEventsCount(user.getId());
                            String eventMessage = botMessageBuilder.buildEventMessageWithHeader(completedEvent, eventCount);
                            
                            // Создаем клавиатуру действий
                            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(completedEvent, user.getId());
                            
                            // Обновляем сообщение
                            messageService.editMessageText(chatId, creationMessageId.intValue(), eventMessage, keyboard);
                            
                            log.debug("Сообщение создания обновлено с финальной карточкой: chatId={}, messageId={}, eventId={}", 
                                    chatId, creationMessageId, completedEvent.getId());
                            
                        } catch (TelegramApiException e) {
                            log.warn("Не удалось обновить сообщение создания с финальной карточкой, отправка нового: chatId={}, messageId={}, error={}", 
                                    chatId, creationMessageId, e.getMessage());
                            
                            // Fallback: отправляем сообщение о созданном событии через eventService
                            try {
                                eventService.sendOrUpdateEventMessage(completedEvent, chatId);
                                log.debug("Сообщение о созданном событии отправлено (fallback): eventId={}", 
                                        completedEvent.getId());
                            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                                log.error("Ошибка при отправке сообщения о созданном событии (fallback): eventId={}, error={}", 
                                        completedEvent.getId(), ex.getMessage());
                                
                                // Последний fallback: простое подтверждающее сообщение
                                String response = bold("✅ Событие успешно создано!") + "\n\n" +
                                    "📅 Дата: " + escape(completedEvent.getFormattedDate()) + "\n" +
                                    "🕐 Время: " + escape(completedEvent.getFormattedTime()) + "\n" +
                                    "📝 Название: " + escape(completedEvent.getTitle()) + "\n" +
                                    (description != null ? "📄 Описание: " + escape(description) : "");
                                ReplyKeyboardMarkup fallbackKeyboard = keyboardService.createAuthorizedUserKeyboard();
                                messageService.sendMessage(chatId, response, fallbackKeyboard);
                            }
                        }
                    } else {
                        log.warn("creationMessageId отсутствует в черновике, отправка нового сообщения: userId={}", 
                                user.getId());
                        
                        // Fallback: отправляем сообщение о созданном событии через eventService
                        try {
                            eventService.sendOrUpdateEventMessage(completedEvent, chatId);
                            log.debug("Сообщение о созданном событии отправлено: eventId={}", 
                                    completedEvent.getId());
                        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                            log.error("Ошибка при отправке сообщения о созданном событии: eventId={}, error={}", 
                                    completedEvent.getId(), e.getMessage());
                            
                            // Последний fallback: простое подтверждающее сообщение
                            String response = bold("✅ Событие успешно создано!") + "\n\n" +
                                "📅 Дата: " + escape(completedEvent.getFormattedDate()) + "\n" +
                                "🕐 Время: " + escape(completedEvent.getFormattedTime()) + "\n" +
                                "📝 Название: " + escape(completedEvent.getTitle()) + "\n" +
                                (description != null ? "📄 Описание: " + escape(description) : "");
                            ReplyKeyboardMarkup fallbackKeyboard = keyboardService.createAuthorizedUserKeyboard();
                            messageService.sendMessage(chatId, response, fallbackKeyboard);
                        }
                    }
                    
                    log.debug("Событие успешно создано: eventId={}, userId={}, telegramId={}", 
                        completedEvent.getId(), user.getId(), telegramId);
                }
                
                default -> {
                    log.warn("Неожиданный шаг диалога: step={}, userId={}, telegramId={}", 
                            step, user.getId(), telegramId);
                    conversationService.cancelEventCreation(user.getId());
                    
                    String response = "❌ " + bold("Произошла ошибка") + "\\. " + 
                                    italic("Попробуйте начать заново с команды /add_event");
                    ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                    messageService.sendMessage(chatId, response, keyboard);
                }
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке сообщения в контексте диалога: userId={}, telegramId={}, error={}, stackTrace={}", 
                user.getId(), user.getTelegramId(), e.getMessage(), getStackTraceString(e), e);
            
            try {
                String response = "❌ " + bold("Произошла ошибка") + "\\. " + 
                                italic("Попробуйте начать заново с команды /add_event");
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}, stackTrace={}", 
                        user.getTelegramId(), ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }

    /**
     * Обрабатывает файловое сообщение (документ или изображение).
     * 
     * @param message сообщение с файлом
     * @param userOptional опциональный пользователь
     */
    private void handleFileMessage(Message message, Optional<User> userOptional) {
        try {
            Long chatId = message.getChatId();
            Long telegramId = message.getFrom().getId();
            
            if (userOptional.isEmpty()) {
                log.warn("Неавторизованный пользователь пытается отправить файл: telegramId={}", telegramId);
                messageService.sendMessage(chatId, 
                    "❌ Для отправки файлов необходимо авторизоваться. Используйте " + escape("/start"));
                return;
            }
            
            User user = userOptional.get();
            
            // Проверяем, ожидает ли пользователь загрузки файла для вложения
            if (conversationStateService.isAwaitingFile(user.getId())) {
                handleAttachmentFileUpload(message, user);
                return;
            }
            
            if (!conversationService.hasActiveDraft(user.getId())) {
                log.debug("Пользователь отправил файл без активного черновика: userId={}, telegramId={}", 
                        user.getId(), telegramId);
                messageService.sendMessage(chatId, 
                    "❌ Для прикрепления файлов сначала создайте событие с помощью /add_event");
                return;
            }
            
            ru.golubyatnikov.family.calendar.bot.model.Event draft = 
                conversationService.getActiveDraft(user.getId());
            
            if (draft.getEventDate() == null || draft.getEventTime() == null) {
                log.debug("Пользователь отправил файл на раннем этапе создания события: userId={}, telegramId={}", 
                        user.getId(), telegramId);
                messageService.sendMessage(chatId, 
                    "❌ Сначала завершите создание события, затем вы сможете прикрепить файлы");
                return;
            }
            
            String fileId;
            String fileName;
            String fileType;
            Long fileSize;
            
            if (message.hasDocument()) {
                org.telegram.telegrambots.meta.api.objects.Document document = message.getDocument();
                fileId = document.getFileId();
                fileName = document.getFileName();
                fileType = document.getMimeType();
                fileSize = document.getFileSize();
                
                log.debug("Получен документ: fileId={}, fileName='{}', size={}, telegramId={}", 
                        fileId, fileName, fileSize, telegramId);
                
            } else if (message.hasPhoto()) {
                List<org.telegram.telegrambots.meta.api.objects.PhotoSize> photos = message.getPhoto();
                org.telegram.telegrambots.meta.api.objects.PhotoSize photo = photos.get(photos.size() - 1);
                
                fileId = photo.getFileId();
                fileName = "photo_" + System.currentTimeMillis() + ".jpg";
                fileType = "image/jpeg";
                fileSize = photo.getFileSize().longValue();
                
                log.debug("Получено изображение: fileId={}, size={}, telegramId={}", 
                        fileId, fileSize, telegramId);
                
            } else {
                log.warn("Сообщение не содержит документа или фото: telegramId={}", telegramId);
                return;
            }
            
            final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 МБ
            if (fileSize > MAX_FILE_SIZE) {
                log.warn("Файл слишком большой: size={}, max={}, telegramId={}", 
                        fileSize, MAX_FILE_SIZE, telegramId);
                messageService.sendMessage(chatId, 
                    formatMessage("❌ Размер файла превышает максимально допустимый (20 МБ).\n\n" +
                                "Размер вашего файла: %.2f МБ", fileSize / (1024.0 * 1024.0)));
                return;
            }
            
            try {
                attachmentService.saveAttachment(draft.getId(), fileId, fileName, fileType, fileSize);
                
                String response = bold("✅ Файл успешно прикреплен!") + "\n\n" +
                    "📎 Название: " + escape(fileName) + "\n" +
                    formatMessage("📊 Размер: %.2f МБ\n\n", fileSize / (1024.0 * 1024.0)) +
                    "Вы можете продолжить прикреплять файлы или завершить создание события.";
                
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(chatId, response, keyboard);
                
                log.debug("Файл успешно прикреплен к событию: eventId={}, fileName='{}', telegramId={}", 
                         draft.getId(), fileName, telegramId);
                
            } catch (ru.golubyatnikov.family.calendar.bot.exception.FileSizeExceededException e) {
                log.warn("Размер файла превышает лимит: error={}, telegramId={}", 
                        e.getMessage(), telegramId);
                messageService.sendMessage(chatId, "❌ " + e.getMessage());
                
            } catch (Exception e) {
                log.error("Ошибка при сохранении вложения: eventId={}, telegramId={}, error={}, stackTrace={}", 
                         draft.getId(), telegramId, e.getMessage(), getStackTraceString(e), e);
                messageService.sendMessage(chatId, 
                    "❌ " + bold("Произошла ошибка при сохранении файла") + "\\. " + 
                    italic("Попробуйте еще раз\\."));
            }
            
        } catch (Exception e) {
            Long telegramId = message.getFrom().getId();
            log.error("Ошибка при обработке файла: telegramId={}, error={}, stackTrace={}", 
                    telegramId, e.getMessage(), getStackTraceString(e), e);
            
            try {
                messageService.sendMessage(message.getChatId(), 
                    "❌ " + bold("Произошла ошибка при обработке файла") + "\\.");
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}, stackTrace={}", 
                        telegramId, ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }


    /**
     * Обрабатывает загрузку файла для вложения к событию.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает контекст ожидания файла</li>
     *   <li>Извлекает информацию о файле из сообщения</li>
     *   <li>Сохраняет вложение через AttachmentService</li>
     *   <li>Удаляет сообщение пользователя с файлом для чистоты чата</li>
     *   <li>Обновляет список вложений</li>
     *   <li>Очищает состояние ожидания файла</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 9.1, 9.2, 9.3, 9.4, 9.5</p>
     * 
     * @param message сообщение с файлом
     * @param user авторизованный пользователь
     */
    private void handleAttachmentFileUpload(Message message, User user) {
        Long chatId = message.getChatId();
        Long userId = user.getId();
        Long telegramId = user.getTelegramId();
        
        log.debug("Обработка загрузки файла для вложения: userId={}, telegramId={}", userId, telegramId);
        
        try {
            // Получаем контекст ожидания файла
            ConversationStateService.AwaitingFileContext context = 
                conversationStateService.getAwaitingFileContext(userId);
            
            if (context == null) {
                log.warn("Контекст ожидания файла не найден для пользователя: userId={}", userId);
                conversationStateService.clearAwaitingFile(userId);
                messageService.sendMessage(chatId, 
                    "❌ Произошла ошибка. Попробуйте добавить файл заново.");
                return;
            }
            
            Long eventId = context.getEventId();
            Integer messageId = context.getMessageId();
            
            // Извлекаем информацию о файле из сообщения
            String fileId;
            String fileName;
            String fileType;
            Long fileSize;
            
            if (message.hasDocument()) {
                org.telegram.telegrambots.meta.api.objects.Document document = message.getDocument();
                fileId = document.getFileId();
                fileName = document.getFileName();
                fileType = "document";
                fileSize = document.getFileSize();
                
                log.debug("Получен документ для вложения: fileId={}, fileName='{}', size={}, eventId={}", 
                        fileId, fileName, fileSize, eventId);
                
            } else if (message.hasPhoto()) {
                // Выбираем самое большое разрешение
                List<org.telegram.telegrambots.meta.api.objects.PhotoSize> photos = message.getPhoto();
                org.telegram.telegrambots.meta.api.objects.PhotoSize photo = photos.get(photos.size() - 1);
                
                fileId = photo.getFileId();
                fileName = "photo_" + System.currentTimeMillis() + ".jpg";
                fileType = "photo";
                fileSize = photo.getFileSize().longValue();
                
                log.debug("Получено фото для вложения: fileId={}, size={}, eventId={}", 
                        fileId, fileSize, eventId);
                
            } else if (message.hasVideo()) {
                org.telegram.telegrambots.meta.api.objects.Video video = message.getVideo();
                fileId = video.getFileId();
                fileName = video.getFileName() != null ? video.getFileName() : "video_" + System.currentTimeMillis() + ".mp4";
                fileType = "video";
                fileSize = video.getFileSize().longValue();
                
                log.debug("Получено видео для вложения: fileId={}, fileName='{}', size={}, eventId={}", 
                        fileId, fileName, fileSize, eventId);
                
            } else if (message.hasAudio()) {
                org.telegram.telegrambots.meta.api.objects.Audio audio = message.getAudio();
                fileId = audio.getFileId();
                fileName = audio.getFileName() != null ? audio.getFileName() : "audio_" + System.currentTimeMillis() + ".mp3";
                fileType = "audio";
                fileSize = audio.getFileSize().longValue();
                
                log.debug("Получено аудио для вложения: fileId={}, fileName='{}', size={}, eventId={}", 
                        fileId, fileName, fileSize, eventId);
                
            } else {
                log.warn("Сообщение не содержит поддерживаемого типа файла: userId={}, eventId={}", 
                        userId, eventId);
                conversationStateService.clearAwaitingFile(userId);
                messageService.sendMessage(chatId, 
                    "❌ Неподдерживаемый тип файла. Отправьте документ, фото, видео или аудио.");
                return;
            }
            
            // Сохраняем вложение
            ru.golubyatnikov.family.calendar.bot.model.Attachment attachment = 
                attachmentService.saveAttachment(eventId, fileId, fileName, fileType, fileSize);
            
            log.info("Вложение успешно сохранено: attachmentId={}, eventId={}, userId={}", 
                    attachment.getId(), eventId, userId);
            
            // Удаляем сообщение пользователя с файлом
            messageService.deleteMessageSilently(chatId, message.getMessageId());
            log.debug("Запрос на удаление сообщения пользователя с файлом отправлен: chatId={}, messageId={}, userId={}", 
                    chatId, message.getMessageId(), userId);
            
            // Обновляем список вложений с использованием editOrSendMessage
            try {
                ru.golubyatnikov.family.calendar.bot.model.Event event = eventService.getEventById(eventId);
                List<ru.golubyatnikov.family.calendar.bot.model.Attachment> attachments = 
                    attachmentService.getEventAttachments(eventId);
                
                boolean isCreator = event.belongsToUser(userId);
                
                // Формируем сообщение со списком вложений
                StringBuilder messageBuilder = new StringBuilder();
                messageBuilder.append(bold("📎 Вложения события")).append("\n\n");
                messageBuilder.append(bold(event.getTitle())).append("\n\n");
                
                if (attachments.isEmpty()) {
                    messageBuilder.append(italic("У этого события пока нет вложений\\."));
                } else {
                    for (int i = 0; i < attachments.size(); i++) {
                        ru.golubyatnikov.family.calendar.bot.model.Attachment att = attachments.get(i);
                        
                        // Эмодзи для типа файла
                        String emoji = switch (att.getFileType()) {
                            case "photo" -> "🖼️";
                            case "video" -> "🎥";
                            case "audio" -> "🎵";
                            default -> "📄";
                        };
                        
                        // Форматирование размера
                        String sizeStr;
                        if (att.getFileSize() >= 1024 * 1024) {
                            sizeStr = String.format("%.2f МБ", att.getFileSize() / (1024.0 * 1024.0));
                        } else {
                            sizeStr = String.format("%.2f КБ", att.getFileSize() / 1024.0);
                        }
                        
                        // Форматирование даты (без двоеточия для избежания проблем с экранированием)
                        String dateStr = att.getUploadedAt()
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH-mm"));
                        
                        messageBuilder.append(emoji).append(" ")
                                     .append(bold(att.getFileName())).append("\n")
                                     .append("   Размер: ").append(escape(sizeStr)).append("\n")
                                     .append("   Загружено: ").append(escape(dateStr)).append("\n");
                        
                        if (i < attachments.size() - 1) {
                            messageBuilder.append("\n");
                        }
                    }
                }
                
                InlineKeyboardMarkup keyboard = keyboardService.createAttachmentsListKeyboard(
                    eventId, attachments, isCreator);
                
                String fullText = messageBuilder.toString();
                
                // Детальное логирование для диагностики
                log.debug("Полный текст сообщения перед отправкой: chatId={}, messageId={}, textLength={}", 
                        chatId, messageId, fullText.length());
                log.debug("Текст сообщения (первые 500 символов): '{}'", 
                        fullText.length() > 500 ? fullText.substring(0, 500) : fullText);
                log.debug("Текст сообщения (последние 200 символов): '{}'", 
                        fullText.length() > 200 ? fullText.substring(fullText.length() - 200) : fullText);
                
                // Используем editOrSendMessage для редактирования или отправки нового сообщения
                Integer resultMessageId = editOrSendMessage(chatId, messageId, fullText, 
                        keyboard, userId, eventId);
                
                log.debug("Список вложений обновлен: eventId={}, messageId={}", eventId, resultMessageId);
                
            } catch (TelegramApiException e) {
                log.warn("Не удалось обновить список вложений: eventId={}, messageId={}, error={}", 
                        eventId, messageId, e.getMessage());
                // Продолжаем выполнение, даже если обновление не удалось
            }
            
            // Очищаем состояние ожидания файла
            conversationStateService.clearAwaitingFile(userId);
            log.debug("Состояние ожидания файла очищено: userId={}", userId);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.FileSizeExceededException e) {
            log.warn("Размер файла превышает лимит: userId={}, error={}", userId, e.getMessage());
            conversationStateService.clearAwaitingFile(userId);
            
            try {
                messageService.sendMessage(chatId, 
                    formatMessage("❌ Размер файла превышает максимально допустимый \\(20 МБ\\)\\."));
            } catch (TelegramApiException ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                        telegramId, ex.getMessage(), ex);
            }
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.error("Событие не найдено при добавлении вложения: userId={}, error={}", 
                    userId, e.getMessage());
            conversationStateService.clearAwaitingFile(userId);
            
            try {
                messageService.sendMessage(chatId, 
                    "❌ Событие не найдено. Возможно, оно было удалено.");
            } catch (TelegramApiException ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                        telegramId, ex.getMessage(), ex);
            }
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            log.error("Нет прав для добавления вложения: userId={}, error={}", 
                    userId, e.getMessage());
            conversationStateService.clearAwaitingFile(userId);
            
            try {
                messageService.sendMessage(chatId, 
                    "❌ У вас нет прав для добавления вложений к этому событию.");
            } catch (TelegramApiException ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                        telegramId, ex.getMessage(), ex);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке загрузки файла для вложения: userId={}, telegramId={}, error={}, stackTrace={}", 
                    userId, telegramId, e.getMessage(), getStackTraceString(e), e);
            conversationStateService.clearAwaitingFile(userId);
            
            try {
                messageService.sendMessage(chatId, 
                    "❌ " + bold("Произошла ошибка при сохранении файла") + "\\. " + 
                    italic("Попробуйте еще раз\\."));
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                        telegramId, ex.getMessage(), ex);
            }
        }
    }


    /**
     * Обрабатывает распознавание события из текстового сообщения.
     * 
     * @param message исходное сообщение от пользователя
     * @param user авторизованный пользователь
     * @param text текст сообщения для парсинга
     */
    private void handleTextEventParsing(Message message, User user, String text) {
        try {
            Long chatId = message.getChatId();
            Long telegramId = user.getTelegramId();
            
            log.debug("Попытка распознать событие из текста для пользователя: userId={}, telegramId={}", 
                    user.getId(), telegramId);
            
            Optional<TextEventParser.ParsedEvent> parsedEventOpt = textEventParser.parseEvent(text);
            
            if (parsedEventOpt.isEmpty()) {
                log.debug("Не удалось распознать событие из текста: text='{}', telegramId={}", 
                        text, telegramId);
                return;
            }
            
            TextEventParser.ParsedEvent parsedEvent = parsedEventOpt.get();
            
            if (!parsedEvent.isValid()) {
                log.warn("Распознанное событие невалидно: parsedEvent={}, telegramId={}", 
                        parsedEvent, telegramId);
                
                StringBuilder responseBuilder = new StringBuilder();
                responseBuilder.append("❌ *Не удалось создать событие*\n\n");
                
                if (parsedEvent.getTitle() == null || parsedEvent.getTitle().trim().isEmpty()) {
                    responseBuilder.append("Название события не может быть пустым.\n\n");
                }
                
                if (parsedEvent.getDate() != null && 
                    parsedEvent.getDate().isBefore(java.time.LocalDate.now())) {
                    responseBuilder.append("Дата события не может быть в прошлом.\n\n");
                }
                
                responseBuilder.append("Попробуйте использовать один из форматов:\n")
                              .append("• `Событие: Встреча Дата: 15.01.2026 Время: 14:30`\n")
                              .append("• `Встреча 15.01.2026 14:30`\n")
                              .append("• `Встреча завтра в 14:30`\n\n")
                              .append("Или используйте команду /add_event для пошагового создания.");
                
                String response = formatMessage(responseBuilder.toString());
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(chatId, response, keyboard);
                return;
            }
            
            log.debug("Событие успешно распознано: title='{}', date={}, time={}, telegramId={}", 
                     parsedEvent.getTitle(), parsedEvent.getDate(), parsedEvent.getTime(), telegramId);
            
            String preview = bold("✅ Распознано событие из текста:") + "\n\n" +
                "📝 Название: " + escape(parsedEvent.getTitle()) + "\n" +
                "📅 Дата: " + escape(parsedEvent.getDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))) + "\n" +
                "🕐 Время: " + escape(parsedEvent.getTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))) + "\n\n" +
                "Подтвердите создание события:";
            
            InlineKeyboardMarkup keyboard = createEventConfirmationKeyboard(parsedEvent);
            messageService.sendMessageWithInlineKeyboard(chatId, preview, keyboard);
            
            log.debug("Отправлен предпросмотр распознанного события пользователю: userId={}, telegramId={}", 
                    user.getId(), telegramId);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке распознавания события из текста: userId={}, telegramId={}, error={}, stackTrace={}", 
                     user.getId(), user.getTelegramId(), e.getMessage(), getStackTraceString(e), e);
            
            try {
                String response = bold("❌ Произошла ошибка при распознавании события") + ".\n\n" +
                        italic("Используйте команду /add_event для пошагового создания.");
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}, stackTrace={}", 
                        user.getTelegramId(), ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }

    /**
     * Создает inline-клавиатуру для подтверждения создания события из текста.
     * 
     * @param parsedEvent распознанное событие
     * @return клавиатура с кнопками подтверждения и отмены
     */
    private InlineKeyboardMarkup createEventConfirmationKeyboard(TextEventParser.ParsedEvent parsedEvent) {
        // Кодируем данные события в callback data
        String eventData = parsedEvent.getTitle() + "|" + 
                          parsedEvent.getDate().toString() + "|" + 
                          parsedEvent.getTime().toString();
        String encodedData = java.util.Base64.getEncoder().encodeToString(eventData.getBytes());
        
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Создать событие");
        confirmButton.setCallbackData("confirm_text_event:" + encodedData);
        
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("cancel_text_event");
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(confirmButton);
        row.add(cancelButton);
        rows.add(row);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Обрабатывает текстовое сообщение как поисковый запрос.
     * 
     * @param message сообщение с поисковым запросом
     * @param user пользователь, выполняющий поиск
     */
    private void handleSearchQuery(Message message, User user) {
        try {
            String query = message.getText();
            Long chatId = message.getChatId();
            Long userId = user.getId();
            Long telegramId = user.getTelegramId();
            
            log.debug("Обработка поискового запроса от пользователя: userId={}, telegramId={}", 
                    userId, telegramId);
            
            conversationStateService.clearAwaitingSearchQuery(userId);
            searchCommandHandler.performSearch(chatId, user, query);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке поискового запроса: userId={}, telegramId={}, error={}, stackTrace={}", 
                     user.getId(), user.getTelegramId(), e.getMessage(), getStackTraceString(e), e);
            
            try {
                conversationStateService.clearAwaitingSearchQuery(user.getId());
                
                String response = "❌ " + bold("Произошла ошибка при обработке поискового запроса") + "\\. " +
                                italic("Попробуйте еще раз\\.");
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}, stackTrace={}", 
                        user.getTelegramId(), ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }

    /**
     * Обрабатывает ввод заметки к завершенному событию.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает контекст добавления заметки из ConversationStateService</li>
     *   <li>Извлекает текст заметки из параметра noteText</li>
     *   <li>Вызывает EventService.addCompletionNote() для сохранения заметки</li>
     *   <li>Отправляет подтверждающее сообщение</li>
     *   <li>Очищает состояние ожидания заметки</li>
     * </ol>
     * 
     * @param message сообщение с текстом заметки
     * @param user пользователь, добавляющий заметку
     * @param noteText оригинальный текст заметки (до преобразования кнопок)
     */
    private void handleCompletionNote(Message message, User user, String noteText) {
        try {
            Long chatId = message.getChatId();
            Long userId = user.getId();
            Long telegramId = user.getTelegramId();
            
            log.debug("Обработка заметки к завершенному событию от пользователя: userId={}, telegramId={}", 
                    userId, telegramId);
            
            // Получаем контекст добавления заметки
            ConversationStateService.CompletionNoteContext context = 
                conversationStateService.getCompletionNoteContext(userId);
            
            if (context == null) {
                log.warn("Контекст добавления заметки не найден для пользователя: userId={}", userId);
                conversationStateService.clearAwaitingCompletionNote(userId);
                
                String response = formatMessage(
                    "❌ Произошла ошибка. Попробуйте завершить событие заново."
                );
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(chatId, response, keyboard);
                return;
            }
            
            Long eventId = context.getEventId();
            
            // Добавляем заметку к событию
            ru.golubyatnikov.family.calendar.bot.model.Event event = 
                eventService.addCompletionNote(eventId, userId, noteText);
            
            log.info("Заметка успешно добавлена к событию ID={} пользователем ID={}", 
                    eventId, userId);
            
            // Обновляем сообщение о событии с заметкой
            try {
                eventService.sendOrUpdateEventMessage(event, chatId);
                log.debug("Сообщение о событии с заметкой обновлено: eventId={}", eventId);
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                log.warn("Не удалось обновить сообщение о событии с заметкой: eventId={}, error={}", 
                        eventId, e.getMessage());
                // Продолжаем выполнение, даже если обновление сообщения не удалось
            }
            
            // Очищаем состояние ожидания заметки
            conversationStateService.clearAwaitingCompletionNote(userId);
            
            // Отправляем подтверждающее сообщение
            String response = formatMessage(
                "✅ Заметка успешно добавлена к событию \"%s\"!\n\n" +
                "📝 Заметка: %s",
                event.getTitle(),
                noteText
            );
            
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.error("Событие не найдено при добавлении заметки: userId={}, error={}", 
                     user.getId(), e.getMessage());
            
            try {
                conversationStateService.clearAwaitingCompletionNote(user.getId());
                
                String response = formatMessage(
                    "❌ Событие не найдено. Возможно, оно было удалено."
                );
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                        user.getTelegramId(), ex.getMessage(), ex);
            }
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            log.error("Нет прав для добавления заметки: userId={}, error={}", 
                     user.getId(), e.getMessage());
            
            try {
                conversationStateService.clearAwaitingCompletionNote(user.getId());
                
                String response = formatMessage(
                    "❌ У вас нет прав для добавления заметки к этому событию."
                );
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                        user.getTelegramId(), ex.getMessage(), ex);
            }
            
        } catch (IllegalStateException e) {
            log.error("Событие не завершено при добавлении заметки: userId={}, error={}", 
                     user.getId(), e.getMessage());
            
            try {
                conversationStateService.clearAwaitingCompletionNote(user.getId());
                
                String response = formatMessage(
                    "❌ Заметку можно добавить только к завершенному событию."
                );
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                        user.getTelegramId(), ex.getMessage(), ex);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обработке заметки к событию: userId={}, telegramId={}, error={}, stackTrace={}", 
                     user.getId(), user.getTelegramId(), e.getMessage(), getStackTraceString(e), e);
            
            try {
                conversationStateService.clearAwaitingCompletionNote(user.getId());
                
                String response = formatMessage(
                    "❌ Произошла ошибка при добавлении заметки. Попробуйте еще раз."
                );
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(message.getChatId(), response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}, stackTrace={}", 
                        user.getTelegramId(), ex.getMessage(), getStackTraceString(ex), ex);
            }
        }
    }


    /**
     * Обрабатывает команду с проверкой авторизации.
     * 
     * @param message входящее сообщение от Telegram, содержащее команду
     */
    private void handleCommand(Message message) {
        String messageText = message.getText().trim();
        Long telegramId = message.getFrom().getId();
        Long chatId = message.getChatId();
        String username = message.getFrom().getUserName();
        
        String commandText = extractCommand(messageText);
        
        log.debug("Обработка команды: command='{}', telegramId={}, chatId={}", 
                commandText, telegramId, chatId);
        
        if (commandText == null) {
            log.warn("Не удалось извлечь команду из текста: '{}', telegramId={}", 
                    messageText, telegramId);
            
            try {
                Optional<User> userOpt = userService.findByTelegramId(telegramId);
                ReplyKeyboardMarkup keyboard = userOpt.isPresent()
                        ? keyboardService.createAuthorizedUserKeyboard()
                        : keyboardService.createUnauthorizedUserKeyboard();
                
                String response = formatMessage(
                        "Команда должна начинаться с символа '/'. Используйте /help для списка доступных команд.");
                messageService.sendMessage(chatId, response, keyboard);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", e.getMessage(), e);
            }
            return;
        }
        
        log.debug("Извлечена команда: '{}' из текста: '{}'", commandText, messageText);
        
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
        
        try {
            String response = commandDispatcher.dispatch(message);
            
            log.debug("Команда '{}' успешно обработана: telegramId={}, responseLength={}", 
                    commandText, telegramId, response != null ? response.length() : 0);
            
            if (response != null && !response.isBlank()) {
                try {
                    Optional<User> userOpt = userService.findByTelegramId(telegramId);
                    ReplyKeyboardMarkup keyboard = userOpt.isPresent()
                            ? keyboardService.createAuthorizedUserKeyboard()
                            : keyboardService.createUnauthorizedUserKeyboard();
                    
                    messageService.sendMessage(chatId, response, keyboard);
                    log.debug("Ответ с клавиатурой успешно отправлен пользователю: chatId={}, responseLength={}", 
                            chatId, response.length());
                } catch (Exception e) {
                    log.error("Ошибка при отправке ответа пользователю: telegramId={}, chatId={}, error={}, stackTrace={}", 
                            telegramId, chatId, e.getMessage(), getStackTraceString(e), e);
                }
            } else {
                log.warn("Пустой ответ от обработчика команды: command={}, telegramId={}", 
                        commandText, telegramId);
            }
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            log.debug("Команда '{}' отклонена из-за отсутствия авторизации: telegramId={}", 
                    commandText, telegramId);
            
            MessageCategory category = determineMessageCategory(commandText);
            authorizationService.checkAuthorizationAndNotify(telegramId, chatId, category, commandText, username);
        }
    }
    
    /**
     * Извлекает команду из текста сообщения.
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
        
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex).toLowerCase();
        }
        
        return trimmed.toLowerCase();
    }
    
    /**
     * Определяет категорию сообщения на основе команды.
     * 
     * @param command команда для определения категории
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
    
    /**
     * Получает строковое представление стека вызовов исключения.
     * 
     * <p>Используется для детального логирования критических ошибок.</p>
     * 
     * @param e исключение
     * @return строка со стеком вызовов (первые 5 элементов)
     */
    private String getStackTraceString(Exception e) {
        if (e == null || e.getStackTrace() == null || e.getStackTrace().length == 0) {
            return "no stack trace";
        }
        
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = e.getStackTrace();
        int limit = Math.min(5, elements.length);
        
        for (int i = 0; i < limit; i++) {
            sb.append(elements[i].toString());
            if (i < limit - 1) {
                sb.append(" -> ");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Обрабатывает ввод текста при редактировании поля события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает EditingContext для пользователя</li>
     *   <li>Определяет редактируемое поле (TITLE или DESCRIPTION)</li>
     *   <li>Вызывает соответствующий метод EventService для обновления</li>
     *   <li>Обновляет сообщение о событии через editMessageText с messageId из контекста</li>
     *   <li>Удаляет текстовое сообщение пользователя через deleteMessage</li>
     *   <li>Очищает состояние редактирования</li>
     *   <li>Обрабатывает ошибки с отправкой сообщения пользователю</li>
     * </ol>
     * 
     * @param message сообщение от пользователя
     * @param user авторизованный пользователь
     */
    private void handleEventEditing(Message message, User user) {
        Long userId = user.getId();
        Long chatId = message.getChatId();
        Integer userMessageId = message.getMessageId();
        String text = message.getText();
        
        handleEventFieldEdit(userId, chatId, userMessageId, text);
    }
    
    /**
     * Обрабатывает ввод текста при редактировании поля события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает EditingContext для пользователя</li>
     *   <li>Определяет редактируемое поле (TITLE или DESCRIPTION)</li>
     *   <li>Вызывает соответствующий метод EventService для обновления</li>
     *   <li>Обновляет сообщение о событии через editMessageText с messageId из контекста</li>
     *   <li>Удаляет текстовое сообщение пользователя через deleteMessage</li>
     *   <li>Очищает состояние редактирования</li>
     *   <li>Обрабатывает ошибки с отправкой сообщения пользователю</li>
     * </ol>
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param userMessageId идентификатор сообщения пользователя
     * @param text введенный текст
     */
    private void handleEventFieldEdit(Long userId, Long chatId, Integer userMessageId, String text) {
        ConversationStateService.EditingContext context = conversationStateService.getEditingContext(userId);
        
        if (context == null || context.getCurrentField() == null) {
            log.warn("Контекст редактирования не найден для пользователя ID={}", userId);
            return;
        }
        
        Long eventId = context.getEventId();
        ConversationStateService.EditField field = context.getCurrentField();
        Integer editingMessageId = context.getMessageId();
        
        log.info("Обработка ввода текста для поля '{}' события ID={} пользователем ID={}", 
                field, eventId, userId);
        
        try {
            ru.golubyatnikov.family.calendar.bot.model.Event updatedEvent = null;
            
            // Обновляем соответствующее поле события
            switch (field) {
                case TITLE -> {
                    updatedEvent = eventService.updateEventTitle(eventId, userId, text);
                    log.debug("Название события обновлено: eventId={}, newTitle='{}'", eventId, text);
                }
                case DESCRIPTION -> {
                    updatedEvent = eventService.updateEventDescription(eventId, userId, text);
                    log.debug("Описание события обновлено: eventId={}", eventId);
                }
                default -> {
                    log.warn("Неподдерживаемое поле для текстового ввода: {}", field);
                    return;
                }
            }
            
            if (updatedEvent != null && editingMessageId != null) {
                // Обновляем сообщение о событии
                try {
                    // Используем buildEventMessageWithHeader для сохранения шапки, если это первое событие
                    int eventCount = eventService.getActiveEventsCount(updatedEvent.getUser().getId());
                    String eventMessage = botMessageBuilder.buildEventMessageWithHeader(updatedEvent, eventCount);
                    InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(updatedEvent, userId);
                    messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
                    
                    log.info("Поле '{}' события обновлено и сообщение обновлено: eventId={}, messageId={}", 
                            field, eventId, editingMessageId);
                } catch (TelegramApiException e) {
                    log.error("Не удалось обновить сообщение о событии: eventId={}, messageId={}, error={}", 
                            eventId, editingMessageId, e.getMessage());
                    // Продолжаем выполнение, даже если обновление сообщения не удалось
                }
                
                // Удаляем сообщение пользователя с введенным текстом
                messageService.deleteMessageSilently(chatId, userMessageId);
                log.debug("Сообщение пользователя удалено: messageId={}", userMessageId);
            }
            
            // Очищаем состояние редактирования
            conversationStateService.clearEventEditing(userId);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            log.error("Нет прав для редактирования события: userId={}, eventId={}, error={}", 
                    userId, eventId, e.getMessage());
            
            try {
                String errorMessage = "❌ У вас нет прав для редактирования этого события.";
                messageService.sendMessage(chatId, errorMessage);
            } catch (TelegramApiException ex) {
                log.error("Не удалось отправить сообщение об ошибке: {}", ex.getMessage());
            }
            
            conversationStateService.clearEventEditing(userId);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.error("Событие не найдено при редактировании: userId={}, eventId={}, error={}", 
                    userId, eventId, e.getMessage());
            
            try {
                String errorMessage = "❌ Событие не найдено. Возможно, оно было удалено.";
                messageService.sendMessage(chatId, errorMessage);
            } catch (TelegramApiException ex) {
                log.error("Не удалось отправить сообщение об ошибке: {}", ex.getMessage());
            }
            
            conversationStateService.clearEventEditing(userId);
            
        } catch (Exception e) {
            log.error("Ошибка при обновлении поля события: userId={}, eventId={}, field={}, error={}", 
                    userId, eventId, field, e.getMessage(), e);
            
            // Отправляем сообщение об ошибке
            try {
                String errorMessage = "❌ Произошла ошибка при обновлении " + 
                                    (field == ConversationStateService.EditField.TITLE ? "названия" : "описания") + 
                                    " события. Попробуйте еще раз.";
                messageService.sendMessage(chatId, errorMessage);
            } catch (TelegramApiException ex) {
                log.error("Не удалось отправить сообщение об ошибке: {}", ex.getMessage());
            }
            
            conversationStateService.clearEventEditing(userId);
        }
    }
    
    /**
     * Редактирует существующее сообщение или отправляет новое при невозможности редактирования.
     * 
     * <p>Метод реализует паттерн "edit-or-send" для обеспечения надежной доставки обновлений:</p>
     * <ol>
     *   <li>Пытается отредактировать существующее сообщение через {@link TelegramMessageService#tryEditMessageText}</li>
     *   <li>При успехе сохраняет messageId в {@link ConversationStateService}</li>
     *   <li>При неудаче (сообщение удалено/старое) отправляет новое сообщение</li>
     *   <li>Сохраняет новый messageId для последующих операций</li>
     * </ol>
     * 
     * <p>Этот подход обеспечивает:</p>
     * <ul>
     *   <li>Чистоту чата (редактирование вместо новых сообщений)</li>
     *   <li>Надежность (fallback на новое сообщение при ошибках)</li>
     *   <li>Сохранение контекста (messageId для следующих операций)</li>
     * </ul>
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param text новый текст сообщения (с MarkdownV2 форматированием)
     * @param keyboard inline-клавиатура для сообщения
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     * @return messageId отредактированного или нового сообщения
     * @throws TelegramApiException при критических ошибках отправки
     */
    private Integer editOrSendMessage(Long chatId, Integer messageId, String text, 
                                     InlineKeyboardMarkup keyboard, 
                                     Long userId, Long eventId) 
            throws TelegramApiException {
        
        log.debug("Попытка редактирования сообщения: chatId={}, messageId={}, userId={}, eventId={}", 
                chatId, messageId, userId, eventId);
        
        try {
            // Попытка отредактировать существующее сообщение
            boolean edited = messageService.tryEditMessageText(chatId, messageId, text, keyboard);
            
            if (edited) {
                log.info("Сообщение успешно отредактировано: chatId={}, messageId={}, userId={}, eventId={}", 
                        chatId, messageId, userId, eventId);
                
                // Сохраняем messageId в ConversationState
                conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, messageId);
                
                return messageId;
            } else {
                // Редактирование не удалось - переключаемся на fallback режим
                log.info("Редактирование не удалось (сообщение удалено/старое), отправка нового сообщения: " +
                        "chatId={}, oldMessageId={}, userId={}, eventId={}", 
                        chatId, messageId, userId, eventId);
                
                // Отправляем новое сообщение
                org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                        messageService.sendMessageAndGet(chatId, text, keyboard);
                
                Integer newMessageId = sentMessage.getMessageId();
                
                log.info("Новое сообщение успешно отправлено (fallback): chatId={}, newMessageId={}, userId={}, eventId={}", 
                        chatId, newMessageId, userId, eventId);
                
                // Сохраняем новый messageId в ConversationState
                conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, newMessageId);
                
                log.debug("Новый messageId сохранен в ConversationState: userId={}, eventId={}, messageId={}", 
                        userId, eventId, newMessageId);
                
                return newMessageId;
            }
            
        } catch (TelegramApiException e) {
            log.error("Критическая ошибка при редактировании/отправке сообщения: " +
                     "chatId={}, messageId={}, userId={}, eventId={}, error={}", 
                     chatId, messageId, userId, eventId, e.getMessage(), e);
            throw e;
        }
    }
}
