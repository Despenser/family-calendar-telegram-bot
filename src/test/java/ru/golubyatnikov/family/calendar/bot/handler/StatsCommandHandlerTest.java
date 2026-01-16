package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.StatisticsService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link StatsCommandHandler}.
 * 
 * <p>Проверяет корректность обработки команды /stats и форматирования
 * сообщений со статистикой.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@ExtendWith(MockitoExtension.class)
class StatsCommandHandlerTest {
    
    @Mock
    private StatisticsService statisticsService;
    
    @Mock
    private TelegramMessageService messageService;
    
    @InjectMocks
    private StatsCommandHandler handler;
    
    @Mock
    private Message message;
    
    private User user;
    private Family family;
    
    @BeforeEach
    void setUp() {
        family = new Family();
        family.setId(1L);
        family.setName("Тестовая семья");
        
        user = new User();
        user.setId(1L);
        user.setTelegramId(123456789L);
        user.setFirstName("Тест");
        user.setFamily(family);
    }
    
    @Test
    void handle_withStatistics_returnsFormattedMessage() {
        // Given
        YearMonth currentMonth = YearMonth.now();
        StatisticsService.EventStatistics stats = new StatisticsService.EventStatistics(
            user.getId(),           // userId
            currentMonth.getYear(), // year
            currentMonth.getMonthValue(), // month
            10L, // totalEvents
            3L,  // activeEvents
            7L,  // completedEvents
            6L,  // familyEvents
            4L,  // personalEvents
            2L   // recurringEvents
        );
        
        when(message.getChatId()).thenReturn(123456789L);
        when(statisticsService.getMonthlyStatistics(
            eq(family.getId()),
            eq(user.getId()),
            eq(currentMonth.getYear()),
            eq(currentMonth.getMonthValue())
        )).thenReturn(stats);
        
        // When
        String result = handler.handle(message, user);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("📊"), "Должен содержать эмодзи статистики");
        assertTrue(result.contains("Статистика событий"), "Должен содержать заголовок");
        assertTrue(result.contains("Всего событий"), "Должен содержать общую статистику");
        assertTrue(result.contains("Завершено"), "Должен содержать количество завершенных");
        assertTrue(result.contains("Активных"), "Должен содержать количество активных");
        assertTrue(result.contains("Семейные"), "Должен содержать семейные события");
        assertTrue(result.contains("Персональные"), "Должен содержать персональные события");
        assertTrue(result.contains("Процент завершения"), "Должен содержать процент завершения");
        
        // Проверяем, что числа обернуты в bold-форматирование (звездочки для MarkdownV2)
        assertTrue(result.contains("*10*"), "Числа должны быть в bold-форматировании");
        assertTrue(result.contains("*7*"), "Числа должны быть в bold-форматировании");
        
        verify(statisticsService).getMonthlyStatistics(
            eq(family.getId()),
            eq(user.getId()),
            eq(currentMonth.getYear()),
            eq(currentMonth.getMonthValue())
        );
    }
    
    @Test
    void handle_withNoEvents_returnsMessageWithHint() {
        // Given
        YearMonth currentMonth = YearMonth.now();
        StatisticsService.EventStatistics stats = new StatisticsService.EventStatistics(
            user.getId(),
            currentMonth.getYear(),
            currentMonth.getMonthValue(),
            0L, 0L, 0L, 0L, 0L, 0L
        );
        
        when(message.getChatId()).thenReturn(123456789L);
        when(statisticsService.getMonthlyStatistics(
            anyLong(), anyLong(), anyInt(), anyInt()
        )).thenReturn(stats);
        
        // When
        String result = handler.handle(message, user);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("В этом месяце пока нет событий"), 
                  "Должен содержать подсказку о создании события");
        // Проверяем, что команда НЕ обернута в code-форматирование (кликабельна)
        // italic() экранирует underscore, поэтому проверяем обе версии
        assertTrue(result.contains("/add_event") || result.contains("/add\\_event"), 
                  "Должен содержать кликабельную команду /add_event");
        assertFalse(result.contains("`/add_event`"), 
                  "НЕ должен содержать команду в code-форматировании (backticks)");
    }
    
    @Test
    void handle_withAllEventsCompleted_returnsSuccessMessage() {
        // Given
        YearMonth currentMonth = YearMonth.now();
        StatisticsService.EventStatistics stats = new StatisticsService.EventStatistics(
            user.getId(),
            currentMonth.getYear(),
            currentMonth.getMonthValue(),
            5L, 0L, 5L, 3L, 2L, 1L
        );
        
        when(message.getChatId()).thenReturn(123456789L);
        when(statisticsService.getMonthlyStatistics(
            anyLong(), anyLong(), anyInt(), anyInt()
        )).thenReturn(stats);
        
        // When
        String result = handler.handle(message, user);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("Все события этого месяца завершены"), 
                  "Должен содержать сообщение об успехе");
        assertTrue(result.contains("🎉"), "Должен содержать эмодзи празднования");
    }
    
    @Test
    void handle_withActiveEvents_returnsActiveEventsMessage() {
        // Given
        YearMonth currentMonth = YearMonth.now();
        StatisticsService.EventStatistics stats = new StatisticsService.EventStatistics(
            user.getId(),
            currentMonth.getYear(),
            currentMonth.getMonthValue(),
            8L, 3L, 5L, 4L, 4L, 2L
        );
        
        when(message.getChatId()).thenReturn(123456789L);
        when(statisticsService.getMonthlyStatistics(
            anyLong(), anyLong(), anyInt(), anyInt()
        )).thenReturn(stats);
        
        // When
        String result = handler.handle(message, user);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("активных событий"), 
                  "Должен содержать информацию об активных событиях");
    }
    
    @Test
    void handle_withException_returnsErrorMessage() {
        // Given
        when(message.getChatId()).thenReturn(123456789L);
        when(statisticsService.getMonthlyStatistics(
            anyLong(), anyLong(), anyInt(), anyInt()
        )).thenThrow(new RuntimeException("Database error"));
        
        // When
        String result = handler.handle(message, user);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("❌"), "Должен содержать эмодзи ошибки");
        assertTrue(result.contains("Произошла ошибка"), "Должен содержать сообщение об ошибке");
    }
    
    @Test
    void getCommand_returnsStatsCommand() {
        assertEquals("/stats", handler.getCommand());
    }
    
    @Test
    void getDescription_returnsDescription() {
        assertEquals("Статистика событий за месяц", handler.getDescription());
    }
    
    @Test
    void requiresAuth_returnsTrue() {
        assertTrue(handler.requiresAuth());
    }
}
