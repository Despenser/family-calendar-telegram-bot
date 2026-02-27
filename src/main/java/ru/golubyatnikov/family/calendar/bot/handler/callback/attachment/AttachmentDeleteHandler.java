package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.exception.AttachmentNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.AttachmentFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.message.AttachmentMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;

/**
 * Обработчик для удаления вложений.
 * Управляет процессом удаления файлов с подтверждением и проверкой прав доступа.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentDeleteHandler {
    
    private final TelegramMessageService messageService;
    private final CallbackQueryService callbackQueryService;
    private final AttachmentService attachmentService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final AttachmentListHandler listHandler;
    private final AttachmentMessageService attachmentMessageService;
    private final AttachmentFormattingService formattingService;
    
    /**
     * Обрабатывает запрос подтверждения удаления файла.
     * Поддерживает контекст постраничного списка /my_events.
     * 
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param context контекст callback query
     * @param page номер страницы (null если не из /my_events)
     *
     * @throws Exception если произошла ошибка при обработке запроса
     */
    public void handleDeleteFile(Long attachmentId,
                                 Long eventId,
                                 @NonNull CallbackQueryContext context,
                                 Integer page) throws Exception {

        try {
            validateUserAccess(eventId, context.user());
            Attachment attachment = attachmentService.getAttachment(attachmentId);
            
            String message = formattingService.formatDeleteConfirmation(attachment.getFileName());
            var keyboard = keyboardService.createDeleteAttachmentConfirmationKeyboard(eventId, attachmentId, page);
            
            attachmentMessageService.editOrSendMessage(context.chatId(), context.messageId(), message, 
                    keyboard, context.getUserId(), eventId);
            
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
        } catch (AttachmentNotFoundException e) {
            log.error("Вложение не найдено при удалении файла: attachmentId={}", attachmentId);
            callbackQueryService.answerCallback(context, CallbackMessageFormatter.notFound("Вложение"));
        }
    }
    
    /**
     * Обрабатывает подтверждение удаления файла.
     * Поддерживает контекст постраничного списка /my_events.
     * 
     * @param attachmentId идентификатор вложения
     * @param eventId идентификатор события
     * @param context контекст callback query
     * @param page номер страницы (null если не из /my_events)
     *
     * @throws Exception если произошла ошибка при удалении
     */
    public void handleConfirmDelete(Long attachmentId,
                                    Long eventId,
                                    @NonNull CallbackQueryContext context,
                                    Integer page) throws Exception {

        try {
            attachmentService.deleteAttachment(attachmentId, context.getUserId());
            callbackQueryService.answerCallback(context, CallbackMessages.DELETED);
            
            showUpdatedAttachmentList(eventId, context, page);
            
        } catch (AttachmentNotFoundException e) {
            log.error("Вложение не найдено при подтверждении удаления файла: attachmentId={}", attachmentId);
            callbackQueryService.answerCallback(context, CallbackMessageFormatter.notFound("Вложение"));

        } catch (UnauthorizedAccessException e) {
            log.warn("Попытка удаления чужого вложения: userId={}, attachmentId={}", context.getUserId(), attachmentId);
            callbackQueryService.answerCallback(context, CallbackMessages.NO_ACCESS);
            messageService.sendMessage(context.chatId(), ERROR + " Только создатель события может удалять вложения\\.");
        }
    }
    
    /**
     * Обрабатывает отмену удаления файла.
     * Поддерживает контекст постраничного списка /my_events.
     * 
     * @param eventId идентификатор события
     * @param context контекст callback query
     * @param page номер страницы (null если не из /my_events)
     *
     * @throws Exception если произошла ошибка при отмене
     */
    public void handleCancelDelete(Long eventId, CallbackQueryContext context, Integer page) throws Exception {
        callbackQueryService.answerCallback(context, CallbackMessageFormatter.actionCancelled("Удаление"));
        listHandler.handleAttachmentList(eventId, context, page);
    }
    
    /**
     * Показывает обновленный список вложений после удаления.
     * Поддерживает контекст постраничного списка /my_events.
     * 
     * @param eventId идентификатор события
     * @param context контекст callback query
     * @param page номер страницы (null если не из /my_events)
     *
     * @throws Exception если произошла ошибка при отображении списка
     */
    private void showUpdatedAttachmentList(Long eventId,
                                           @NonNull CallbackQueryContext context,
                                           Integer page) throws Exception {

        if (page != null) {
            attachmentMessageService.showAttachmentListWithContext(eventId, context.user(),
                    context.chatId(), context.messageId(), page);

        } else {
            attachmentMessageService.showAttachmentList(eventId, context.user(),
                    context.chatId(), context.messageId());
        }
    }
    
    /**
     * Валидирует права доступа пользователя к событию.
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     */
    private void validateUserAccess(Long eventId, @NonNull User user) {
        Event event = eventService.getEventById(eventId);
        if (!event.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("Только создатель события может удалять вложения");
        }
    }
}
