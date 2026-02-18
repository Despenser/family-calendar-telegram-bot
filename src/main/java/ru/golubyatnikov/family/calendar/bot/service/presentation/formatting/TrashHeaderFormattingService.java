package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.planner.MyEventsHeaderUpdater;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для управления шапками корзины и "Мои события".
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrashHeaderFormattingService {
    
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TelegramMessageService messageService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final KeyboardService keyboardService;
    private final MyEventsHeaderUpdater myEventsHeaderUpdater;
    
    private static final int TRASH_RETENTION_DAYS = 30;
    
    /**
     * Обновляет шапку корзины после удаления или восстановления события.
     * 
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void updateTrashHeaderAfterRemoval(Long userId) {
        if (userId == null) {
            log.error("Попытка обновить шапку корзины с userId=null");
            throw new IllegalArgumentException("ID пользователя не может быть null");
        }
        
        log.debug("Обновление шапки корзины для пользователя ID={}", userId);
        
        List<Event> trashedEvents = getTrashedEvents(userId);
        Long chatId = getChatId(userId);
        
        if (chatId == null) {
            return;
        }
        
        if (trashedEvents.isEmpty()) {
            sendEmptyTrashMessage(chatId, userId);
            return;
        }
        
        updateTrashHeaderFlags(trashedEvents);
        updateTrashHeaderCount(userId);
        
        log.debug("Шапка корзины обновлена для пользователя ID={}", userId);
    }
    
    /**
     * Обновляет счетчик событий в шапке корзины.
     * 
     * @param userId идентификатор пользователя
     */
    public void updateTrashHeaderCount(Long userId) {
        if (userId == null) {
            log.error("Попытка обновить счетчик корзины с userId=null");
            throw new IllegalArgumentException("ID пользователя не может быть null");
        }
        
        log.debug("Обновление счетчика в шапке корзины для пользователя ID={}", userId);
        
        List<Event> trashedEvents = getTrashedEvents(userId);
        
        if (trashedEvents.isEmpty()) {
            log.debug("Корзина пуста, обновление счетчика не требуется для пользователя ID={}", userId);
            return;
        }
        
        Event headerEvent = findHeaderEvent(trashedEvents);
        
        if (headerEvent == null || headerEvent.getMessageId() == null) {
            return;
        }
        
        updateHeaderMessage(headerEvent, trashedEvents.size());
    }
    
    /**
     * Получает список удаленных событий пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return список удаленных событий
     */
    private List<Event> getTrashedEvents(Long userId) {
        return eventRepository.findByUserIdAndStatusOrderByDeletedAtDesc(
            userId, 
            EventStatus.DELETED
        );
    }
    
    /**
     * Обновляет счетчик событий в шапке "Мои события".
     * 
     * @param userId идентификатор пользователя
     */
    public void updateMyEventsHeaderCount(Long userId) {
        log.debug("Обновление счетчика 'Мои события' для пользователя ID={}", userId);
        myEventsHeaderUpdater.updateMyEventsHeaderCount(userId);
    }
    
    /**
     * Получает chatId пользователя.
     */
    private Long getChatId(Long userId) {
        Long chatId = userRepository.findById(userId)
            .map(User::getTelegramId)
            .orElse(null);
        
        if (chatId == null) {
            log.warn("Не удалось получить chatId для пользователя ID={}", userId);
        }
        
        return chatId;
    }
    
    /**
     * Отправляет сообщение о пустой корзине.
     */
    private void sendEmptyTrashMessage(Long chatId, Long userId) {
        String emptyMessage = buildEmptyTrashMessage();
        
        try {
            messageService.sendMessage(chatId, emptyMessage);
            log.info("Отправлено сообщение о пустой корзине для пользователя ID={}", userId);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения о пустой корзине для пользователя ID={}: {}", 
                     userId, e.getMessage(), e);
        }
    }
    
    /**
     * Обновляет флаги isTrashHeader для событий.
     */
    private void updateTrashHeaderFlags(@NonNull List<Event> trashedEvents) {
        Event newFirstEvent = trashedEvents.getFirst();
        
        for (Event event : trashedEvents) {
            boolean shouldBeHeader = event.getId().equals(newFirstEvent.getId());
            boolean isCurrentlyHeader = Boolean.TRUE.equals(event.getIsTrashHeader());
            
            if (shouldBeHeader != isCurrentlyHeader) {
                event.setIsTrashHeader(shouldBeHeader);
                eventRepository.save(event);
                log.debug("Флаг isTrashHeader {} для события ID={}", 
                         shouldBeHeader ? "установлен" : "сброшен", event.getId());
            }
        }
    }
    
    /**
     * Находит событие с шапкой.
     */
    private Event findHeaderEvent(@NonNull List<Event> trashedEvents) {
        Event headerEvent = trashedEvents.stream()
            .filter(e -> Boolean.TRUE.equals(e.getIsTrashHeader()))
            .findFirst()
            .orElse(null);
        
        if (headerEvent == null) {
            log.warn("Не найдено событие с флагом isTrashHeader");
        }
        
        return headerEvent;
    }
    
    /**
     * Обновляет сообщение шапки.
     */
    private void updateHeaderMessage(@NonNull Event headerEvent, int totalCount) {
        if (headerEvent.getMessageId() == null) {
            log.warn("У события с шапкой ID={} отсутствует messageId", headerEvent.getId());
            return;
        }
        
        String header = botMessageFormattingService.buildTrashHeader(totalCount);
        String eventText = botMessageFormattingService.buildEventMessage(headerEvent);
        String combinedMessage = header + "\n" + eventText;
        
        InlineKeyboardMarkup keyboard = keyboardService.createTrashActionsKeyboard(headerEvent.getId());
        Long chatId = headerEvent.getUser().getTelegramId();
        
        try {
            boolean updated = messageService.tryEditMessageText(
                chatId,
                headerEvent.getMessageId().intValue(),
                combinedMessage,
                keyboard
            );
            
            if (updated) {
                log.info("Счетчик в шапке корзины обновлен для события ID={}", headerEvent.getId());
            } else {
                log.warn("Не удалось обновить счетчик в шапке корзины для события ID={}", headerEvent.getId());
            }
        } catch (Exception e) {
            log.error("Ошибка при обновлении счетчика в шапке корзины: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Формирует сообщение о пустой корзине.
     */
    private @NonNull String buildEmptyTrashMessage() {
        return "🗑️ " + bold("Корзина") + "\n\n" +
                escape("Корзина пуста.\n\n") +
                italic("Удаленные события хранятся здесь " +
                    TRASH_RETENTION_DAYS + " дней, после чего автоматически удаляются навсегда.");
    }
}
