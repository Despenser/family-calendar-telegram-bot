package ru.golubyatnikov.family.calendar.bot.service.event;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.model.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventCommandService.EventCreatedEvent;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CorrelationIdUtil;

import java.util.List;

/**
 * Сервис для отправки уведомлений о событиях.
 * 
 * <p>Предоставляет методы для отправки уведомлений пользователям о событиях
 * и обрабатывает доменные события для автоматической отправки уведомлений.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-01
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class EventNotificationService {
    
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TelegramMessageService telegramMessageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder botMessageBuilder;
    private final ReminderService reminderService;
    
    /**
     * Обрабатывает создание события и автоматически создает напоминания по умолчанию.
     * 
     * @param event созданное событие
     * @param user пользователь, создавший событие
     */
    @Transactional
    public void handleEventCreated(Event event, User user) {
        log.debug("Обработка создания события ID={} пользователем ID={}", event.getId(), user.getId());
        
        List<Reminder> createdReminders = reminderService.createDefaultReminders(event, user);
        
        if (event.getEventTime() == null) {
            log.debug("Событие ID={} без времени, напоминания не созданы", event.getId());
        } else if (createdReminders.isEmpty()) {
            log.debug("Все напоминания пропущены для события ID={} (событие слишком скоро)", event.getId());
        } else {
            log.info("Автоматически созданы {} напоминаний для события ID={}", 
                    createdReminders.size(), event.getId());
        }
    }
    
    /**
     * Отправляет или обновляет сообщение о событии в Telegram.
     * 
     * @param event событие для отправки/обновления
     * @param chatId ID чата для отправки
     * @return обновленное событие с актуальным messageId
     * @throws TelegramApiException при критических ошибках отправки
     */
    @Transactional
    public Event sendOrUpdateEventMessage(
            @NotNull(message = "event не может быть null") Event event,
            @NotNull(message = "chatId не может быть null") Long chatId) throws TelegramApiException {
        
        if (event == null) {
            log.error("Попытка отправить/обновить сообщение для null события");
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        if (chatId == null) {
            log.error("Попытка отправить/обновить сообщение с null chatId для события ID={}", 
                    event.getId());
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        log.debug("Отправка/обновление сообщения о событии: eventId={}, chatId={}, messageId={}, isMyEventsHeader={}", 
                event.getId(), chatId, event.getMessageId(), event.getIsMyEventsHeader());
        
        String messageText = botMessageBuilder.buildEventMessage(event);
        log.debug("Текст сообщения сформирован: eventId={}, textLength={}", 
                event.getId(), messageText.length());
        
        if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
            int eventCount = eventRepository.countByUserIdAndStatus(event.getUser().getId(), EventStatus.ACTIVE);
            String header = botMessageBuilder.buildMyEventsHeader(eventCount);
            messageText = header + "\n" + messageText;
            log.debug("Добавлена шапка 'Мои события' к сообщению: eventId={}, eventCount={}", 
                    event.getId(), eventCount);
        }
        
        InlineKeyboardMarkup keyboard;
        if (event.getStatus() == EventStatus.DRAFT) {
            keyboard = keyboardService.createEditFieldSelectionKeyboard(event.getId());
            log.debug("Клавиатура для черновика создана для события ID={}", event.getId());
        } else {
            Long userId = event.getUser().getId();
            keyboard = keyboardService.createEventActionsKeyboard(event, userId);
            log.debug("Клавиатура для события создана: eventId={}, status={}, userId={}", 
                    event.getId(), event.getStatus(), userId);
        }
        
        if (event.getMessageId() != null) {
            log.debug("Попытка обновления существующего сообщения: eventId={}, messageId={}", 
                    event.getId(), event.getMessageId());
            
            boolean updated = telegramMessageService.tryEditMessageText(
                    chatId, 
                    event.getMessageId().intValue(), 
                    messageText, 
                    keyboard
            );
            
            if (updated) {
                log.info("Сообщение о событии успешно обновлено: eventId={}, messageId={}", 
                        event.getId(), event.getMessageId());
                return event;
            }
            
            log.info("Не удалось обновить сообщение, отправляем новое: eventId={}, oldMessageId={}", 
                    event.getId(), event.getMessageId());
        } else {
            log.debug("MessageId отсутствует, отправляем новое сообщение: eventId={}", event.getId());
        }
        
        Message sentMessage = telegramMessageService.sendMessageAndGet(chatId, messageText, keyboard);
        
        Long oldMessageId = event.getMessageId();
        event.setMessageId((long) sentMessage.getMessageId());
        Event savedEvent = eventRepository.save(event);
        
        log.info("Новое сообщение о событии отправлено и messageId сохранён: eventId={}, oldMessageId={}, newMessageId={}", 
                event.getId(), oldMessageId, sentMessage.getMessageId());
        
        return savedEvent;
    }
    
    /**
     * Обрабатывает доменное событие о создании события.
     * 
     * @param event доменное событие
     */
    @EventListener
    @Async
    public void onEventCreated(EventCreatedEvent event) {
        CorrelationIdUtil.executeWithCorrelationId(() -> {
            log.debug("Получено доменное событие EventCreatedEvent для события ID={}", event.event().getId());
            
            try {
                handleEventCreated(event.event(), event.event().getUser());
            } catch (Exception e) {
                log.error("Ошибка при обработке EventCreatedEvent для события ID={}: {}", 
                         event.event().getId(), e.getMessage(), e);
            }
        });
    }
    
    /**
     * Обновляет шапку /my_events после удаления или завершения события.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Получает актуальный список активных событий пользователя</li>
     *   <li>Если список пуст - отправляет сообщение о пустом состоянии</li>
     *   <li>Если есть события - устанавливает isMyEventsHeader для первого события</li>
     *   <li>Сбрасывает флаг для остальных событий</li>
     * </ol>
     * 
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void updateMyEventsHeaderAfterRemoval(Long userId) {
        log.debug("Обновление шапки /my_events после удаления события для пользователя ID={}", userId);
        
        List<Event> activeEvents = eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(
            userId, EventStatus.ACTIVE);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при обновлении шапки /my_events", userId);
                return new UserNotFoundException(userId);
            });
        
        Long chatId = user.getTelegramId();
        
        if (chatId == null) {
            log.warn("Не удалось получить chatId для пользователя ID={}", userId);
            return;
        }
        
        if (activeEvents.isEmpty()) {
            String emptyMessage = buildEmptyStateMessage();
            try {
                telegramMessageService.sendMessage(chatId, emptyMessage);
                log.info("Сообщение о пустом состоянии отправлено пользователю ID={}", userId);
            } catch (Exception e) {
                log.error("Ошибка при отправке сообщения о пустом состоянии пользователю ID={}: {}", 
                         userId, e.getMessage(), e);
            }
            return;
        }
        
        Event newFirstEvent = activeEvents.get(0);
        
        if (!Boolean.TRUE.equals(newFirstEvent.getIsMyEventsHeader())) {
            newFirstEvent.setIsMyEventsHeader(true);
            eventRepository.save(newFirstEvent);
            log.debug("Флаг isMyEventsHeader установлен для события ID={}", newFirstEvent.getId());
        }
        
        for (int i = 1; i < activeEvents.size(); i++) {
            Event evt = activeEvents.get(i);
            if (Boolean.TRUE.equals(evt.getIsMyEventsHeader())) {
                log.debug("Сброс флага isMyEventsHeader=false для события ID={}", evt.getId());
                evt.setIsMyEventsHeader(false);
                eventRepository.save(evt);
            }
        }
        
        // Всегда обновляем сообщение первого события, чтобы счетчик был актуальным
        try {
            sendOrUpdateEventMessage(newFirstEvent, chatId);
            log.info("Сообщение с шапкой /my_events обновлено для события ID={}", newFirstEvent.getId());
        } catch (Exception e) {
            log.error("Ошибка при обновлении сообщения с шапкой для события ID={}: {}", 
                     newFirstEvent.getId(), e.getMessage(), e);
        }
        
        log.debug("Шапка /my_events успешно обновлена для пользователя ID={}", userId);
    }
    
    /**
     * Формирует сообщение о пустом состоянии /my_events.
     * 
     * @return отформатированное сообщение о пустом состоянии
     */
    private String buildEmptyStateMessage() {
        return botMessageBuilder.buildEmptyMyEventsMessage();
    }
}
