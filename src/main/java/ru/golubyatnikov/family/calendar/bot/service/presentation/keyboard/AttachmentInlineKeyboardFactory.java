package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;

import java.util.ArrayList;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.DELETE;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.ADD_EVENT;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.BACK;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.FileTypes.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.CANCELLED;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.SUCCESS;

/**
 * Фабрика для создания inline клавиатур, связанных с вложениями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class AttachmentInlineKeyboardFactory {

    private final KeyboardFactory keyboardFactory;

    /**
     * Создает inline клавиатуру для списка вложений события.
     * 
     * @param eventId идентификатор события
     * @param attachments список вложений
     * @param isCreator является ли пользователь создателем
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentsListKeyboard(Long eventId,
                                                              List<Attachment> attachments, boolean isCreator) {

        return createAttachmentsListKeyboard(eventId, attachments, isCreator, null);
    }

    /**
     * Создает inline клавиатуру для списка вложений события с контекстом страницы.
     * 
     * @param eventId идентификатор события
     * @param attachments список вложений
     * @param isCreator является ли пользователь создателем
     * @param page номер страницы для возврата к списку (может быть null)
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentsListKeyboard(Long eventId, List<Attachment> attachments,
                                                              boolean isCreator, Integer page) {

        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        
        if (attachments != null && !attachments.isEmpty()) {
            for (Attachment attachment : attachments) {
                String emoji = switch (attachment.getFileType()) {
                    case "photo" -> PHOTO;
                    case "video" -> VIDEO;
                    case "audio" -> AUDIO;
                    default -> DOCUMENT;
                };
                
                String buttonText = emoji + " " + (attachment.getFileName() != null ? attachment.getFileName() : "Файл");
                
                // Формируем payload с учетом контекста страницы
                String viewPayload = page != null 
                    ? "view_" + eventId + "_" + attachment.getId() + "_" + page
                    : "view_" + eventId + "_" + attachment.getId();
                    
                String deletePayload = page != null
                    ? "delete_" + eventId + "_" + attachment.getId() + "_" + page
                    : "delete_" + eventId + "_" + attachment.getId();
                
                if (isCreator) {
                    rows.add(keyboardFactory.createRow(
                        keyboardFactory.createButton(buttonText, CallbackPrefix.ATTACH_FILE.withPayload(viewPayload)),
                        keyboardFactory.createButton(DELETE, CallbackPrefix.ATTACH_FILE.withPayload(deletePayload))
                    ));

                } else {
                    rows.add(keyboardFactory.createRow(
                        keyboardFactory.createButton(buttonText, CallbackPrefix.ATTACH_FILE.withPayload(viewPayload))
                    ));
                }
            }
        }
        
        if (isCreator) {
            String addPayload = page != null ? "add_" + eventId + "_" + page : "add_" + eventId;
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(ADD_EVENT + " Добавить файл", CallbackPrefix.ATTACH_FILE.withPayload(addPayload))
            ));
        }
        
        String backPayload = page != null ? "back_" + eventId + "_" + page : "back_" + eventId;
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton(BACK + " Назад к событию", CallbackPrefix.ATTACH_FILE.withPayload(backPayload))
        ));
        
        return keyboardFactory.createMarkup(rows);
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления вложения с контекстом страницы.
     * 
     * @param eventId идентификатор события
     * @param attachmentId идентификатор вложения
     * @param page номер страницы для возврата к списку (может быть null)
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createDeleteAttachmentConfirmationKeyboard(Long eventId, Long attachmentId, Integer page) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        if (attachmentId == null || attachmentId <= 0) {
            throw new IllegalArgumentException("AttachmentId должен быть положительным числом");
        }
        
        String confirmPayload = page != null 
            ? "confirm_delete_" + eventId + "_" + attachmentId + "_" + page
            : "confirm_delete_" + eventId + "_" + attachmentId;
            
        String cancelPayload = page != null
            ? "cancel_delete_" + eventId + "_" + page
            : "cancel_delete_" + eventId;
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(SUCCESS + " Да, удалить", CallbackPrefix.ATTACH_FILE.withPayload(confirmPayload)),
                keyboardFactory.createButton(CANCELLED + " Отмена", CallbackPrefix.ATTACH_FILE.withPayload(cancelPayload))
            )
        );
    }

    /**
     * Создает inline клавиатуру для просмотра файла вложения с контекстом страницы.
     * 
     * @param eventId идентификатор события
     * @param page номер страницы для возврата к списку (может быть null)
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createFileViewKeyboard(Long eventId, Integer page) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        String backPayload = page != null ? "list_" + eventId + "_" + page : "list_" + eventId;
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(BACK + " Назад к вложениям", CallbackPrefix.ATTACH_FILE.withPayload(backPayload))
            )
        );
    }

    /**
     * Создает inline клавиатуру для режима загрузки вложения с контекстом страницы.
     * 
     * @param eventId идентификатор события
     * @param page номер страницы для возврата к списку (может быть null)
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentUploadKeyboard(Long eventId, Integer page) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        String cancelPayload = page != null ? "cancel_add_" + eventId + "_" + page : "cancel_add_" + eventId;
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(CANCELLED + " Отмена", CallbackPrefix.ATTACH_FILE.withPayload(cancelPayload))
            )
        );
    }
}
