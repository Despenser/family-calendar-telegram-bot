package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
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
            log.error("Попытка создать клавиатуру вложений с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры списка вложений для события ID={}, isCreator={}, attachmentsCount={}", 
                eventId, isCreator, attachments != null ? attachments.size() : 0);
        
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
                        keyboardFactory.createButton(buttonText, "attach_file_view_" + eventId + "_" + attachment.getId()),
                        keyboardFactory.createButton("🗑️", "attach_file_delete_" + eventId + "_" + attachment.getId())
                    ));
                } else {
                    rows.add(keyboardFactory.createRow(
                        keyboardFactory.createButton(buttonText, "attach_file_view_" + eventId + "_" + attachment.getId())
                    ));
                }
            }
        }
        
        if (isCreator) {
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton("➕ Добавить файл", "attach_file_add_" + eventId)
            ));
        }
        
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад к событию", "attach_file_back_" + eventId)
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
            log.error("Попытка создать клавиатуру подтверждения удаления вложения с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        if (attachmentId == null || attachmentId <= 0) {
            log.error("Попытка создать клавиатуру подтверждения удаления вложения с некорректным attachmentId: {}", attachmentId);
            throw new IllegalArgumentException("AttachmentId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры подтверждения удаления вложения ID={} для события ID={}", 
                attachmentId, eventId);
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("✅ Да, удалить", "attach_file_confirm_delete_" + eventId + "_" + attachmentId),
                keyboardFactory.createButton("❌ Отмена", "attach_file_cancel_delete_" + eventId)
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
            log.error("Попытка создать клавиатуру просмотра файла с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для просмотра файла события ID={}", eventId);
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("🔙 Назад к вложениям", "attach_file_list_" + eventId)
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
            log.error("Попытка создать клавиатуру с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для загрузки вложения к событию ID={}", eventId);
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("❌ Отмена", "attach_file_cancel_add_" + eventId)
            )
        );
    }
}
