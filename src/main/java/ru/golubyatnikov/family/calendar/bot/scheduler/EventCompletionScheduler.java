package ru.golubyatnikov.family.calendar.bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.enums.ActionType;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventHistoryService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CorrelationIdUtil;

import java.time.LocalDateTime;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.DATE;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.DESCRIPTION;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.SUCCESS;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Планировщик для автоматического завершения истекших событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCompletionScheduler {

    private final EventRepository eventRepository;
    private final EventHistoryService eventHistoryService;
    private final TelegramMessageService messageService;
    private final DateTimeFormattingService dateTimeFormattingService;
    private final KeyboardFactory keyboardFactory;

    /**
     * Автоматически завершает истекшие события.
     *
     */
    @Scheduled(fixedDelayString = "${app.scheduler.event-completion-interval}")
    @Transactional
    public void completeExpiredEvents() {
        CorrelationIdUtil.executeWithCorrelationId(() -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                List<Event> expiredEvents = eventRepository.findExpiredActiveEvents(now);

                if (expiredEvents.isEmpty()) {
                    return;
                }

                for (Event event : expiredEvents) {
                    try {
                        EventStatus oldStatus = event.getStatus();
                        event.setStatus(EventStatus.COMPLETED);
                        event.setCompletedAt(now);

                        eventRepository.save(event);

                        eventHistoryService.recordChange(
                                event.getId(),
                                event.getUser().getId(),
                                ActionType.UPDATED,
                                "status",
                                oldStatus.name(),
                                EventStatus.COMPLETED.name()
                        );

                        // Отправляем уведомление создателю
                        sendCompletionNotification(event);

                    } catch (Exception e) {
                        log.error("Ошибка при завершении события ID={}: {}", event.getId(), e.getMessage(), e);
                    }
                }

            } catch (Exception e) {
                log.error("Ошибка при выполнении автоматического завершения событий: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Отправляет уведомление создателю о завершении события.
     *
     * @param event завершенное событие
     */
    private void sendCompletionNotification(Event event) {
        try {
            Long chatId = event.getUser().getTelegramId();

            // Формируем сообщение
            StringBuilder message = new StringBuilder();
            message.append(SUCCESS + " ")
                    .append(bold("Событие завершено"))
                    .append("\n\n");

            // Название события
            message.append(DESCRIPTION + " ")
                    .append(bold(event.getTitle()))
                    .append("\n");

            // Дата события
            message.append(DATE + " ")
                    .append(escape(dateTimeFormattingService.formatDate(event.getEventDate())));

            // Время события
            if (event.getEventTime() != null) {
                message.append(" в ")
                        .append(escape(dateTimeFormattingService.formatTime(event.getEventTime())));

                if (event.getEndTime() != null) {
                    message.append(" - ")
                            .append(escape(dateTimeFormattingService.formatTime(event.getEndTime())));
                }
            }

            message.append("\n\n");
            message.append(italic("Событие автоматически отмечено как завершенное.")).append("\n");
            message.append(italic("Хотите добавить заметку о том, как прошло событие?"));

            // Создаем клавиатуру с кнопкой добавления заметки
            InlineKeyboardMarkup keyboard = createCompletionKeyboard(event.getId());

            messageService.sendMessage(chatId, message.toString(), keyboard);

        } catch (Exception e) {
            log.error("Ошибка при отправке уведомления о завершении события ID={}: {}",
                    event.getId(), e.getMessage(), e);
        }
    }

    /**
     * Создает inline-клавиатуру с кнопкой добавления заметки.
     *
     * @param eventId идентификатор события
     * @return объект InlineKeyboardMarkup с кнопкой
     */
    private @NonNull InlineKeyboardMarkup createCompletionKeyboard(@NonNull Long eventId) {
        InlineKeyboardButton button = keyboardFactory.createButton(DESCRIPTION + " Добавить заметку",
                CallbackPrefix.ADD_COMPLETION_NOTE.withPayload(eventId.toString()));

        InlineKeyboardRow row = keyboardFactory.createRow(button);
        return keyboardFactory.createMarkup(row);
    }
}

