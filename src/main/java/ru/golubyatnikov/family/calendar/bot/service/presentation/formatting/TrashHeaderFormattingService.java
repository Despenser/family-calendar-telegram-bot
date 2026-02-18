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
            throw new IllegalArgumentException("ID пользователя не может быть null");
        }
        
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
        
        }
    
    /**
     * Обновляет счетчик событий в шапке корзины.
     * 
     * @param userId идентификатор пользователя
     */
    public void updateTrashHeaderCount(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("ID пользователя не может быть null");
        }
        
        List<Event> trashedEvents = getTrashedEvents(userId);
        
        if (trashedEvents.isEmpty()) {
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
        myEventsHeaderUpdater.updateMyEventsHeaderCount(userId);
    }
    
    /**
     * Получает chatId пользователя.
     */
    private Long getChatId(Long userId) {
        return userRepository.findById(userId)
            .map(User::getTelegramId)
            .orElse(null);
    }
    
    /**
     * Отправляет сообщение о пустой корзине.
     */
    private void sendEmptyTrashMessage(Long chatId, Long userId) {
        String emptyMessage = buildEmptyTrashMessage();
        
        try {
            messageService.sendMessage(chatId, emptyMessage);

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
            }
        }
    }
    
    /**
     * Находит событие с шапкой.
     */
    private Event findHeaderEvent(@NonNull List<Event> trashedEvents) {
        return trashedEvents.stream()
            .filter(e -> Boolean.TRUE.equals(e.getIsTrashHeader()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Обновляет сообщение шапки.
     */
    private void updateHeaderMessage(@NonNull Event headerEvent, int totalCount) {
        if (headerEvent.getMessageId() == null) {
            return;
        }
        
        String header = botMessageFormattingService.buildTrashHeader(totalCount);
        String eventText = botMessageFormattingService.buildEventMessage(headerEvent);
        String combinedMessage = header + "\n" + eventText;
        
        InlineKeyboardMarkup keyboard = keyboardService.createTrashActionsKeyboard(headerEvent.getId());
        Long chatId = headerEvent.getUser().getTelegramId();
        
        try {
            messageService.tryEditMessageText(
                chatId,
                headerEvent.getMessageId().intValue(),
                combinedMessage,
                keyboard);

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
