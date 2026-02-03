package ru.golubyatnikov.family.calendar.bot.service.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.AttachmentNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.FileSizeExceededException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.repository.AttachmentRepository;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.util.List;

/**
 * Сервис для управления вложениями событий.
 * Предоставляет функциональность для сохранения, получения и удаления файлов,
 * прикрепленных к событиям (билеты, документы, изображения).
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Сохранение вложений с проверкой размера файла (макс 20 МБ)</li>
 *   <li>Получение списка вложений события</li>
 *   <li>Удаление вложений с проверкой прав доступа</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 20.2, 20.3, 20.4, 20.6</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see Attachment
 * @see AttachmentRepository
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AttachmentService {
    
    /**
     * Максимальный размер файла в байтах (20 МБ)
     */
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;
    
    private final AttachmentRepository attachmentRepository;
    private final EventRepository eventRepository;
    
    /**
     * Сохраняет вложение к событию с проверкой размера файла.
     * 
     * <p>Проверяет, что размер файла не превышает 20 МБ.
     * Сохраняет file_id от Telegram, имя файла, тип и размер.</p>
     * 
     * @param eventId идентификатор события
     * @param fileId Telegram file_id для получения файла через Bot API
     * @param fileName оригинальное имя файла
     * @param fileType тип файла (document, photo, video, audio)
     * @param fileSize размер файла в байтах
     * @return сохраненное вложение
     * @throws EventNotFoundException если событие не найдено
     * @throws FileSizeExceededException если размер файла превышает 20 МБ
     */
    public Attachment saveAttachment(Long eventId, String fileId, 
                                    String fileName, String fileType, Long fileSize) {
        log.debug("Сохранение вложения для события ID {}: fileName={}, fileType={}, fileSize={}", 
                  eventId, fileName, fileType, fileSize);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        // Проверка размера файла (макс 20 МБ)
        if (fileSize != null && fileSize > MAX_FILE_SIZE) {
            log.warn("Попытка загрузить файл размером {} байт, что превышает лимит {} байт", 
                     fileSize, MAX_FILE_SIZE);
            throw new FileSizeExceededException(
                String.format("Размер файла %.2f МБ превышает максимально допустимый размер 20 МБ", 
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
        
        Attachment saved = attachmentRepository.save(attachment);
        log.info("Вложение ID {} успешно сохранено для события ID {}", saved.getId(), eventId);
        
        return saved;
    }
    
    /**
     * Получает вложение по идентификатору.
     * 
     * @param attachmentId идентификатор вложения
     * @return вложение
     * @throws AttachmentNotFoundException если вложение не найдено
     */
    @Transactional(readOnly = true)
    public Attachment getAttachment(Long attachmentId) {
        log.debug("Получение вложения ID {}", attachmentId);
        
        return attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> {
                log.warn("Вложение ID {} не найдено", attachmentId);
                return new AttachmentNotFoundException(attachmentId);
            });
    }
    
    /**
     * Получает все вложения события, отсортированные по дате загрузки.
     * 
     * @param eventId идентификатор события
     * @return список вложений события, отсортированный по дате загрузки (от старых к новым)
     */
    @Transactional(readOnly = true)
    public List<Attachment> getEventAttachments(Long eventId) {
        log.debug("Получение вложений для события ID {}", eventId);
        
        List<Attachment> attachments = attachmentRepository.findByEventIdOrderByUploadedAtAsc(eventId);
        
        log.debug("Найдено {} вложений для события ID {}", attachments.size(), eventId);
        return attachments;
    }
    
    /**
     * Подсчитывает количество вложений у события.
     * 
     * <p>Этот метод выполняет COUNT запрос к базе данных без загрузки самих вложений,
     * что эффективно для случаев, когда нужно только количество (например, для отображения
     * счетчика в UI).</p>
     * 
     * @param eventId идентификатор события
     * @return количество вложений у события
     */
    @Transactional(readOnly = true)
    public long countEventAttachments(Long eventId) {
        log.debug("Подсчет вложений для события ID {}", eventId);
        
        long count = attachmentRepository.countByEventId(eventId);
        
        log.debug("Событие ID {} имеет {} вложений", eventId, count);
        return count;
    }
    
    /**
     * Удаляет вложение с проверкой прав доступа.
     * 
     * <p>Только создатель события может удалять вложения.</p>
     * 
     * @param attachmentId идентификатор вложения
     * @param userId идентификатор пользователя, пытающегося удалить вложение
     * @throws AttachmentNotFoundException если вложение не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     */
    @Transactional
    public void deleteAttachment(Long attachmentId, Long userId) {
        log.debug("Удаление вложения ID {} пользователем ID {}", attachmentId, userId);
        
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
        log.info("Вложение ID {} успешно удалено пользователем ID {}", attachmentId, userId);
    }
}
