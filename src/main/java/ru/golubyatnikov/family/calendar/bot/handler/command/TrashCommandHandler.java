package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.domain.trash.TrashService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import java.util.List;
import java.util.stream.IntStream;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.TRASH;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /trash для управления корзиной удаленных событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-19
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrashCommandHandler implements CommandHandler {
    
    private final TrashService trashService;
    private final EventService eventService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    
    /**
     * Обрабатывает команду /trash.
     *
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     *
     * @return null, так как сообщения отправляются напрямую
     */
    @Override
    public String handle(@NonNull Message message,
                         @NonNull User user) {
        Long chatId = message.getChatId();
        try {
            List<Event> trashedEvents = trashService.getUserTrash(user.getId());

            if (trashedEvents.isEmpty()) {
                return buildEmptyTrashMessage();
            }
            
            // Управление флагами isTrashHeader
            Event firstEvent = trashedEvents.getFirst();
            if (!Boolean.TRUE.equals(firstEvent.getIsTrashHeader())) {
                firstEvent.setIsTrashHeader(true);
                eventService.saveEvent(firstEvent);
                }
            
            // Сбрасываем флаг для остальных событий
            IntStream.range(1, trashedEvents.size())
                    .mapToObj(trashedEvents::get)
                    .filter(event -> Boolean.TRUE.equals(event.getIsTrashHeader()))
                    .forEach(event -> {
                        event.setIsTrashHeader(false);
                        eventService.saveEvent(event);
                        });
            
            // Формируем шапку
            String header = botMessageFormattingService.buildTrashHeader(trashedEvents.size());
            
            // Отправляем первое событие с шапкой
            String firstEventText = botMessageFormattingService.buildEventMessage(firstEvent);
            String combinedMessage = header + "\n" + firstEventText;
            InlineKeyboardMarkup keyboard = keyboardService.createTrashActionsKeyboard(firstEvent.getId());
            
            Message sentMessage = messageService.sendMessageAndGet(chatId, combinedMessage, keyboard);
            firstEvent.setMessageId((long) sentMessage.getMessageId());
            eventService.saveEvent(firstEvent);
            // Отправляем остальные события
            for (int i = 1; i < trashedEvents.size(); i++) {
                Event event = trashedEvents.get(i);
                String eventText = botMessageFormattingService.buildEventMessage(event);
                InlineKeyboardMarkup eventKeyboard = keyboardService.createTrashActionsKeyboard(event.getId());
                
                Message eventMessage = messageService.sendMessageAndGet(chatId, eventText, eventKeyboard);

                event.setMessageId((long) eventMessage.getMessageId());
                eventService.saveEvent(event);
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /trash для пользователя ID={}", user.getId(), e);
            return ERROR + " Произошла ошибка при получении корзины. Попробуйте позже.";
        }
    }
    
    /**
     * Формирует сообщение о пустой корзине.
     * 
     * @return отформатированное сообщение о пустой корзине
     */
    private @NonNull String buildEmptyTrashMessage() {
        return TRASH + " " + bold("Корзина") + "\n\n" +
                escape("Корзина пуста.") + "\n\n" +
                italic("Удаленные события хранятся здесь 30 дней, после чего автоматически удаляются навсегда.");
    }
    
    @Override
    public String getCommand() {
        return "/trash";
    }
    
    @Override
    public String getDescription() {
        return "Корзина удаленных событий";
    }
}
