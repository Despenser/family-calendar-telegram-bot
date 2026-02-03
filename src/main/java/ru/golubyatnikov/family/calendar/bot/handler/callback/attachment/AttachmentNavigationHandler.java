package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Обработчик для навигации между экранами вложений.
 * 
 * <p>Управляет переходами:</p>
 * <ul>
 *   <li>Возврат к списку вложений из просмотра файла</li>
 *   <li>Возврат к карточке события из списка вложений</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentNavigationHandler {
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    
    private final TelegramMessageService messageService;
    private final AttachmentService attachmentService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    private final BotMessageBuilder botMessageBuilder;
    
    /**
     * Обрабатывает возврат к списку вложений из просмотра файла.
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     * @param callbackQuery объект callback query
     */
    public void handleBackToAttachments(Long eventId, User user, Long chatId, 
                                        Integer messageId, String callbackQueryId,
                                        CallbackQuery callbackQuery) throws Exception {
        log.debug("Возврат к списку вложений для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        try {
            // Получаем текущее сообщение из CallbackQuery
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
            String message = buildAttachmentListMessage(attachments);
            
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
                }
                
                // Отправляем новое текстовое сообщение с обработкой ошибок
                try {
                    org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                            messageService.sendMessageAndGet(chatId, message, keyboard);
                    
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
                }
                
            } else {
                log.debug("Текущее сообщение является текстовым, используем редактирование: " +
                        "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
                
                // Используем существующий механизм редактирования
                resultMessageId = editOrSendMessage(chatId, messageId, message, 
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
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    public void handleBackToEvent(Long eventId, User user, Long chatId, 
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
     * Проверяет, является ли сообщение медиа-сообщением.
     * 
     * @param message объект сообщения
     * @return true если сообщение содержит медиа-контент
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
