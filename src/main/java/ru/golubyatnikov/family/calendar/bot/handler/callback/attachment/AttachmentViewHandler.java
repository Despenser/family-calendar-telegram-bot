package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

/**
 * Обработчик для просмотра вложений.
 * 
 * <p>Отправляет файл пользователю через Telegram API с клавиатурой навигации.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentViewHandler {
    
    private final TelegramMessageService messageService;
    private final AttachmentService attachmentService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    
    /**
     * Обрабатывает просмотр файла.
     * 
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор текущего сообщения для удаления
     * @param callbackQueryId идентификатор callback query
     */
    public void handleViewFile(Long attachmentId, Long eventId, User user, 
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
}
