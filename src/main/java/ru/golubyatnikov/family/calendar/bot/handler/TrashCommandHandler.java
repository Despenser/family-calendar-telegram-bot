package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.TrashService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /trash для управления корзиной удаленных событий.
 * 
 * <p>Этот обработчик позволяет пользователю:</p>
 * <ul>
 *   <li>Просматривать удаленные события (корзина)</li>
 *   <li>Восстанавливать события из корзины</li>
 *   <li>Окончательно удалять события</li>
 * </ul>
 * 
 * <p>События хранятся в корзине 30 дней, после чего автоматически удаляются.</p>
 * 
 * <p><b>Требования:</b> 19.4</p>
 * 
 * @see CommandHandler
 * @see TrashService
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrashCommandHandler implements CommandHandler {
    
    private final TrashService trashService;
    private final TelegramMessageService messageService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    
    /**
     * Обрабатывает команду /trash.
     * 
     * <p>Получает список удаленных событий пользователя и отправляет их
     * с inline-кнопками для восстановления или окончательного удаления.</p>
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, User user) {
        Long chatId = message.getChatId();
        log.debug("Обработка команды /trash для пользователя ID={}", user.getId());
        
        try {
            List<Event> trashedEvents = trashService.getUserTrash(user.getId());
            
            if (trashedEvents.isEmpty()) {
                String responseMessage = escape("🗑️ ") + bold("Корзина") + escape("\n\n") +
                                        escape("Корзина пуста.") + escape("\n\n") +
                                        italic("Удаленные события хранятся здесь 30 дней, после чего автоматически удаляются навсегда.");
                log.debug("Пользователю ID={} будет отправлено сообщение о пустой корзине", user.getId());
                return responseMessage;
            }
            
            // Формирование сообщения с удаленными событиями
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(escape("🗑️ ")).append(bold("Корзина")).append(escape("\n\n"));
            messageBuilder.append(italic("Удаленные события хранятся 30 дней")).append(escape("\n\n"));
            
            for (int i = 0; i < trashedEvents.size(); i++) {
                Event event = trashedEvents.get(i);
                messageBuilder.append(formatEvent(event, i + 1));
                messageBuilder.append("\n");
                
                // Отправка сообщения с кнопками для каждого события
                InlineKeyboardMarkup keyboard = createEventActionsKeyboard(event.getId());
                try {
                    messageService.sendMessage(chatId, messageBuilder.toString(), keyboard);
                } catch (Exception ex) {
                    log.error("Ошибка при отправке сообщения: {}", ex.getMessage(), ex);
                }
                messageBuilder.setLength(0);
            }
            
            log.debug("Пользователю ID={} отправлено {} удаленных событий", user.getId(), trashedEvents.size());
            // Возвращаем null, так как сообщения уже отправлены
            return null;
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /trash для пользователя ID={}", user.getId(), e);
            return "❌ Произошла ошибка при получении корзины. Попробуйте позже.";
        }
    }
    
    /**
     * Создает inline-клавиатуру с действиями для события в корзине.
     * 
     * @param eventId идентификатор события
     * @return объект InlineKeyboardMarkup с кнопками действий
     */
    private InlineKeyboardMarkup createEventActionsKeyboard(Long eventId) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        // Кнопка "Восстановить"
        InlineKeyboardButton restoreButton = new InlineKeyboardButton();
        restoreButton.setText("♻️ Восстановить");
        restoreButton.setCallbackData("trash_restore_" + eventId);
        row.add(restoreButton);
        
        // Кнопка "Удалить навсегда"
        InlineKeyboardButton deleteButton = new InlineKeyboardButton();
        deleteButton.setText("❌ Удалить навсегда");
        deleteButton.setCallbackData("trash_delete_" + eventId);
        row.add(deleteButton);
        
        keyboard.add(row);
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    /**
     * Обрабатывает callback query от inline-кнопок корзины.
     * 
     * <p>Этот метод вызывается из UpdateProcessor при нажатии на кнопку
     * восстановления или удаления события.</p>
     * 
     * @param callbackQuery callback query от Telegram
     * @param user пользователь, нажавший кнопку
     */
    public void handleTrashCallback(CallbackQuery callbackQuery, User user) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        
        log.debug("Обработка callback корзины '{}' для пользователя ID={}", data, user.getId());
        
        try {
            if (data.startsWith("trash_restore_")) {
                Long eventId = Long.parseLong(data.substring("trash_restore_".length()));
                handleRestore(chatId, user, eventId);
            } else if (data.startsWith("trash_delete_")) {
                Long eventId = Long.parseLong(data.substring("trash_delete_".length()));
                handlePermanentDelete(chatId, user, eventId);
            }
        } catch (NumberFormatException e) {
            log.error("Ошибка парсинга ID события из callback data: {}", data, e);
            try {
                messageService.sendMessage(chatId, "❌ Ошибка обработки запроса.");
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: {}", ex.getMessage(), ex);
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке callback корзины для пользователя ID={}", user.getId(), e);
            try {
                messageService.sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.");
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает восстановление события из корзины.
     * 
     * @param chatId идентификатор чата
     * @param user пользователь, восстанавливающий событие
     * @param eventId идентификатор события
     */
    private void handleRestore(Long chatId, User user, Long eventId) {
        log.debug("Восстановление события ID={} пользователем ID={}", eventId, user.getId());
        
        try {
            Event restoredEvent = trashService.restoreEvent(eventId, user.getId());
            
            String responseMessage = escape("♻️ ") + bold("Событие восстановлено") + escape("\n\n") +
                                    bold(restoredEvent.getTitle()) + escape("\n") +
                                    escape("Дата: ") + escape(restoredEvent.getEventDate().format(DATE_FORMATTER)) + escape("\n\n") +
                                    italic("Событие снова доступно в календаре.");
            
            messageService.sendMessage(chatId, responseMessage);
            log.debug("Событие ID={} успешно восстановлено пользователем ID={}", eventId, user.getId());
            
        } catch (Exception e) {
            log.error("Ошибка при восстановлении события ID={} пользователем ID={}", eventId, user.getId(), e);
            try {
                messageService.sendMessage(chatId, 
                    escape("❌ Не удалось восстановить событие. ") + escape(e.getMessage()));
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Обрабатывает окончательное удаление события.
     * 
     * @param chatId идентификатор чата
     * @param user пользователь, удаляющий событие
     * @param eventId идентификатор события
     */
    private void handlePermanentDelete(Long chatId, User user, Long eventId) {
        log.debug("Окончательное удаление события ID={} пользователем ID={}", eventId, user.getId());
        
        try {
            trashService.permanentlyDelete(eventId, user.getId());
            
            String responseMessage = escape("❌ ") + bold("Событие удалено навсегда") + escape("\n\n") +
                                    italic("Событие окончательно удалено из системы и не может быть восстановлено.");
            
            messageService.sendMessage(chatId, responseMessage);
            log.debug("Событие ID={} окончательно удалено пользователем ID={}", eventId, user.getId());
            
        } catch (Exception e) {
            log.error("Ошибка при окончательном удалении события ID={} пользователем ID={}", 
                     eventId, user.getId(), e);
            try {
                messageService.sendMessage(chatId, 
                    escape("❌ Не удалось удалить событие. ") + escape(e.getMessage()));
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Форматирует событие для отображения в корзине.
     * 
     * @param event событие для форматирования
     * @param number порядковый номер события
     * @return отформатированная строка с информацией о событии
     */
    private String formatEvent(Event event, int number) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(bold(String.valueOf(number))).append(escape(". "));
        
        // Иконка типа события
        if (event.getIsPersonal()) {
            sb.append(escape("🔒 "));
        } else {
            sb.append(escape("👨‍👩‍👧‍👦 "));
        }
        
        // Название события
        sb.append(bold(event.getTitle())).append(escape("\n"));
        
        // Дата события
        sb.append(escape("   📅 ")).append(escape(event.getEventDate().format(DATE_FORMATTER)));
        
        // Время события
        if (event.getEventTime() != null) {
            sb.append(escape(" в ")).append(escape(event.getEventTime().format(TIME_FORMATTER)));
            
            if (event.getEndTime() != null) {
                sb.append(escape(" - ")).append(escape(event.getEndTime().format(TIME_FORMATTER)));
            }
        }
        
        sb.append(escape("\n"));
        
        // Дата удаления
        if (event.getDeletedAt() != null) {
            sb.append(escape("   🗑️ Удалено: ")).append(escape(event.getDeletedAt().format(DATETIME_FORMATTER))).append(escape("\n"));
        }
        
        return sb.toString();
    }
    
    @Override
    public String getCommand() {
        return "/trash";
    }
    
    @Override
    public String getDescription() {
        return "Корзина удаленных событий";
    }
    
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
