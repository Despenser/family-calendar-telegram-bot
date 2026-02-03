package ru.golubyatnikov.family.calendar.bot.service.planner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;

import java.util.List;

/**
 * Сервис для навигации по событиям планировщика.
 * 
 * <p>Отвечает за управление флагами событий, отправку сообщений
 * и обновление счетчиков в шапке списка событий.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerNavigationService {

    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;

    /**
     * Управляет флагами isMyEventsHeader для событий.
     * Устанавливает флаг для первого события и сбрасывает для остальных.
     * 
     * @param userEvents список событий пользователя
     */
    public void manageHeaderFlags(List<Event> userEvents) {
        if (userEvents.isEmpty()) {
            return;
        }

        // Устанавливаем флаг для первого события
        Event firstEvent = userEvents.get(0);
        if (!Boolean.TRUE.equals(firstEvent.getIsMyEventsHeader())) {
            log.debug("Установка флага isMyEventsHeader=true для первого события ID={}", firstEvent.getId());
            firstEvent.setIsMyEventsHeader(true);
            eventService.saveEvent(firstEvent);
        }
        
        // Сбрасываем флаг для остальных событий
        for (int i = 1; i < userEvents.size(); i++) {
            Event event = userEvents.get(i);
            if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
                log.debug("Сброс флага isMyEventsHeader=false для события ID={}", event.getId());
                event.setIsMyEventsHeader(false);
                eventService.saveEvent(event);
            }
        }
    }

    /**
     * Отправляет объединенное сообщение (заголовок + первое событие).
     * 
     * @param chatId идентификатор чата
     * @param combinedMessage объединенное сообщение
     * @param firstEvent первое событие
     * @param userId идентификатор пользователя
     * @return true если отправка успешна
     */
    public boolean sendCombinedMessage(Long chatId, String combinedMessage, Event firstEvent, Long userId) {
        try {
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(firstEvent, userId);
            
            if (keyboard == null || keyboard.getKeyboard() == null || keyboard.getKeyboard().isEmpty()) {
                log.warn("Клавиатура для первого события ID={} некорректна", firstEvent.getId());
                return false;
            }
            
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                messageService.sendMessageAndGet(chatId, combinedMessage, keyboard);
            
            // Сохраняем messageId
            firstEvent.setMessageId((long) sentMessage.getMessageId());
            eventService.saveEvent(firstEvent);
            
            // Сохраняем контекст шапки
            int eventCount = 1; // Будет обновлено вызывающим кодом
            conversationStateService.saveEventHeaderContext(userId, true, eventCount);
            
            log.debug("Объединенное сообщение с первым событием ID={} успешно отправлено, messageId={}", 
                    firstEvent.getId(), sentMessage.getMessageId());
            
            return true;
        } catch (Exception e) {
            log.error("Ошибка при отправке объединенного сообщения: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Отправляет событие отдельным сообщением.
     * 
     * @param chatId идентификатор чата
     * @param eventText текст события
     * @param event событие
     * @param userId идентификатор пользователя
     * @return true если отправка успешна
     */
    public boolean sendEventMessage(Long chatId, String eventText, Event event, Long userId) {
        try {
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
            
            if (keyboard == null || keyboard.getKeyboard() == null || keyboard.getKeyboard().isEmpty()) {
                log.warn("Клавиатура для события ID={} некорректна", event.getId());
                return false;
            }
            
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                messageService.sendMessageAndGet(chatId, eventText, keyboard);
            
            // Сохраняем messageId
            event.setMessageId((long) sentMessage.getMessageId());
            eventService.saveEvent(event);
            
            log.debug("Событие ID={} успешно отправлено, messageId={}", 
                    event.getId(), sentMessage.getMessageId());
            
            return true;
        } catch (Exception e) {
            log.error("Ошибка при отправке события ID={}: {}", event.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Отправляет событие без форматирования (fallback).
     * 
     * @param chatId идентификатор чата
     * @param plainText простой текст события
     * @param event событие
     * @param userId идентификатор пользователя
     * @return true если отправка успешна
     */
    public boolean sendPlainEventMessage(Long chatId, String plainText, Event event, Long userId) {
        try {
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
            
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = 
                messageService.sendMessageWithoutFormattingAndGet(chatId, plainText, keyboard);
            
            // Сохраняем messageId
            event.setMessageId((long) sentMessage.getMessageId());
            eventService.saveEvent(event);
            
            log.debug("Событие ID={} успешно отправлено без форматирования, messageId={}", 
                    event.getId(), sentMessage.getMessageId());
            
            return true;
        } catch (Exception e) {
            log.error("Ошибка при отправке события без форматирования ID={}: {}", event.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Обновляет счетчик событий в шапке первого сообщения.
     * 
     * @param userId идентификатор пользователя
     * @param userEvents список событий пользователя
     * @param header новый заголовок
     * @param eventText текст первого события
     */
    public void updateHeaderCount(Long userId, List<Event> userEvents, String header, String eventText) {
        if (userId == null || userEvents.isEmpty()) {
            return;
        }
        
        log.debug("Обновление счетчика событий в шапке для пользователя ID={}", userId);
        
        try {
            Event headerEvent = userEvents.stream()
                    .filter(e -> Boolean.TRUE.equals(e.getIsMyEventsHeader()))
                    .findFirst()
                    .orElse(null);
            
            if (headerEvent == null || headerEvent.getMessageId() == null) {
                log.warn("Событие с шапкой не найдено или отсутствует messageId для пользователя ID={}", userId);
                return;
            }
            
            String combinedMessage = header + "\n" + eventText;
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(headerEvent, userId);
            Long chatId = headerEvent.getUser().getTelegramId();
            
            boolean updated = messageService.tryEditMessageText(
                    chatId, 
                    headerEvent.getMessageId().intValue(), 
                    combinedMessage, 
                    keyboard);
            
            if (updated) {
                log.info("Счетчик событий в шапке успешно обновлен для пользователя ID={}", userId);
            } else {
                log.warn("Не удалось обновить счетчик событий в шапке для пользователя ID={}", userId);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при обновлении счетчика событий в шапке для пользователя ID={}: {}", 
                    userId, e.getMessage(), e);
        }
    }

    /**
     * Сохраняет контекст шапки для пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param eventCount количество событий
     */
    public void saveHeaderContext(Long userId, int eventCount) {
        conversationStateService.saveEventHeaderContext(userId, true, eventCount);
        log.debug("Контекст шапки сохранен для пользователя ID={}: eventCount={}", userId, eventCount);
    }
}
