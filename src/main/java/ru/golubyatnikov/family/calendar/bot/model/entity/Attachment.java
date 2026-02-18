package ru.golubyatnikov.family.calendar.bot.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity класс для вложений событий.
 * Хранит информацию о файлах, прикрепленных к событиям (билеты, документы, изображения).
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Entity
@Table(name = "attachments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {
    
    /**
     * Уникальный идентификатор вложения
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Событие, к которому прикреплен файл
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    /**
     * Telegram file_id для получения файла через Bot API
     */
    @Column(name = "file_id", nullable = false)
    private String fileId;
    
    /**
     * Оригинальное имя файла
     */
    @Column(name = "file_name")
    private String fileName;
    
    /**
     * Тип файла: document, photo, video, audio
     */
    @Column(name = "file_type")
    private String fileType;
    
    /**
     * Размер файла в байтах
     */
    @Column(name = "file_size")
    private Long fileSize;
    
    /**
     * Дата и время загрузки файла
     */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    
    /**
     * Автоматически устанавливает дату загрузки при создании записи
     */
    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
