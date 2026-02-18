package ru.golubyatnikov.family.calendar.bot.service.domain.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.config.FileConfig;
import ru.golubyatnikov.family.calendar.bot.exception.AttachmentNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.FileSizeExceededException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.repository.AttachmentRepository;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import java.util.List;

/**
 * Сервис для управления вложениями событий.
 * Предоставляет функциональность для сохранения, получения и удаления файлов,
 * прикрепленных к событиям (билеты, документы, изображения).
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AttachmentService {
    
    private final AttachmentRepository attachmentRepository;
    private final EventRepository eventRepository;
    private final FileConfig fileConfig;
    
    /**
     * Сохраняет вложение к событию с проверкой размера файла.
     *
     * @param eventId идентификатор события
     * @param fileId Telegram file_id для получения файла через Bot API
     * @param fileName оригинальное имя файла
     * @param fileType тип файла (document, photo, video, audio)
     * @param fileSize размер файла в байтах
     *
     * @return сохраненное вложение
     * @throws EventNotFoundException если событие не найдено
     * @throws FileSizeExceededException если размер файла превышает максимально допустимый размер
     */
    public Attachment saveAttachment(Long eventId,
                                     String fileId,
                                     String fileName,
                                     String fileType,
                                     Long fileSize) {

        Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));

        if (fileSize != null && fileSize > fileConfig.getMaxSizeBytes()) {
            log.warn("Попытка загрузить файл размером {} байт, что превышает лимит {} байт",
                    fileSize, fileConfig.getMaxSizeBytes());

            throw new FileSizeExceededException(
                String.format("Размер файла %.2f МБ превышает максимально допустимый размер",
                        fileSize / (1024.0 * 1024.0))
            );
        }
        
        Attachment attachment = Attachment.builder()
            .event(event)
            .fileId(fileId)
            .fileName(fileName)
            .fileType(fileType)
            .fileSize(fileSize)
            .build();

        return attachmentRepository.save(attachment);
    }
    
    /**
     * Получает вложение по идентификатору.
     * 
     * @param attachmentId идентификатор вложения
     *
     * @return вложение
     * @throws AttachmentNotFoundException если вложение не найдено
     */
    @Transactional(readOnly = true)
    public Attachment getAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
    }
    
    /**
     * Получает все вложения события, отсортированные по дате загрузки.
     * 
     * @param eventId идентификатор события
     * @return список вложений события, отсортированный по дате загрузки (от старых к новым)
     */
    @Transactional(readOnly = true)
    public List<Attachment> getEventAttachments(Long eventId) {
        return attachmentRepository.findByEventIdOrderByUploadedAtAsc(eventId);
    }
    
    /**
     * Подсчитывает количество вложений у события.
     * 
     * @param eventId идентификатор события
     * @return количество вложений у события
     */
    @Transactional(readOnly = true)
    public long countEventAttachments(Long eventId) {
        return attachmentRepository.countByEventId(eventId);
    }
    
    /**
     * Удаляет вложение с проверкой прав доступа.
     * 
     * @param attachmentId идентификатор вложения
     * @param userId идентификатор пользователя, пытающегося удалить вложение
     *
     * @throws AttachmentNotFoundException если вложение не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     */
    @Transactional
    public void deleteAttachment(Long attachmentId, Long userId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        
        // Проверка прав доступа - только создатель события может удалять вложения
        if (!attachment.getEvent().getUser().getId().equals(userId)) {
            log.warn("Пользователь ID {} попытался удалить вложение ID {}, принадлежащее пользователю ID {}", 
                     userId, attachmentId, attachment.getEvent().getUser().getId());

            throw new UnauthorizedAccessException(
                    "Вы не можете удалить это вложение, так как не являетесь создателем события"
            );
        }
        
        attachmentRepository.delete(attachment);
        }
}
