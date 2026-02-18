package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Audio;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Video;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.FileSizeExceededException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.context.AwaitingFileContext;
import ru.golubyatnikov.family.calendar.bot.model.dto.FileInfo;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик файловых сообщений.
 *
 * @author Golubyatnikov Aleksey
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
    private final DateTimeFormattingService dateTimeFormattingService;

    /**
     * Обрабатывает подсказку при ожидании файла (текстовое сообщение).
     */
    public void handleAwaitingFileHint(@NonNull Message message, User user) {
        Long chatId = message.getChatId();
        String hintMessage = formatMessage(
            "📎 Пожалуйста, отправьте файл " + escape("(документ, фото, видео или аудио)") + "\n\n" +
            "_Для отмены нажмите кнопку 'Отмена' в списке вложений_"
        );
        sendMessage(chatId, hintMessage);
    }

    /**
     * Обрабатывает загрузку файла для вложения к событию.
     */
    public void handleAttachmentUpload(@NonNull Message message, @NonNull User user) {
        Long chatId = message.getChatId();
        Long userId = user.getId();
        Long telegramId = user.getTelegramId();
        
        try {
            AwaitingFileContext context = conversationStateService.getAwaitingFileContext(userId);
            
            if (context == null) {
                conversationStateService.clearAwaitingFile(userId);
                messageService.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте добавить файл заново.");
                return;
            }
            
            Long eventId = context.getEventId();
            Integer messageId = context.getMessageId();
            
            FileInfo fileInfo = extractFileInfo(message);
            if (fileInfo == null) {
                conversationStateService.clearAwaitingFile(userId);
                messageService.sendMessage(chatId,
                        "❌ Неподдерживаемый тип файла. Отправьте документ, фото, видео или аудио.");

                return;
            }
            
            attachmentService.saveAttachment(
                    eventId, fileInfo.fileId(), fileInfo.fileName(), fileInfo.fileType(), fileInfo.fileSize()
            );
            
            messageService.deleteMessageSilently(chatId, message.getMessageId());
            updateAttachmentsList(chatId, messageId, eventId, userId);
            
            conversationStateService.clearAwaitingFile(userId);

        } catch (FileSizeExceededException e) {
            handleFileSizeError(chatId, userId, telegramId);

        } catch (EventNotFoundException e) {
            handleEventNotFoundError(chatId, userId, telegramId);

        } catch (UnauthorizedAccessException e) {
            handleUnauthorizedError(chatId, userId, telegramId);

        } catch (Exception e) {
            handleGeneralError(chatId, userId, telegramId, e);
        }
    }


    /**
     * Обрабатывает общий файл (с активным черновиком или без).
     */
    public void handleGeneralFile(@NonNull Message message, @NonNull User user) {
        Long chatId = message.getChatId();
        Long telegramId = user.getTelegramId();
        
        if (!conversationService.hasActiveDraft(user.getId())) {
            sendMessage(chatId, 
                "❌ Для прикрепления файлов сначала создайте событие с помощью ➕ " + escape("/add_event"));
            return;
        }
        
        Event draft = conversationService.getActiveDraft(user.getId());
        
        if (draft.getEventDate() == null || draft.getEventTime() == null) {
            sendMessage(chatId, "❌ Сначала завершите создание события, затем вы сможете прикрепить файлы");
            return;
        }
        
        try {
            processGeneralFileAttachment(message, chatId, telegramId, draft);

        } catch (FileSizeExceededException e) {
            handleFileSizeExceededForGeneralFile(chatId, telegramId, e);

        } catch (Exception e) {
            handleGeneralFileError(chatId, telegramId, draft, e);
        }
    }

    /**
     * Обрабатывает прикрепление файла к черновику события.
     */
    private void processGeneralFileAttachment(Message message, Long chatId, Long telegramId, Event draft) {
        FileInfo fileInfo = extractFileInfo(message);
        if (fileInfo == null) {
            return;
        }
        
        final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 МБ

        if (fileInfo.fileSize() > MAX_FILE_SIZE) {
            log.warn("Файл слишком большой: size={}, max={}, telegramId={}", 
                    fileInfo.fileSize(), MAX_FILE_SIZE, telegramId);

            sendMessage(chatId,
                    formatMessage("""
                        ❌ Размер файла превышает максимально допустимый (20 МБ).
                        
                        Размер вашего файла: %.2f МБ""", fileInfo.fileSize() / (1024.0 * 1024.0))
            );

            return;
        }
        
        attachmentService.saveAttachment(draft.getId(), fileInfo.fileId(), fileInfo.fileName(), 
                                       fileInfo.fileType(), fileInfo.fileSize());
        
        sendSuccessfulAttachmentMessage(chatId, fileInfo);
    }

    /**
     * Отправляет сообщение об успешном прикреплении файла.
     */
    private void sendSuccessfulAttachmentMessage(Long chatId, @NonNull FileInfo fileInfo) {
        String response = bold("✅ Файл успешно прикреплен!") + "\n\n" +
            "📎 Название: " + escape(fileInfo.fileName()) + "\n" +
            formatMessage("📊 Размер: %.2f МБ\n\n", fileInfo.fileSize() / (1024.0 * 1024.0)) +
            "Вы можете продолжить прикреплять файлы или завершить создание события.";
        
        ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();

        try {
            messageService.sendMessage(chatId, response, keyboard);

        } catch (Exception ex) {
            logMessageSendError(ex);
        }
    }

    /**
     * Обрабатывает ошибку превышения размера файла для общего файла.
     */
    private void handleFileSizeExceededForGeneralFile(Long chatId,
                                                      Long telegramId,
                                                      @NonNull FileSizeExceededException e) {

        log.warn("Размер файла превышает лимит: error={}, telegramId={}", e.getMessage(), telegramId);
        sendMessage(chatId, "❌ " + e.getMessage());
    }

    /**
     * Обрабатывает общую ошибку при сохранении файла.
     */
    private void handleGeneralFileError(Long chatId,
                                        Long telegramId,
                                        @NonNull Event draft,
                                        Exception e) {

        log.error("Ошибка при сохранении вложения: eventId={}, telegramId={}, error={}", 
                 draft.getId(), telegramId, e.getMessage(), e);
        
        sendMessage(chatId, 
            "❌ " + bold("Произошла ошибка при сохранении файла") + "\\. " + 
            italic("Попробуйте еще раз\\."));
    }

    /**
     * Извлекает информацию о файле из сообщения.
     */
    private @Nullable FileInfo extractFileInfo(@NonNull Message message) {
        if (message.hasDocument()) {
            Document document = message.getDocument();
            return new FileInfo(document.getFileId(), document.getFileName(), "document", document.getFileSize());
            
        } else if (message.hasPhoto()) {
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize photo = photos.getLast();
            String fileName = "photo_" + System.currentTimeMillis() + ".jpg";

            return new FileInfo(photo.getFileId(), fileName, "photo", photo.getFileSize().longValue());
            
        } else if (message.hasVideo()) {
            Video video = message.getVideo();
            String fileName = video.getFileName() != null
                    ? video.getFileName()
                    : "video_" + System.currentTimeMillis() + ".mp4";

            return new FileInfo(video.getFileId(), fileName, "video", video.getFileSize());
            
        } else if (message.hasAudio()) {
            Audio audio = message.getAudio();
            String fileName = audio.getFileName() != null
                    ? audio.getFileName()
                    : "audio_" + System.currentTimeMillis() + ".mp3";

            return new FileInfo(audio.getFileId(), fileName, "audio", audio.getFileSize());
        }
        
        return null;
    }


    /**
     * Обновляет список вложений события.
     */
    private void updateAttachmentsList(Long chatId, Integer messageId, Long eventId, Long userId) {
        try {
            Event event = eventService.getEventById(eventId);
            List<Attachment> attachments = attachmentService.getEventAttachments(eventId);
            
            boolean isCreator = event.belongsToUser(userId);
            
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(bold("📎 Вложения события")).append("\n\n");
            messageBuilder.append(bold(event.getTitle())).append("\n\n");
            
            if (attachments.isEmpty()) {
                messageBuilder.append(italic("У этого события пока нет вложений\\."));

            } else {
                for (int i = 0; i < attachments.size(); i++) {
                    Attachment att = attachments.get(i);
                    
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
                    
                    String dateStr = dateTimeFormattingService.formatDateTime(att.getUploadedAt());
                    
                    messageBuilder.append(emoji).append(" ")
                                 .append(bold(att.getFileName())).append("\n")
                                 .append("📊 Размер: ").append(escape(sizeStr)).append("\n")
                                 .append("📅 Загружено: ").append(escape(dateStr));
                    
                    if (i < attachments.size() - 1) {
                        messageBuilder.append("\n\n━━━━━━━━━━━━━━━━━━━━\n\n");
                    }
                }
            }
            
            InlineKeyboardMarkup keyboard = keyboardService.createAttachmentsListKeyboard(
                eventId, attachments, isCreator);
            
            String fullText = messageBuilder.toString();
            
            editOrSendMessage(chatId, messageId, fullText, keyboard, userId, eventId);
            
        } catch (TelegramApiException e) {
            log.warn("Не удалось обновить список вложений: eventId={}, messageId={}, error={}", 
                    eventId, messageId, e.getMessage());
        }
    }

    /**
     * Редактирует существующее сообщение или отправляет новое.
     */
    private void editOrSendMessage(Long chatId,
                                   Integer messageId,
                                   String text,
                                   InlineKeyboardMarkup keyboard,
                                   Long userId,
                                   Long eventId) throws TelegramApiException {
        
        try {
            boolean edited = messageService.tryEditMessageText(chatId, messageId, text, keyboard);
            
            if (edited) {
                conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, messageId);

            } else {
                Message sentMessage = messageService.sendMessageAndGet(chatId, text, keyboard);
                
                Integer newMessageId = sentMessage.getMessageId();
                conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, newMessageId);
            }

        } catch (TelegramApiException e) {
            log.error("Критическая ошибка при редактировании/отправке сообщения: chatId={}, messageId={}, userId={}, eventId={}, error={}", 
                     chatId, messageId, userId, eventId, e.getMessage(), e);

            throw e;
        }
    }

    private void handleFileSizeError(Long chatId, Long userId, Long telegramId) {
        String errorMessage = formatMessage("❌ Размер файла превышает максимально допустимый.");
        sendErrorMessageAndClearState(chatId, userId, telegramId, errorMessage);
    }

    private void handleEventNotFoundError(Long chatId, Long userId, Long telegramId) {
        String errorMessage = "❌ Событие не найдено. Возможно, оно было удалено.";
        sendErrorMessageAndClearState(chatId, userId, telegramId, errorMessage);
    }

    private void handleUnauthorizedError(Long chatId, Long userId, Long telegramId) {
        String errorMessage = "❌ У вас нет прав для добавления вложений к этому событию.";
        sendErrorMessageAndClearState(chatId, userId, telegramId, errorMessage);
    }

    private void handleGeneralError(Long chatId, Long userId, Long telegramId, Exception e) {
        log.error("Ошибка при обработке загрузки файла для вложения: userId={}, telegramId={}, error={}", 
                userId, telegramId, e.getMessage(), e);

        String errorMessage = "❌ "
                + bold("Произошла ошибка при сохранении файла") + "\\. "
                + italic("Попробуйте еще раз\\.");

        sendErrorMessageAndClearState(chatId, userId, telegramId, errorMessage);
    }

    /**
     * Отправляет сообщение об ошибке и очищает состояние ожидания файла.
     */
    private void sendErrorMessageAndClearState(Long chatId, Long userId, Long telegramId, String errorMessage) {
        conversationStateService.clearAwaitingFile(userId);
        try {
            messageService.sendMessage(chatId, errorMessage);

        } catch (TelegramApiException ex) {
            logErrorMessageSendFailure(telegramId, ex);
        }
    }

    /**
     * Отправляет сообщение с обработкой ошибок.
     */
    private void sendMessage(Long chatId, String message) {
        try {
            messageService.sendMessage(chatId, message);

        } catch (Exception e) {
            logMessageSendError(e);
        }
    }

    /**
     * Логирует ошибку при отправке сообщения об ошибке.
     */
    private void logErrorMessageSendFailure(Long telegramId, TelegramApiException ex) {
        log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", telegramId, ex.getMessage(), ex);
    }

    /**
     * Логирует ошибку при отправке сообщения.
     */
    private void logMessageSendError(@NonNull Exception ex) {
        log.error("Ошибка при отправке сообщения: {}", ex.getMessage());
    }
}
