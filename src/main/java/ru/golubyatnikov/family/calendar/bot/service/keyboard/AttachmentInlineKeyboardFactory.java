package ru.golubyatnikov.family.calendar.bot.service.keyboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;

import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания inline клавиатур, связанных с вложениями.
 * 
 * <p>Отвечает за создание клавиатур для управления вложениями событий,
 * просмотра, загрузки и удаления файлов.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.3</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@Slf4j
public class AttachmentInlineKeyboardFactory {

    /**
     * Создает inline клавиатуру для прикрепления файла к событию.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createAttachmentKeyboard(Long eventId) {
        log.debug("Создание inline клавиатуры для вложений события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton attachBtn = new InlineKeyboardButton("📎 Прикрепить файл");
        attachBtn.setCallbackData("attach_file_" + eventId);
        row1.add(attachBtn);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("attach_cancel_" + eventId);
        row2.add(cancelBtn);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для списка вложений события.
     * 
     * @param eventId идентификатор события
     * @param attachments список вложений
     * @param isCreator является ли пользователь создателем
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
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        if (attachments != null && !attachments.isEmpty()) {
            for (Attachment attachment : attachments) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                
                String emoji = switch (attachment.getFileType()) {
                    case "photo" -> "🖼️";
                    case "video" -> "🎥";
                    case "audio" -> "🎵";
                    default -> "📄";
                };
                
                String buttonText = emoji + " " + (attachment.getFileName() != null ? attachment.getFileName() : "Файл");
                InlineKeyboardButton viewBtn = new InlineKeyboardButton(buttonText);
                viewBtn.setCallbackData("attach_file_view_" + eventId + "_" + attachment.getId());
                row.add(viewBtn);
                
                if (isCreator) {
                    InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️");
                    deleteBtn.setCallbackData("attach_file_delete_" + eventId + "_" + attachment.getId());
                    row.add(deleteBtn);
                }
                
                rows.add(row);
            }
        }
        
        if (isCreator) {
            List<InlineKeyboardButton> addRow = new ArrayList<>();
            InlineKeyboardButton addBtn = new InlineKeyboardButton("➕ Добавить файл");
            addBtn.setCallbackData("attach_file_add_" + eventId);
            addRow.add(addBtn);
            rows.add(addRow);
        }
        
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton("🔙 Назад к событию");
        backBtn.setCallbackData("attach_file_back_" + eventId);
        backRow.add(backBtn);
        rows.add(backRow);
        
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления вложения.
     * 
     * @param eventId идентификатор события
     * @param attachmentId идентификатор вложения
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
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton("✅ Да, удалить");
        confirmBtn.setCallbackData("attach_file_confirm_delete_" + eventId + "_" + attachmentId);
        row1.add(confirmBtn);
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("attach_file_cancel_delete_" + eventId);
        row1.add(cancelBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для просмотра файла вложения.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createFileViewKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            log.error("Попытка создать клавиатуру просмотра файла с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для просмотра файла события ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton("⬅️ Назад к вложениям");
        backBtn.setCallbackData("attach_file_list_" + eventId);
        row1.add(backBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для режима загрузки вложения.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentUploadKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            log.error("Попытка создать клавиатуру с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для загрузки вложения к событию ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("attach_file_cancel_add_" + eventId);
        row.add(cancelBtn);
        
        rows.add(row);
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для добавления чек-листа к событию.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createChecklistKeyboard(Long eventId) {
        log.debug("Создание inline клавиатуры для чек-листа события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton addBtn = new InlineKeyboardButton("✅ Добавить чек-лист");
        addBtn.setCallbackData("checklist_add_" + eventId);
        row1.add(addBtn);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("checklist_cancel_" + eventId);
        row2.add(cancelBtn);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }
}
