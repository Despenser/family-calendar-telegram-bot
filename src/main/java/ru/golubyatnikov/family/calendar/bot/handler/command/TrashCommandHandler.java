package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.trash.TrashService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import java.util.List;
import java.util.stream.IntStream;

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
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder botMessageBuilder;
    
    /**
     * Обрабатывает команду /trash.
     *
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return null, так как сообщения отправляются напрямую
     */
    @Override
    public String handle(@NonNull Message message,
                         @NonNull User user) {
        Long chatId = message.getChatId();
        log.debug("Обработка команды /trash для пользователя ID={}", user.getId());
        
        try {
            List<Event> trashedEvents = trashService.getUserTrash(user.getId());

            if (trashedEvents.isEmpty()) {
                String responseMessage = buildEmptyTrashMessage();
                log.debug("Пользователю ID={} будет отправлено сообщение о пустой корзине", user.getId());
                return responseMessage;
            }
            
            // Управление флагами isTrashHeader
            Event firstEvent = trashedEvents.getFirst();
            if (!Boolean.TRUE.equals(firstEvent.getIsTrashHeader())) {
                firstEvent.setIsTrashHeader(true);
                trashService.saveEvent(firstEvent);
                log.debug("Установлен флаг isTrashHeader=true для первого события ID={}", firstEvent.getId());
            }
            
            // Сбрасываем флаг для остальных событий
            IntStream.range(1, trashedEvents.size())
                    .mapToObj(trashedEvents::get)
                    .filter(event -> Boolean.TRUE.equals(event.getIsTrashHeader()))
                    .forEach(event -> {
                        event.setIsTrashHeader(false);
                        trashService.saveEvent(event);
                        log.debug("Сброшен флаг isTrashHeader для события ID={}", event.getId());
            });
            
            // Формируем шапку
            String header = botMessageBuilder.buildTrashHeader(trashedEvents.size());
            
            // Отправляем первое событие с шапкой
            String firstEventText = botMessageBuilder.buildEventMessage(firstEvent);
            String combinedMessage = header + "\n" + firstEventText;
            InlineKeyboardMarkup keyboard = keyboardService.createTrashActionsKeyboard(firstEvent.getId());
            
            log.debug("Отправка первого события ID={} с шапкой корзины", firstEvent.getId());
            Message sentMessage = messageService.sendMessageAndGet(chatId, combinedMessage, keyboard);
            firstEvent.setMessageId((long) sentMessage.getMessageId());
            trashService.saveEvent(firstEvent);
            log.debug("Сохранен messageId={} для первого события ID={}", sentMessage.getMessageId(), firstEvent.getId());
            
            // Отправляем остальные события
            for (int i = 1; i < trashedEvents.size(); i++) {
                Event event = trashedEvents.get(i);
                String eventText = botMessageBuilder.buildEventMessage(event);
                InlineKeyboardMarkup eventKeyboard = keyboardService.createTrashActionsKeyboard(event.getId());
                
                log.debug("Отправка события ID={} (позиция {})", event.getId(), i + 1);
                Message eventMessage = messageService.sendMessageAndGet(chatId, eventText, eventKeyboard);
                event.setMessageId((long) eventMessage.getMessageId());
                trashService.saveEvent(event);
                log.debug("Сохранен messageId={} для события ID={}", eventMessage.getMessageId(), event.getId());
            }
            
            log.debug("Пользователю ID={} отправлено {} удаленных событий", user.getId(), trashedEvents.size());
            return null;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /trash для пользователя ID={}", user.getId(), e);
            return "❌ Произошла ошибка при получении корзины. Попробуйте позже.";
        }
    }
    
    /**
     * Формирует сообщение о пустой корзине.
     * 
     * @return отформатированное сообщение о пустой корзине
     */
    private @NonNull String buildEmptyTrashMessage() {
        return "🗑️ " + bold("Корзина") + "\n\n" +
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
