package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.service.presentation.message.AttachmentMessageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Обработчик для просмотра списка вложений события.
 * Отображает все файлы, прикрепленные к событию, с возможностью просмотра и удаления.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentListHandler {
    
    private final CallbackQueryService callbackQueryService;
    private final AttachmentMessageService attachmentMessageService;
    
    /**
     * Отображает список вложений события.
     * 
     * @param eventId идентификатор события
     * @param context контекст callback query
     *
     * @throws Exception если произошла ошибка при отображении списка
     */
    public void handleAttachmentList(Long eventId, @NonNull CallbackQueryContext context) throws Exception {
        attachmentMessageService.showAttachmentList(eventId, context.user(), context.chatId(), context.messageId());
        callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
    }
}
