package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.AttachmentFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.domain.attachment.AttachmentService;
import java.util.List;

/**
 * Сервис для работы с сообщениями вложений.
 * Централизует логику редактирования/отправки сообщений и форматирования.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentMessageService {
    
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final AttachmentFormattingService formattingService;
    private final AttachmentService attachmentService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    
    /**
     * Редактирует существующее сообщение или отправляет новое при неудаче.
     * Автоматически сохраняет messageId в ConversationState.
     * 
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param text текст сообщения
     * @param keyboard inline-клавиатура для сообщения
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     *
     * @return messageId отредактированного или нового сообщения
     * @throws TelegramApiException если произошла критическая ошибка при отправке сообщения
     */
    public Integer editOrSendMessage(Long chatId,
                                     Integer messageId,
                                     String text,
                                     InlineKeyboardMarkup keyboard, 
                                     Long userId,
                                     Long eventId) throws TelegramApiException {
        
        log.debug("Обновление сообщения: chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
        
        boolean edited = messageService.tryEditMessageText(chatId, messageId, text, keyboard);
        
        Integer resultMessageId;
        if (edited) {
            resultMessageId = messageId;
            log.debug("Сообщение отредактировано: messageId={}", messageId);

        } else {
            Message sentMessage = messageService.sendMessageAndGet(chatId, text, keyboard);
            resultMessageId = sentMessage.getMessageId();
            log.debug("Отправлено новое сообщение: messageId={}", resultMessageId);
        }
        
        conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, resultMessageId);
        return resultMessageId;
    }
    
    /**
     * Удаляет медиа-сообщение и отправляет текстовое.
     * Используется при возврате из просмотра файла к списку вложений.
     * 
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор медиа-сообщения для удаления
     * @param text текст нового текстового сообщения
     * @param keyboard inline-клавиатура для нового сообщения
     * @param userId идентификатор пользователя
     * @param eventId идентификатор события
     *
     * @return messageId нового текстового сообщения
     * @throws TelegramApiException если произошла критическая ошибка при отправке сообщения
     */
    public Integer replaceMediaWithText(Long chatId,
                                        Integer messageId,
                                        String text,
                                        InlineKeyboardMarkup keyboard,
                                        Long userId,
                                        Long eventId) throws TelegramApiException {
        
        log.debug("Замена медиа-сообщения на текстовое: chatId={}, messageId={}", chatId, messageId);

        try {
            messageService.deleteMessage(chatId, messageId);

        } catch (Exception e) {
            log.debug("Не удалось удалить медиа-сообщение (возможно, уже удалено): {}", e.getMessage());
        }
        
        // Отправляем новое текстовое сообщение
        Message sentMessage = messageService.sendMessageAndGet(chatId, text, keyboard);
        Integer newMessageId = sentMessage.getMessageId();
        
        conversationStateService.saveAttachmentMessageId(userId, eventId, chatId, newMessageId);
        
        log.debug("Медиа заменено на текст: newMessageId={}", newMessageId);
        return newMessageId;
    }
    
    /**
     * Формирует и отправляет сообщение со списком вложений события.
     * Централизует логику получения данных, форматирования и отправки.
     *
     * @param eventId идентификатор события
     * @param user пользователь, запросивший список вложений
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     *
     * @throws TelegramApiException если произошла ошибка при отправке сообщения
     */
    public void showAttachmentList(Long eventId,
                                   @NonNull User user,
                                   Long chatId,
                                   Integer messageId) throws TelegramApiException {
        
        Event event = eventService.getEventById(eventId);
        List<Attachment> attachments = attachmentService.getEventAttachments(eventId);
        
        String message = formattingService.formatAttachmentList(attachments);
        boolean isCreator = event.belongsToUser(user.getId());
        var keyboard = keyboardService.createAttachmentsListKeyboard(eventId, attachments, isCreator);

        editOrSendMessage(chatId, messageId, message, keyboard, user.getId(), eventId);
    }
    
    /**
     * Формирует сообщение со списком вложений.
     * 
     * @param attachments список вложений для форматирования
     * @return отформатированное сообщение со списком вложений
     */
    public String buildAttachmentListMessage(List<Attachment> attachments) {
        return formattingService.formatAttachmentList(attachments);
    }
    
    /**
     * Проверяет, является ли сообщение медиа-сообщением.
     * Медиа-сообщения содержат фото, документы, видео или аудио.
     * 
     * @param message объект сообщения Telegram для проверки
     * @return true, если сообщение содержит медиа-контент, false в противном случае
     */
    public boolean isMediaMessage(Message message) {
        if (message == null) {
            return false;
        }
        return message.hasPhoto()
                || message.hasDocument()
                || message.hasVideo()
                || message.hasAudio();
    }
}
