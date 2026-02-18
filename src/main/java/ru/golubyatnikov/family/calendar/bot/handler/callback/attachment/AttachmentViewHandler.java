package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.AttachmentNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.domain.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

/**
 * Обработчик для просмотра вложений.
 * Отправляет файл пользователю с клавиатурой для возврата к списку.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentViewHandler {
    
    private final TelegramMessageService messageService;
    private final CallbackQueryService callbackQueryService;
    private final AttachmentService attachmentService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    
    /**
     * Обрабатывает просмотр файла.
     * 
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param context контекст callback query
     *
     * @throws Exception если произошла ошибка при просмотре файла
     */
    public void handleViewFile(Long attachmentId,
                               Long eventId,
                               @NonNull CallbackQueryContext context) throws Exception {

        try {
            deleteCurrentMessage(context.chatId(), context.messageId());
            
            Attachment attachment = attachmentService.getAttachment(attachmentId);
            String caption = formatCaption(attachment);
            var keyboard = keyboardService.createFileViewKeyboard(eventId);

            Message sentMessage = messageService.sendFileWithKeyboardAndGet(
                    context.chatId(), attachment.getFileId(), attachment.getFileType(), caption, keyboard);
            
            saveMessageContext(context.getUserId(), eventId, context.chatId(), sentMessage.getMessageId());
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);

        } catch (AttachmentNotFoundException e) {
            log.error("Вложение не найдено: attachmentId={}", attachmentId);
            callbackQueryService.answerCallback(context, CallbackMessageFormatter.notFound("Вложение"));

        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API при отправке файла: attachmentId={}", attachmentId, e);
            callbackQueryService.answerCallback(context, CallbackMessages.ERROR);
            messageService.sendMessage(context.chatId(), "❌ Не удалось отправить файл\\. Попробуйте позже\\.");
        }
    }
    
    /**
     * Удаляет текущее сообщение со списком вложений.
     * 
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения
     */
    private void deleteCurrentMessage(Long chatId, Integer messageId) {
        try {
            messageService.deleteMessage(chatId, messageId);

        } catch (Exception e) {
            log.error("Не удалось удалить сообщение: messageId={}", messageId);
        }
    }
    
    /**
     * Форматирует caption для файла.
     * 
     * @param attachment вложение
     * @return отформатированный caption
     */
    private @NonNull String formatCaption(@NonNull Attachment attachment) {
        String fileName = attachment.getFileName() != null
                ? attachment.getFileName()
                : "Вложение";

        return MarkdownFormatter.escapeMarkdownV2(fileName);
    }
    
    /**
     * Сохраняет контекст сообщения.
     * 
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения
     */
    private void saveMessageContext(Long userId, Long eventId, Long chatId, Integer messageId) {
        try {
            conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, messageId);

        } catch (Exception e) {
            log.error("Ошибка при сохранении messageId: userId={}, eventId={}", userId, eventId, e);
        }
    }
}
