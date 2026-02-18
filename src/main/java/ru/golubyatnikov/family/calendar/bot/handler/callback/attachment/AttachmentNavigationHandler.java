package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.context.EventHeaderContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.message.AttachmentMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import java.util.List;

/**
 * Обработчик для навигации между экранами вложений.
 * Управляет переходами между списком вложений и карточкой события.
 * 
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentNavigationHandler {
    
    private final TelegramMessageService messageService;
    private final CallbackQueryService callbackQueryService;
    private final AttachmentService attachmentService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final AttachmentMessageService attachmentMessageService;
    
    /**
     * Обрабатывает возврат к списку вложений из просмотра файла.
     * Заменяет медиа-сообщение на текстовое при необходимости.
     * 
     * @param eventId идентификатор события
     * @param context контекст callback query
     * @param callbackQuery объект callback query для доступа к текущему сообщению
     *
     * @throws Exception если произошла ошибка при возврате к списку вложений
     */
    public void handleBackToAttachments(Long eventId,
                                        CallbackQueryContext context,
                                        CallbackQuery callbackQuery) throws Exception {

        log.debug("Возврат к списку вложений: eventId={}", eventId);
        
        try {
            var maybeMessage = callbackQuery.getMessage();
            Message currentMessage = null;
            
            // Проверяем, что это доступное сообщение
            if (maybeMessage instanceof Message) {
                currentMessage = (Message) maybeMessage;
            }
            
            Event event = eventService.getEventById(eventId);
            List<Attachment> attachments = attachmentService.getEventAttachments(eventId);
            
            String message = attachmentMessageService.buildAttachmentListMessage(attachments);
            boolean isCreator = event.belongsToUser(context.getUserId());
            var keyboard = keyboardService.createAttachmentsListKeyboard(eventId, attachments, isCreator);
            
            Integer resultMessageId;
            if (attachmentMessageService.isMediaMessage(currentMessage)) {
                resultMessageId = attachmentMessageService.replaceMediaWithText(
                        context.chatId(), context.messageId(), message, keyboard, context.getUserId(), eventId);
            } else {
                resultMessageId = attachmentMessageService.editOrSendMessage(
                        context.chatId(), context.messageId(), message, keyboard, context.getUserId(), eventId);
            }
            
            log.debug("Список вложений отображен: messageId={}", resultMessageId);
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к списку вложений: eventId={}", eventId, e);
            callbackQueryService.answerCallback(context, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Обрабатывает возврат к карточке события.
     * Очищает контекст сообщений о вложениях.
     * 
     * @param eventId идентификатор события
     * @param context контекст callback query
     *
     * @throws Exception если произошла ошибка при возврате к карточке события
     */
    public void handleBackToEvent(Long eventId, CallbackQueryContext context) throws Exception {
        log.debug("Возврат к карточке события: eventId={}", eventId);
        
        try {
            Event event = eventService.getEventById(eventId);
            String message = buildEventMessage(event, context.user());
            var keyboard = keyboardService.createEventActionsKeyboard(event, context.getUserId());
            
            messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
            conversationStateService.clearAttachmentMessageContext(context.getUserId());
            
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к карточке события: eventId={}", eventId, e);
            callbackQueryService.answerCallback(context, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Формирует сообщение о событии с учетом контекста шапки.
     * 
     * @param event событие для форматирования
     * @param user пользователь, для которого формируется сообщение
     *
     * @return отформатированное сообщение о событии
     */
    private String buildEventMessage(Event event, @NonNull User user) {
        EventHeaderContext headerContext = conversationStateService.getEventHeaderContext(user.getId());
        
        if (headerContext != null && headerContext.isHasMyEventsHeader()) {
            return botMessageFormattingService.buildEventMessageWithHeader(event, headerContext.getEventCount());
        }
        
        return botMessageFormattingService.buildEventMessage(event);
    }
}
