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
import ru.golubyatnikov.family.calendar.bot.model.dto.ParsedEvent;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.TextEventParsingService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
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
    private final DateTimeFormattingService dateTimeFormattingService;

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
            
            Optional<ParsedEvent> parsedEventOpt = textEventParsingService.parseEvent(text);
            
            if (parsedEventOpt.isEmpty()) {
                return;
            }
            
            ParsedEvent parsedEvent = parsedEventOpt.get();
            
            if (!parsedEvent.isValid()) {
                handleInvalidEvent(chatId, parsedEvent);
                return;
            }
            
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
    private void handleInvalidEvent(Long chatId, @NonNull ParsedEvent parsedEvent) {
        
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
        
        String response = formatMessage(responseBuilder.toString());
        ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
        sendMessage(chatId, response, keyboard);
    }

    /**
     * Отправляет предпросмотр распознанного события.
     */
    private void sendEventPreview(Long chatId, @NonNull ParsedEvent parsedEvent, User user) {
        String preview = bold("✅ Распознано событие из текста:") + "\n\n" +
            "📝 Название: " + escape(parsedEvent.title()) + "\n" +
            "📅 Дата: " + escape(dateTimeFormattingService.formatDate(parsedEvent.date())) + "\n" +
            "🕐 Время: " + escape(dateTimeFormattingService.formatTime(parsedEvent.time())) + "\n\n" +
            "Подтвердите создание события:";
        
        sendMessageWithKeyboard(chatId, preview, createEventConfirmationKeyboard(parsedEvent));
    }

    /**
     * Создает inline-клавиатуру для подтверждения создания события из текста.
     */
    private InlineKeyboardMarkup createEventConfirmationKeyboard(@NonNull ParsedEvent parsedEvent) {
        String eventData = parsedEvent.title() + "|" +
                          parsedEvent.date().toString() + "|" +
                          parsedEvent.time().toString();

        String encodedData = Base64.getEncoder().encodeToString(eventData.getBytes());
        
        InlineKeyboardButton confirmButton = InlineKeyboardButton.builder()
                .text("✅ Создать событие")
                .callbackData(CallbackPrefix.CONFIRM_TEXT_EVENT.withPayload(encodedData))
                .build();
        
        InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                .text("✖️ Отменить создание")
                .callbackData(CallbackPrefix.CANCEL_TEXT_EVENT.withPayload(""))
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

    /**
     * Отправляет сообщение с inline клавиатурой.
     */
    private void sendMessageWithKeyboard(Long chatId, String message, InlineKeyboardMarkup keyboard) {
        try {
            messageService.sendMessageWithInlineKeyboard(chatId, message, keyboard);
            
        } catch (Exception e) {
            logMessageSendError(e);
        }
    }

    /**
     * Отправляет сообщение с reply клавиатурой.
     */
    private void sendMessage(Long chatId, String message, ReplyKeyboardMarkup keyboard) {
        try {
            messageService.sendMessage(chatId, message, keyboard);

        } catch (Exception e) {
            logMessageSendError(e);
        }
    }

    /**
     * Логирует ошибку при отправке сообщения.
     */
    private void logMessageSendError(@NonNull Exception e) {
        log.error("Ошибка при отправке сообщения: {}", e.getMessage());
    }
}
