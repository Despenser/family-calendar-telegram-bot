package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.golubyatnikov.family.calendar.bot.handler.command.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Тесты для проверки описаний команд согласно требованиям 4.1-4.7.
 * 
 * <p>Этот тест проверяет, что все описания команд соответствуют спецификации
 * из документа requirements.md.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-20
 */
@DisplayName("Тесты описаний команд")
class CommandDescriptionsTest {
    
    @Test
    @DisplayName("Требование 4.1: /today должна иметь описание 'Показать события на сегодня'")
    void todayCommandShouldHaveCorrectDescription() {
        TodayCommandHandler handler = new TodayCommandHandler(null, null, null);
        assertEquals("Показать события на сегодня", handler.getDescription(),
                "Описание команды /today не соответствует требованию 4.1");
    }
    
    @Test
    @DisplayName("Требование 4.2: /week должна иметь описание 'Показать события на неделю (7 дней)'")
    void weekCommandShouldHaveCorrectDescription() {
        WeekCommandHandler handler = new WeekCommandHandler(null, null, null);
        assertEquals("Показать события на неделю (7 дней)", handler.getDescription(),
                "Описание команды /week не соответствует требованию 4.2");
    }
    
    @Test
    @DisplayName("Требование 4.3: /search должна иметь описание 'Поиск событий по тексту'")
    void searchCommandShouldHaveCorrectDescription() {
        SearchCommandHandler handler = new SearchCommandHandler(null, null, null);
        assertEquals("Поиск событий по тексту", handler.getDescription(),
                "Описание команды /search не соответствует требованию 4.3");
    }
    
    @Test
    @DisplayName("Требование 4.4: /filter должна иметь описание 'Фильтрация событий по типу'")
    void filterCommandShouldHaveCorrectDescription() {
        FilterCommandHandler handler = new FilterCommandHandler(null, null, null, null);
        assertEquals("Фильтрация событий по типу", handler.getDescription(),
                "Описание команды /filter не соответствует требованию 4.4");
    }
    
    @Test
    @DisplayName("Требование 4.5: /stats должна иметь описание 'Статистика событий за месяц'")
    void statsCommandShouldHaveCorrectDescription() {
        StatsCommandHandler handler = new StatsCommandHandler(null, null);
        assertEquals("Статистика событий за месяц", handler.getDescription(),
                "Описание команды /stats не соответствует требованию 4.5");
    }
    
    @Test
    @DisplayName("Требование 4.6: /trash должна иметь описание 'Корзина удаленных событий'")
    void trashCommandShouldHaveCorrectDescription() {
        TrashCommandHandler handler = new TrashCommandHandler(null, null, null, null);
        assertEquals("Корзина удаленных событий", handler.getDescription(),
                "Описание команды /trash не соответствует требованию 4.6");
    }
    
    @Test
    @DisplayName("Требование 4.7: /upcoming_events должна иметь описание 'Показать планы на 30 дней'")
    void upcomingEventsCommandShouldHaveCorrectDescription() {
        UpcomingEventsCommandHandler handler = new UpcomingEventsCommandHandler(null, null);
        assertEquals("Показать планы на 30 дней", handler.getDescription(),
                "Описание команды /upcoming_events не соответствует требованию 4.7");
    }
}
