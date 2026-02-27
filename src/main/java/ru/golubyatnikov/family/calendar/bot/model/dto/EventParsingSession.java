package ru.golubyatnikov.family.calendar.bot.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventParsingState;
import java.time.LocalDateTime;

/**
 * Сессия парсинга события для отслеживания диалога с пользователем.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-27
 */
@Getter
@Setter
@Builder
public class EventParsingSession {
    
    /**
     * ID пользователя
     */
    private Long userId;
    
    /**
     * Текущее состояние парсинга
     */
    @Builder.Default
    private EventParsingState state = EventParsingState.INITIAL;
    
    /**
     * Полностью распознанное событие (готово к созданию)
     */
    private ParsedEvent parsedEvent;
    
    /**
     * Время создания сессии
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Время последнего обновления сессии
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    /**
     * Обновляет состояние сессии.
     */
    public void updateState(EventParsingState newState) {
        this.state = newState;
        this.updatedAt = LocalDateTime.now();
    }
}
