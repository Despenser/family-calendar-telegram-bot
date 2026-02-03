package ru.golubyatnikov.family.calendar.bot.service.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик файловых сообщений.
 * 
 * <p>Обрабатывает три типа файловых сообщений:</p>
 * <ul>
 *   <li>Загрузка вложения к событию (режим ожидания файла)</li>
 *   <li>Общая обработка файлов (с активным черновиком)</li>
 *   <li>Подсказка при ожидании файла (текстовое сообщение)</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileMessageHandler {

    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final ConversationService conversationService;
    private final ConversationStateService conversationStateService;
    private final AttachmentService attachmentService;
    private final EventService eventService;
    private final BotMessageBuilder botMessageBuilder;

    /**
     * Обрабатывает подсказку при ожидании файла (текстовое сообщение).
     */
    public void handleAwaitingFileHint(Message message, User user) {
        Long chatId = message.getChatId();
        String hintMessage = formatMessage(
            "📎 Пожалуйста, отправьте файл " + escape("(документ, фото, видео или аудио)") + "\n\n" +
            "_Для отмены нажмите кнопку 'Отмена' в списке вложений_"
        );
        try {
            messageService.sendMessage(chatId, hintMessage);
            log.debug("Отправлена подсказка пользователю в режиме ожидания файла: userId={}", user.getId());
        } catch (Exception e) {
            log.error("Ошибка при отправке подсказки: {}", e.getMessage());
        }
    }

    /**
     * Обрабатывает загрузку файла для вложения к событию.
     */
    public void handleAttachmentUpload(Message message, User user) {
        Long chatId = message.getChatId();
        Long userId = user.getId();
        Long telegramId = user.getTelegramId();
        
        log.debug("Обработка загрузки файла для вложения: userId={}, telegramId={}", userId, telegramId);
        
        try {
            ConversationStateService.AwaitingFileContext context = 
                conversationStateService.getAwaitingFileContext(userId);
            
            if (context == null) {
                log.warn("Контекст ожидания файла не найден для пользователя: userId={}", userId);
                conversationStateService.clearAwaitingFile(userId);
                messageService.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте добавить файл заново.");
                return;
            }
            
            Long eventId = context.getEventId();
            Integer messageId = context.getMessageId();
            
            FileInfo fileInfo = extractFileInfo(message, telegramId, eventId);
            if (fileInfo == null) {
                conversationStateService.clearAwaitingFile(userId);
                messageService.sendMessage(chatId, 
                    "❌ Неподдерживаемый тип файла. Отправьте документ, фото, видео или аудио.");
                return;
            }
            
            ru.golubyatnikov.family.calendar.bot.model.Attachment attachment = 
                attachmentService.saveAttachment(eventId, fileInfo.fileId, fileInfo.fileName, 
                                                fileInfo.fileType, fileInfo.fileSize);
            
            log.info("Вложение успешно сохранено: attachmentId={}, eventId={}, userId={}", 
                    attachment.getId(), eventId, userId);
            
            messageService.deleteMessageSilently(chatId, message.getMessageId());
            log.debug("Запрос на удаление сообщения пользователя с файлом отправлен: chatId={}, messageId={}, userId={}", 
                    chatId, message.getMessageId(), userId);
            
            updateAttachmentsList(chatId, messageId, eventId, userId);
            
            conversationStateService.clearAwaitingFile(userId);
            log.debug("Состояние ожидания файла очищено: userId={}", userId);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.FileSizeExceededException e) {
            handleFileSizeError(chatId, userId, telegramId, e);
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            handleEventNotFoundError(chatId, userId, telegramId, e);
        } catch (ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
            handleUnauthorizedError(chatId, userId, telegramId, e);
        } catch (Exception e) {
            handleGeneralError(chatId, userId, telegramId, e);
        }
    }


    /**
     * Обрабатывает общий файл (с активным черновиком или без).
     */
    public void handleGeneralFile(Message message, User user) {
        Long chatId = message.getChatId();
        Long telegramId = user.getTelegramId();
        
        if (!conversationService.hasActiveDraft(user.getId())) {
            log.debug("Пользователь отправил файл без активного черновика: userId={}, telegramId={}", 
                    user.getId(), telegramId);
            try {
                messageService.sendMessage(chatId, 
                    "❌ Для прикрепления файлов сначала создайте событие с помощью ➕ " + escape("/add_event"));
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения: {}", e.getMessage());
            }
            return;
        }
        
        ru.golubyatnikov.family.calendar.bot.model.Event draft = 
            conversationService.getActiveDraft(user.getId());
        
        if (draft.getEventDate() == null || draft.getEventTime() == null) {
            log.debug("Пользователь отправил файл на раннем этапе создания события: userId={}, telegramId={}", 
                    user.getId(), telegramId);
            try {
                messageService.sendMessage(chatId, 
                    "❌ Сначала завершите создание события, затем вы сможете прикрепить файлы");
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения: {}", e.getMessage());
            }
            return;
        }
        
        try {
            FileInfo fileInfo = extractFileInfo(message, telegramId, draft.getId());
            if (fileInfo == null) {
                log.warn("Сообщение не содержит документа или фото: telegramId={}", telegramId);
                return;
            }
            
            final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 МБ
            if (fileInfo.fileSize > MAX_FILE_SIZE) {
                log.warn("Файл слишком большой: size={}, max={}, telegramId={}", 
                        fileInfo.fileSize, MAX_FILE_SIZE, telegramId);
                messageService.sendMessage(chatId, 
                    formatMessage("❌ Размер файла превышает максимально допустимый (20 МБ).\n\n" +
                                "Размер вашего файла: %.2f МБ", fileInfo.fileSize / (1024.0 * 1024.0)));
                return;
            }
            
            attachmentService.saveAttachment(draft.getId(), fileInfo.fileId, fileInfo.fileName, 
                                           fileInfo.fileType, fileInfo.fileSize);
            
            String response = bold("✅ Файл успешно прикреплен!") + "\n\n" +
                "📎 Название: " + escape(fileInfo.fileName) + "\n" +
                formatMessage("📊 Размер: %.2f МБ\n\n", fileInfo.fileSize / (1024.0 * 1024.0)) +
                "Вы можете продолжить прикреплять файлы или завершить создание события.";
            
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            try {
                messageService.sendMessage(chatId, response, keyboard);
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: {}", ex.getMessage());
            }
            
            log.debug("Файл успешно прикреплен к событию: eventId={}, fileName='{}', telegramId={}", 
                     draft.getId(), fileInfo.fileName, telegramId);
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.FileSizeExceededException e) {
            log.warn("Размер файла превышает лимит: error={}, telegramId={}", e.getMessage(), telegramId);
            try {
                messageService.sendMessage(chatId, "❌ " + e.getMessage());
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении вложения: eventId={}, telegramId={}, error={}", 
                     draft.getId(), telegramId, e.getMessage(), e);
            try {
                messageService.sendMessage(chatId, 
                    "❌ " + bold("Произошла ошибка при сохранении файла") + "\\. " + 
                    italic("Попробуйте еще раз\\."));
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: {}", ex.getMessage());
            }
        }
    }

    /**
     * Извлекает информацию о файле из сообщения.
     */
    private FileInfo extractFileInfo(Message message, Long telegramId, Long eventId) {
        if (message.hasDocument()) {
            org.telegram.telegrambots.meta.api.objects.Document document = message.getDocument();
            log.debug("Получен документ: fileId={}, fileName='{}', size={}, eventId={}", 
                    document.getFileId(), document.getFileName(), document.getFileSize(), eventId);
            return new FileInfo(document.getFileId(), document.getFileName(), 
                              "document", document.getFileSize());
            
        } else if (message.hasPhoto()) {
            List<org.telegram.telegrambots.meta.api.objects.PhotoSize> photos = message.getPhoto();
            org.telegram.telegrambots.meta.api.objects.PhotoSize photo = photos.get(photos.size() - 1);
            String fileName = "photo_" + System.currentTimeMillis() + ".jpg";
            log.debug("Получено фото: fileId={}, size={}, eventId={}", 
                    photo.getFileId(), photo.getFileSize(), eventId);
            return new FileInfo(photo.getFileId(), fileName, "photo", photo.getFileSize().longValue());
            
        } else if (message.hasVideo()) {
            org.telegram.telegrambots.meta.api.objects.Video video = message.getVideo();
            String fileName = video.getFileName() != null ? video.getFileName() : 
                            "video_" + System.currentTimeMillis() + ".mp4";
            log.debug("Получено видео: fileId={}, fileName='{}', size={}, eventId={}", 
                    video.getFileId(), fileName, video.getFileSize(), eventId);
            return new FileInfo(video.getFileId(), fileName, "video", video.getFileSize().longValue());
            
        } else if (message.hasAudio()) {
            org.telegram.telegrambots.meta.api.objects.Audio audio = message.getAudio();
            String fileName = audio.getFileName() != null ? audio.getFileName() : 
                            "audio_" + System.currentTimeMillis() + ".mp3";
            log.debug("Получено аудио: fileId={}, fileName='{}', size={}, eventId={}", 
                    audio.getFileId(), fileName, audio.getFileSize(), eventId);
            return new FileInfo(audio.getFileId(), fileName, "audio", audio.getFileSize().longValue());
        }
        
        log.warn("Сообщение не содержит поддерживаемого типа файла: telegramId={}", telegramId);
        return null;
    }


    /**
     * Обновляет список вложений события.
     */
    private void updateAttachmentsList(Long chatId, Integer messageId, Long eventId, Long userId) {
        try {
            ru.golubyatnikov.family.calendar.bot.model.Event event = eventService.getEventById(eventId);
            List<ru.golubyatnikov.family.calendar.bot.model.Attachment> attachments = 
                attachmentService.getEventAttachments(eventId);
            
            boolean isCreator = event.belongsToUser(userId);
            
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(bold("📎 Вложения события")).append("\n\n");
            messageBuilder.append(bold(event.getTitle())).append("\n\n");
            
            if (attachments.isEmpty()) {
                messageBuilder.append(italic("У этого события пока нет вложений\\."));
            } else {
                for (int i = 0; i < attachments.size(); i++) {
                    ru.golubyatnikov.family.calendar.bot.model.Attachment att = attachments.get(i);
                    
                    String emoji = switch (att.getFileType()) {
                        case "photo" -> "🖼️";
                        case "video" -> "🎥";
                        case "audio" -> "🎵";
                        default -> "📄";
                    };
                    
                    String sizeStr;
                    if (att.getFileSize() >= 1024 * 1024) {
                        sizeStr = String.format("%.2f МБ", att.getFileSize() / (1024.0 * 1024.0));
                    } else {
                        sizeStr = String.format("%.2f КБ", att.getFileSize() / 1024.0);
                    }
                    
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
            
            log.debug("Полный текст сообщения перед отправкой: chatId={}, messageId={}, textLength={}", 
                    chatId, messageId, fullText.length());
            
            Integer resultMessageId = editOrSendMessage(chatId, messageId, fullText, keyboard, userId, eventId);
            
            log.debug("Список вложений обновлен: eventId={}, messageId={}", eventId, resultMessageId);
            
        } catch (TelegramApiException e) {
            log.warn("Не удалось обновить список вложений: eventId={}, messageId={}, error={}", 
                    eventId, messageId, e.getMessage());
        }
    }

    /**
     * Редактирует существующее сообщение или отправляет новое.
     */
    private Integer editOrSendMessage(Long chatId, Integer messageId, String text, 
                                     InlineKeyboardMarkup keyboard, Long userId, Long eventId) 
            throws TelegramApiException {
        
        log.debug("Попытка редактирования сообщения: chatId={}, messageId={}, userId={}, eventId={}", 
                chatId, messageId, userId, eventId);
        
        try {
            boolean edited = messageService.tryEditMessageText(chatId, messageId, text, keyboard);
            
            if (edited) {
                log.info("Сообщение успешно отредактировано: chatId={}, messageId={}, userId={}, eventId={}", 
                        chatId, messageId, userId, eventId);
                conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, messageId);
                return messageId;
            } else {
                log.info("Редактирование не удалось, отправка нового сообщения: chatId={}, oldMessageId={}, userId={}, eventId={}", 
                        chatId, messageId, userId, eventId);
                
                org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                        messageService.sendMessageAndGet(chatId, text, keyboard);
                
                Integer newMessageId = sentMessage.getMessageId();
                log.info("Новое сообщение успешно отправлено: chatId={}, newMessageId={}, userId={}, eventId={}", 
                        chatId, newMessageId, userId, eventId);
                
                conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, newMessageId);
                return newMessageId;
            }
        } catch (TelegramApiException e) {
            log.error("Критическая ошибка при редактировании/отправке сообщения: chatId={}, messageId={}, userId={}, eventId={}, error={}", 
                     chatId, messageId, userId, eventId, e.getMessage(), e);
            throw e;
        }
    }

    private void handleFileSizeError(Long chatId, Long userId, Long telegramId, 
                                     ru.golubyatnikov.family.calendar.bot.exception.FileSizeExceededException e) {
        log.warn("Размер файла превышает лимит: userId={}, error={}", userId, e.getMessage());
        conversationStateService.clearAwaitingFile(userId);
        try {
            messageService.sendMessage(chatId, 
                formatMessage("❌ Размер файла превышает максимально допустимый \\(20 МБ\\)\\."));
        } catch (TelegramApiException ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", telegramId, ex.getMessage(), ex);
        }
    }

    private void handleEventNotFoundError(Long chatId, Long userId, Long telegramId, 
                                         ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
        log.error("Событие не найдено при добавлении вложения: userId={}, error={}", userId, e.getMessage());
        conversationStateService.clearAwaitingFile(userId);
        try {
            messageService.sendMessage(chatId, "❌ Событие не найдено. Возможно, оно было удалено.");
        } catch (TelegramApiException ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", telegramId, ex.getMessage(), ex);
        }
    }

    private void handleUnauthorizedError(Long chatId, Long userId, Long telegramId, 
                                        ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException e) {
        log.error("Нет прав для добавления вложения: userId={}, error={}", userId, e.getMessage());
        conversationStateService.clearAwaitingFile(userId);
        try {
            messageService.sendMessage(chatId, "❌ У вас нет прав для добавления вложений к этому событию.");
        } catch (TelegramApiException ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", telegramId, ex.getMessage(), ex);
        }
    }

    private void handleGeneralError(Long chatId, Long userId, Long telegramId, Exception e) {
        log.error("Ошибка при обработке загрузки файла для вложения: userId={}, telegramId={}, error={}", 
                userId, telegramId, e.getMessage(), e);
        conversationStateService.clearAwaitingFile(userId);
        try {
            messageService.sendMessage(chatId, 
                "❌ " + bold("Произошла ошибка при сохранении файла") + "\\. " + 
                italic("Попробуйте еще раз\\."));
        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", telegramId, ex.getMessage(), ex);
        }
    }

    /**
     * Вспомогательный класс для хранения информации о файле.
     */
    private record FileInfo(String fileId, String fileName, String fileType, Long fileSize) {}
}
