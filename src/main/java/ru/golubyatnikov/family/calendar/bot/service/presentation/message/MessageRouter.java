package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.TextEventParsingService;

/**
 * Маршрутизатор входящих сообщений.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageRouter {

    private final ConversationStateService conversationStateService;
    private final ConversationService conversationService;
    private final TextEventParsingService textEventParsingService;
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
     *
     * @return true, если сообщение обработано, false, если требуется обработка команды
     */
    public boolean routeMessage(Message message, @NonNull User user, String originalText, String commandText) {
        Long userId = user.getId();
        
        // Приоритет 1: Редактирование события
        if (conversationStateService.isEditingEvent(userId)) {
            eventEditingMessageHandler.handle(message, user);
            return true;
        }
        
        // Приоритет 2: Ожидание файла
        if (conversationStateService.isAwaitingFile(userId)) {
            fileMessageHandler.handleAwaitingFileHint(message, user);
            return true;
        }
        
        // Приоритет 3: Заметка к завершенному событию
        if (conversationStateService.isAwaitingCompletionNote(userId)) {
            completionNoteMessageHandler.handle(message, user, originalText);
            return true;
        }
        
        // Приоритет 4: Поисковый запрос
        if (conversationStateService.isAwaitingSearchQuery(userId)) {
            searchQueryMessageHandler.handle(message, user);
            return true;
        }
        
        // Приоритет 5: Диалог создания события
        if (conversationService.hasActiveDraft(userId)) {
            conversationMessageHandler.handle(message, user);
            return true;
        }
        
        // Проверяем, является ли текст командой
        boolean isCommand = commandText != null && commandText.startsWith("/");
        
        // Приоритет 6: Текстовое событие (если не команда)
        if (!isCommand && originalText != null && textEventParsingService.looksLikeEvent(originalText)) {
            textEventMessageHandler.handle(message, user, originalText);
            return true;
        }
        
        // Команда - требуется обработка в UpdateProcessor
        return false;
    }

    /**
     * Маршрутизирует файловое сообщение к соответствующему обработчику.
     * 
     * @param message сообщение с файлом
     * @param user авторизованный пользователь
     */
    public void routeFileMessage(Message message, @NonNull User user) {
        Long userId = user.getId();
        
        // Проверяем, ожидает ли пользователь загрузки файла для вложения
        if (conversationStateService.isAwaitingFile(userId)) {
            fileMessageHandler.handleAttachmentUpload(message, user);

        } else {
            fileMessageHandler.handleGeneralFile(message, user);
        }
    }
}
