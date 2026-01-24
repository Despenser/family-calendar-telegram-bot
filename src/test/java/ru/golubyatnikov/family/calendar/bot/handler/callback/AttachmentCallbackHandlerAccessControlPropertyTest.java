package ru.golubyatnikov.family.calendar.bot.handler.callback;

import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.*;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based тесты для проверки прав доступа в AttachmentCallbackHandler.
 * 
 * <p>Тесты проверяют свойство контроля доступа: для любого пользователя,
 * не являющегося создателем события, попытка добавить вложение должна
 * быть отклонена с соответствующим сообщением об ошибке.</p>
 * 
 * <p><b>Feature: attachment-upload-message-improvement, Property 11: Отказ в доступе не создателю</b></p>
 * <p><b>Validates: Requirements 8.1, 8.2, 8.3, 8.4</b></p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-22
 */
class AttachmentCallbackHandlerAccessControlPropertyTest {
    
    /**
     * Property 11: Отказ в доступе не создателю
     * 
     * <p>Для любого пользователя, не являющегося создателем события,
     * при попытке добавить вложение система должна:</p>
     * <ul>
     *   <li>Отправить callback ответ "❌ Нет прав доступа"</li>
     *   <li>Не вызывать tryEditMessageText</li>
     *   <li>Не вызывать setAwaitingFile</li>
     *   <li>Не изменять состояние ConversationState</li>
     * </ul>
     * 
     * <p>Validates: Requirements 8.1, 8.2, 8.3, 8.4</p>
     */
    @Property(tries = 100)
    @Label("Feature: attachment-upload-message-improvement, Property 11: Отказ в доступе не создателю")
    void nonCreatorCannotAddAttachment(
            @ForAll("eventProvider") Event event,
            @ForAll("userProvider") User nonCreator) throws Exception {
        
        // Arrange: убеждаемся, что nonCreator НЕ является создателем события
        Assume.that(!event.getUser().getId().equals(nonCreator.getId()));
        
        // Создаём моки сервисов
        TelegramMessageService messageService = mock(TelegramMessageService.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        EventService eventService = mock(EventService.class);
        KeyboardService keyboardService = mock(KeyboardService.class);
        ConversationStateService conversationStateService = mock(ConversationStateService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        BotMessageBuilder botMessageBuilder = mock(BotMessageBuilder.class);
        
        // Настраиваем eventService для возврата события
        when(eventService.getEventById(event.getId())).thenReturn(event);
        
        // Создаём handler
        AttachmentCallbackHandler handler = new AttachmentCallbackHandler(
                messageService,
                attachmentService,
                eventService,
                keyboardService,
                conversationStateService,
                authorizationService,
                botMessageBuilder
        );
        
        // Создаём mock CallbackQuery
        CallbackQuery callbackQuery = createMockCallbackQuery(
                "attach_file_add_" + event.getId(),
                "test_callback_id",
                nonCreator.getTelegramId(),
                123L,
                456
        );
        
        // Act: вызываем handle
        handler.handle(callbackQuery, nonCreator);
        
        // Assert: проверяем, что отправлен callback ответ с отказом в доступе
        ArgumentCaptor<String> queryIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(messageService, times(1))
                .answerCallbackQuery(queryIdCaptor.capture(), textCaptor.capture());
        
        assertThat(queryIdCaptor.getValue())
                .as("CallbackQueryId должен соответствовать исходному")
                .isEqualTo("test_callback_id");
        
        assertThat(textCaptor.getValue())
                .as("Сообщение об ошибке должно содержать отказ в доступе")
                .isEqualTo("❌ Нет прав доступа");
        
        // Assert: проверяем, что tryEditMessageText НЕ был вызван
        verify(messageService, never())
                .tryEditMessageText(anyLong(), anyInt(), anyString(), any(InlineKeyboardMarkup.class));
        
        // Assert: проверяем, что setAwaitingFile НЕ был вызван
        verify(conversationStateService, never())
                .setAwaitingFile(anyLong(), anyLong(), anyLong(), anyInt());
        
        // Assert: проверяем, что sendMessageAndGet НЕ был вызван
        verify(messageService, never())
                .sendMessageAndGet(anyLong(), anyString(), any(InlineKeyboardMarkup.class));
    }
    
    /**
     * Провайдер для генерации событий с валидными данными.
     */
    @Provide
    Arbitrary<Event> eventProvider() {
        return Combinators.combine(
                Arbitraries.longs().between(1L, 1000000L),  // eventId
                Arbitraries.longs().between(1L, 1000000L),  // creatorId
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),  // title
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(500)  // description
        ).as((eventId, creatorId, title, description) -> {
            // Создаём пользователя-создателя
            User creator = User.builder()
                    .id(creatorId)
                    .telegramId(creatorId * 1000)  // Уникальный telegramId
                    .firstName("Creator")
                    .lastName("User")
                    .username("creator_" + creatorId)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Создаём семью
            Family family = Family.builder()
                    .id(1L)
                    .name("Test Family")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            creator.setFamily(family);
            
            // Создаём событие с фиксированной датой и временем
            Event event = Event.builder()
                    .id(eventId)
                    .user(creator)
                    .family(family)
                    .title(title)
                    .description(description.isEmpty() ? null : description)
                    .eventDate(LocalDate.of(2026, 1, 22))
                    .eventTime(LocalTime.of(12, 0))
                    .status(Event.EventStatus.ACTIVE)
                    .isPersonal(false)
                    .notified(false)
                    .isMyEventsHeader(false)
                    .isTrashHeader(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            return event;
        });
    }
    
    /**
     * Провайдер для генерации пользователей с валидными данными.
     */
    @Provide
    Arbitrary<User> userProvider() {
        return Combinators.combine(
                Arbitraries.longs().between(1000001L, 2000000L),  // userId (отличается от creatorId)
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),  // firstName
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(50),  // lastName
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(50)   // username
        ).as((userId, firstName, lastName, username) -> {
            User user = User.builder()
                    .id(userId)
                    .telegramId(userId * 1000)  // Уникальный telegramId
                    .firstName(firstName)
                    .lastName(lastName.isEmpty() ? null : lastName)
                    .username(username.isEmpty() ? null : username)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Создаём семью
            Family family = Family.builder()
                    .id(1L)
                    .name("Test Family")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            user.setFamily(family);
            
            return user;
        });
    }
    
    /**
     * Создаёт mock CallbackQuery с заданными параметрами.
     */
    private CallbackQuery createMockCallbackQuery(String callbackData, String callbackQueryId,
                                                   Long telegramUserId, Long chatId, Integer messageId) {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getId()).thenReturn(callbackQueryId);
        
        org.telegram.telegrambots.meta.api.objects.User telegramUser = 
                mock(org.telegram.telegrambots.meta.api.objects.User.class);
        when(telegramUser.getId()).thenReturn(telegramUserId);
        when(callbackQuery.getFrom()).thenReturn(telegramUser);
        
        Message message = mock(Message.class);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageId()).thenReturn(messageId);
        when(callbackQuery.getMessage()).thenReturn(message);
        
        return callbackQuery;
    }
}
