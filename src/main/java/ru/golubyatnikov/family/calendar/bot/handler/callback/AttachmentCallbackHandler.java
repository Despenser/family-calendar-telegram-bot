package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
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
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректные данные");
            return;
        }
        
        // Формат: attach_file_{action}_{eventId}[_{attachmentId}]
        // Для составных действий: attach_file_{action}_{subAction}_{eventId}[_{attachmentId}]
        String payload = CallbackPrefix.ATTACH_FILE.extractPayload(callbackData);
        
        // Проверка на null или пустой payload после извлечения префикса
        if (payload == null || payload.isEmpty()) {
            log.error("Получен null или пустой payload после извлечения префикса: callbackData='{}', userId={}", 
                    callbackData, user.getId());
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат данных");
            return;
        }
        
        String[] parts = payload.split("_");
        
        log.debug("Payload разобран: parts={}, length={}", java.util.Arrays.toString(parts), parts.length);
        
        if (parts.length < 2) {
            log.warn("Некорректный формат callback data (недостаточно частей): callbackData='{}', parts={}, userId={}", 
                    callbackData, java.util.Arrays.toString(parts), user.getId());
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат данных");
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
                        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID события");
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    log.debug("Обработка действия 'list': eventId={}", eventId);
                    handleBackToAttachments(eventId, user, chatId, messageId, callbackQueryId);
                }
                case "add" -> {
                    // Формат: add_{eventId}
                    if (parts.length < 2) {
                        log.warn("Недостаточно частей для действия 'add': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID события");
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
                        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID вложения");
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    Long attachmentId = Long.parseLong(parts[2]);
                    log.debug("Обработка действия 'view': eventId={}, attachmentId={}", eventId, attachmentId);
                    handleViewFile(attachmentId, eventId, user, chatId, callbackQueryId);
                }
                case "delete" -> {
                    // Формат: delete_{eventId}_{attachmentId}
                    if (parts.length < 3) {
                        log.warn("Недостаточно частей для действия 'delete': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID вложения");
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
                        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат данных");
                        return;
                    }
                    if (!parts[1].equals("delete")) {
                        log.warn("Некорректный subAction для 'confirm': ожидается 'delete', получено '{}', callbackData='{}', userId={}", 
                                parts[1], callbackData, user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: неподдерживаемое действие");
                        return;
                    }
                    Long eventId = Long.parseLong(parts[2]);
                    Long attachmentId = Long.parseLong(parts[3]);
                    log.debug("Обработка составного действия 'confirm_delete': eventId={}, attachmentId={}", 
                            eventId, attachmentId);
                    handleConfirmDelete(attachmentId, eventId, user, chatId, messageId, callbackQueryId);
                }
                case "cancel" -> {
                    // Составное действие: cancel_delete_{eventId}
                    if (parts.length < 3) {
                        log.warn("Недостаточно частей для действия 'cancel': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат данных");
                        return;
                    }
                    if (!parts[1].equals("delete")) {
                        log.warn("Некорректный subAction для 'cancel': ожидается 'delete', получено '{}', callbackData='{}', userId={}", 
                                parts[1], callbackData, user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: неподдерживаемое действие");
                        return;
                    }
                    Long eventId = Long.parseLong(parts[2]);
                    log.debug("Обработка составного действия 'cancel_delete': eventId={}", eventId);
                    handleCancelDelete(eventId, user, chatId, messageId, callbackQueryId);
                }
                case "back" -> {
                    // Формат: back_{eventId}
                    if (parts.length < 2) {
                        log.warn("Недостаточно частей для действия 'back': callbackData='{}', parts={}, userId={}", 
                                callbackData, java.util.Arrays.toString(parts), user.getId());
                        messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: не указан ID события");
                        return;
                    }
                    Long eventId = Long.parseLong(parts[1]);
                    log.debug("Обработка действия 'back': eventId={}", eventId);
                    handleBackToEvent(eventId, user, chatId, messageId, callbackQueryId);
                }
                default -> {
                    log.warn("Неизвестное действие: action='{}', callbackData='{}', userId={}", 
                            action, callbackData, user.getId());
                    messageService.answerCallbackQuery(callbackQueryId, "❌ Неизвестное действие");
                }
            }
        } catch (NumberFormatException e) {
            log.error("Ошибка парсинга числа в callback data: callbackData='{}', parts={}, userId={}, error={}", 
                    callbackData, java.util.Arrays.toString(parts), user.getId(), e.getMessage());
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат ID");
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Ошибка доступа к элементу массива в callback data: callbackData='{}', parts={}, userId={}, error={}", 
                    callbackData, java.util.Arrays.toString(parts), user.getId(), e.getMessage());
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка: некорректный формат данных");
        } catch (Exception e) {
            log.error("Неожиданная ошибка при обработке callback вложения: callbackData='{}', userId={}, error={}", 
                    callbackData, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка при обработке запроса");
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
        
        messageService.answerCallbackQuery(callbackQueryId, "");
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
     * Обрабатывает начало добавления файла.
     * 
     * <p>Проверяет права доступа (только создатель события может добавлять файлы),
     * сохраняет контекст сообщения для последующего редактирования,
     * устанавливает состояние ожидания файла и отправляет инструкцию пользователю.</p>
     * 
     * <p><b>Требования:</b> 2.1, 7.1, 8.1, 9.1</p>
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
                messageService.answerCallbackQuery(callbackQueryId, "❌ Нет прав доступа");
                messageService.sendMessage(chatId, 
                        "❌ Только создатель события может добавлять вложения\\.");
                return;
            }
            
            // Сохраняем messageId в ConversationState для последующего редактирования
            conversationStateService.saveAttachmentMessageId(user.getId(), eventId, chatId, messageId);
            
            log.debug("Контекст сообщения сохранен для пользователя ID={}: eventId={}, chatId={}, messageId={}", 
                    user.getId(), eventId, chatId, messageId);
            
            // Устанавливаем состояние ожидания файла
            conversationStateService.setAwaitingFile(user.getId(), eventId, chatId, messageId);
            
            // Формируем сообщение с инструкцией
            String message = "📎 *Отправьте файл для прикрепления к событию*\n\n" +
                           "_Максимальный размер: 20 МБ_\n\n" +
                           "Поддерживаемые типы файлов:\n" +
                           "📄 Документы\n" +
                           "🖼️ Фотографии\n" +
                           "🎥 Видео\n" +
                           "🎵 Аудио";
            
            // Отправляем новое сообщение с инструкцией
            messageService.sendMessage(chatId, message);
            messageService.answerCallbackQuery(callbackQueryId, "");
            
            log.info("Пользователь ID={} переведен в режим ожидания файла для события ID={}", 
                    user.getId(), eventId);
            
        } catch (Exception e) {
            log.error("Ошибка при начале добавления файла: eventId={}, userId={}, error={}", 
                    eventId, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка");
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
     * <p><b>Требования:</b> 2.4, 3.1, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 9.1, 9.2</p>
     * 
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param callbackQueryId идентификатор callback query
     */
    private void handleViewFile(Long attachmentId, Long eventId, User user, 
                               Long chatId, String callbackQueryId) throws Exception {
        log.debug("Просмотр файла ID={}, пользователь ID={}", attachmentId, user.getId());
        
        try {
            // Получаем вложение
            Attachment attachment = attachmentService.getAttachment(attachmentId);
            
            // Формируем caption с именем файла (экранируем для MarkdownV2)
            String fileName = attachment.getFileName() != null ? 
                    attachment.getFileName() : "Вложение";
            String caption = MarkdownFormatter.escapeMarkdownV2(fileName);
            
            // Создаем клавиатуру с кнопкой "Назад к вложениям"
            var keyboard = keyboardService.createFileViewKeyboard(eventId);
            
            // Отправляем файл с клавиатурой через TelegramMessageService
            messageService.sendFileWithKeyboard(chatId, attachment.getFileId(), 
                    attachment.getFileType(), caption, keyboard);
            
            messageService.answerCallbackQuery(callbackQueryId, "");
            
            log.info("Файл ID={} успешно отправлен с клавиатурой пользователю ID={}", 
                    attachmentId, user.getId());
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.AttachmentNotFoundException e) {
            log.error("Вложение ID={} не найдено", attachmentId);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Вложение не найдено");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка Telegram API при отправке файла ID={}: {}", 
                    attachmentId, e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка отправки файла");
            messageService.sendMessage(chatId, 
                    "❌ Не удалось отправить файл\\. Попробуйте позже\\.");
        } catch (Exception e) {
            log.error("Неожиданная ошибка при просмотре файла ID={}: {}", 
                    attachmentId, e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка");
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
                messageService.answerCallbackQuery(callbackQueryId, "❌ Нет прав доступа");
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
            
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.AttachmentNotFoundException e) {
            log.error("Вложение ID={} не найдено", attachmentId);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Вложение не найдено");
        } catch (Exception e) {
            log.error("Ошибка при запросе удаления файла ID={}: {}", 
                    attachmentId, e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка");
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
            messageService.answerCallbackQuery(callbackQueryId, "✅ Вложение удалено");
            
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
            messageService.answerCallbackQuery(callbackQueryId, "❌ Вложение не найдено");
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            log.warn("Пользователь ID={} попытался удалить вложение ID={} без прав доступа", 
                    user.getId(), attachmentId);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Нет прав доступа");
            messageService.sendMessage(chatId, 
                    "❌ Только создатель события может удалять вложения\\.");
        } catch (Exception e) {
            log.error("Ошибка при удалении файла ID={}: {}", 
                    attachmentId, e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка");
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
        messageService.answerCallbackQuery(callbackQueryId, "Удаление отменено");
        
        // Возвращаем к списку вложений через handleAttachmentList
        // который использует editOrSendMessage для редактирования
        handleAttachmentList(eventId, user, chatId, messageId, callbackQueryId);
    }
    
    /**
     * Обрабатывает возврат к списку вложений из просмотра файла.
     * 
     * <p>Этот метод вызывается при нажатии кнопки "Назад к вложениям" при просмотре файла.
     * Получает событие и список вложений, формирует сообщение со списком вложений
     * (аналогично {@link #handleAttachmentList}) и использует {@link #editOrSendMessage}
     * для редактирования существующего сообщения или отправки нового при невозможности редактирования.</p>
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
     * <p><b>Требования:</b> 3.2, 3.3, 3.4</p>
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleBackToAttachments(Long eventId, User user, Long chatId, 
                                        Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Возврат к списку вложений для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        try {
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
            
            log.debug("Список вложений отображен при возврате: eventId={}, userId={}, messageId={}", 
                    eventId, user.getId(), resultMessageId);
            
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к списку вложений: eventId={}, userId={}, error={}", 
                    eventId, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка");
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
            
            messageService.answerCallbackQuery(callbackQueryId, "");
            
        } catch (Exception e) {
            log.error("Критическая ошибка при возврате к карточке события ID={}, пользователь ID={}: {}", 
                    eventId, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Произошла ошибка");
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
