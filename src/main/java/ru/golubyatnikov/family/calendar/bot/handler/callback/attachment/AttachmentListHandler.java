package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Обработчик для просмотра списка вложений события.
 * 
 * <p>Формирует и отображает список всех вложений события с информацией:</p>
 * <ul>
 *   <li>Эмодзи для типа файла (📄, 🖼️, 🎥, 🎵)</li>
 *   <li>Имя файла</li>
 *   <li>Размер файла в КБ/МБ</li>
 *   <li>Дата загрузки</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentListHandler {
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    
    private final TelegramMessageService messageService;
    private final AttachmentService attachmentService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    
    /**
     * Отображает список вложений события.
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    public void handleAttachmentList(Long eventId, User user, Long chatId, 
                                     Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Отображение списка вложений для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        // Получаем событие для проверки прав доступа
        Event event = eventService.getEventById(eventId);
        
        // Получаем список вложений
        List<Attachment> attachments = attachmentService.getEventAttachments(eventId);
        
        // Формируем сообщение
        String message = buildAttachmentListMessage(attachments);
        
        // Проверяем, является ли пользователь создателем события
        boolean isCreator = event.belongsToUser(user.getId());
        
        // Создаем клавиатуру
        InlineKeyboardMarkup keyboard = keyboardService.createAttachmentsListKeyboard(eventId, attachments, isCreator);
        
        // Используем editOrSendMessage для редактирования или отправки нового сообщения
        Integer resultMessageId = editOrSendMessage(chatId, messageId, message, 
                keyboard, user.getId(), eventId);
        
        log.debug("Список вложений отображен: eventId={}, userId={}, messageId={}", 
                eventId, user.getId(), resultMessageId);
        
        messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
    }
    
    /**
     * Формирует сообщение со списком вложений.
     * 
     * @param attachments список вложений
     * @return отформатированное сообщение
     */
    private String buildAttachmentListMessage(List<Attachment> attachments) {
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
        
        return message.toString();
    }
    
    /**
     * Возвращает эмодзи для типа файла.
     * 
     * @param fileType тип файла
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
     * @return отформатированная строка
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
     * Редактирует существующее сообщение или отправляет новое при неудаче.
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param text текст сообщения
     * @param keyboard клавиатура
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     * @return messageId отредактированного или нового сообщения
     */
    private Integer editOrSendMessage(Long chatId, Integer messageId, String text, 
                                     InlineKeyboardMarkup keyboard, 
                                     Long userId, Long eventId) 
            throws org.telegram.telegrambots.meta.exceptions.TelegramApiException {
        
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
                log.info("Редактирование не удалось (сообщение удалено/старое), отправка нового сообщения: " +
                        "chatId={}, oldMessageId={}, userId={}, eventId={}", 
                        chatId, messageId, userId, eventId);
                
                org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                        messageService.sendMessageAndGet(chatId, text, keyboard);
                
                Integer newMessageId = sentMessage.getMessageId();
                
                log.info("Новое сообщение успешно отправлено (fallback): chatId={}, newMessageId={}, userId={}, eventId={}", 
                        chatId, newMessageId, userId, eventId);
                
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
