package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import java.time.LocalDate;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Обработчик команды /add_event для создания новых событий в семейном календаре.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AddEventCommandHandler implements CommandHandler {

    private final ConversationService conversationService;
    private final KeyboardService keyboardService;
    private final TelegramMessageService messageService;

    /**
     * Обрабатывает команду /add_event и начинает диалог создания события.
     *
     * @param message входящее сообщение от Telegram
     * @param user пользователь из базы данных (не может быть null, так как команда требует авторизации)
     *
     * @return текст ответа пользователю (null, так как ответ отправляется через TelegramMessageService)
     * @throws IllegalArgumentException если message или user равны null
     */
    @Override
    @Transactional
    public String handle(Message message, User user) {
        validateInput(message, user);
        
        if (!user.hasFamily()) {
            return buildNoFamilyMessage();
        }
        
        try {
            startEventCreationFlow(message.getChatId(), user);
            return null;

        } catch (Exception e) {
            log.error("Ошибка при создании события: userId={}, error={}", 
                    user.getId(), e.getMessage(), e);

            handleCreationError(user.getId());
            return buildErrorMessage(e);
        }
    }

    private void validateInput(Message message, User user) {
        if (message == null) {
            throw new IllegalArgumentException("Сообщение не может быть null");
        }
        
        if (user == null) {
            throw new IllegalArgumentException("Пользователь не может быть null для команды /add_event");
        }
    }

    private @NonNull String buildNoFamilyMessage() {
        return "❌ " + escape("Вы не принадлежите ни одной семье.") + "\n\n" +
               escape("Для создания событий необходимо быть членом семьи. ") +
               escape("Обратитесь к администратору для добавления в семью.");
    }

    private void startEventCreationFlow(Long chatId, @NonNull User user) throws TelegramApiException {
        conversationService.startEventCreation(user.getId(), true);
        
        LocalDate currentDate = user.getCurrentDate();
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(
                currentDate.getYear(), currentDate.getMonthValue(), user);
        
        Message sentMessage = messageService.sendMessageWithInlineKeyboardAndGet(
                chatId, 
                bold("📋 Создание нового события") + "\n\nВыберите дату события:", 
                calendar);
        
        conversationService.setCreationMessageId(user.getId(), sentMessage.getMessageId().longValue());
        
        }

    private void handleCreationError(Long userId) {
        try {
            conversationService.cancelEventCreation(userId);

        } catch (Exception cleanupError) {
            log.error("Ошибка при очистке черновика: userId={}, error={}", 
                    userId, cleanupError.getMessage(), cleanupError);
        }
    }

    private @NonNull String buildErrorMessage(@NonNull Exception e) {
        return String.format("❌ %s: %s\n\n%s", 
                bold("Произошла ошибка при создании события"),
                escape(e.getMessage()),
                escape("Попробуйте снова, используя команду /add_event"));
    }

    @Override
    public String getCommand() {
        return "/add_event";
    }

    @Override
    public String getDescription() {
        return "Добавить новое событие в календарь";
    }
}
