package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;

/**
 * Сервис для работы с сообщениями событий в корзине.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrashMessageService {
    
    private final TelegramMessageService messageService;
    
    /**
     * Удаляет сообщение события из Telegram.
     * 
     * @param event событие, сообщение которого нужно удалить
     */
    public void deleteEventMessage(@NonNull Event event) {
        if (event.getMessageId() == null) {
            return;
        }
        
        try {
            Long chatId = event.getUser().getTelegramId();
            messageService.deleteMessageSilently(chatId, event.getMessageId().intValue());

            } catch (Exception e) {
            log.warn("Не удалось удалить сообщение события ID={}, messageId={}: {}. Продолжаем операцию.", 
                    event.getId(), event.getMessageId(), e.getMessage());
        }
    }
}
