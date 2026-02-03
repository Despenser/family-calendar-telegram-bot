package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.golubyatnikov.family.calendar.bot.handler.command.CommandHandler;
import ru.golubyatnikov.family.calendar.bot.handler.command.HelpCommandHandler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для {@link HelpCommandHandler}.
 * 
 * <p>Проверяет корректность обработки команды /help и формирования
 * справочного сообщения со списком всех доступных команд.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HelpCommandHandler Unit Tests")
class HelpCommandHandlerTest {

    private HelpCommandHandler helpCommandHandler;

    @Test
    @DisplayName("Должен вернуть корректную команду /help")
    void shouldReturnCorrectCommand() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        // When
        String command = helpCommandHandler.getCommand();

        // Then
        assertEquals("/help", command, "Команда должна быть /help");
    }

    @Test
    @DisplayName("Должен вернуть корректное описание команды")
    void shouldReturnCorrectDescription() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        // When
        String description = helpCommandHandler.getDescription();

        // Then
        assertEquals("Показать список всех команд", description);
    }

    @Test
    @DisplayName("Команда /help не должна требовать авторизации")
    void shouldNotRequireAuth() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        // When
        boolean requiresAuth = helpCommandHandler.requiresAuth();

        // Then
        assertFalse(requiresAuth, "Команда /help не должна требовать авторизации");
    }

    @Test
    @DisplayName("Должен сформировать справку со списком команд")
    void shouldGenerateHelpMessageWithCommands() {
        // Given
        CommandHandler startHandler = createMockHandler("/start", "Начать работу с ботом");
        CommandHandler addEventHandler = createMockHandler("/add_event", "Добавить новое событие");
        CommandHandler helpHandler = createMockHandler("/help", "Показать список всех команд");

        List<CommandHandler> handlers = Arrays.asList(startHandler, addEventHandler, helpHandler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        assertNotNull(response, "Ответ не должен быть null");
        assertTrue(response.contains("Справка по командам"), "Ответ должен содержать заголовок");
        // Команды теперь экранируются для MarkdownV2, поэтому _ становится \_
        // Команда /start исключена из списка
        assertFalse(response.contains("/start"), "Ответ НЕ должен содержать команду /start");
        assertTrue(response.contains("/add\\_event") || response.contains("/add_event"), 
                "Ответ должен содержать команду /add_event");
        assertTrue(response.contains("/help"), "Ответ должен содержать команду /help");
        assertFalse(response.contains("Начать работу с ботом"), "Ответ НЕ должен содержать описание команды /start");
        assertTrue(response.contains("Добавить новое событие"), "Ответ должен содержать описание команды /add_event");
    }

    @Test
    @DisplayName("Должен отсортировать команды в алфавитном порядке")
    void shouldSortCommandsAlphabetically() {
        // Given
        CommandHandler zCommand = createMockHandler("/z_command", "Z команда");
        CommandHandler aCommand = createMockHandler("/a_command", "A команда");
        CommandHandler mCommand = createMockHandler("/m_command", "M команда");

        List<CommandHandler> handlers = Arrays.asList(zCommand, aCommand, mCommand);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        // Команды экранируются для MarkdownV2, поэтому _ становится \_
        int aIndex = response.indexOf("/a\\_command");
        int mIndex = response.indexOf("/m\\_command");
        int zIndex = response.indexOf("/z\\_command");

        assertTrue(aIndex > 0, "/a_command должна присутствовать в ответе");
        assertTrue(mIndex > 0, "/m_command должна присутствовать в ответе");
        assertTrue(zIndex > 0, "/z_command должна присутствовать в ответе");
        assertTrue(aIndex < mIndex, "/a_command должна быть перед /m_command");
        assertTrue(mIndex < zIndex, "/m_command должна быть перед /z_command");
    }

    @Test
    @DisplayName("Должен обработать пустой список команд")
    void shouldHandleEmptyCommandList() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        assertNotNull(response, "Ответ не должен быть null");
        assertTrue(response.contains("команды недоступны"), 
                "Ответ должен содержать сообщение об отсутствии команд");
    }

    @Test
    @DisplayName("Должен обработать null список команд")
    void shouldHandleNullCommandList() {
        // Given
        helpCommandHandler = new HelpCommandHandler(null);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        assertNotNull(response, "Ответ не должен быть null");
        assertTrue(response.contains("команды недоступны"), 
                "Ответ должен содержать сообщение об отсутствии команд");
    }

    @Test
    @DisplayName("Должен выбросить исключение при null сообщении")
    void shouldThrowExceptionWhenMessageIsNull() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> helpCommandHandler.handle(null, null),
                "Должно быть выброшено исключение при null сообщении"
        );

        assertEquals("Сообщение не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен использовать Markdown форматирование")
    void shouldUseMarkdownFormatting() {
        // Given
        CommandHandler handler = createMockHandler("/test", "Тестовая команда");
        List<CommandHandler> handlers = Collections.singletonList(handler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        assertTrue(response.contains("*"), "Ответ должен содержать Markdown форматирование (жирный текст)");
    }

    @Test
    @DisplayName("Должен показать эмодзи замка для команд с авторизацией для неавторизованного пользователя")
    void shouldShowLockIconForAuthCommandsWhenUnauthorized() {
        // Given
        CommandHandler publicHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler authHandler = createMockHandler("/add_event", "Добавить событие", true);
        
        List<CommandHandler> handlers = Arrays.asList(publicHandler, authHandler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null); // null = неавторизован

        // Then
        assertTrue(response.contains("🔒"), "Ответ должен содержать эмодзи замка");
        assertTrue(response.contains("🔒 /add\\_event") || response.contains("🔒 /add_event"), 
                "Команда с авторизацией должна быть помечена замком");
        // Команда /start исключена из списка, поэтому не проверяем её наличие
        assertFalse(response.contains("/start"), 
                "Команда /start не должна отображаться в списке");
    }

    @Test
    @DisplayName("Не должен показывать эмодзи замка для авторизованного пользователя")
    void shouldNotShowLockIconForAuthorizedUser() {
        // Given
        CommandHandler publicHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler authHandler = createMockHandler("/add_event", "Добавить событие", true);
        
        List<CommandHandler> handlers = Arrays.asList(publicHandler, authHandler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();
        ru.golubyatnikov.family.calendar.bot.model.User user = 
                mock(ru.golubyatnikov.family.calendar.bot.model.User.class);
        when(user.hasFamily()).thenReturn(true); // Пользователь состоит в семье

        // When
        String response = helpCommandHandler.handle(message, user); // user != null && user.hasFamily() = авторизован

        // Then
        assertFalse(response.contains("🔒"), "Ответ не должен содержать эмодзи замка для авторизованного пользователя");
        // Команда /start исключена из списка
        assertFalse(response.contains("/start"), "Команда /start не должна отображаться в списке");
        assertTrue(response.contains("/add\\_event") || response.contains("/add_event"), 
                "Ответ должен содержать команду /add_event");
    }

    @Test
    @DisplayName("Должен показать информацию о регистрации для неавторизованного пользователя")
    void shouldShowRegistrationInfoForUnauthorizedUser() {
        // Given
        CommandHandler handler = createMockHandler("/test", "Тестовая команда", true);
        List<CommandHandler> handlers = Collections.singletonList(handler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null); // null = неавторизован

        // Then
        assertTrue(response.contains("не зарегистрированы"), 
                "Ответ должен содержать информацию о том, что пользователь не зарегистрирован");
        assertTrue(response.contains("требуют регистрации"), 
                "Ответ должен содержать информацию о командах, требующих регистрации");
        assertTrue(response.contains("администратору"), 
                "Ответ должен содержать информацию о том, как получить доступ");
    }

    @Test
    @DisplayName("Не должен показывать информацию о регистрации для авторизованного пользователя")
    void shouldNotShowRegistrationInfoForAuthorizedUser() {
        // Given
        CommandHandler handler = createMockHandler("/test", "Тестовая команда", false);
        List<CommandHandler> handlers = Collections.singletonList(handler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();
        ru.golubyatnikov.family.calendar.bot.model.User user = 
                mock(ru.golubyatnikov.family.calendar.bot.model.User.class);
        when(user.hasFamily()).thenReturn(true); // Пользователь состоит в семье

        // When
        String response = helpCommandHandler.handle(message, user); // user != null && user.hasFamily() = авторизован

        // Then
        assertFalse(response.contains("не зарегистрированы"), 
                "Ответ не должен содержать информацию о регистрации для авторизованного пользователя");
        assertFalse(response.contains("требуют регистрации"), 
                "Ответ не должен содержать информацию о командах, требующих регистрации");
    }

    @Test
    @DisplayName("Должен показать описание возможностей бота")
    void shouldShowBotCapabilitiesDescription() {
        // Given
        CommandHandler handler = createMockHandler("/test", "Тестовая команда");
        List<CommandHandler> handlers = Collections.singletonList(handler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        assertTrue(response.contains("Семейный календарь"), 
                "Ответ должен содержать название бота");
        assertTrue(response.contains("организовать события"), 
                "Ответ должен содержать описание возможностей");
    }

    // ========== Тесты для проверки отображения эмодзи (Требования: 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5) ==========

    @Test
    @DisplayName("Должен вернуть корректные тематические эмодзи для всех известных команд")
    void shouldReturnCorrectEmojiForAllKnownCommands() {
        // Given
        CommandHandler startHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler helpHandler = createMockHandler("/help", "Показать справку", false);
        CommandHandler addEventHandler = createMockHandler("/add_event", "Добавить событие", true);
        CommandHandler myEventsHandler = createMockHandler("/my_events", "Мои события", true);
        CommandHandler upcomingEventsHandler = createMockHandler("/upcoming_events", "Предстоящие события", true);
        CommandHandler todayHandler = createMockHandler("/today", "События на сегодня", true);
        CommandHandler weekHandler = createMockHandler("/week", "События на неделю", true);
        CommandHandler searchHandler = createMockHandler("/search", "Поиск событий", true);
        CommandHandler filterHandler = createMockHandler("/filter", "Фильтр событий", true);
        CommandHandler statsHandler = createMockHandler("/stats", "Статистика", true);
        CommandHandler trashHandler = createMockHandler("/trash", "Корзина", true);
        
        List<CommandHandler> handlers = Arrays.asList(
            startHandler, helpHandler, addEventHandler, myEventsHandler, 
            upcomingEventsHandler, todayHandler, weekHandler, searchHandler, 
            filterHandler, statsHandler, trashHandler
        );
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();
        ru.golubyatnikov.family.calendar.bot.model.User user = 
                mock(ru.golubyatnikov.family.calendar.bot.model.User.class);
        when(user.hasFamily()).thenReturn(true); // Авторизованный пользователь

        // When
        String response = helpCommandHandler.handle(message, user);

        // Then
        // Проверяем наличие всех тематических эмодзи (кроме /start, который исключен)
        assertFalse(response.contains("🚀"), "Ответ НЕ должен содержать эмодзи 🚀, так как /start исключен");
        assertTrue(response.contains("📚"), "Ответ должен содержать эмодзи 📚 для /help");
        assertTrue(response.contains("➕"), "Ответ должен содержать эмодзи ➕ для /add_event");
        assertTrue(response.contains("📝"), "Ответ должен содержать эмодзи 📝 для /my_events");
        assertTrue(response.contains("📋"), "Ответ должен содержать эмодзи 📋 для /upcoming_events");
        assertTrue(response.contains("📅"), "Ответ должен содержать эмодзи 📅 для /today");
        assertTrue(response.contains("📆"), "Ответ должен содержать эмодзи 📆 для /week");
        assertTrue(response.contains("🔍"), "Ответ должен содержать эмодзи 🔍 для /search");
        assertTrue(response.contains("🫧"), "Ответ должен содержать эмодзи 🫧 для /filter");
        assertTrue(response.contains("📊"), "Ответ должен содержать эмодзи 📊 для /stats");
        assertTrue(response.contains("🗑️"), "Ответ должен содержать эмодзи 🗑️ для /trash");
        
        // Проверяем, что эмодзи замка НЕТ для авторизованного пользователя
        assertFalse(response.contains("🔒"), 
                "Ответ не должен содержать эмодзи замка для авторизованного пользователя");
    }

    @Test
    @DisplayName("Должен вернуть пустую строку для неизвестной команды")
    void shouldReturnEmptyStringForUnknownCommand() {
        // Given
        CommandHandler unknownHandler = createMockHandler("/unknown_command", "Неизвестная команда", false);
        
        List<CommandHandler> handlers = Collections.singletonList(unknownHandler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();
        ru.golubyatnikov.family.calendar.bot.model.User user = 
                mock(ru.golubyatnikov.family.calendar.bot.model.User.class);
        when(user.hasFamily()).thenReturn(true); // Авторизованный пользователь

        // When
        String response = helpCommandHandler.handle(message, user);

        // Then
        // Для неизвестной команды не должно быть тематического эмодзи
        // Команда должна отображаться без эмодзи (кроме стандартных эмодзи в заголовке)
        assertTrue(response.contains("/unknown\\_command") || response.contains("/unknown_command"), 
                "Ответ должен содержать неизвестную команду");
        
        // Проверяем, что команда отображается без тематического эмодзи перед ней
        // (т.е. нет эмодзи между началом строки и командой, кроме возможных пробелов)
        String[] lines = response.split("\n");
        boolean foundCommandWithoutEmoji = false;
        for (String line : lines) {
            if (line.contains("/unknown")) {
                // Проверяем, что перед командой нет эмодзи (только пробелы или начало строки)
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("/unknown")) {
                    foundCommandWithoutEmoji = true;
                    break;
                }
            }
        }
        assertTrue(foundCommandWithoutEmoji, 
                "Неизвестная команда должна отображаться без тематического эмодзи");
    }

    @Test
    @DisplayName("Должен отображать эмодзи замка только для команд с авторизацией у неавторизованных пользователей")
    void shouldShowLockEmojiOnlyForAuthCommandsWhenUnauthorized() {
        // Given
        CommandHandler publicHandler1 = createMockHandler("/start", "Начать работу", false);
        CommandHandler publicHandler2 = createMockHandler("/help", "Показать справку", false);
        CommandHandler authHandler1 = createMockHandler("/add_event", "Добавить событие", true);
        CommandHandler authHandler2 = createMockHandler("/my_events", "Мои события", true);
        
        List<CommandHandler> handlers = Arrays.asList(
            publicHandler1, publicHandler2, authHandler1, authHandler2
        );
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null); // null = неавторизован

        // Then
        // Проверяем наличие эмодзи замка
        assertTrue(response.contains("🔒"), 
                "Ответ должен содержать эмодзи замка для команд с авторизацией");
        
        // Проверяем, что команды с авторизацией помечены замком
        assertTrue(response.contains("🔒 /add\\_event") || response.contains("🔒 /add_event"), 
                "Команда /add_event должна быть помечена замком");
        assertTrue(response.contains("🔒 /my\\_events") || response.contains("🔒 /my_events"), 
                "Команда /my_events должна быть помечена замком");
        
        // Команда /start исключена из списка
        assertFalse(response.contains("/start"), 
                "Команда /start не должна отображаться в списке");
        
        // Проверяем, что публичная команда /help НЕ помечена замком
        assertFalse(response.contains("🔒 /help"), 
                "Публичная команда /help не должна быть помечена замком");
    }

    @Test
    @DisplayName("Должен отображать тематические эмодзи для всех команд у авторизованных пользователей")
    void shouldShowThematicEmojiForAllCommandsWhenAuthorized() {
        // Given
        CommandHandler startHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler addEventHandler = createMockHandler("/add_event", "Добавить событие", true);
        CommandHandler myEventsHandler = createMockHandler("/my_events", "Мои события", true);
        CommandHandler searchHandler = createMockHandler("/search", "Поиск событий", true);
        
        List<CommandHandler> handlers = Arrays.asList(
            startHandler, addEventHandler, myEventsHandler, searchHandler
        );
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();
        ru.golubyatnikov.family.calendar.bot.model.User user = 
                mock(ru.golubyatnikov.family.calendar.bot.model.User.class);
        when(user.hasFamily()).thenReturn(true); // Авторизованный пользователь

        // When
        String response = helpCommandHandler.handle(message, user);

        // Then
        // Проверяем наличие тематических эмодзи (кроме /start, который исключен)
        assertFalse(response.contains("/start"), 
                "Команда /start не должна отображаться в списке");
        assertTrue(response.contains("➕ /add\\_event") || response.contains("➕ /add_event") || response.contains("➕ \\/add"), 
                "Команда /add_event должна иметь тематический эмодзи ➕");
        assertTrue(response.contains("📝 /my\\_events") || response.contains("📋 /my_events") || response.contains("📋 \\/my"), 
                "Команда /my_events должна иметь тематический эмодзи 📋");
        assertTrue(response.contains("🔍 /search") || response.contains("🔍 \\/search"), 
                "Команда /search должна иметь тематический эмодзи 🔍");
        
        // Проверяем, что эмодзи замка НЕТ
        assertFalse(response.contains("🔒"), 
                "Ответ не должен содержать эмодзи замка для авторизованного пользователя");
    }

    @Test
    @DisplayName("Должен корректно маппить каждую команду на соответствующий эмодзи")
    void shouldCorrectlyMapEachCommandToItsEmoji() {
        // Given
        // Создаем по одной команде каждого типа
        CommandHandler startHandler = createMockHandler("/start", "Начать", false);
        CommandHandler helpHandler = createMockHandler("/help", "Справка", false);
        CommandHandler addEventHandler = createMockHandler("/add_event", "Добавить", true);
        CommandHandler myEventsHandler = createMockHandler("/my_events", "Мои события", true);
        CommandHandler upcomingHandler = createMockHandler("/upcoming_events", "Предстоящие", true);
        CommandHandler todayHandler = createMockHandler("/today", "Сегодня", true);
        CommandHandler weekHandler = createMockHandler("/week", "Неделя", true);
        CommandHandler searchHandler = createMockHandler("/search", "Поиск", true);
        CommandHandler filterHandler = createMockHandler("/filter", "Фильтр", true);
        CommandHandler statsHandler = createMockHandler("/stats", "Статистика", true);
        CommandHandler trashHandler = createMockHandler("/trash", "Корзина", true);
        
        List<CommandHandler> handlers = Arrays.asList(
            startHandler, helpHandler, addEventHandler, myEventsHandler, upcomingHandler,
            todayHandler, weekHandler, searchHandler, filterHandler, statsHandler, trashHandler
        );
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();
        ru.golubyatnikov.family.calendar.bot.model.User user = 
                mock(ru.golubyatnikov.family.calendar.bot.model.User.class);
        when(user.hasFamily()).thenReturn(true);

        // When
        String response = helpCommandHandler.handle(message, user);

        // Then
        // Проверяем точное соответствие команд и эмодзи согласно таблице из документа проектирования
        // Команда /start исключена из списка
        assertFalse(response.contains("🚀"), 
                "Ответ НЕ должен содержать эмодзи 🚀, так как /start исключен");
        assertFalse(response.contains("/start"), 
                "Команда /start не должна отображаться в списке");
        assertTrue(response.contains("📚") && response.contains("/help"), 
                "/help должен иметь эмодзи 📚");
        assertTrue(response.contains("➕") && (response.contains("/add_event") || response.contains("/add\\_event")), 
                "/add_event должен иметь эмодзи ➕");
        assertTrue(response.contains("📝") && (response.contains("/my_events") || response.contains("/my\\_events")), 
                "/my_events должен иметь эмодзи 📝");
        assertTrue(response.contains("📋") && (response.contains("/upcoming_events") || response.contains("/upcoming\\_events")), 
                "/upcoming_events должен иметь эмодзи 📋");
        assertTrue(response.contains("📅") && response.contains("/today"), 
                "/today должен иметь эмодзи 📅");
        assertTrue(response.contains("📆") && response.contains("/week"), 
                "/week должен иметь эмодзи 📆");
        assertTrue(response.contains("🔍") && response.contains("/search"), 
                "/search должен иметь эмодзи 🔍");
        assertTrue(response.contains("🫧") && response.contains("/filter"), 
                "/filter должен иметь эмодзи 🫧");
        assertTrue(response.contains("📊") && response.contains("/stats"), 
                "/stats должен иметь эмодзи 📊");
        assertTrue(response.contains("🗑️") && response.contains("/trash"), 
                "/trash должен иметь эмодзи 🗑️");
    }

    // ========== Тесты для проверки корректности авторизации (Требования: 1.1, 1.2, 1.3, 1.4) ==========

    @Test
    @DisplayName("Пользователь с семьей должен быть авторизован")
    void shouldConsiderUserWithFamilyAsAuthorized() {
        // Given
        CommandHandler publicHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler authHandler = createMockHandler("/add_event", "Добавить событие", true);
        
        List<CommandHandler> handlers = Arrays.asList(publicHandler, authHandler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();
        
        // Создаем пользователя с семьей
        ru.golubyatnikov.family.calendar.bot.model.User user = 
                mock(ru.golubyatnikov.family.calendar.bot.model.User.class);
        when(user.hasFamily()).thenReturn(true);

        // When
        String response = helpCommandHandler.handle(message, user);

        // Then
        // Проверяем, что пользователь считается авторизованным:
        // 1. Нет эмодзи замка (🔒) для команд с авторизацией
        assertFalse(response.contains("🔒"), 
                "Для авторизованного пользователя не должно быть эмодзи замка");
        
        // 2. Нет информации о регистрации
        assertFalse(response.contains("не зарегистрированы"), 
                "Для авторизованного пользователя не должно быть информации о регистрации");
        assertFalse(response.contains("требуют регистрации"), 
                "Для авторизованного пользователя не должно быть информации о командах, требующих регистрации");
        
        // 3. Присутствуют тематические эмодзи (например, ➕ для /add_event)
        assertTrue(response.contains("➕") || response.contains("🚀") || response.contains("📚"), 
                "Для авторизованного пользователя должны быть тематические эмодзи");
        
        // Проверяем, что метод hasFamily() был вызван
        verify(user, atLeastOnce()).hasFamily();
    }

    @Test
    @DisplayName("Пользователь без семьи должен быть неавторизован")
    void shouldConsiderUserWithoutFamilyAsUnauthorized() {
        // Given
        CommandHandler publicHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler authHandler = createMockHandler("/add_event", "Добавить событие", true);
        
        List<CommandHandler> handlers = Arrays.asList(publicHandler, authHandler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();
        
        // Создаем пользователя БЕЗ семьи (family = null)
        ru.golubyatnikov.family.calendar.bot.model.User user = 
                mock(ru.golubyatnikov.family.calendar.bot.model.User.class);
        when(user.hasFamily()).thenReturn(false);

        // When
        String response = helpCommandHandler.handle(message, user);

        // Then
        // Проверяем, что пользователь считается неавторизованным:
        // 1. Есть эмодзи замка (🔒) для команд с авторизацией
        assertTrue(response.contains("🔒"), 
                "Для неавторизованного пользователя должен быть эмодзи замка");
        
        // 2. Есть информация о регистрации
        assertTrue(response.contains("не зарегистрированы"), 
                "Для неавторизованного пользователя должна быть информация о регистрации");
        assertTrue(response.contains("требуют регистрации"), 
                "Для неавторизованного пользователя должна быть информация о командах, требующих регистрации");
        
        // 3. Команда с авторизацией помечена замком
        assertTrue(response.contains("🔒 /add\\_event") || response.contains("🔒 /add_event"), 
                "Команда с авторизацией должна быть помечена замком");
        
        // 4. Публичная команда /start исключена из списка
        assertFalse(response.contains("/start"), 
                "Команда /start не должна отображаться в списке");
        
        // Проверяем, что метод hasFamily() был вызван
        verify(user, atLeastOnce()).hasFamily();
    }

    @Test
    @DisplayName("Null пользователь должен быть неавторизован")
    void shouldConsiderNullUserAsUnauthorized() {
        // Given
        CommandHandler publicHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler authHandler = createMockHandler("/add_event", "Добавить событие", true);
        
        List<CommandHandler> handlers = Arrays.asList(publicHandler, authHandler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null); // null пользователь

        // Then
        // Проверяем, что null пользователь считается неавторизованным:
        // 1. Есть эмодзи замка (🔒) для команд с авторизацией
        assertTrue(response.contains("🔒"), 
                "Для null пользователя должен быть эмодзи замка");
        
        // 2. Есть информация о регистрации
        assertTrue(response.contains("не зарегистрированы"), 
                "Для null пользователя должна быть информация о регистрации");
        assertTrue(response.contains("требуют регистрации"), 
                "Для null пользователя должна быть информация о командах, требующих регистрации");
        
        // 3. Команда с авторизацией помечена замком
        assertTrue(response.contains("🔒 /add\\_event") || response.contains("🔒 /add_event"), 
                "Команда с авторизацией должна быть помечена замком");
        
        // 4. Публичная команда /start исключена из списка
        assertFalse(response.contains("/start"), 
                "Команда /start не должна отображаться в списке");
    }

    // ========== Тесты для проверки формирования списка команд (Требования: 2.1, 2.2, 2.3, 2.4) ==========

    @Test
    @DisplayName("Должен сформировать список с тематическими эмодзи для авторизованного пользователя")
    void shouldBuildCommandsListWithThematicEmojiForAuthorizedUser() {
        // Given
        CommandHandler startHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler addEventHandler = createMockHandler("/add_event", "Добавить событие", true);
        CommandHandler myEventsHandler = createMockHandler("/my_events", "Мои события", true);
        CommandHandler searchHandler = createMockHandler("/search", "Поиск событий", true);
        
        List<CommandHandler> handlers = Arrays.asList(
            startHandler, addEventHandler, myEventsHandler, searchHandler
        );
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();
        ru.golubyatnikov.family.calendar.bot.model.User user = 
                mock(ru.golubyatnikov.family.calendar.bot.model.User.class);
        when(user.hasFamily()).thenReturn(true); // Авторизованный пользователь

        // When
        String response = helpCommandHandler.handle(message, user);

        // Then
        // Проверяем, что все команды имеют тематические эмодзи (кроме /start, который исключен)
        assertFalse(response.contains("/start"), 
                "Команда /start не должна отображаться в списке");
        assertTrue(response.contains("➕") && (response.contains("/add_event") || response.contains("/add\\_event")), 
                "Команда /add_event должна иметь тематический эмодзи ➕");
        assertTrue(response.contains("📝") && (response.contains("/my_events") || response.contains("/my\\_events")), 
                "Команда /my_events должна иметь тематический эмодзи 📝");
        assertTrue(response.contains("🔍") && response.contains("/search"), 
                "Команда /search должна иметь тематический эмодзи 🔍");
        
        // Проверяем, что эмодзи замка НЕТ
        assertFalse(response.contains("🔒"), 
                "Для авторизованного пользователя не должно быть эмодзи замка");
        
        // Проверяем, что все команды присутствуют в списке (кроме /start)
        assertTrue(response.contains("/add_event") || response.contains("/add\\_event"), 
                "Список должен содержать команду /add_event");
        assertTrue(response.contains("/my_events") || response.contains("/my\\_events"), 
                "Список должен содержать команду /my_events");
        assertTrue(response.contains("/search"), "Список должен содержать команду /search");
        
        // Проверяем, что описания команд присутствуют (кроме /start)
        assertFalse(response.contains("Начать работу"), "Список НЕ должен содержать описание команды /start");
        assertTrue(response.contains("Добавить событие"), "Список должен содержать описание команды /add_event");
        assertTrue(response.contains("Мои события"), "Список должен содержать описание команды /my_events");
        assertTrue(response.contains("Поиск событий"), "Список должен содержать описание команды /search");
    }

    @Test
    @DisplayName("Должен сформировать список с эмодзи замка для неавторизованного пользователя")
    void shouldBuildCommandsListWithLockEmojiForUnauthorizedUser() {
        // Given
        CommandHandler startHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler helpHandler = createMockHandler("/help", "Показать справку", false);
        CommandHandler addEventHandler = createMockHandler("/add_event", "Добавить событие", true);
        CommandHandler myEventsHandler = createMockHandler("/my_events", "Мои события", true);
        CommandHandler searchHandler = createMockHandler("/search", "Поиск событий", true);
        
        List<CommandHandler> handlers = Arrays.asList(
            startHandler, helpHandler, addEventHandler, myEventsHandler, searchHandler
        );
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null); // null = неавторизован

        // Then
        // Проверяем, что команды с авторизацией помечены замком
        assertTrue(response.contains("🔒"), 
                "Для неавторизованного пользователя должен быть эмодзи замка");
        assertTrue(response.contains("🔒 /add\\_event") || response.contains("🔒 /add_event"), 
                "Команда /add_event должна быть помечена замком");
        assertTrue(response.contains("🔒 /my\\_events") || response.contains("🔒 /my_events"), 
                "Команда /my_events должна быть помечена замком");
        assertTrue(response.contains("🔒 /search") || response.contains("🔒 \\/search"), 
                "Команда /search должна быть помечена замком");
        
        // Команда /start исключена из списка
        assertFalse(response.contains("/start"), 
                "Команда /start не должна отображаться в списке");
        
        // Проверяем, что публичная команда /help НЕ помечена замком
        assertFalse(response.contains("🔒 /help"), 
                "Публичная команда /help не должна быть помечена замком");
        
        // Проверяем, что тематических эмодзи НЕТ (только замки для auth команд)
        assertFalse(response.contains("🚀"), 
                "Для неавторизованного пользователя не должно быть эмодзи 🚀 (команда /start исключена)");
        assertFalse(response.contains("📚 /help"), 
                "Для неавторизованного пользователя не должно быть тематического эмодзи у /help");
        
        // Проверяем, что все команды присутствуют в списке (кроме /start)
        assertTrue(response.contains("/help"), "Список должен содержать команду /help");
        assertTrue(response.contains("/add_event") || response.contains("/add\\_event"), 
                "Список должен содержать команду /add_event");
        assertTrue(response.contains("/my_events") || response.contains("/my\\_events"), 
                "Список должен содержать команду /my_events");
        assertTrue(response.contains("/search"), "Список должен содержать команду /search");
    }

    @Test
    @DisplayName("Должен проверить отсутствие эмодзи замка у команд, не требующих авторизации")
    void shouldNotShowLockEmojiForPublicCommands() {
        // Given
        CommandHandler startHandler = createMockHandler("/start", "Начать работу", false);
        CommandHandler helpHandler = createMockHandler("/help", "Показать справку", false);
        CommandHandler addEventHandler = createMockHandler("/add_event", "Добавить событие", true);
        CommandHandler myEventsHandler = createMockHandler("/my_events", "Мои события", true);
        
        List<CommandHandler> handlers = Arrays.asList(
            startHandler, helpHandler, addEventHandler, myEventsHandler
        );
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null); // null = неавторизован

        // Then
        // Команда /start исключена из списка
        assertFalse(response.contains("/start"), 
                "Команда /start не должна отображаться в списке");
        
        // Проверяем, что публичная команда /help НЕ имеет эмодзи замка
        assertFalse(response.contains("🔒 /help"), 
                "Команда /help не требует авторизации и не должна иметь эмодзи замка");
        
        // Проверяем, что команды с авторизацией ИМЕЮТ эмодзи замка
        assertTrue(response.contains("🔒 /add\\_event") || response.contains("🔒 /add_event"), 
                "Команда /add_event требует авторизации и должна иметь эмодзи замка");
        assertTrue(response.contains("🔒 /my\\_events") || response.contains("🔒 /my_events"), 
                "Команда /my_events требует авторизации и должна иметь эмодзи замка");
        
        // Проверяем, что публичная команда /help отображается без эмодзи
        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            // Проверяем строки, содержащие публичную команду /help
            if (trimmedLine.contains("/help") && trimmedLine.contains("Показать справку")) {
                // Это строка с командой, проверяем, что перед /help нет замка
                assertFalse(trimmedLine.contains("🔒 /help"), 
                        "Публичная команда /help не должна иметь эмодзи замка");
            }
        }
        
        // Проверяем, что все команды присутствуют в списке (кроме /start)
        assertTrue(response.contains("/help"), "Список должен содержать команду /help");
        assertTrue(response.contains("/add_event") || response.contains("/add\\_event"), 
                "Список должен содержать команду /add_event");
        assertTrue(response.contains("/my_events") || response.contains("/my\\_events"), 
                "Список должен содержать команду /my_events");
    }

    /**
     * Создает mock обработчик команды с заданной командой и описанием.
     *
     * @param command команда
     * @param description описание
     * @return mock обработчик
     */
    private CommandHandler createMockHandler(String command, String description) {
        CommandHandler handler = mock(CommandHandler.class);
        lenient().when(handler.getCommand()).thenReturn(command);
        lenient().when(handler.getDescription()).thenReturn(description);
        lenient().when(handler.requiresAuth()).thenReturn(false); // По умолчанию не требует авторизации
        return handler;
    }

    /**
     * Создает mock обработчик команды с заданной командой, описанием и требованием авторизации.
     *
     * @param command команда
     * @param description описание
     * @param requiresAuth требуется ли авторизация
     * @return mock обработчик
     */
    private CommandHandler createMockHandler(String command, String description, boolean requiresAuth) {
        CommandHandler handler = mock(CommandHandler.class);
        lenient().when(handler.getCommand()).thenReturn(command);
        lenient().when(handler.getDescription()).thenReturn(description);
        lenient().when(handler.requiresAuth()).thenReturn(requiresAuth);
        return handler;
    }

    /**
     * Создает mock сообщения с базовыми настройками.
     *
     * @return mock сообщение
     */
    private Message createMockMessage() {
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User telegramUser = 
                mock(org.telegram.telegrambots.meta.api.objects.User.class);
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        
        return message;
    }
}
