package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import java.util.List;
import java.util.stream.IntStream;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escapeMarkdownV2;

/**
 * Сервис для форматирования информации о вложениях.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@RequiredArgsConstructor
public class AttachmentFormattingService {
    
    private final DateTimeFormattingService dateTimeFormattingService;
    
    /**
     * Форматирует список вложений в текстовое сообщение.
     * 
     * @param attachments список вложений
     * @return отформатированное сообщение
     */
    public String formatAttachmentList(@NonNull List<Attachment> attachments) {
        StringBuilder message = new StringBuilder("📎 *Вложения события*\n\n");
        
        if (attachments.isEmpty()) {
            message.append("_У этого события пока нет вложений_");
            return message.toString();
        }

        IntStream.range(0, attachments.size()).forEach(i -> {
            if (i > 0) {
                message.append("\n━━━━━━━━━━━━━━━━━━━━\n\n");
            }
            message.append(formatAttachment(attachments.get(i)));
        });
        
        return message.toString();
    }
    
    /**
     * Форматирует информацию об одном вложении.
     * 
     * @param attachment вложение
     * @return отформатированная строка
     */
    private @NonNull String formatAttachment(@NonNull Attachment attachment) {
        String emoji = getFileTypeEmoji(attachment.getFileType());

        String fileName = attachment.getFileName() != null
                ? attachment.getFileName()
                : "Без названия";

        String formattedDate = dateTimeFormattingService.formatDateTime(attachment.getUploadedAt());
        
        return String.format("%s *%s*\n📊 Размер: %s\n📅 Загружено: %s",
                emoji,
                escapeMarkdownV2(fileName),
                escapeMarkdownV2(formatFileSize(attachment.getFileSize())),
                escapeMarkdownV2(formattedDate));
    }
    
    /**
     * Возвращает эмодзи для типа файла.
     * 
     * @param fileType тип файла
     * @return эмодзи
     */
    private @NonNull String getFileTypeEmoji(String fileType) {
        if (fileType == null) {
            return "📄";
        }
        
        return switch (fileType.toLowerCase()) {
            case "photo" -> "🖼️";
            case "video" -> "🎥";
            case "audio" -> "🎵";
            default -> "📄";
        };
    }
    
    /**
     * Форматирует размер файла в удобочитаемый формат.
     * 
     * @param fileSize размер файла в байтах
     * @return отформатированная строка
     */
    private @NonNull String formatFileSize(Long fileSize) {
        if (fileSize == null) {
            return "Неизвестно";
        }
        
        double sizeInKb = fileSize / 1024.0;
        if (sizeInKb < 1024) {
            return String.format("%.2f КБ", sizeInKb);
        }
        
        double sizeInMb = sizeInKb / 1024.0;
        return String.format("%.2f МБ", sizeInMb);
    }
    
    /**
     * Формирует инструкцию по загрузке файла.
     * 
     * @return отформатированная инструкция
     */
    public String formatUploadInstruction() {
        return """
                📎 *Отправьте файл для прикрепления к событию*
                
                _Максимальный размер: 20 МБ_
                
                Поддерживаемые типы файлов:
                📄 Документы
                🖼️ Фотографии
                🎥 Видео
                🎵 Аудио
                """;
    }
    
    /**
     * Формирует сообщение с запросом подтверждения удаления.
     * 
     * @param fileName имя файла
     * @return отформатированное сообщение
     */
    public String formatDeleteConfirmation(String fileName) {
        String displayName = fileName != null ? fileName : "Без названия";
        return String.format("""
                ⚠️ *Подтверждение удаления*
                
                Вы действительно хотите удалить вложение?
                
                📎 %s
                """, escapeMarkdownV2(displayName));
    }
}
