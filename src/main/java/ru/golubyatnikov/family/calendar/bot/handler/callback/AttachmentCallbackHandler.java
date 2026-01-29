package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.AuthorizationService;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Обработчик callback queries для работы с вложениями к событиям.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>attach_file_list - просмотр списка вложений</li>
 *   <li>attach_file_add - добавление файла</li>
 *   <li>attach_file_view - просмотр файла</li>
 *   <li>attach_file_delete - удаление файла</li>
 *   <li>attach_file_confirm_delete - подтверждение удаления</li>
 *   <li>attach_file_cancel_delete - отмена удаления</li>
 *   <li>attach_file_back - возврат к карточке события</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.2, 2.1, 4.1-4.5, 5.1-5.6, 6.1-6.4, 7.1-7.4, 9.1-9.2, 10.1-10.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentCallbackHandler implements CallbackHandler {
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    
    private final TelegramMessageService messageService;
    private final AttachmentService attachmentService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    private final AuthorizationService authorizationService;
    private final BotMessageBuilder botMessageBuilder;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.ATTACH_FILE;
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback вложения: data='{}', userId={}", 
                callbackData, user.getId());
        
        // Проверка на null или пустые callback-данные
        if (callbackData == null || callbackData.isEmpty()) {
            log.error("Получены null или пустые callback-данные: userId={}", user.getId());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректные данные"));
            return;
        }
        
        // Формат: attach_file_{action}_{eventId}[_{attachmentId}]
        // Для составных действий: attach_file_{action}_{subAction}_{eventId}[_{attachmentId}]
        String payload = CallbackPrefix.ATTACH_FILE.extractPayload(callbackData);
        
        // Проверка на null или пустой payload после извлечения префикса
        if (payload == null || payload.isEmpty()) {
            log.error("Получен null или пустой payload после извлечения префикса: callbackData='{}', userId={}", 
                    callbackData, user.getId());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
            return;
        }
        
        String[] parts = payload.split("_");
        
        log.debug("Payload разобран: parts={}, length={}", java.util.Arrays.toString(parts), parts.length);
        
        if (parts.length < 2) {
            log.warn("Некорректный формат callback data (недостаточно частей): callbackData='{}', parts={}, userId={}", 
                    callbackData, java.util.Arrays.toString(parts), user.getId());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
            return;
        }
        
        String action = parts[0];
        log.debug("Определено действие: action={}", action);
        
        try {
            switch (action) {
                case "list" -> {
                    // Формат: list_{eventId}
                    if (parts.length < 2) {
                        log.warn("Недостаточно частей для действия 'list': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID события"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    log.debug("Обработка действия 'list': eventId={}", eventId);
                    handleBackToAttachments(eventId, user, chatId, messageId, callbackQueryId, callbackQuery);
                }
                case "add" -> {
                    // Формат: add_{eventId}
                    if (parts.length < 2) {
                        log.warn("Недостаточно частей для действия 'add': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID события"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    log.debug("Обработка действия 'add': eventId={}", eventId);
                    handleAddFile(eventId, user, chatId, messageId, callbackQueryId);
                }
                case "view" -> {
                    // Формат: view_{eventId}_{attachmentId}
                    if (parts.length < 3) {
                        log.warn("Недостаточно частей для действия 'view': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID вложения"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    Long attachmentId = Long.parseLong(parts[2]);
                    log.debug("Обработка действия 'view': eventId={}, attachmentId={}", eventId, attachmentId);
                    handleViewFile(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
                }
                case "delete" -> {
                    // Формат: delete_{eventId}_{attachmentId}
                    if (parts.length < 3) {
                        log.warn("Недостаточно частей для действия 'delete': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID вложения"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    Long attachmentId = Long.parseLong(parts[2]);
                    log.debug("Обработка действия 'delete': eventId={}, attachmentId={}", eventId, attachmentId);
                    handleDeleteFile(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
                }
                case "confirm" -> {
                    // Составное действие: confirm_delete_{eventId}_{attachmentId}
                    if (parts.length < 4) {
                        log.warn("Недостаточно частей для действия 'confirm': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
                        return;
                    }
                    if (!parts[1].equals("delete")) {
                        log.warn("Некорректный subAction для 'confirm': ожидается 'delete', получено '{}', callbackData='{}', userId={}", 
                                parts[1], callbackData, user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("неподдерживаемое действие"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[2]);
                    Long attachmentId = Long.parseLong(parts[3]);
                    log.debug("Обработка составного действия 'confirm_delete': eventId={}, attachmentId={}", 
                            eventId, attachmentId);
                    handleConfirmDelete(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
                }
                case "cancel" -> {
                    // Составное действие: cancel_delete_{eventId} или cancel_add_{eventId}
                    if (parts.length < 3) {
                        log.warn("Недостаточно частей для действия 'cancel': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
                        return;
                    }
                    
                    String subAction = parts[1];
                    Long eventId = Long.parseLong(parts[2]);
                    
                    if (subAction.equals("delete")) {
                        log.debug("Обработка составного действия 'cancel_delete': eventId={}", eventId);
                        handleCancelDelete(eventId, user, chatId, messageId, callbackQueryId);
                    } else if (subAction.equals("add")) {
                        log.debug("Обработка составного действия 'cancel_add': eventId={}", eventId);
                        handleCancelAddFile(eventId, user, chatId, messageId, callbackQueryId);
                    } else {
                        log.warn("Некорректный subAction для 'cancel': ожидается 'delete' или 'add', получено '{}', callbackData='{}', userId={}", 
                                subAction, callbackData, user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("неподдерживаемое действие"));
                    }
                }
                case "back" -> {
                    // Формат: back_{eventId}
                    if (parts.length < 2) {
                        log.warn("Недостаточно частей для действия 'back': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("не указан ID события"));
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    log.debug("Обработка действия 'back': eventId={}", eventId);
                    handleBackToEvent(eventId, user, chatId, messageId, callbackQueryId);
                }
                default -> {
                    log.warn("Неизвестное действие: action='{}', callbackData='{}', userId={}", 
                            action, callbackData, user.getId());
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.UNKNOWN_ACTION);
                }
            }
        } catch (NumberFormatException e) {
            log.error("Ошибка парсинга числа в callback data: callbackData='{}', parts={}, userId={}, error={}", 
                    callbackData, java.util.Arrays.toString(parts), user.getId(), e.getMessage());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат ID"));
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Ошибка доступа к элементу массива в callback data: callbackData='{}', parts={}, userId={}, error={}", 
                    callbackData, java.util.Arrays.toString(parts), user.getId(), e.getMessage());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.validationError("некорректный формат данных"));
        } catch (Exception e) {
            log.error("Неожиданная ошибка при обработке callback вложения: callbackData='{}', userId={}, error={}", 
                    callbackData, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Обрабатывает отображение списка вложений события.
     * 
     * <p>Формирует сообщение с информацией о каждом вложении:</p>
     * <ul>
     *   <li>Эмодзи для типа файла (📄, 🖼️, 🎥, 🎵)</li>
     *   <li>Имя файла</li>
     *   <li>Размер файла в КБ/МБ</li>
     *   <li>Дата загрузки в формате "dd.MM.yyyy HH:mm"</li>
     * </ul>
     * 
     * <p>Все текстовые данные экранируются через {@link MarkdownFormatter#escapeMarkdownV2(String)}
     * для корректного отображения в формате MarkdownV2.</p>
     * 
     * <p>Если список пуст, отображает сообщение "У этого события пока нет вложений".</p>
     * 
     * <p>Использует {@link #editOrSendMessage} для редактирования существующего сообщения
     * или отправки нового при невозможности редактирования.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 4.1, 4.2, 4.3, 4.4, 4.5, 7.1, 10.1, 10.2, 10.3, 10.5</p>
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleAttachmentList(Long eventId, User user, Long chatId, 
                                     Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Отображение списка вложений для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        // Получаем событие для проверки прав доступа
        Event event = eventService.getEventById(eventId);
        
        // Получаем список вложений
        List<Attachment> attachments = attachmentService.getEventAttachments(eventId);
        
        // Формируем сообщение
        StringBuilder message = new StringBuilder();
        message.append("📎 *Вложения события*\n\n");
        
        if (attachments.isEmpty()) {
            message.append("_У этого события пока нет вложений_");
        } else {
            for (int i = 0; i < attachments.size(); i++) {
                Attachment attachment = attachments.get(i);
                
                // Добавляем разделитель между вложениями
                if (i > 0) {
                    message.append("\n━━━━━━━━━━━━━━━━━━━━\n\n");
                }
                
                // Эмодзи для типа файла
                String emoji = getFileTypeEmoji(attachment.getFileType());
                message.append(emoji).append(" ");
                
                // Имя файла
                String fileName = attachment.getFileName() != null ? 
                        attachment.getFileName() : "Без названия";
                message.append("*").append(MarkdownFormatter.escapeMarkdownV2(fileName)).append("*\n");
                
                // Размер файла
                message.append("📊 Размер: ")
                       .append(MarkdownFormatter.escapeMarkdownV2(formatFileSize(attachment.getFileSize())))
                       .append("\n");
                
                // Дата загрузки
                String formattedDate = attachment.getUploadedAt().format(DATE_TIME_FORMATTER);
                message.append("📅 Загружено: ")
                       .append(MarkdownFormatter.escapeMarkdownV2(formattedDate));
            }
        }
        
        // Проверяем, является ли пользователь создателем события
        boolean isCreator = event.belongsToUser(user.getId());
        
        // Создаем клавиатуру
        var keyboard = keyboardService.createAttachmentsListKeyboard(eventId, attachments, isCreator);
        
        // Используем editOrSendMessage для редактирования или отправки нового сообщения
        Integer resultMessageId = editOrSendMessage(chatId, messageId, message.toString(), 
                keyboard, user.getId(), eventId);
        
        log.debug("Список вложений отображен: eventId={}, userId={}, messageId={}", 
                eventId, user.getId(), resultMessageId);
        
        messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
    }
    
    /**
     * Возвращает эмодзи для типа файла.
     * 
     * @param fileType тип файла (document, photo, video, audio)
     * @return эмодзи
     */
    private String getFileTypeEmoji(String fileType) {
        if (fileType == null) {
            return "📄";
        }
        
        return switch (fileType.toLowerCase()) {
            case "photo" -> "🖼️";
            case "video" -> "🎥";
            case "audio" -> "🎵";
            default -> "📄";
        };
    }
    
    /**
     * Форматирует размер файла в удобочитаемый формат.
     * 
     * @param fileSize размер файла в байтах
     * @return отформатированная строка (КБ или МБ)
     */
    private String formatFileSize(Long fileSize) {
        if (fileSize == null) {
            return "Неизвестно";
        }
        
        double sizeInKb = fileSize / 1024.0;
        if (sizeInKb < 1024) {
            return String.format("%.2f КБ", sizeInKb);
        } else {
            double sizeInMb = sizeInKb / 1024.0;
            return String.format("%.2f МБ", sizeInMb);
        }
    }
    
    /**
     * Проверяет, является ли сообщение медиа-сообщением.
     * 
     * <p>Медиа-сообщением считается сообщение, содержащее:</p>
     * <ul>
     *   <li>Фото (hasPhoto())</li>
     *   <li>Документ (hasDocument())</li>
     *   <li>Видео (hasVideo())</li>
     *   <li>Аудио (hasAudio())</li>
     * </ul>
     * 
     * <p>Метод безопасно обрабатывает null-значения, возвращая false
     * для null сообщений.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4</p>
     * 
     * @param message объект сообщения из Telegram API
     * @return true если сообщение содержит медиа-контент, false в противном случае
     */
    private boolean isMediaMessage(org.telegram.telegrambots.meta.api.objects.Message message) {
        if (message == null) {
            return false;
        }
        
        return message.hasPhoto() || 
               message.hasDocument() || 
               message.hasVideo() || 
               message.hasAudio();
    }
    
    /**
     * Обрабатывает начало добавления файла.
     * 
     * <p>Проверяет права доступа (только создатель события может добавлять файлы),
     * редактирует текущее сообщение с инструкцией по загрузке файла,
     * устанавливает состояние ожидания файла.</p>
     * 
     * <p>Использует механизм fallback: если редактирование не удалось (сообщение удалено
     * или слишком старое), отправляет новое сообщение и обновляет messageId в состоянии.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 4.1, 4.2, 4.4</p>
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleAddFile(Long eventId, User user, Long chatId, 
                              Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Начало добавления файла для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        try {
            // Получаем событие для проверки прав доступа
            Event event = eventService.getEventById(eventId);
            
            // Проверяем, что пользователь является создателем события
            if (!event.getUser().getId().equals(user.getId())) {
                log.warn("Пользователь ID={} попытался добавить вложение к чужому событию ID={}", 
                        user.getId(), eventId);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.NO_ACCESS);
                return;
            }
            
            // Формируем инструкцию по загрузке файла
            String instruction = buildAttachmentUploadInstruction();
            
            // Создаем клавиатуру с кнопкой "Отмена"
            InlineKeyboardMarkup keyboard = keyboardService.createAttachmentUploadKeyboard(eventId);
            
            log.debug("Попытка редактирования сообщения для режима загрузки вложения: " +
                    "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
            
            // Попытка редактирования сообщения
            boolean edited = messageService.tryEditMessageText(chatId, messageId, instruction, keyboard);
            
            if (!edited) {
                // Fallback: отправка нового сообщения
                log.info("Редактирование не удалось (сообщение удалено/старое), отправка нового сообщения: " +
                        "chatId={}, oldMessageId={}, eventId={}", chatId, messageId, eventId);
                
                org.telegram.telegrambots.meta.api.objects.Message newMessage = 
                        messageService.sendMessageAndGet(chatId, instruction, keyboard);
                messageId = newMessage.getMessageId();
                
                log.info("Новое сообщение отправлено (fallback): chatId={}, newMessageId={}, eventId={}", 
                        chatId, messageId, eventId);
            } else {
                log.info("Сообщение успешно отредактировано для режима загрузки вложения: " +
                        "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
            }
            
            // Устанавливаем состояние ожидания файла с актуальным messageId
            conversationStateService.setAwaitingFile(user.getId(), eventId, chatId, messageId);
            
            log.debug("Состояние ожидания файла установлено: userId={}, eventId={}, chatId={}, messageId={}", 
                    user.getId(), eventId, chatId, messageId);
            
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
            log.info("Пользователь ID={} переведен в режим ожидания файла для события ID={}", 
                    user.getId(), eventId);
            
        } catch (Exception e) {
            log.error("Ошибка при начале добавления файла: eventId={}, userId={}, error={}", 
                    eventId, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Формирует инструкцию по загрузке файла.
     * 
     * <p>Инструкция содержит:</p>
     * <ul>
     *   <li>Заголовок "Отправьте файл для прикрепления к событию"</li>
     *   <li>Максимальный размер файла (20 МБ)</li>
     *   <li>Список поддерживаемых типов файлов (документы, фотографии, видео, аудио)</li>
     * </ul>
     * 
     * <p>Использует MarkdownV2 форматирование для улучшения читаемости.</p>
     * 
     * <p><b>Требования:</b> 1.2, 5.1, 5.2, 5.3, 5.4</p>
     * 
     * @return отформатированная инструкция с MarkdownV2 форматированием
     */
    private String buildAttachmentUploadInstruction() {
        return "📎 *Отправьте файл для прикрепления к событию*\n\n" +
               "_Максимальный размер: 20 МБ_\n\n" +
               "Поддерживаемые типы файлов:\n" +
               "📄 Документы\n" +
               "🖼️ Фотографии\n" +
               "🎥 Видео\n" +
               "🎵 Аудио";
    }
    
    /**
     * Обрабатывает отмену добавления файла.
     * 
     * <p>Очищает состояние ожидания файла и восстанавливает стандартный вид события.
     * Использует механизм fallback: если редактирование не удалось (сообщение удалено
     * или слишком старое), отправляет новое сообщение.</p>
     * 
     * <p>Алгоритм работы:</p>
     * <ol>
     *   <li>Очистка состояния ожидания файла через {@link ConversationStateService#clearAwaitingFile}</li>
     *   <li>Получение события через {@link EventService#getEventById}</li>
     *   <li>Формирование стандартного сообщения события через {@link BotMessageBuilder#buildEventMessage}</li>
     *   <li>Создание стандартной клавиатуры через {@link KeyboardService#createEventActionsKeyboard}</li>
     *   <li>Попытка редактирования сообщения через {@link TelegramMessageService#tryEditMessageText}</li>
     *   <li>При неудаче - отправка нового сообщения через {@link TelegramMessageService#sendMessage}</li>
     *   <li>Отправка callback ответа "Отменено"</li>
     * </ol>
     * 
     * <p><b>Логирование:</b></p>
     * <ul>
     *   <li>DEBUG - начало обработки отмены</li>
     *   <li>DEBUG - попытка редактирования сообщения</li>
     *   <li>INFO - успешное редактирование или fallback на новое сообщение</li>
     *   <li>INFO - завершение обработки отмены</li>
     *   <li>ERROR - ошибки при обработке с полным stack trace</li>
     * </ul>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>При ошибке получения события - логирование ERROR и отправка callback ответа с ошибкой</li>
     *   <li>При ошибке редактирования - автоматический fallback на отправку нового сообщения</li>
     *   <li>При любой неожиданной ошибке - логирование ERROR, callback ответ и пробрасывание исключения</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 2.2, 2.3, 2.4, 7.1, 7.2, 7.3, 7.4</p>
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     * @throws Exception при критических ошибках обработки
     */
    private void handleCancelAddFile(Long eventId, User user, Long chatId, 
                                    Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Отмена добавления файла для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        try {
            // Очистка состояния ожидания файла
            conversationStateService.clearAwaitingFile(user.getId());
            log.debug("Состояние ожидания файла очищено для пользователя ID={}", user.getId());
            
            // Получение события для восстановления карточки
            Event event = eventService.getEventById(eventId);
            log.debug("Событие ID={} получено для восстановления карточки", eventId);
            
            // Получаем контекст шапки с обработкой ошибок
            ConversationStateService.EventHeaderContext headerContext = null;
            try {
                headerContext = conversationStateService.getEventHeaderContext(user.getId());
                
                if (headerContext != null) {
                    log.debug("Контекст шапки найден для пользователя ID={}: hasMyEventsHeader={}, eventCount={}", 
                            user.getId(), headerContext.isHasMyEventsHeader(), headerContext.getEventCount());
                } else {
                    log.debug("Контекст шапки не найден для пользователя ID={}", user.getId());
                }
            } catch (Exception e) {
                log.error("Ошибка при получении контекста шапки для пользователя ID={}: {}", 
                        user.getId(), e.getMessage(), e);
                // Продолжаем работу без контекста шапки
            }
            
            // Формируем сообщение о событии с учетом контекста шапки
            String eventMessage;
            if (headerContext != null && headerContext.isHasMyEventsHeader()) {
                log.debug("Использование buildEventMessageWithHeader для события ID={} с количеством событий: {}", 
                        eventId, headerContext.getEventCount());
                eventMessage = botMessageBuilder.buildEventMessageWithHeader(event, headerContext.getEventCount());
            } else {
                log.debug("Использование buildEventMessage для события ID={} (без шапки)", eventId);
                eventMessage = botMessageBuilder.buildEventMessage(event);
            }
            
            // Создание стандартной клавиатуры события
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
            
            log.debug("Попытка редактирования сообщения для восстановления карточки события: " +
                    "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
            
            // Попытка редактирования сообщения
            boolean edited = messageService.tryEditMessageText(chatId, messageId, eventMessage, keyboard);
            
            if (!edited) {
                // Fallback: отправка нового сообщения
                log.info("Редактирование не удалось (сообщение удалено/старое), отправка нового сообщения: " +
                        "chatId={}, oldMessageId={}, eventId={}", chatId, messageId, eventId);
                
                messageService.sendMessage(chatId, eventMessage, keyboard);
                
                log.info("Новое сообщение отправлено (fallback) при отмене добавления файла: " +
                        "chatId={}, eventId={}", chatId, eventId);
            } else {
                log.info("Сообщение успешно отредактировано для восстановления карточки события: " +
                        "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
            }
            
            // Отправка callback ответа
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.CANCELLED);
            
            log.info("Добавление файла отменено для события ID={}, пользователь ID={}", 
                    eventId, user.getId());
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.error("Событие ID={} не найдено при отмене добавления файла: userId={}", 
                    eventId, user.getId());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Событие"));
        } catch (Exception e) {
            log.error("Ошибка при отмене добавления файла: eventId={}, userId={}, error={}", 
                    eventId, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Обрабатывает просмотр файла.
     * 
     * <p>Получает вложение из БД и отправляет файл пользователю через Telegram API
     * с клавиатурой, содержащей кнопку "Назад к вложениям".</p>
     * 
     * <p>Формирует caption с именем файла, экранируя все специальные символы MarkdownV2
     * через {@link MarkdownFormatter#escapeMarkdownV2(String)}.</p>
     * 
     * <p>Caption отправляется с parseMode="MarkdownV2", поэтому все специальные символы
     * (точки, подчеркивания, скобки и т.д.) должны быть экранированы.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 3.1, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 9.1, 9.2</p>
     * 
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор текущего сообщения для удаления
     * @param callbackQueryId идентификатор callback query
     */
    private void handleViewFile(Long attachmentId, Long eventId, User user, 
                               Long chatId, Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Просмотр файла ID={}, пользователь ID={}", attachmentId, user.getId());
        
        try {
            // Удаляем текущее сообщение со списком вложений
            log.debug("Попытка удаления сообщения перед отправкой файла: chatId={}, messageId={}, userId={}", 
                    chatId, messageId, user.getId());
            
            boolean deleted = messageService.deleteMessage(chatId, messageId);
            
            if (deleted) {
                log.info("Сообщение успешно удалено перед отправкой файла: chatId={}, messageId={}, userId={}", 
                        chatId, messageId, user.getId());
            } else {
                log.warn("Не удалось удалить сообщение (возможно, уже удалено пользователем): " +
                        "chatId={}, messageId={}, userId={}", chatId, messageId, user.getId());
            }
            
            // Получаем вложение
            Attachment attachment = attachmentService.getAttachment(attachmentId);
            
            // Формируем caption с именем файла (экранируем для MarkdownV2)
            String fileName = attachment.getFileName() != null ? 
                    attachment.getFileName() : "Вложение";
            String caption = MarkdownFormatter.escapeMarkdownV2(fileName);
            
            // Создаем клавиатуру с кнопкой "Назад к вложениям"
            var keyboard = keyboardService.createFileViewKeyboard(eventId);
            
            // Отправляем файл с клавиатурой через TelegramMessageService и получаем Message объект
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                    messageService.sendFileWithKeyboardAndGet(chatId, attachment.getFileId(), 
                            attachment.getFileType(), caption, keyboard);
            
            // Извлекаем messageId из отправленного сообщения
            Integer newMessageId = sentMessage.getMessageId();
            
            log.info("Файл ID={} успешно отправлен с клавиатурой пользователю ID={}, новый messageId={}", 
                    attachmentId, user.getId(), newMessageId);
            
            // Сохраняем новый messageId в ConversationState
            try {
                conversationStateService.saveAttachmentMessageId(user.getId(), eventId, chatId, newMessageId);
                log.debug("Message_Id сохранен в ConversationState: userId={}, eventId={}, messageId={}", 
                        user.getId(), eventId, newMessageId);
            } catch (Exception e) {
                log.error("Ошибка при сохранении messageId в ConversationState: " +
                        "userId={}, eventId={}, messageId={}, error={}", 
                        user.getId(), eventId, newMessageId, e.getMessage(), e);
                // Не пробрасываем исключение - файл уже отправлен пользователю
            }
            
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.AttachmentNotFoundException e) {
            log.error("Вложение ID={} не найдено", attachmentId);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Вложение"));
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при отправке файла ID={}: {}", 
                    attachmentId, e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            messageService.sendMessage(chatId, 
                    "❌ Не удалось отправить файл\\. Попробуйте позже\\.");
        } catch (Exception e) {
            log.error("Неожиданная ошибка при просмотре файла ID={}: {}", 
                    attachmentId, e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Обрабатывает запрос подтверждения удаления файла.
     * 
     * <p>Проверяет права доступа (только создатель события может удалять файлы),
     * создает клавиатуру подтверждения и использует {@link #editOrSendMessage}
     * для редактирования сообщения или отправки нового.</p>
     * 
     * <p>Имя файла экранируется через {@link MarkdownFormatter#escapeMarkdownV2(String)}
     * для корректного отображения в формате MarkdownV2.</p>
     * 
     * <p><b>Требования:</b> 4.1, 6.1, 7.2</p>
     * 
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleDeleteFile(Long attachmentId, Long eventId, User user, 
                                 Long chatId, Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Запрос удаления файла ID={}, пользователь ID={}", 
                attachmentId, user.getId());
        
        try {
            // Получаем событие для проверки прав доступа
            Event event = eventService.getEventById(eventId);
            
            // Проверяем, что пользователь является создателем события
            if (!event.getUser().getId().equals(user.getId())) {
                log.warn("Пользователь ID={} попытался удалить вложение ID={} из чужого события ID={}", 
                        user.getId(), attachmentId, eventId);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.NO_ACCESS);
                messageService.sendMessage(chatId, 
                        "❌ Только создатель события может удалять вложения\\.");
                return;
            }
            
            // Получаем вложение для отображения информации
            Attachment attachment = attachmentService.getAttachment(attachmentId);
            
            // Формируем сообщение с запросом подтверждения
            String fileName = attachment.getFileName() != null ? 
                    attachment.getFileName() : "Без названия";
            String message = "⚠️ *Подтверждение удаления*\n\n" +
                           "Вы действительно хотите удалить вложение?\n\n" +
                           "📎 " + MarkdownFormatter.escapeMarkdownV2(fileName);
            
            // Создаем клавиатуру подтверждения
            var keyboard = keyboardService.createDeleteAttachmentConfirmationKeyboard(
                    eventId, attachmentId);
            
            // Используем editOrSendMessage для редактирования или отправки нового сообщения
            Integer resultMessageId = editOrSendMessage(chatId, messageId, message, 
                    keyboard, user.getId(), eventId);
            
            log.debug("Запрос подтверждения удаления отображен: attachmentId={}, userId={}, messageId={}", 
                    attachmentId, user.getId(), resultMessageId);
            
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.AttachmentNotFoundException e) {
            log.error("Вложение ID={} не найдено", attachmentId);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Вложение"));
        } catch (Exception e) {
            log.error("Ошибка при запросе удаления файла ID={}: {}", 
                    attachmentId, e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Обрабатывает подтверждение удаления файла.
     * 
     * <p>Удаляет вложение из БД через {@link AttachmentService#deleteAttachment},
     * получает обновленный список вложений и использует {@link #editOrSendMessage}
     * для отображения обновленного списка.</p>
     * 
     * <p><b>Требования:</b> 2.1, 3.1, 6.2, 6.3, 6.4</p>
     * 
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleConfirmDelete(Long attachmentId, Long eventId, User user, 
                                    Long chatId, Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Подтверждение удаления файла ID={}, пользователь ID={}", 
                attachmentId, user.getId());
        
        try {
            // Удаляем вложение через AttachmentService (с проверкой прав доступа)
            attachmentService.deleteAttachment(attachmentId, user.getId());
            
            // Отправляем callback answer с подтверждением
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.DELETED);
            
            log.info("Вложение ID={} успешно удалено пользователем ID={}", 
                    attachmentId, user.getId());
            
            // Получаем обновленный список вложений
            List<Attachment> attachments = attachmentService.getEventAttachments(eventId);
            
            // Получаем событие для проверки прав доступа
            Event event = eventService.getEventById(eventId);
            
            // Формируем сообщение с обновленным списком
            StringBuilder message = new StringBuilder();
            message.append("📎 *Вложения события*\n\n");
            
            if (attachments.isEmpty()) {
                message.append("_У этого события пока нет вложений_");
            } else {
                for (int i = 0; i < attachments.size(); i++) {
                    Attachment attachment = attachments.get(i);
                    
                    // Добавляем разделитель между вложениями
                    if (i > 0) {
                        message.append("\n━━━━━━━━━━━━━━━━━━━━\n\n");
                    }
                    
                    // Эмодзи для типа файла
                    String emoji = getFileTypeEmoji(attachment.getFileType());
                    message.append(emoji).append(" ");
                    
                    // Имя файла
                    String fileName = attachment.getFileName() != null ? 
                            attachment.getFileName() : "Без названия";
                    message.append("*").append(MarkdownFormatter.escapeMarkdownV2(fileName)).append("*\n");
                    
                    // Размер файла
                    message.append("📊 Размер: ")
                           .append(MarkdownFormatter.escapeMarkdownV2(formatFileSize(attachment.getFileSize())))
                           .append("\n");
                    
                    // Дата загрузки
                    String formattedDate = attachment.getUploadedAt().format(DATE_TIME_FORMATTER);
                    message.append("📅 Загружено: ")
                           .append(MarkdownFormatter.escapeMarkdownV2(formattedDate));
                }
            }
            
            // Проверяем, является ли пользователь создателем события
            boolean isCreator = event.belongsToUser(user.getId());
            
            // Создаем клавиатуру
            var keyboard = keyboardService.createAttachmentsListKeyboard(eventId, attachments, isCreator);
            
            // Используем editOrSendMessage для отображения обновленного списка
            Integer resultMessageId = editOrSendMessage(chatId, messageId, message.toString(), 
                    keyboard, user.getId(), eventId);
            
            log.debug("Обновленный список вложений отображен после удаления: eventId={}, userId={}, messageId={}", 
                    eventId, user.getId(), resultMessageId);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.AttachmentNotFoundException e) {
            log.error("Вложение ID={} не найдено при попытке удаления", attachmentId);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Вложение"));
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            log.warn("Пользователь ID={} попытался удалить вложение ID={} без прав доступа", 
                    user.getId(), attachmentId);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.NO_ACCESS);
            messageService.sendMessage(chatId, 
                    "❌ Только создатель события может удалять вложения\\.");
        } catch (Exception e) {
            log.error("Ошибка при удалении файла ID={}: {}", 
                    attachmentId, e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Обрабатывает отмену удаления файла.
     * 
     * <p>Возвращает к списку вложений, используя {@link #handleAttachmentList}
     * для отображения списка через механизм редактирования сообщений.</p>
     * 
     * <p><b>Требования:</b> 4.2</p>
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleCancelDelete(Long eventId, User user, Long chatId, 
                                   Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Отмена удаления файла для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        // Отправляем callback answer
        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.actionCancelled("Удаление"));
        
        // Возвращаем к списку вложений через handleAttachmentList
        // который использует editOrSendMessage для редактирования
        handleAttachmentList(eventId, user, chatId, messageId, callbackQueryId);
    }
    
    /**
     * Обрабатывает возврат к списку вложений из просмотра файла.
     * 
     * <p>Этот метод вызывается при нажатии кнопки "Назад к вложениям" при просмотре файла.
     * Получает событие и список вложений, формирует сообщение со списком вложений
     * и выбирает стратегию обработки в зависимости от типа текущего сообщения.</p>
     * 
     * <h3>Определение типа сообщения</h3>
     * <p>Метод использует {@link #isMediaMessage(org.telegram.telegrambots.meta.api.objects.Message)}
     * для определения типа текущего сообщения:</p>
     * <ul>
     *   <li><b>Медиа-сообщение</b> - содержит фото, документ, видео или аудио</li>
     *   <li><b>Текстовое сообщение</b> - содержит только текст и inline-клавиатуру</li>
     * </ul>
     * 
     * <h3>Обработка медиа-сообщений</h3>
     * <p>Для медиа-сообщений выполняется следующая последовательность действий:</p>
     * <ol>
     *   <li>Удаление текущего медиа-сообщения через {@link TelegramMessageService#deleteMessage}</li>
     *   <li>Отправка нового текстового сообщения через {@link TelegramMessageService#sendMessageAndGet}</li>
     *   <li>Сохранение нового messageId через {@link ConversationStateService#saveAttachmentMessageId}</li>
     * </ol>
     * 
     * <p><b>Причина:</b> Telegram API не поддерживает редактирование медиа-сообщений методом EditMessageText.
     * Попытка редактирования приводит к ошибке "Bad Request: there is no text in the message to edit".</p>
     * 
     * <h3>Обработка текстовых сообщений</h3>
     * <p>Для текстовых сообщений используется существующий механизм {@link #editOrSendMessage},
     * который пытается отредактировать сообщение, а при неудаче отправляет новое.</p>
     * 
     * <h3>Формирование сообщения</h3>
     * <p>Сообщение со списком вложений содержит информацию о каждом вложении:</p>
     * <ul>
     *   <li>Эмодзи для типа файла (📄, 🖼️, 🎥, 🎵)</li>
     *   <li>Имя файла</li>
     *   <li>Размер файла в КБ/МБ</li>
     *   <li>Дата загрузки в формате "dd.MM.yyyy HH:mm"</li>
     * </ul>
     * 
     * <p>Все текстовые данные экранируются через {@link MarkdownFormatter#escapeMarkdownV2(String)}
     * для корректного отображения в формате MarkdownV2.</p>
     * 
     * <p>Если список пуст, отображает сообщение "У этого события пока нет вложений".</p>
     * 
     * <h3>Обработка ошибок</h3>
     * <p>Метод реализует устойчивую обработку ошибок на каждом этапе:</p>
     * <ul>
     *   <li><b>Ошибка удаления медиа-сообщения:</b> логирование WARN, продолжение выполнения
     *       (сообщение могло быть удалено пользователем)</li>
     *   <li><b>Ошибка отправки нового сообщения:</b> логирование ERROR, отправка callback ответа
     *       с ошибкой, пробрасывание исключения</li>
     *   <li><b>Ошибка сохранения контекста:</b> логирование ERROR, продолжение выполнения
     *       (основная функциональность уже выполнена)</li>
     * </ul>
     * 
     * <h3>Логирование</h3>
     * <p>Метод выполняет подробное логирование на всех этапах:</p>
     * <ul>
     *   <li><b>DEBUG:</b> начало операции, определение типа сообщения, выбор стратегии обработки,
     *       сохранение messageId, завершение операции</li>
     *   <li><b>INFO:</b> успешное удаление медиа-сообщения, отправка нового сообщения</li>
     *   <li><b>WARN:</b> неудачное удаление медиа-сообщения (возможно, уже удалено),
     *       ошибка Telegram API при удалении</li>
     *   <li><b>ERROR:</b> ошибка отправки нового сообщения, ошибка сохранения контекста,
     *       общая ошибка обработки</li>
     * </ul>
     * 
     * <h3>Примеры логирования</h3>
     * <pre>
     * // DEBUG - начало операции
     * log.debug("Возврат к списку вложений для события ID={}, пользователь ID={}", eventId, user.getId());
     * 
     * // DEBUG - определение типа сообщения
     * log.debug("Проверка типа сообщения: chatId={}, messageId={}, isMedia={}, eventId={}", 
     *         chatId, messageId, isMedia, eventId);
     * 
     * // INFO - успешное удаление медиа-сообщения
     * log.info("Медиа-сообщение успешно удалено: chatId={}, messageId={}", chatId, messageId);
     * 
     * // WARN - неудачное удаление
     * log.warn("Не удалось удалить медиа-сообщение (возможно, уже удалено): " +
     *         "chatId={}, messageId={}", chatId, messageId);
     * 
     * // INFO - отправка нового сообщения
     * log.info("Новое текстовое сообщение отправлено после удаления медиа: " +
     *         "chatId={}, newMessageId={}, eventId={}", chatId, resultMessageId, eventId);
     * 
     * // ERROR - ошибка отправки
     * log.error("Ошибка Telegram API при отправке нового сообщения: " +
     *         "chatId={}, eventId={}, error={}", chatId, eventId, e.getMessage(), e);
     * </pre>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2, 4.3, 5.1, 5.2, 5.3</p>
     * 
     * @param eventId идентификатор события
     * @param user пользователь, инициировавший возврат к списку вложений
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор текущего сообщения (медиа или текстового)
     * @param callbackQueryId идентификатор callback query для отправки ответа
     * @param callbackQuery объект callback query для доступа к текущему сообщению и определения его типа
     * @throws Exception при критических ошибках обработки (ошибка отправки нового сообщения,
     *                   ошибка получения события или вложений)
     */
    private void handleBackToAttachments(Long eventId, User user, Long chatId, 
                                        Integer messageId, String callbackQueryId,
                                        CallbackQuery callbackQuery) throws Exception {
        log.debug("Возврат к списку вложений для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        try {
            // Получаем текущее сообщение из CallbackQuery
            // getMessage() возвращает MaybeInaccessibleMessage, который может быть Message или InaccessibleMessage
            var maybeMessage = callbackQuery.getMessage();
            org.telegram.telegrambots.meta.api.objects.Message currentMessage = null;
            
            // Проверяем, что это доступное сообщение
            if (maybeMessage instanceof org.telegram.telegrambots.meta.api.objects.Message) {
                currentMessage = (org.telegram.telegrambots.meta.api.objects.Message) maybeMessage;
            }
            
            // Определяем тип сообщения
            boolean isMedia = isMediaMessage(currentMessage);
            log.debug("Проверка типа сообщения: chatId={}, messageId={}, isMedia={}, eventId={}", 
                    chatId, messageId, isMedia, eventId);
            
            // Получаем событие для проверки прав доступа
            Event event = eventService.getEventById(eventId);
            
            // Получаем список вложений
            List<Attachment> attachments = attachmentService.getEventAttachments(eventId);
            
            // Формируем сообщение
            StringBuilder message = new StringBuilder();
            message.append("📎 *Вложения события*\n\n");
            
            if (attachments.isEmpty()) {
                message.append("_У этого события пока нет вложений_");
            } else {
                for (int i = 0; i < attachments.size(); i++) {
                    Attachment attachment = attachments.get(i);
                    
                    // Добавляем разделитель между вложениями
                    if (i > 0) {
                        message.append("\n━━━━━━━━━━━━━━━━━━━━\n\n");
                    }
                    
                    // Эмодзи для типа файла
                    String emoji = getFileTypeEmoji(attachment.getFileType());
                    message.append(emoji).append(" ");
                    
                    // Имя файла
                    String fileName = attachment.getFileName() != null ? 
                            attachment.getFileName() : "Без названия";
                    message.append("*").append(MarkdownFormatter.escapeMarkdownV2(fileName)).append("*\n");
                    
                    // Размер файла
                    message.append("📊 Размер: ")
                           .append(MarkdownFormatter.escapeMarkdownV2(formatFileSize(attachment.getFileSize())))
                           .append("\n");
                    
                    // Дата загрузки
                    String formattedDate = attachment.getUploadedAt().format(DATE_TIME_FORMATTER);
                    message.append("📅 Загружено: ")
                           .append(MarkdownFormatter.escapeMarkdownV2(formattedDate));
                }
            }
            
            // Проверяем, является ли пользователь создателем события
            boolean isCreator = event.belongsToUser(user.getId());
            
            // Создаем клавиатуру
            var keyboard = keyboardService.createAttachmentsListKeyboard(eventId, attachments, isCreator);
            
            Integer resultMessageId;
            
            // Проверяем тип сообщения и выбираем стратегию обработки
            if (isMedia) {
                log.debug("Текущее сообщение является медиа-сообщением, удаляем и отправляем новое: " +
                        "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
                
                // Удаляем медиа-сообщение с обработкой ошибок
                try {
                    boolean deleted = messageService.deleteMessage(chatId, messageId);
                    
                    if (deleted) {
                        log.info("Медиа-сообщение успешно удалено: chatId={}, messageId={}", 
                                chatId, messageId);
                    } else {
                        log.warn("Не удалось удалить медиа-сообщение (возможно, уже удалено): " +
                                "chatId={}, messageId={}", chatId, messageId);
                    }
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    log.warn("Ошибка Telegram API при удалении медиа-сообщения (продолжаем выполнение): " +
                            "chatId={}, messageId={}, error={}", chatId, messageId, e.getMessage());
                    // Продолжаем выполнение - попытаемся отправить новое сообщение
                }
                
                // Отправляем новое текстовое сообщение с обработкой ошибок
                try {
                    org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                            messageService.sendMessageAndGet(chatId, message.toString(), keyboard);
                    
                    resultMessageId = sentMessage.getMessageId();
                    
                    log.info("Новое текстовое сообщение отправлено после удаления медиа: " +
                            "chatId={}, newMessageId={}, eventId={}", chatId, resultMessageId, eventId);
                    
                } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                    log.error("Ошибка Telegram API при отправке нового сообщения: " +
                            "chatId={}, eventId={}, error={}", chatId, eventId, e.getMessage(), e);
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
                    throw e;
                }
                
                // Сохраняем новый messageId в ConversationState с обработкой ошибок
                try {
                    conversationStateService.saveAttachmentMessageId(user.getId(), eventId, 
                            chatId, resultMessageId);
                    log.debug("Message_Id сохранен в ConversationState: userId={}, eventId={}, messageId={}", 
                            user.getId(), eventId, resultMessageId);
                } catch (Exception e) {
                    log.error("Ошибка при сохранении messageId в ConversationState (продолжаем выполнение): " +
                            "userId={}, eventId={}, messageId={}, error={}", 
                            user.getId(), eventId, resultMessageId, e.getMessage(), e);
                    // Не пробрасываем исключение - основная функциональность уже выполнена
                }
                
            } else {
                log.debug("Текущее сообщение является текстовым, используем редактирование: " +
                        "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
                
                // Используем существующий механизм редактирования
                resultMessageId = editOrSendMessage(chatId, messageId, message.toString(), 
                        keyboard, user.getId(), eventId);
            }
            
            log.debug("Список вложений отображен при возврате: eventId={}, userId={}, messageId={}", 
                    eventId, user.getId(), resultMessageId);
            
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к списку вложений: eventId={}, userId={}, error={}", 
                    eventId, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Обрабатывает возврат к карточке события.
     * 
     * <p>Очищает attachment message context через {@link ConversationStateService#clearAttachmentMessageContext},
     * получает событие, формирует сообщение о событии с учетом сохраненного контекста шапки
     * и создает клавиатуру действий события.</p>
     * 
     * <p>Если контекст шапки сохранен и флаг hasMyEventsHeader = true, использует
     * {@link BotMessageBuilder#buildEventMessageWithHeader} для включения шапки "📋 Мои события".
     * В противном случае использует {@link BotMessageBuilder#buildEventMessage} без шапки.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.4, 4.1, 4.2, 4.3, 4.4, 6.3</p>
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleBackToEvent(Long eventId, User user, Long chatId, 
                                   Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Возврат к карточке события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        try {
            // Получаем событие
            Event event = eventService.getEventById(eventId);
            
            // Получаем контекст шапки с обработкой ошибок
            ConversationStateService.EventHeaderContext headerContext = null;
            try {
                headerContext = conversationStateService.getEventHeaderContext(user.getId());
                
                if (headerContext != null) {
                    log.debug("Контекст шапки найден для пользователя ID={}: hasMyEventsHeader={}, eventCount={}", 
                            user.getId(), headerContext.isHasMyEventsHeader(), headerContext.getEventCount());
                } else {
                    log.debug("Контекст шапки не найден для пользователя ID={}", user.getId());
                }
            } catch (Exception e) {
                log.error("Ошибка при получении контекста шапки для пользователя ID={}: {}", 
                        user.getId(), e.getMessage(), e);
                // Продолжаем работу без контекста шапки
            }
            
            // Формируем сообщение о событии с учетом контекста шапки
            String message;
            if (headerContext != null && headerContext.isHasMyEventsHeader()) {
                log.debug("Использование buildEventMessageWithHeader для события ID={} с количеством событий: {}", 
                        eventId, headerContext.getEventCount());
                message = botMessageBuilder.buildEventMessageWithHeader(event, headerContext.getEventCount());
            } else {
                log.debug("Использование buildEventMessage для события ID={} (без шапки)", eventId);
                message = botMessageBuilder.buildEventMessage(event);
            }
            
            // Создаем клавиатуру действий события
            var keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
            
            // Редактируем сообщение
            messageService.editMessageText(chatId, messageId, message, keyboard);
            
            // Очищаем attachment message context
            conversationStateService.clearAttachmentMessageContext(user.getId());
            
            log.debug("Attachment message context очищен для пользователя ID={}", user.getId());
            
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (Exception e) {
            log.error("Критическая ошибка при возврате к карточке события ID={}, пользователь ID={}: {}", 
                    eventId, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Вспомогательный метод для редактирования или отправки нового сообщения.
     * 
     * <p>Этот метод реализует fallback механизм для работы с сообщениями:
     * сначала пытается отредактировать существующее сообщение, а при неудаче
     * отправляет новое сообщение и сохраняет его messageId.</p>
     * 
     * <p><b>Алгоритм работы:</b></p>
     * <ol>
     *   <li>Попытка отредактировать сообщение через {@link TelegramMessageService#tryEditMessageText}</li>
     *   <li>При успехе - возвращает тот же messageId</li>
     *   <li>При ошибке (сообщение удалено/старое) - отправляет новое сообщение</li>
     *   <li>Сохраняет новый messageId в ConversationState</li>
     *   <li>Возвращает новый messageId</li>
     * </ol>
     * 
     * <p><b>Логирование:</b></p>
     * <ul>
     *   <li>DEBUG - попытка редактирования</li>
     *   <li>INFO - успешное редактирование</li>
     *   <li>INFO - fallback на новое сообщение с причиной</li>
     *   <li>DEBUG - сохранение нового messageId</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 2.2, 3.2, 4.3, 5.1, 5.2</p>
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param text новый текст сообщения (с MarkdownV2 форматированием)
     * @param keyboard inline клавиатура
     * @param userId идентификатор пользователя для сохранения контекста
     * @param eventId идентификатор события для сохранения контекста
     * @return messageId отредактированного или нового сообщения
     * @throws org.telegram.telegrambots.meta.exceptions.TelegramApiException при критических ошибках отправки
     */
    private Integer editOrSendMessage(Long chatId, Integer messageId, String text, 
                                     org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard, 
                                     Long userId, Long eventId) 
            throws org.telegram.telegrambots.meta.exceptions.TelegramApiException {
        
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
            
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Критическая ошибка при редактировании/отправке сообщения: " +
                     "chatId={}, messageId={}, userId={}, eventId={}, error={}", 
                     chatId, messageId, userId, eventId, e.getMessage(), e);
            throw e;
        }
    }
}
