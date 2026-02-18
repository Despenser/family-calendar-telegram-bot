package ru.golubyatnikov.family.calendar.bot.service.domain.planner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

/**
 * Сервис для навигации по событиям планировщика.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
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
    public void manageHeaderFlags(@NonNull List<Event> userEvents) {
        if (userEvents.isEmpty()) {
            return;
        }

        // Устанавливаем флаг для первого события
        Event firstEvent = userEvents.getFirst();
        if (!Boolean.TRUE.equals(firstEvent.getIsMyEventsHeader())) {
            firstEvent.setIsMyEventsHeader(true);
            eventService.saveEvent(firstEvent);
        }
        
        // Сбрасываем флаг для остальных событий
        IntStream.range(1, userEvents.size()).mapToObj(userEvents::get)
                .filter(event -> Boolean.TRUE.equals(event.getIsMyEventsHeader()))
                .forEach(event -> {
                    event.setIsMyEventsHeader(false);
                    eventService.saveEvent(event);
                });
    }

    /**
     * Отправляет объединенное сообщение (заголовок + первое событие) с fallback механизмом.
     * При ошибке форматирования пытается отправить сообщение без форматирования.
     * 
     * @param chatId идентификатор чата
     * @param combinedMessage объединенное сообщение
     * @param header заголовок
     * @param firstEvent первое событие
     * @param userId идентификатор пользователя
     *
     * @return true, если отправка успешна
     */
    public boolean sendCombinedMessageWithFallback(Long chatId,
                                                   String combinedMessage,
                                                   String header,
                                                   Event firstEvent,
                                                   Long userId) {

        boolean sent = sendCombinedMessage(chatId, combinedMessage, firstEvent, userId);
        if (!sent) {
            return executeFallback(chatId, header, firstEvent, userId);
        }
        
        return true;
    }

    /**
     * Отправляет событие отдельным сообщением с fallback механизмом.
     * При ошибке форматирования пытается отправить сообщение без форматирования.
     *
     * @param chatId идентификатор чата
     * @param eventText текст события
     * @param event событие
     * @param userId идентификатор пользователя
     */
    public void sendEventMessageWithFallback(Long chatId,
                                             String eventText,
                                             Event event,
                                             Long userId) {

        boolean sent = sendEventMessage(chatId, eventText, event, userId);
        if (!sent) {
            executeFallback(chatId, null, event, userId);
        }
    }

    /**
     * Выполняет fallback отправку сообщения без форматирования.
     * 
     * @param chatId идентификатор чата
     * @param header заголовок (может быть null)
     * @param event событие
     * @param userId идентификатор пользователя
     *
     * @return true, если отправка успешна
     */
    private boolean executeFallback(Long chatId, String header, Event event, Long userId) {
        try {
            if (header != null) {
                messageService.sendMessage(chatId, header);
            }
            
            String plainText = buildPlainEventText(event);
            return sendPlainEventMessage(chatId, plainText, event, userId);
            
        } catch (Exception e) {
            log.error("Fallback не сработал для события ID={}: {}", event.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Формирует простое текстовое представление события без форматирования.
     * 
     * @param event событие
     * @return простой текст
     */
    private @NonNull String buildPlainEventText(@NonNull Event event) {
        StringBuilder plainText = new StringBuilder();
        
        plainText.append("📌 ").append(event.getTitle()).append("\n");
        plainText.append("📅 Дата: ").append(event.getFormattedDate()).append("\n");
        plainText.append("🕐 Время: ").append(event.getFormattedTime());
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            plainText.append("\n📝 Описание: ").append(event.getDescription());
        }
        
        return plainText.toString();
    }

    /**
     * Отправляет объединенное сообщение (заголовок + первое событие).
     * 
     * @param chatId идентификатор чата
     * @param combinedMessage объединенное сообщение
     * @param firstEvent первое событие
     * @param userId идентификатор пользователя
     *
     * @return true, если отправка успешна
     */
    private boolean sendCombinedMessage(Long chatId, String combinedMessage, Event firstEvent, Long userId) {
        boolean success = sendEventMessageWithKeyboard(combinedMessage, firstEvent, userId,
                (text, keyboard) -> {
                    try {
                        return messageService.sendMessageAndGet(chatId, text, keyboard);

                    } catch (Exception e) {
                        throw new RuntimeException("Ошибка при отправке объединенного сообщения", e);
                    }
                }
        );

        if (success) {
            // Сохраняем контекст шапки
            int eventCount = 1;
            conversationStateService.saveEventHeaderContext(userId, true, eventCount);
        }

        return success;
    }

    /**
     * Отправляет событие отдельным сообщением.
     * 
     * @param chatId идентификатор чата
     * @param eventText текст события
     * @param event событие
     * @param userId идентификатор пользователя
     *
     * @return true, если отправка успешна
     */
    private boolean sendEventMessage(Long chatId, String eventText, Event event, Long userId) {
        return sendEventMessageWithKeyboard(eventText, event, userId,
                (text, keyboard) -> {
                    try {
                        return messageService.sendMessageAndGet(chatId, text, keyboard);

                    } catch (Exception e) {
                        throw new RuntimeException("Ошибка при отправке события", e);
                    }
                }
        );
    }

    /**
     * Отправляет событие без форматирования (fallback).
     * 
     * @param chatId идентификатор чата
     * @param plainText простой текст события
     * @param event событие
     * @param userId идентификатор пользователя
     *
     * @return true, если отправка успешна
     */
    private boolean sendPlainEventMessage(Long chatId, String plainText, Event event, Long userId) {
        return sendEventMessageWithKeyboard(plainText, event, userId,
                (text, keyboard) -> {
                    try {
                        return messageService.sendMessageWithoutFormattingAndGet(chatId, text, keyboard);

                    } catch (Exception e) {
                        throw new RuntimeException("Ошибка при отправке события без форматирования", e);
                    }
                }
        );
    }

    /**
     * Отправляет сообщение с клавиатурой и сохраняет messageId в событии.
     * Общий метод для устранения дублирования кода.
     *
     * @param messageText текст сообщения
     * @param event событие для сохранения messageId
     * @param userId идентификатор пользователя
     * @param sendFunction функция отправки сообщения
     *
     * @return true, если отправка успешна
     */
    private boolean sendEventMessageWithKeyboard(String messageText,
                                                 Event event,
                                                 Long userId,
                                                 BiFunction<String, InlineKeyboardMarkup, Message> sendFunction) {

        try {
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
            if (keyboard == null || keyboard.getKeyboard().isEmpty()) {
                return false;
            }

            Message sentMessage = sendFunction.apply(messageText, keyboard);

            event.setMessageId((long) sentMessage.getMessageId());
            eventService.saveEvent(event);

            return true;
            
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения для события ID={}: {}", event.getId(), e.getMessage(), e);
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

        try {
            Event headerEvent = userEvents.stream()
                    .filter(e -> Boolean.TRUE.equals(e.getIsMyEventsHeader()))
                    .findFirst()
                    .orElse(null);
            
            if (headerEvent == null || headerEvent.getMessageId() == null) {
                return;
            }
            
            String combinedMessage = header + "\n" + eventText;
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(headerEvent, userId);
            Long chatId = headerEvent.getUser().getTelegramId();
            
            messageService.tryEditMessageText(
                    chatId, 
                    headerEvent.getMessageId().intValue(), 
                    combinedMessage, 
                    keyboard);
            
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
    }
}
