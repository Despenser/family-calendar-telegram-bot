package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import java.util.List;
import java.util.stream.IntStream;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.ATTACHMENT;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.DATE;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.FileTypes.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.CHART;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.SEPARATOR;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.WARNING;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escapeMarkdownV2;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.italic;

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
        StringBuilder message = new StringBuilder(bold(ATTACHMENT + " Вложения события") + "\n\n");
        
        if (attachments.isEmpty()) {
            message.append(italic("У этого события пока нет вложений"));
            return message.toString();
        }

        IntStream.range(0, attachments.size()).forEach(i -> {
            if (i > 0) {
                message.append("\n").append(SEPARATOR).append("\n\n");
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
        
        return String.format("%s %s\n%s Размер: %s\n%s Загружено: %s",
                emoji,
                bold(fileName),
                CHART,
                escapeMarkdownV2(formatFileSize(attachment.getFileSize())),
                DATE,
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
            return DOCUMENT;
        }
        
        return switch (fileType.toLowerCase()) {
            case "photo" -> PHOTO;
            case "video" -> VIDEO;
            case "audio" -> AUDIO;
            default -> DOCUMENT;
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
        return bold(ATTACHMENT + " Отправьте файл для прикрепления к событию") + "\n\n" +
                italic("Максимальный размер: 20 МБ") + "\n\n" +
                "Поддерживаемые типы файлов:\n" +
                DOCUMENT + " Документы\n" +
                PHOTO + " Фотографии\n" +
                VIDEO + " Видео\n" +
                AUDIO + " Аудио\n";
    }
    
    /**
     * Формирует сообщение с запросом подтверждения удаления.
     * 
     * @param fileName имя файла
     * @return отформатированное сообщение
     */
    public String formatDeleteConfirmation(String fileName) {
        String displayName = fileName != null ? fileName : "Без названия";
        return WARNING + " " + bold("Подтверждение удаления") + "\n\n" +
                "Вы действительно хотите удалить вложение?\n\n" +
                ATTACHMENT + " " + escapeMarkdownV2(displayName) + "\n";
    }
}
