package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.handler.command.SearchCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.search.SearchService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для проверки реализации CallbackHandler в SearchCommandHandler.
 * 
 * <p>Эти тесты проверяют, что SearchCommandHandler корректно реализует интерфейс
 * CallbackHandler и может обрабатывать callback "search_again:".</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-26
 */
@ExtendWith(MockitoExtension.class)
class SearchCommandHandlerCallbackTest {
    
    @Mock
    private SearchService searchService;
    
    @Mock
    private TelegramMessageService messageService;
    
    @Mock
    private ConversationStateService conversationStateService;
    
    private SearchCommandHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new SearchCommandHandler(searchService, messageService, conversationStateService);
    }
    
    /**
     * Проверяет, что SearchCommandHandler возвращает правильный префикс.
     */
    @Test
    void testGetPrefix() {
        // Act
        CallbackPrefix prefix = handler.getPrefix();
        
        // Assert
        assertThat(prefix)
            .as("SearchCommandHandler должен возвращать CallbackPrefix.SEARCH_AGAIN")
            .isEqualTo(CallbackPrefix.SEARCH_AGAIN);
    }
    
    /**
     * Проверяет, что SearchCommandHandler может обработать callback "search_again:".
     */
    @Test
    void testCanHandleSearchAgainCallback() {
        // Arrange
        String callbackData = "search_again:";
        
        // Act
        boolean canHandle = handler.canHandle(callbackData);
        
        // Assert
        assertThat(canHandle)
            .as("SearchCommandHandler должен canHandle('search_again:') == true")
            .isTrue();
    }
    
    /**
     * Проверяет, что SearchCommandHandler не обрабатывает другие callback.
     */
    @Test
    void testCannotHandleOtherCallbacks() {
        // Arrange
        String[] otherCallbacks = {
            "view_event_123",
            "edit_event_456",
            "filter_today",
            "date_2026-01-26",
            "calendar_2026-01"
        };
        
        // Act & Assert
        for (String callbackData : otherCallbacks) {
            boolean canHandle = handler.canHandle(callbackData);
            
            assertThat(canHandle)
                .as("SearchCommandHandler не должен обрабатывать '%s'", callbackData)
                .isFalse();
        }
    }
    
    /**
     * Проверяет, что handle() редактирует сообщение и устанавливает состояние ожидания.
     */
    @Test
    void testHandleSearchAgainCallback() throws Exception {
        // Arrange
        Long chatId = 123L;
        Integer messageId = 456;
        Long userId = 789L;
        
        User user = new User();
        user.setId(userId);
        
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        
        when(callbackQuery.getMessage()).thenReturn(message);
        when(callbackQuery.getId()).thenReturn("callback_123");
        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageId()).thenReturn(messageId);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        // Проверяем, что сообщение было отредактировано
        verify(messageService).editMessageText(
            eq(chatId), 
            eq(messageId), 
            contains("Поиск событий"), 
            isNull()
        );
        
        // Проверяем, что состояние ожидания было установлено
        verify(conversationStateService).setAwaitingSearchQuery(userId, chatId, messageId);
        
        // Проверяем, что был отправлен ответ на callback query
        verify(messageService).answerCallbackQuery(eq("callback_123"), isNull());
    }
    
    /**
     * Проверяет, что handle() обрабатывает исключения корректно.
     */
    @Test
    void testHandleSearchAgainCallbackWithError() throws Exception {
        // Arrange
        Long chatId = 123L;
        Integer messageId = 456;
        Long userId = 789L;
        
        User user = new User();
        user.setId(userId);
        
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        
        when(callbackQuery.getMessage()).thenReturn(message);
        when(callbackQuery.getId()).thenReturn("callback_123");
        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageId()).thenReturn(messageId);
        
        // Симулируем ошибку при редактировании сообщения
        doThrow(new RuntimeException("Test error"))
            .when(messageService).editMessageText(any(), any(), any(), any());
        
        // Act & Assert
        try {
            handler.handle(callbackQuery, user);
        } catch (Exception e) {
            // Ожидаем, что исключение будет выброшено
            assertThat(e)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test error");
        }
        
        // Проверяем, что был отправлен ответ на callback query с сообщением об ошибке
        verify(messageService).answerCallbackQuery(
            eq("callback_123"), 
            contains("Произошла ошибка")
        );
    }
}
