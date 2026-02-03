package ru.golubyatnikov.family.calendar.bot.service.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.util.TextEventParser;

/**
 * Маршрутизатор входящих сообщений.
 * 
 * <p>Определяет тип сообщения и направляет его соответствующему обработчику:</p>
 * <ul>
 *   <li>Редактирование события → EventEditingMessageHandler</li>
 *   <li>Ожидание файла → FileMessageHandler</li>
 *   <li>Заметка к событию → CompletionNoteMessageHandler</li>
 *   <li>Поисковый запрос → SearchQueryMessageHandler</li>
 *   <li>Диалог создания → ConversationMessageHandler</li>
 *   <li>Текстовое событие → TextEventMessageHandler</li>
 *   <li>Команда → возврат false для обработки в UpdateProcessor</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageRouter {

    private final ConversationStateService conversationStateService;
    private final ConversationService conversationService;
    private final TextEventParser textEventParser;
    private final EventEditingMessageHandler eventEditingMessageHandler;
    private final FileMessageHandler fileMessageHandler;
    private final CompletionNoteMessageHandler completionNoteMessageHandler;
    private final SearchQueryMessageHandler searchQueryMessageHandler;
    private final ConversationMessageHandler conversationMessageHandler;
    private final TextEventMessageHandler textEventMessageHandler;

    /**
     * Маршрутизирует сообщение к соответствующему обработчику.
     * 
     * @param message сообщение от пользователя
     * @param user авторизованный пользователь
     * @param originalText оригинальный текст сообщения
     * @param commandText преобразованный текст команды
     * @return true если сообщение обработано, false если требуется обработка команды
     */
    public boolean routeMessage(Message message, User user, String originalText, String commandText) {
        Long userId = user.getId();
        
        log.debug("Маршрутизация сообщения: userId={}, hasText={}, isCommand={}", 
                userId, originalText != null, commandText != null && commandText.startsWith("/"));
        
        // Приоритет 1: Редактирование события
        if (conversationStateService.isEditingEvent(userId)) {
            log.debug("Маршрутизация к EventEditingMessageHandler: userId={}", userId);
            eventEditingMessageHandler.handle(message, user);
            return true;
        }
        
        // Приоритет 2: Ожидание файла
        if (conversationStateService.isAwaitingFile(userId)) {
            log.debug("Маршрутизация к FileMessageHandler (подсказка): userId={}", userId);
            fileMessageHandler.handleAwaitingFileHint(message, user);
            return true;
        }
        
        // Приоритет 3: Заметка к завершенному событию
        if (conversationStateService.isAwaitingCompletionNote(userId)) {
            log.debug("Маршрутизация к CompletionNoteMessageHandler: userId={}", userId);
            completionNoteMessageHandler.handle(message, user, originalText);
            return true;
        }
        
        // Приоритет 4: Поисковый запрос
        if (conversationStateService.isAwaitingSearchQuery(userId)) {
            log.debug("Маршрутизация к SearchQueryMessageHandler: userId={}", userId);
            searchQueryMessageHandler.handle(message, user);
            return true;
        }
        
        // Приоритет 5: Диалог создания события
        if (conversationService.hasActiveDraft(userId)) {
            log.debug("Маршрутизация к ConversationMessageHandler: userId={}", userId);
            conversationMessageHandler.handle(message, user);
            return true;
        }
        
        // Проверяем, является ли текст командой
        boolean isCommand = commandText != null && commandText.startsWith("/");
        
        // Приоритет 6: Текстовое событие (если не команда)
        if (!isCommand && originalText != null && textEventParser.looksLikeEvent(originalText)) {
            log.debug("Маршрутизация к TextEventMessageHandler: userId={}", userId);
            textEventMessageHandler.handle(message, user, originalText);
            return true;
        }
        
        // Команда - требуется обработка в UpdateProcessor
        log.debug("Сообщение не обработано маршрутизатором, требуется обработка команды: userId={}", userId);
        return false;
    }

    /**
     * Маршрутизирует файловое сообщение к соответствующему обработчику.
     * 
     * @param message сообщение с файлом
     * @param user авторизованный пользователь
     */
    public void routeFileMessage(Message message, User user) {
        Long userId = user.getId();
        
        log.debug("Маршрутизация файлового сообщения: userId={}", userId);
        
        // Проверяем, ожидает ли пользователь загрузки файла для вложения
        if (conversationStateService.isAwaitingFile(userId)) {
            log.debug("Маршрутизация к FileMessageHandler (загрузка вложения): userId={}", userId);
            fileMessageHandler.handleAttachmentUpload(message, user);
        } else {
            log.debug("Маршрутизация к FileMessageHandler (общая обработка): userId={}", userId);
            fileMessageHandler.handleGeneralFile(message, user);
        }
    }
}
