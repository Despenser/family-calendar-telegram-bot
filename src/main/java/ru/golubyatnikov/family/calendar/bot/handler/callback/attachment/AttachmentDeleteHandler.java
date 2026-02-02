package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Обработчик для удаления вложений.
 * 
 * <p>Управляет процессом удаления файлов:</p>
 * <ul>
 *   <li>Запрос подтверждения удаления</li>
 *   <li>Выполнение удаления</li>
 *   <li>Отмена удаления</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentDeleteHandler {
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    
    private final TelegramMessageService messageService;
    private final AttachmentService attachmentService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    private final AttachmentListHandler listHandler;
    
    /**
     * Обрабатывает запрос подтверждения удаления файла.
     * 
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    public void handleDeleteFile(Long attachmentId, Long eventId, User user, 
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
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    public void handleConfirmDelete(Long attachmentId, Long eventId, User user, 
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
            String message = buildUpdatedAttachmentListMessage(attachments);
            
            // Проверяем, является ли пользователь создателем события
            boolean isCreator = event.belongsToUser(user.getId());
            
            // Создаем клавиатуру
            var keyboard = keyboardService.createAttachmentsListKeyboard(eventId, attachments, isCreator);
            
            // Используем editOrSendMessage для отображения обновленного списка
            Integer resultMessageId = editOrSendMessage(chatId, messageId, message, 
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
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    public void handleCancelDelete(Long eventId, User user, Long chatId, 
                                   Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Отмена удаления файла для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        // Отправляем callback answer
        messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.actionCancelled("Удаление"));
        
        // Возвращаем к списку вложений через AttachmentListHandler
        listHandler.handleAttachmentList(eventId, user, chatId, messageId, callbackQueryId);
    }
    
    /**
     * Формирует сообщение с обновленным списком вложений.
     * 
     * @param attachments список вложений
     * @return отформатированное сообщение
     */
    private String buildUpdatedAttachmentListMessage(List<Attachment> attachments) {
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
