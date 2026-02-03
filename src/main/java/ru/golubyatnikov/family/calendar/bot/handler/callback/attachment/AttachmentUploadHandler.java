package ru.golubyatnikov.family.calendar.bot.handler.callback.attachment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Обработчик для загрузки вложений к событию.
 * 
 * <p>Управляет процессом добавления файлов к событию:</p>
 * <ul>
 *   <li>Инициирует режим ожидания файла</li>
 *   <li>Отображает инструкции по загрузке</li>
 *   <li>Обрабатывает отмену загрузки</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentUploadHandler {
    
    private final TelegramMessageService messageService;
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final ConversationStateService conversationStateService;
    private final BotMessageBuilder botMessageBuilder;
    
    /**
     * Обрабатывает начало добавления файла.
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    public void handleAddFile(Long eventId, User user, Long chatId, 
                              Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Начало добавления файла для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        try {
            // Получаем событие для проверки прав доступа
            Event event = eventService.getEventById(eventId);
            
            // Проверяем, что пользователь является создателем события
            if (!event.getUser().getId().equals(user.getId())) {
                log.warn("Пользователь ID={} попытался добавить вложение к чужому событию ID={}", 
                        user.getId(), eventId);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.NO_ACCESS);
                return;
            }
            
            // Формируем инструкцию по загрузке файла
            String instruction = buildAttachmentUploadInstruction();
            
            // Создаем клавиатуру с кнопкой "Отмена"
            InlineKeyboardMarkup keyboard = keyboardService.createAttachmentUploadKeyboard(eventId);
            
            log.debug("Попытка редактирования сообщения для режима загрузки вложения: " +
                    "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
            
            // Попытка редактирования сообщения
            boolean edited = messageService.tryEditMessageText(chatId, messageId, instruction, keyboard);
            
            if (!edited) {
                // Fallback: отправка нового сообщения
                log.info("Редактирование не удалось (сообщение удалено/старое), отправка нового сообщения: " +
                        "chatId={}, oldMessageId={}, eventId={}", chatId, messageId, eventId);
                
                org.telegram.telegrambots.meta.api.objects.Message newMessage = 
                        messageService.sendMessageAndGet(chatId, instruction, keyboard);
                messageId = newMessage.getMessageId();
                
                log.info("Новое сообщение отправлено (fallback): chatId={}, newMessageId={}, eventId={}", 
                        chatId, messageId, eventId);
            } else {
                log.info("Сообщение успешно отредактировано для режима загрузки вложения: " +
                        "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
            }
            
            // Устанавливаем состояние ожидания файла с актуальным messageId
            conversationStateService.setAwaitingFile(user.getId(), eventId, chatId, messageId);
            
            log.debug("Состояние ожидания файла установлено: userId={}, eventId={}, chatId={}, messageId={}", 
                    user.getId(), eventId, chatId, messageId);
            
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
            log.info("Пользователь ID={} переведен в режим ожидания файла для события ID={}", 
                    user.getId(), eventId);
            
        } catch (Exception e) {
            log.error("Ошибка при начале добавления файла: eventId={}, userId={}, error={}", 
                    eventId, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Обрабатывает отмену добавления файла.
     * 
     * @param eventId идентификатор события
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    public void handleCancelAddFile(Long eventId, User user, Long chatId, 
                                    Integer messageId, String callbackQueryId) throws Exception {
        log.debug("Отмена добавления файла для события ID={}, пользователь ID={}", 
                eventId, user.getId());
        
        try {
            // Очистка состояния ожидания файла
            conversationStateService.clearAwaitingFile(user.getId());
            log.debug("Состояние ожидания файла очищено для пользователя ID={}", user.getId());
            
            // Получение события для восстановления карточки
            Event event = eventService.getEventById(eventId);
            log.debug("Событие ID={} получено для восстановления карточки", eventId);
            
            // Получаем контекст шапки с обработкой ошибок
            ConversationStateService.EventHeaderContext headerContext = null;
            try {
                headerContext = conversationStateService.getEventHeaderContext(user.getId());
                
                if (headerContext != null) {
                    log.debug("Контекст шапки найден для пользователя ID={}: hasMyEventsHeader={}, eventCount={}", 
                            user.getId(), headerContext.isHasMyEventsHeader(), headerContext.getEventCount());
                } else {
                    log.debug("Контекст шапки не найден для пользователя ID={}", user.getId());
                }
            } catch (Exception e) {
                log.error("Ошибка при получении контекста шапки для пользователя ID={}: {}", 
                        user.getId(), e.getMessage(), e);
            }
            
            // Формируем сообщение о событии с учетом контекста шапки
            String eventMessage;
            if (headerContext != null && headerContext.isHasMyEventsHeader()) {
                log.debug("Использование buildEventMessageWithHeader для события ID={} с количеством событий: {}", 
                        eventId, headerContext.getEventCount());
                eventMessage = botMessageBuilder.buildEventMessageWithHeader(event, headerContext.getEventCount());
            } else {
                log.debug("Использование buildEventMessage для события ID={} (без шапки)", eventId);
                eventMessage = botMessageBuilder.buildEventMessage(event);
            }
            
            // Создание стандартной клавиатуры события
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, user.getId());
            
            log.debug("Попытка редактирования сообщения для восстановления карточки события: " +
                    "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
            
            // Попытка редактирования сообщения
            boolean edited = messageService.tryEditMessageText(chatId, messageId, eventMessage, keyboard);
            
            if (!edited) {
                // Fallback: отправка нового сообщения
                log.info("Редактирование не удалось (сообщение удалено/старое), отправка нового сообщения: " +
                        "chatId={}, oldMessageId={}, eventId={}", chatId, messageId, eventId);
                
                messageService.sendMessage(chatId, eventMessage, keyboard);
                
                log.info("Новое сообщение отправлено (fallback) при отмене добавления файла: " +
                        "chatId={}, eventId={}", chatId, eventId);
            } else {
                log.info("Сообщение успешно отредактировано для восстановления карточки события: " +
                        "chatId={}, messageId={}, eventId={}", chatId, messageId, eventId);
            }
            
            // Отправка callback ответа
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.CANCELLED);
            
            log.info("Добавление файла отменено для события ID={}, пользователь ID={}", 
                    eventId, user.getId());
            
        } catch (ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException e) {
            log.error("Событие ID={} не найдено при отмене добавления файла: userId={}", 
                    eventId, user.getId());
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Событие"));
        } catch (Exception e) {
            log.error("Ошибка при отмене добавления файла: eventId={}, userId={}, error={}", 
                    eventId, user.getId(), e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            throw e;
        }
    }
    
    /**
     * Формирует инструкцию по загрузке файла.
     * 
     * @return отформатированная инструкция
     */
    private String buildAttachmentUploadInstruction() {
        return "📎 *Отправьте файл для прикрепления к событию*\n\n" +
               "_Максимальный размер: 20 МБ_\n\n" +
               "Поддерживаемые типы файлов:\n" +
               "📄 Документы\n" +
               "🖼️ Фотографии\n" +
               "🎥 Видео\n" +
               "🎵 Аудио";
    }
}
