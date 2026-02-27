package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.context.EventHeaderContext;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.AttachmentFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Обработчик для загрузки вложений к событию.
 * Управляет процессом добавления файлов и отменой загрузки.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentUploadHandler {

    private final TelegramMessageService messageService;
    private final CallbackQueryService callbackQueryService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final AttachmentFormattingService formattingService;

    /**
     * Обрабатывает начало добавления файла.
     * Переводит пользователя в режим ожидания файла.
     * Поддерживает контекст постраничного списка /my_events.
     *
     * @param eventId идентификатор события
     * @param context контекст callback query
     * @param page номер страницы (null если не из /my_events)
     *
     * @throws Exception если произошла ошибка при начале добавления файла
     */
    public void handleAddFile(Long eventId, CallbackQueryContext context, Integer page) throws Exception {
        try {
            validateUserAccess(eventId, context.user());

            String instruction = formattingService.formatUploadInstruction();
            InlineKeyboardMarkup keyboard = keyboardService.createAttachmentUploadKeyboard(eventId, page);

            Integer resultMessageId = editOrSendInstruction(context.chatId(), context.messageId(), instruction, keyboard);
            conversationStateService.setAwaitingFile(context.getUserId(), eventId, context.chatId(), resultMessageId, page);

            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);

        } catch (Exception e) {
            log.error("Ошибка при начале добавления файла: eventId={}", eventId, e);
            callbackQueryService.answerCallback(context, CallbackMessages.ERROR);
            throw e;
        }
    }

    /**
     * Обрабатывает отмену добавления файла.
     * Возвращает пользователя к карточке события.
     * Поддерживает контекст постраничного списка /my_events.
     *
     * @param eventId идентификатор события
     * @param context контекст callback query
     * @param page номер страницы (null если не из /my_events)
     *
     * @throws Exception если произошла ошибка при отмене добавления файла
     */
    public void handleCancelAddFile(Long eventId, @NonNull CallbackQueryContext context, Integer page) throws Exception {
        try {
            conversationStateService.clearAwaitingFile(context.getUserId());

            Event event = eventService.getEventById(eventId);
            String eventMessage = buildEventMessage(event, context.user());
            
            // Используем клавиатуру с контекстом страницы, если он есть
            InlineKeyboardMarkup keyboard = page != null
                ? keyboardService.createEventActionsKeyboardWithContext(event, context.getUserId(), page)
                : keyboardService.createEventActionsKeyboard(event, context.getUserId());

            editOrSendEventCard(context.chatId(), context.messageId(), eventMessage, keyboard);
            callbackQueryService.answerCallback(context, CallbackMessages.CANCELLED);

        } catch (EventNotFoundException e) {
            log.error("Событие не найдено: eventId={}", eventId);
            callbackQueryService.answerCallback(context, CallbackMessageFormatter.notFound("Событие"));
        }
    }

    /**
     * Редактирует сообщение с инструкцией или отправляет новое.
     *
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param instruction текст инструкции
     * @param keyboard клавиатура
     *
     * @return идентификатор результирующего сообщения
     * @throws TelegramApiException если произошла ошибка при работе с Telegram API
     */
    private Integer editOrSendInstruction(Long chatId,
                                          Integer messageId,
                                          String instruction,
                                          InlineKeyboardMarkup keyboard) throws TelegramApiException {

        boolean edited = messageService.tryEditMessageText(chatId, messageId, instruction, keyboard);

        if (edited) {
            return messageId;
        }

        Message newMessage = messageService.sendMessageAndGet(chatId, instruction, keyboard);
        return newMessage.getMessageId();
    }

    /**
     * Редактирует сообщение с карточкой события или отправляет новое.
     *
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param eventMessage текст сообщения о событии
     * @param keyboard клавиатура
     * @throws TelegramApiException если произошла ошибка при работе с Telegram API
     */
    private void editOrSendEventCard(Long chatId,
                                     Integer messageId,
                                     String eventMessage,
                                     InlineKeyboardMarkup keyboard) throws TelegramApiException {

        boolean edited = messageService.tryEditMessageText(chatId, messageId, eventMessage, keyboard);

        if (!edited) {
            messageService.sendMessage(chatId, eventMessage, keyboard);
        }
    }

    /**
     * Формирует сообщение о событии с учетом контекста шапки.
     *
     * @param event событие для форматирования
     * @param user пользователь, для которого формируется сообщение
     * @return отформатированное сообщение о событии
     */
    private String buildEventMessage(Event event, @NonNull User user) {
        EventHeaderContext headerContext = conversationStateService.getEventHeaderContext(user.getId());

        if (headerContext != null && headerContext.isHasMyEventsHeader()) {
            return botMessageFormattingService.buildEventMessageWithHeader(event, headerContext.getEventCount());
        }

        return botMessageFormattingService.buildEventMessage(event);
    }

    /**
     * Валидирует права доступа пользователя к событию.
     *
     * @param eventId идентификатор события
     * @param user пользователь для проверки прав доступа
     *
     * @throws IllegalAccessError если пользователь не является создателем события
     */
    private void validateUserAccess(Long eventId, @NonNull User user) {
        Event event = eventService.getEventById(eventId);
        if (!event.getUser().getId().equals(user.getId())) {
            throw new IllegalAccessError("Только создатель события может добавлять вложения");
        }
    }
}
