package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания inline клавиатур, связанных с вложениями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@Slf4j
public class AttachmentInlineKeyboardFactory {

    private final KeyboardFactory keyboardFactory;

    public AttachmentInlineKeyboardFactory(KeyboardFactory keyboardFactory) {
        this.keyboardFactory = keyboardFactory;
    }

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
    public InlineKeyboardMarkup createAttachmentsListKeyboard(Long eventId, List<Attachment> attachments, boolean isCreator) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        
        if (attachments != null && !attachments.isEmpty()) {
            for (Attachment attachment : attachments) {
                String emoji = switch (attachment.getFileType()) {
                    case "photo" -> "🖼️";
                    case "video" -> "🎥";
                    case "audio" -> "🎵";
                    default -> "📄";
                };
                
                String buttonText = emoji + " " + (attachment.getFileName() != null ? attachment.getFileName() : "Файл");
                
                if (isCreator) {
                    rows.add(keyboardFactory.createRow(
                        keyboardFactory.createButton(buttonText, CallbackPrefix.ATTACH_FILE.withPayload("view_" + eventId + "_" + attachment.getId())),
                        keyboardFactory.createButton("🗑️", CallbackPrefix.ATTACH_FILE.withPayload("delete_" + eventId + "_" + attachment.getId()))
                    ));
                } else {
                    rows.add(keyboardFactory.createRow(
                        keyboardFactory.createButton(buttonText, CallbackPrefix.ATTACH_FILE.withPayload("view_" + eventId + "_" + attachment.getId()))
                    ));
                }
            }
        }
        
        if (isCreator) {
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton("➕ Добавить файл", CallbackPrefix.ATTACH_FILE.withPayload("add_" + eventId))
            ));
        }
        
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад к событию", CallbackPrefix.ATTACH_FILE.withPayload("back_" + eventId))
        ));
        
        return keyboardFactory.createMarkup(rows);
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления вложения.
     * 
     * @param eventId идентификатор события
     * @param attachmentId идентификатор вложения
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createDeleteAttachmentConfirmationKeyboard(Long eventId, Long attachmentId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        if (attachmentId == null || attachmentId <= 0) {
            throw new IllegalArgumentException("AttachmentId должен быть положительным числом");
        }
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("✅ Да, удалить", CallbackPrefix.ATTACH_FILE.withPayload("confirm_delete_" + eventId + "_" + attachmentId)),
                keyboardFactory.createButton("❌ Отмена", CallbackPrefix.ATTACH_FILE.withPayload("cancel_delete_" + eventId))
            )
        );
    }

    /**
     * Создает inline клавиатуру для просмотра файла вложения.
     * 
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createFileViewKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("🔙 Назад к вложениям", CallbackPrefix.ATTACH_FILE.withPayload("list_" + eventId))
            )
        );
    }

    /**
     * Создает inline клавиатуру для режима загрузки вложения.
     * 
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentUploadKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("❌ Отмена", CallbackPrefix.ATTACH_FILE.withPayload("cancel_add_" + eventId))
            )
        );
    }
}
