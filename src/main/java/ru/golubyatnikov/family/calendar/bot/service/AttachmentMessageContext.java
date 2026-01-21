package ru.golubyatnikov.family.calendar.bot.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Контекст сообщения с вложениями.
 * 
 * <p>Хранит информацию о сообщении, которое используется для редактирования
 * при работе с вложениями события. Позволяет системе редактировать одно и то же
 * сообщение вместо создания новых сообщений при каждой операции.</p>
 * 
 * <p>Контекст имеет ограниченное время жизни (47 часов), после чего считается
 * истекшим из-за ограничений Telegram API на редактирование сообщений (48 часов).
 * Оставляем запас в 1 час для надежности.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentMessageContext {
    
    /**
     * Максимальное время жизни контекста в часах.
     * Telegram API позволяет редактировать сообщения в течение 48 часов,
     * оставляем запас в 1 час.
     */
    private static final long MAX_CONTEXT_AGE_HOURS = 47;
    
    /**
     * Идентификатор события, к которому относятся вложения
     */
    private Long eventId;
    
    /**
     * Идентификатор чата, в котором находится сообщение
     */
    private Long chatId;
    
    /**
     * Идентификатор сообщения для редактирования
     */
    private Integer messageId;
    
    /**
     * Время создания контекста.
     * Используется для проверки истечения срока действия.
     */
    private Instant createdAt;
    
    /**
     * Проверяет, не истек ли контекст.
     * 
     * <p>Контекст считается истекшим, если прошло более 47 часов с момента создания.
     * Это связано с ограничением Telegram API на редактирование сообщений (48 часов),
     * оставляем запас в 1 час для надежности.</p>
     * 
     * @return true, если контекст истек (старше 47 часов), false в противном случае
     */
    public boolean isExpired() {
        if (createdAt == null) {
            return true;
        }
        
        long hoursSinceCreation = Duration.between(createdAt, Instant.now()).toHours();
        return hoursSinceCreation > MAX_CONTEXT_AGE_HOURS;
    }
    
    /**
     * Проверяет валидность контекста.
     * 
     * <p>Контекст считается валидным, если все обязательные поля заполнены
     * и контекст не истек.</p>
     * 
     * @return true, если контекст валиден, false в противном случае
     */
    public boolean isValid() {
        return eventId != null 
            && chatId != null 
            && messageId != null 
            && createdAt != null 
            && !isExpired();
    }
}
