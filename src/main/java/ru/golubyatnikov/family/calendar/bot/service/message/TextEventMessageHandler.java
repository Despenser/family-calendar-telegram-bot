package ru.golubyatnikov.family.calendar.bot.service.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.TextEventParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик распознавания событий из текстовых сообщений.
 * 
 * <p>Парсит текст сообщения и пытается извлечь информацию о событии
 * (название, дата, время). При успешном распознавании отправляет
 * предпросмотр с кнопками подтверждения.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TextEventMessageHandler {

    private final TextEventParser textEventParser;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;

    /**
     * Обрабатывает распознавание события из текстового сообщения.
     * 
     * @param message исходное сообщение от пользователя
     * @param user авторизованный пользователь
     * @param text текст сообщения для парсинга
     */
    public void handle(Message message, User user, String text) {
        try {
            Long chatId = message.getChatId();
            Long telegramId = user.getTelegramId();
            
            log.debug("Попытка распознать событие из текста для пользователя: userId={}, telegramId={}", 
                    user.getId(), telegramId);
            
            Optional<TextEventParser.ParsedEvent> parsedEventOpt = textEventParser.parseEvent(text);
            
            if (parsedEventOpt.isEmpty()) {
                log.debug("Не удалось распознать событие из текста: text='{}', telegramId={}", 
                        text, telegramId);
                return;
            }
            
            TextEventParser.ParsedEvent parsedEvent = parsedEventOpt.get();
            
            if (!parsedEvent.isValid()) {
                handleInvalidEvent(chatId, parsedEvent, telegramId);
                return;
            }
            
            log.debug("Событие успешно распознано: title='{}', date={}, time={}, telegramId={}", 
                     parsedEvent.getTitle(), parsedEvent.getDate(), parsedEvent.getTime(), telegramId);
            
            sendEventPreview(chatId, parsedEvent, user);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке распознавания события из текста: userId={}, telegramId={}, error={}", 
                     user.getId(), user.getTelegramId(), e.getMessage(), e);
            
            handleError(message.getChatId(), user);
        }
    }

    /**
     * Обрабатывает невалидное распознанное событие.
     */
    private void handleInvalidEvent(Long chatId, TextEventParser.ParsedEvent parsedEvent, Long telegramId) {
        log.warn("Распознанное событие невалидно: parsedEvent={}, telegramId={}", parsedEvent, telegramId);
        
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("❌ *Не удалось создать событие*\n\n");
        
        if (parsedEvent.getTitle() == null || parsedEvent.getTitle().trim().isEmpty()) {
            responseBuilder.append("Название события не может быть пустым.\n\n");
        }
        
        if (parsedEvent.getDate() != null && 
            parsedEvent.getDate().isBefore(java.time.LocalDate.now())) {
            responseBuilder.append("Дата события не может быть в прошлом.\n\n");
        }
        
        responseBuilder.append("Попробуйте использовать один из форматов:\n")
                      .append("• `Событие: Встреча Дата: 15.01.2026 Время: 14:30`\n")
                      .append("• `Встреча 15.01.2026 14:30`\n")
                      .append("• `Встреча завтра в 14:30`\n\n")
                      .append("Или используйте команду ➕ /add_event для пошагового создания.");
        
        try {
            String response = formatMessage(responseBuilder.toString());
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения: {}", e.getMessage());
        }
    }

    /**
     * Отправляет предпросмотр распознанного события.
     */
    private void sendEventPreview(Long chatId, TextEventParser.ParsedEvent parsedEvent, User user) {
        String preview = bold("✅ Распознано событие из текста:") + "\n\n" +
            "📝 Название: " + escape(parsedEvent.getTitle()) + "\n" +
            "📅 Дата: " + escape(parsedEvent.getDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))) + "\n" +
            "🕐 Время: " + escape(parsedEvent.getTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))) + "\n\n" +
            "Подтвердите создание события:";
        
        try {
            InlineKeyboardMarkup keyboard = createEventConfirmationKeyboard(parsedEvent);
            messageService.sendMessageWithInlineKeyboard(chatId, preview, keyboard);
            
            log.debug("Отправлен предпросмотр распознанного события пользователю: userId={}, telegramId={}", 
                    user.getId(), user.getTelegramId());
        } catch (Exception e) {
            log.error("Ошибка при отправке предпросмотра: {}", e.getMessage());
        }
    }

    /**
     * Создает inline-клавиатуру для подтверждения создания события из текста.
     */
    private InlineKeyboardMarkup createEventConfirmationKeyboard(TextEventParser.ParsedEvent parsedEvent) {
        String eventData = parsedEvent.getTitle() + "|" + 
                          parsedEvent.getDate().toString() + "|" + 
                          parsedEvent.getTime().toString();
        String encodedData = java.util.Base64.getEncoder().encodeToString(eventData.getBytes());
        
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Создать событие");
        confirmButton.setCallbackData("confirm_text_event:" + encodedData);
        
        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("cancel_text_event");
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(confirmButton);
        row.add(cancelButton);
        rows.add(row);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Обрабатывает ошибку.
     */
    private void handleError(Long chatId, User user) {
        try {
            String response = bold("❌ Произошла ошибка при распознавании события") + ".\n\n" +
                    italic("Используйте команду ➕ /add_event для пошагового создания.");
            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);
        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }
}
