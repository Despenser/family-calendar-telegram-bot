package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.TextEventParsingService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

import java.util.Base64;
import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик распознавания событий из текстовых сообщений.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TextEventMessageHandler {

    private final TextEventParsingService textEventParsingService;
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
            
            Optional<TextEventParsingService.ParsedEvent> parsedEventOpt = textEventParsingService.parseEvent(text);
            
            if (parsedEventOpt.isEmpty()) {
                log.debug("Не удалось распознать событие из текста: text='{}', telegramId={}", 
                        text, telegramId);
                return;
            }
            
            TextEventParsingService.ParsedEvent parsedEvent = parsedEventOpt.get();
            
            if (!parsedEvent.isValid()) {
                handleInvalidEvent(chatId, parsedEvent, telegramId);
                return;
            }
            
            log.debug("Событие успешно распознано: title='{}', date={}, time={}, telegramId={}", 
                     parsedEvent.title(), parsedEvent.date(), parsedEvent.time(), telegramId);
            
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
    private void handleInvalidEvent(Long chatId, TextEventParsingService.ParsedEvent parsedEvent, Long telegramId) {
        log.warn("Распознанное событие невалидно: parsedEvent={}, telegramId={}", parsedEvent, telegramId);
        
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("❌ *Не удалось создать событие*\n\n");
        
        if (parsedEvent.title() == null || parsedEvent.title().trim().isEmpty()) {
            responseBuilder.append("Название события не может быть пустым.\n\n");
        }
        
        if (parsedEvent.date() != null &&
            parsedEvent.date().isBefore(java.time.LocalDate.now())) {
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
    private void sendEventPreview(Long chatId, @NonNull TextEventParsingService.ParsedEvent parsedEvent, User user) {
        String preview = bold("✅ Распознано событие из текста:") + "\n\n" +
            "📝 Название: " + escape(parsedEvent.title()) + "\n" +
            "📅 Дата: " + escape(parsedEvent.date().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))) + "\n" +
            "🕐 Время: " + escape(parsedEvent.time().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))) + "\n\n" +
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
    private InlineKeyboardMarkup createEventConfirmationKeyboard(@NonNull TextEventParsingService.ParsedEvent parsedEvent) {
        String eventData = parsedEvent.title() + "|" +
                          parsedEvent.date().toString() + "|" +
                          parsedEvent.time().toString();

        String encodedData = Base64.getEncoder().encodeToString(eventData.getBytes());
        
        InlineKeyboardButton confirmButton = InlineKeyboardButton.builder()
                .text("✅ Создать событие")
                .callbackData("confirm_text_event:" + encodedData)
                .build();
        
        InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                .text("❌ Отмена")
                .callbackData("cancel_text_event")
                .build();
        
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(confirmButton, cancelButton))
                .build();
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
