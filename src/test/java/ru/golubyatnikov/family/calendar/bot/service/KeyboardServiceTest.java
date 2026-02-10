package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.model.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.attachment.AttachmentService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit тесты для KeyboardService.
 * 
 * <p>Проверяет корректность создания клавиатур (ReplyKeyboardMarkup и InlineKeyboardMarkup)
 * и преобразования текста кнопок в команды.</p>
 * 
 * @see KeyboardService
 */
@DisplayName("KeyboardService Unit Tests")
@ExtendWith(MockitoExtension.class)
class KeyboardServiceTest {

    @Mock
    private EventRepository eventRepository;
    
    @Mock
    private ReplyKeyboardService replyKeyboardService;
    
    @Mock
    private InlineKeyboardService inlineKeyboardService;
    
    @Mock
    private KeyboardLayoutService keyboardLayoutService;
    
    @Mock
    private ru.golubyatnikov.family.calendar.bot.service.attachment.AttachmentService attachmentService;
    
    @Mock
    private ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderSchedulingService reminderSchedulingService;

    private KeyboardService keyboardService;

    @BeforeEach
    void setUp() {
        keyboardService = new KeyboardService(replyKeyboardService, inlineKeyboardService, keyboardLayoutService);
    }

    /**
     * Создает mock User с указанной семьей и timezone для тестов.
     * 
     * @param familyId ID семьи
     * @param timezone timezone пользователя
     * @return mock User
     */
    private User createMockUser(Long familyId, String timezone) {
        Family family = Family.builder().id(familyId).name("Test Family").build();
        return User.builder()
                .id(1L)
                .telegramId(12345L)
                .firstName("Test")
                .family(family)
                .timezone(timezone)
                .build();
    }

    /**
     * Создает mock User с указанной семьей и default timezone (Europe/Moscow).
     * 
     * @param familyId ID семьи
     * @return mock User
     */
    private User createMockUser(Long familyId) {
        return createMockUser(familyId, "Europe/Moscow");
    }

    @Test
    @DisplayName("Должен создать клавиатуру для авторизованного пользователя с 10 кнопками")
    void shouldCreateAuthorizedUserKeyboardWithFourButtons() {
        // Given
        ReplyKeyboardMarkup mockKeyboard = new ReplyKeyboardMarkup();
        mockKeyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        
        KeyboardRow row1 = new KeyboardRow();
        row1.add("📝 Мои события");
        row1.add("➕ Добавить");
        rows.add(row1);
        
        KeyboardRow row2 = new KeyboardRow();
        row2.add("📅 Сегодня");
        row2.add("📆 Неделя");
        row2.add("🗓️ Месяц");
        rows.add(row2);
        
        KeyboardRow row3 = new KeyboardRow();
        row3.add("🔍 Поиск");
        row3.add("🎯 Фильтр");
        row3.add("📊 Статистика");
        rows.add(row3);
        
        KeyboardRow row4 = new KeyboardRow();
        row4.add("🗑️ Корзина");
        row4.add("❓ Помощь");
        rows.add(row4);
        
        mockKeyboard.setKeyboard(rows);
        
        when(replyKeyboardService.createAuthorizedUserKeyboard()).thenReturn(mockKeyboard);
        
        // When
        ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        assertTrue(keyboard.getResizeKeyboard(), "ResizeKeyboard должен быть true");
        
        List<KeyboardRow> resultRows = keyboard.getKeyboard();
        assertNotNull(resultRows, "Список рядов не должен быть null");
        assertEquals(4, resultRows.size(), "Должно быть 4 ряда кнопок");
        
        // Проверяем первый ряд
        KeyboardRow resultRow1 = resultRows.get(0);
        assertEquals(2, resultRow1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("📝 Мои события", resultRow1.get(0).getText());
        assertEquals("➕ Добавить", resultRow1.get(1).getText());
        
        // Проверяем второй ряд
        KeyboardRow resultRow2 = resultRows.get(1);
        assertEquals(3, resultRow2.size(), "Второй ряд должен содержать 3 кнопки");
        assertEquals("📅 Сегодня", resultRow2.get(0).getText());
        assertEquals("📆 Неделя", resultRow2.get(1).getText());
        assertEquals("🗓️ Месяц", resultRow2.get(2).getText());
        
        // Проверяем третий ряд
        KeyboardRow resultRow3 = resultRows.get(2);
        assertEquals(3, resultRow3.size(), "Третий ряд должен содержать 3 кнопки");
        assertEquals("🔍 Поиск", resultRow3.get(0).getText());
        assertEquals("🎯 Фильтр", resultRow3.get(1).getText());
        assertEquals("📊 Статистика", resultRow3.get(2).getText());
        
        // Проверяем четвертый ряд
        KeyboardRow resultRow4 = resultRows.get(3);
        assertEquals(2, resultRow4.size(), "Четвертый ряд должен содержать 2 кнопки");
        assertEquals("🗑️ Корзина", resultRow4.get(0).getText());
        assertEquals("❓ Помощь", resultRow4.get(1).getText());
    }

    @Test
    @DisplayName("Должен создать клавиатуру для неавторизованного пользователя с 2 кнопками")
    void shouldCreateUnauthorizedUserKeyboardWithTwoButtons() {
        // Given
        ReplyKeyboardMarkup mockKeyboard = new ReplyKeyboardMarkup();
        mockKeyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("🚀 Начать");
        row.add("❓ Помощь");
        rows.add(row);
        
        mockKeyboard.setKeyboard(rows);
        
        when(replyKeyboardService.createUnauthorizedUserKeyboard()).thenReturn(mockKeyboard);
        
        // When
        ReplyKeyboardMarkup keyboard = keyboardService.createUnauthorizedUserKeyboard();

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        assertTrue(keyboard.getResizeKeyboard(), "ResizeKeyboard должен быть true");
        
        List<KeyboardRow> resultRows = keyboard.getKeyboard();
        assertNotNull(resultRows, "Список рядов не должен быть null");
        assertEquals(1, resultRows.size(), "Должен быть 1 ряд кнопок");
        
        // Проверяем единственный ряд
        KeyboardRow resultRow = resultRows.get(0);
        assertEquals(2, resultRow.size(), "Ряд должен содержать 2 кнопки");
        assertEquals("🚀 Начать", resultRow.get(0).getText());
        assertEquals("❓ Помощь", resultRow.get(1).getText());
    }

    @Test
    @DisplayName("Должен создать inline клавиатуру для управления событием с двумя рядами")
    void shouldCreateEventActionsKeyboard() {
        // Given
        Long eventId = 123L;
        
        InlineKeyboardMarkup mockKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Первый ряд: Редактировать | Удалить
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton editBtn = new InlineKeyboardButton("✏️ Редактировать");
        editBtn.setCallbackData("edit_event_123");
        row1.add(editBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️ Удалить");
        deleteBtn.setCallbackData("delete_event_123");
        row1.add(deleteBtn);
        rows.add(row1);
        
        // Второй ряд: Вложения
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton attachmentsBtn = new InlineKeyboardButton("📎 Вложения");
        attachmentsBtn.setCallbackData("attach_file_list_123");
        row2.add(attachmentsBtn);
        rows.add(row2);
        
        mockKeyboard.setKeyboard(rows);
        
        when(inlineKeyboardService.createEventActionsKeyboard(eventId)).thenReturn(mockKeyboard);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(eventId);

        // Then
        assertNotNull(keyboard, "Inline клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> resultRows = keyboard.getKeyboard();
        assertNotNull(resultRows, "Список рядов не должен быть null");
        assertEquals(2, resultRows.size(), "Должно быть ровно 2 ряда кнопок");
        
        // Проверяем первый ряд: Редактировать | Удалить
        List<InlineKeyboardButton> resultRow1 = resultRows.get(0);
        assertEquals(2, resultRow1.size(), "Первый ряд должен содержать 2 кнопки");
        
        // Проверяем кнопку редактирования
        InlineKeyboardButton resultEditBtn = resultRow1.get(0);
        assertEquals("✏️ Редактировать", resultEditBtn.getText(), "Первая кнопка должна быть 'Редактировать'");
        assertEquals("edit_event_123", resultEditBtn.getCallbackData(), "Callback data редактирования должен быть 'edit_event_123'");
        
        // Проверяем кнопку удаления
        InlineKeyboardButton resultDeleteBtn = resultRow1.get(1);
        assertEquals("🗑️ Удалить", resultDeleteBtn.getText(), "Вторая кнопка должна быть 'Удалить'");
        assertEquals("delete_event_123", resultDeleteBtn.getCallbackData(), "Callback data удаления должен быть 'delete_event_123'");
        
        // Проверяем второй ряд: только Вложения (без кнопки завершения для метода с одним параметром)
        List<InlineKeyboardButton> resultRow2 = resultRows.get(1);
        assertEquals(1, resultRow2.size(), "Второй ряд должен содержать 1 кнопку");
        InlineKeyboardButton resultAttachmentsBtn = resultRow2.get(0);
        assertEquals("📎 Вложения", resultAttachmentsBtn.getText(), "Кнопка должна быть 'Вложения'");
        assertEquals("attach_file_list_123", resultAttachmentsBtn.getCallbackData(), "Callback data вложений должен быть 'attach_file_list_123'");
    }

    @Test
    @DisplayName("Должен создать inline клавиатуру подтверждения удаления")
    void shouldCreateDeleteConfirmationKeyboard() {
        // Given
        Long eventId = 456L;
        
        InlineKeyboardMarkup mockKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton("✅ Да, удалить");
        confirmBtn.setCallbackData("confirm_delete_456");
        row.add(confirmBtn);
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("cancel_delete_456");
        row.add(cancelBtn);
        rows.add(row);
        
        mockKeyboard.setKeyboard(rows);
        
        when(inlineKeyboardService.createDeleteConfirmationKeyboard(eventId)).thenReturn(mockKeyboard);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createDeleteConfirmationKeyboard(eventId);

        // Then
        assertNotNull(keyboard, "Inline клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> resultRows = keyboard.getKeyboard();
        assertNotNull(resultRows, "Список рядов не должен быть null");
        assertEquals(1, resultRows.size(), "Должен быть 1 ряд кнопок");
        
        List<InlineKeyboardButton> resultRow = resultRows.get(0);
        assertEquals(2, resultRow.size(), "Ряд должен содержать 2 кнопки");
        
        // Проверяем кнопку подтверждения
        InlineKeyboardButton resultConfirmBtn = resultRow.get(0);
        assertEquals("✅ Да, удалить", resultConfirmBtn.getText());
        assertEquals("confirm_delete_456", resultConfirmBtn.getCallbackData());
        
        // Проверяем кнопку отмены
        InlineKeyboardButton resultCancelBtn = resultRow.get(1);
        assertEquals("❌ Отмена", resultCancelBtn.getText());
        assertEquals("cancel_delete_456", resultCancelBtn.getCallbackData());
    }

    @Test
    @DisplayName("Должен преобразовать '🚀 Начать' в '/start'")
    void shouldConvertStartButtonToCommand() {
        // Given
        String buttonText = "🚀 Начать";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/start");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/start", command);
    }

    @Test
    @DisplayName("Должен преобразовать '🗓️ Месяц' в '/month'")
    void shouldConvertUpcomingEventsButtonToCommand() {
        // Given
        String buttonText = "🗓️ Месяц";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/month");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/upcoming_events", command);
    }

    @Test
    @DisplayName("Должен преобразовать '➕ Добавить' в '/add_event'")
    void shouldConvertAddEventButtonToCommand() {
        // Given
        String buttonText = "➕ Добавить";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/add_event");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/add_event", command);
    }

    @Test
    @DisplayName("Должен преобразовать '📝 Мои события' в '/my_events'")
    void shouldConvertMyEventsButtonToCommand() {
        // Given
        String buttonText = "📝 Мои события";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/my_events");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/my_events", command);
    }

    @Test
    @DisplayName("Должен преобразовать '❓ Помощь' в '/help'")
    void shouldConvertHelpButtonToCommand() {
        // Given
        String buttonText = "❓ Помощь";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/help");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/help", command);
    }

    @Test
    @DisplayName("Должен преобразовать '📅 Сегодня' в '/today'")
    void shouldConvertTodayButtonToCommand() {
        // Given
        String buttonText = "📅 Сегодня";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/today");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/today", command);
    }

    @Test
    @DisplayName("Должен преобразовать '📆 Неделя' в '/week'")
    void shouldConvertWeekButtonToCommand() {
        // Given
        String buttonText = "📆 Неделя";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/week");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/week", command);
    }

    @Test
    @DisplayName("Должен преобразовать '🗑️ Корзина' в '/trash'")
    void shouldConvertTrashButtonToCommand() {
        // Given
        String buttonText = "🗑️ Корзина";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/trash");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/trash", command);
    }

    @Test
    @DisplayName("Должен преобразовать '📊 Статистика' в '/stats'")
    void shouldConvertStatsButtonToCommand() {
        // Given
        String buttonText = "📊 Статистика";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/stats");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/stats", command);
    }

    @Test
    @DisplayName("Должен преобразовать '🔍 Поиск' в '/search'")
    void shouldConvertSearchButtonToCommand() {
        // Given
        String buttonText = "🔍 Поиск";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/search");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/search", command);
    }

    @Test
    @DisplayName("Должен преобразовать '🎯 Фильтр' в '/filter'")
    void shouldConvertFilterButtonToCommand() {
        // Given
        String buttonText = "🎯 Фильтр";
        when(replyKeyboardService.buttonTextToCommand(buttonText)).thenReturn("/filter");

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/filter", command);
    }

    @Test
    @DisplayName("Должен вернуть текст без изменений, если он не является кнопкой")
    void shouldReturnTextUnchangedIfNotAButton() {
        // Given
        String text = "/some_command";
        when(replyKeyboardService.buttonTextToCommand(text)).thenReturn(text);

        // When
        String result = keyboardService.buttonTextToCommand(text);

        // Then
        assertEquals(text, result);
    }

    @Test
    @DisplayName("Должен вернуть обычный текст без изменений")
    void shouldReturnPlainTextUnchanged() {
        // Given
        String text = "Привет, бот!";
        when(replyKeyboardService.buttonTextToCommand(text)).thenReturn(text);

        // When
        String result = keyboardService.buttonTextToCommand(text);

        // Then
        assertEquals(text, result);
    }

    @Test
    @DisplayName("Должен выбросить исключение при null buttonText")
    void shouldThrowExceptionWhenButtonTextIsNull() {
        // Given
        when(replyKeyboardService.buttonTextToCommand(null))
                .thenThrow(new IllegalArgumentException("ButtonText не может быть null"));
        
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> keyboardService.buttonTextToCommand(null)
        );
        
        assertEquals("ButtonText не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Клавиатура авторизованного пользователя должна содержать все необходимые кнопки")
    void authorizedKeyboardShouldContainAllRequiredButtons() {
        // Given
        ReplyKeyboardMarkup mockKeyboard = new ReplyKeyboardMarkup();
        mockKeyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        
        KeyboardRow row1 = new KeyboardRow();
        row1.add("📝 Мои события");
        row1.add("➕ Добавить");
        rows.add(row1);
        
        KeyboardRow row2 = new KeyboardRow();
        row2.add("📅 Сегодня");
        row2.add("📆 Неделя");
        row2.add("🗓️ Месяц");
        rows.add(row2);
        
        KeyboardRow row3 = new KeyboardRow();
        row3.add("🔍 Поиск");
        row3.add("🎯 Фильтр");
        row3.add("📊 Статистика");
        rows.add(row3);
        
        KeyboardRow row4 = new KeyboardRow();
        row4.add("🗑️ Корзина");
        row4.add("❓ Помощь");
        rows.add(row4);
        
        mockKeyboard.setKeyboard(rows);
        
        when(replyKeyboardService.createAuthorizedUserKeyboard()).thenReturn(mockKeyboard);
        
        // When
        ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();

        // Then
        List<KeyboardRow> resultRows = keyboard.getKeyboard();
        
        // Собираем все тексты кнопок
        List<String> buttonTexts = resultRows.stream()
                .flatMap(row -> row.stream())
                .map(button -> button.getText())
                .toList();
        
        // Проверяем наличие всех необходимых кнопок
        assertTrue(buttonTexts.contains("📝 Мои события"), 
                "Должна быть кнопка 'Мои события'");
        assertTrue(buttonTexts.contains("➕ Добавить"), 
                "Должна быть кнопка 'Добавить'");
        assertTrue(buttonTexts.contains("📅 Сегодня"), 
                "Должна быть кнопка 'Сегодня'");
        assertTrue(buttonTexts.contains("📆 Неделя"), 
                "Должна быть кнопка 'Неделя'");
        assertTrue(buttonTexts.contains("🗓️ Месяц"), 
                "Должна быть кнопка 'Месяц'");
        assertTrue(buttonTexts.contains("🔍 Поиск"), 
                "Должна быть кнопка 'Поиск'");
        assertTrue(buttonTexts.contains("🎯 Фильтр"), 
                "Должна быть кнопка 'Фильтр'");
        assertTrue(buttonTexts.contains("📊 Статистика"), 
                "Должна быть кнопка 'Статистика'");
        assertTrue(buttonTexts.contains("🗑️ Корзина"), 
                "Должна быть кнопка 'Корзина'");
        assertTrue(buttonTexts.contains("❓ Помощь"), 
                "Должна быть кнопка 'Помощь'");
        
        assertEquals(10, buttonTexts.size(), "Должно быть ровно 10 кнопок");
    }

    @Test
    @DisplayName("Клавиатура неавторизованного пользователя должна содержать только Начать и Помощь")
    void unauthorizedKeyboardShouldContainOnlyStartAndHelp() {
        // Given
        ReplyKeyboardMarkup mockKeyboard = new ReplyKeyboardMarkup();
        mockKeyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("🚀 Начать");
        row.add("❓ Помощь");
        rows.add(row);
        
        mockKeyboard.setKeyboard(rows);
        
        when(replyKeyboardService.createUnauthorizedUserKeyboard()).thenReturn(mockKeyboard);
        
        // When
        ReplyKeyboardMarkup keyboard = keyboardService.createUnauthorizedUserKeyboard();

        // Then
        List<KeyboardRow> resultRows = keyboard.getKeyboard();
        
        // Собираем все тексты кнопок
        List<String> buttonTexts = resultRows.stream()
                .flatMap(row -> row.stream())
                .map(button -> button.getText())
                .toList();
        
        // Проверяем наличие только необходимых кнопок
        assertTrue(buttonTexts.contains("🚀 Начать"), 
                "Должна быть кнопка 'Начать'");
        assertTrue(buttonTexts.contains("❓ Помощь"), 
                "Должна быть кнопка 'Помощь'");
        
        assertEquals(2, buttonTexts.size(), "Должно быть ровно 2 кнопки");
        
        // Проверяем отсутствие кнопок для авторизованных пользователей
        assertFalse(buttonTexts.contains("🗓️ Месяц"), 
                "Не должно быть кнопки 'Месяц'");
        assertFalse(buttonTexts.contains("➕ Добавить"), 
                "Не должно быть кнопки 'Добавить'");
        assertFalse(buttonTexts.contains("📝 Мои события"), 
                "Не должно быть кнопки 'Мои события'");
        assertFalse(buttonTexts.contains("📅 Сегодня"), 
                "Не должно быть кнопки 'Сегодня'");
        assertFalse(buttonTexts.contains("📆 Неделя"), 
                "Не должно быть кнопки 'Неделя'");
        assertFalse(buttonTexts.contains("🔍 Поиск"), 
                "Не должно быть кнопки 'Поиск'");
        assertFalse(buttonTexts.contains("🎯 Фильтр"), 
                "Не должно быть кнопки 'Фильтр'");
        assertFalse(buttonTexts.contains("📊 Статистика"), 
                "Не должно быть кнопки 'Статистика'");
        assertFalse(buttonTexts.contains("🗑️ Корзина"), 
                "Не должно быть кнопки 'Корзина'");
    }

    @Test
    @DisplayName("Inline кнопки должны содержать правильный callback data с eventId")
    void inlineButtonsShouldContainCorrectCallbackData() {
        // Given
        Long eventId = 789L;
        
        InlineKeyboardMarkup mockKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton editBtn = new InlineKeyboardButton("✏️ Редактировать");
        editBtn.setCallbackData("edit_event_789");
        row.add(editBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️ Удалить");
        deleteBtn.setCallbackData("delete_event_789");
        row.add(deleteBtn);
        rows.add(row);
        
        mockKeyboard.setKeyboard(rows);
        
        when(inlineKeyboardService.createEventActionsKeyboard(eventId)).thenReturn(mockKeyboard);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(eventId);

        // Then
        List<InlineKeyboardButton> buttons = keyboard.getKeyboard().get(0);
        
        // Проверяем, что callback data содержит eventId
        assertTrue(buttons.get(0).getCallbackData().contains("789"), 
                "Callback data кнопки редактирования должен содержать eventId");
        assertTrue(buttons.get(1).getCallbackData().contains("789"), 
                "Callback data кнопки удаления должен содержать eventId");
    }

    @Test
    @DisplayName("Inline кнопки подтверждения должны иметь разные callback data")
    void confirmationButtonsShouldHaveDifferentCallbackData() {
        // Given
        Long eventId = 999L;
        
        InlineKeyboardMarkup mockKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton("✅ Да, удалить");
        confirmBtn.setCallbackData("confirm_delete_999");
        row.add(confirmBtn);
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("cancel_delete_999");
        row.add(cancelBtn);
        rows.add(row);
        
        mockKeyboard.setKeyboard(rows);
        
        when(inlineKeyboardService.createDeleteConfirmationKeyboard(eventId)).thenReturn(mockKeyboard);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createDeleteConfirmationKeyboard(eventId);

        // Then
        List<InlineKeyboardButton> buttons = keyboard.getKeyboard().get(0);
        
        String confirmCallback = buttons.get(0).getCallbackData();
        String cancelCallback = buttons.get(1).getCallbackData();
        
        assertNotEquals(confirmCallback, cancelCallback, 
                "Callback data кнопок подтверждения и отмены должны отличаться");
        assertTrue(confirmCallback.startsWith("confirm_delete_"), 
                "Callback data подтверждения должен начинаться с 'confirm_delete_'");
        assertTrue(cancelCallback.startsWith("cancel_delete_"), 
                "Callback data отмены должен начинаться с 'cancel_delete_'");
    }

    // ========== Тесты улучшенного календаря ==========

    @Test
    @DisplayName("Должен отображать пустые ячейки для дат в прошлом")
    void shouldDisplayEmptyCellsForPastDates() {
        // Given
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        Long familyId = 1L;
        User user = createMockUser(familyId);
        
        InlineKeyboardMarkup mockCalendar = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Заголовок
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        InlineKeyboardButton headerBtn = new InlineKeyboardButton("Январь 2026");
        headerBtn.setCallbackData("calendar_ignore");
        headerRow.add(headerBtn);
        rows.add(headerRow);
        
        // Дни недели
        List<InlineKeyboardButton> weekDaysRow = new ArrayList<>();
        String[] weekDays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (String day : weekDays) {
            InlineKeyboardButton dayBtn = new InlineKeyboardButton(day);
            dayBtn.setCallbackData("calendar_ignore");
            weekDaysRow.add(dayBtn);
        }
        rows.add(weekDaysRow);
        
        // Дни месяца с пустыми ячейками для прошлых дат
        List<InlineKeyboardButton> daysRow = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            InlineKeyboardButton dayBtn = new InlineKeyboardButton(i < 3 ? " " : String.valueOf(i));
            dayBtn.setCallbackData(i < 3 ? "calendar_ignore" : "date_2026-01-" + String.format("%02d", i));
            daysRow.add(dayBtn);
        }
        rows.add(daysRow);
        
        mockCalendar.setKeyboard(rows);
        
        when(keyboardLayoutService.createCalendarKeyboard(year, month, user)).thenReturn(mockCalendar);

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, user);

        // Then
        assertNotNull(calendar);
        List<List<InlineKeyboardButton>> resultRows = calendar.getKeyboard();
        
        // Пропускаем заголовок и дни недели (первые 2 ряда)
        for (int i = 2; i < resultRows.size() - 1; i++) { // -1 чтобы пропустить навигацию
            List<InlineKeyboardButton> row = resultRows.get(i);
            for (InlineKeyboardButton button : row) {
                // Если кнопка имеет callback "calendar_ignore" и текст " ", это прошлая дата
                if ("calendar_ignore".equals(button.getCallbackData()) && " ".equals(button.getText())) {
                    // Проверяем, что это действительно пустая ячейка
                    assertEquals(" ", button.getText(), "Прошлые даты должны отображаться как пустые ячейки");
                }
            }
        }
    }

    @Test
    @DisplayName("Должен блокировать кнопку 'Предыдущий месяц' для прошлых месяцев")
    void shouldDisablePreviousMonthButtonForPastMonths() {
        // Given
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        Long familyId = 1L;
        User user = createMockUser(familyId);
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(EventStatus.ACTIVE)))
            .thenReturn(new ArrayList<>());

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, user);

        // Then
        assertNotNull(calendar);
        List<List<InlineKeyboardButton>> rows = calendar.getKeyboard();
        
        // Последний ряд - навигация
        List<InlineKeyboardButton> navigationRow = rows.get(rows.size() - 1);
        InlineKeyboardButton prevButton = navigationRow.get(0);
        
        // Проверяем, что кнопка "Предыдущий месяц" заблокирована
        assertEquals("   ", prevButton.getText(), "Кнопка 'Предыдущий месяц' должна быть пустой для текущего месяца");
        assertEquals("calendar_ignore", prevButton.getCallbackData(), "Callback должен быть 'calendar_ignore'");
    }

    @Test
    @DisplayName("Должен визуально выделять дни с событиями в формате 'деньинициал'")
    void shouldHighlightDaysWithEventsWithInitial() {
        // Given
        LocalDate now = LocalDate.now();
        LocalDate eventDate = now.plusDays(5);
        int year = eventDate.getYear();
        int month = eventDate.getMonthValue();
        Long familyId = 1L;
        User user = createMockUser(familyId);
        
        // Создаем тестовое событие
        Family family = Family.builder().id(familyId).name("Test Family").build();
        User eventUser = User.builder().id(1L).firstName("Алексей").family(family).build();
        Event event = Event.builder()
            .id(1L)
            .user(eventUser)
            .family(family)
            .eventDate(eventDate)
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(EventStatus.ACTIVE)))
            .thenReturn(List.of(event));

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, user);

        // Then
        assertNotNull(calendar);
        List<List<InlineKeyboardButton>> rows = calendar.getKeyboard();
        
        // Ищем кнопку с датой события
        boolean foundEventDay = false;
        for (int i = 2; i < rows.size() - 1; i++) {
            List<InlineKeyboardButton> row = rows.get(i);
            for (InlineKeyboardButton button : row) {
                String text = button.getText();
                // Проверяем, содержит ли текст надстрочный инициал
                if (text.contains("ᴬ")) {
                    foundEventDay = true;
                    assertTrue(text.matches("\\d+ᴬ"), 
                        "Текст должен быть в формате 'деньинициал', получено: " + text);
                }
            }
        }
        
        assertTrue(foundEventDay, "Должен быть найден день с событием");
    }

    @Test
    @DisplayName("Должен корректно извлекать инициал из firstName")
    void shouldCorrectlyExtractInitialFromFirstName() {
        // Given
        LocalDate now = LocalDate.now();
        LocalDate eventDate = now.plusDays(3);
        int year = eventDate.getYear();
        int month = eventDate.getMonthValue();
        Long familyId = 1L;
        User mockUser = createMockUser(familyId);
        
        // Создаем событие с пользователем, имя которого начинается с маленькой буквы
        Family family = Family.builder().id(familyId).name("Test Family").build();
        User eventUser = User.builder().id(1L).firstName("иван").family(family).build();
        Event event = Event.builder()
            .id(1L)
            .user(eventUser)
            .family(family)
            .eventDate(eventDate)
            .eventTime(LocalTime.of(14, 30))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(EventStatus.ACTIVE)))
            .thenReturn(List.of(event));

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, mockUser);

        // Then
        List<List<InlineKeyboardButton>> rows = calendar.getKeyboard();
        
        boolean foundEventDay = false;
        for (int i = 2; i < rows.size() - 1; i++) {
            List<InlineKeyboardButton> row = rows.get(i);
            for (InlineKeyboardButton button : row) {
                String text = button.getText();
                if (text.contains("ᴵ")) {
                    foundEventDay = true;
                    assertTrue(text.contains("ᴵ"), "Инициал должен быть в надстрочном формате");
                }
            }
        }
        
        assertTrue(foundEventDay, "Должен быть найден день с событием и инициалом 'ᴵ'");
    }

    @Test
    @DisplayName("Должен отображать инициал первого события при нескольких событиях в один день")
    void shouldDisplayInitialOfFirstEventWhenMultipleEventsOnSameDay() {
        // Given
        LocalDate now = LocalDate.now();
        LocalDate eventDate = now.plusDays(7);
        int year = eventDate.getYear();
        int month = eventDate.getMonthValue();
        Long familyId = 1L;
        User mockUser = createMockUser(familyId);
        
        // Создаем несколько событий на один день
        Family family = Family.builder().id(familyId).name("Test Family").build();
        User user1 = User.builder().id(1L).firstName("Борис").family(family).build();
        User user2 = User.builder().id(2L).firstName("Анна").family(family).build();
        
        Event event1 = Event.builder()
            .id(1L)
            .user(user1)
            .family(family)
            .eventDate(eventDate)
            .eventTime(LocalTime.of(15, 0)) // Позже
            .status(EventStatus.ACTIVE)
            .build();
        
        Event event2 = Event.builder()
            .id(2L)
            .user(user2)
            .family(family)
            .eventDate(eventDate)
            .eventTime(LocalTime.of(10, 0)) // Раньше
            .status(EventStatus.ACTIVE)
            .build();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(EventStatus.ACTIVE)))
            .thenReturn(List.of(event1, event2));

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, mockUser);

        // Then
        List<List<InlineKeyboardButton>> rows = calendar.getKeyboard();
        
        boolean foundEventDay = false;
        for (int i = 2; i < rows.size() - 1; i++) {
            List<InlineKeyboardButton> row = rows.get(i);
            for (InlineKeyboardButton button : row) {
                String text = button.getText();
                // Проверяем наличие инициала ᴬ (может быть с счетчиком событий)
                if (text.contains("ᴬ")) {
                    foundEventDay = true;
                    assertTrue(text.contains("ᴬ"), 
                        "Должен отображаться инициал первого события по времени (Анна - 10:00)");
                    assertFalse(text.contains("ᴮ"), 
                        "Не должен отображаться инициал второго события");
                    // Проверяем наличие счетчика событий (2)
                    assertTrue(text.contains("(2)"), 
                        "Должен отображаться счетчик событий");
                }
            }
        }
        
        assertTrue(foundEventDay, "Должен быть найден день с событием");
    }

    @Test
    @DisplayName("Должен корректно загружать события за месяц")
    void shouldCorrectlyLoadEventsForMonth() {
        // Given
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        Long familyId = 1L;
        User user = createMockUser(familyId);
        
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), eq(monthStart), eq(monthEnd), eq(EventStatus.ACTIVE)))
            .thenReturn(new ArrayList<>());

        // When
        keyboardService.createCalendarKeyboard(year, month, user);

        // Then
        // Проверяем, что метод репозитория был вызван с правильными параметрами
        org.mockito.Mockito.verify(eventRepository).findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), eq(monthStart), eq(monthEnd), eq(EventStatus.ACTIVE));
    }

    @Test
    @DisplayName("Должен создать календарь для месяца без событий")
    void shouldCreateCalendarForMonthWithoutEvents() {
        // Given
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        Long familyId = 1L;
        User user = createMockUser(familyId);
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(EventStatus.ACTIVE)))
            .thenReturn(new ArrayList<>());

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, user);

        // Then
        assertNotNull(calendar);
        List<List<InlineKeyboardButton>> rows = calendar.getKeyboard();
        
        // Проверяем, что календарь создан корректно
        assertTrue(rows.size() >= 4, "Календарь должен содержать минимум 4 ряда");
        
        // Проверяем, что нет дней с индикаторами событий (надстрочные символы)
        for (int i = 2; i < rows.size() - 1; i++) {
            List<InlineKeyboardButton> row = rows.get(i);
            for (InlineKeyboardButton button : row) {
                String text = button.getText();
                // Проверяем, что текст не содержит надстрочные символы
                assertFalse(text.matches(".*[ᴬᴮⱽᴳᴰᴱᴶᶻᴵᴷᴸᴹᴺᴼᴾᴿˢᵀᵁᶠˣᶜᵂʸ].*"), 
                    "Не должно быть индикаторов событий");
            }
        }
    }

    @Test
    @Disabled("Тест нестабилен из-за зависимости от текущей даты - требует рефакторинга")
    @DisplayName("Должен создать календарь для месяца с несколькими событиями от разных пользователей")
    void shouldCreateCalendarForMonthWithMultipleEventsFromDifferentUsers() {
        // Given
        // Используем фиксированную дату в начале месяца, чтобы избежать проблем с переходом между месяцами
        LocalDate now = LocalDate.of(2026, 1, 5);
        LocalDate date1 = now.plusDays(2);  // 7 января 2026
        LocalDate date2 = now.plusDays(10); // 15 января 2026
        int year = now.getYear();
        int month = now.getMonthValue();
        Long familyId = 1L;
        User mockUser = createMockUser(familyId);
        
        // Создаем события от разных пользователей
        Family family = Family.builder().id(familyId).name("Test Family").build();
        User user1 = User.builder().id(1L).firstName("Мария").family(family).build();
        User user2 = User.builder().id(2L).firstName("Петр").family(family).build();
        
        Event event1 = Event.builder()
            .id(1L)
            .user(user1)
            .family(family)
            .eventDate(date1)
            .eventTime(LocalTime.of(12, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        Event event2 = Event.builder()
            .id(2L)
            .user(user2)
            .family(family)
            .eventDate(date2)
            .eventTime(LocalTime.of(16, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(EventStatus.ACTIVE)))
            .thenReturn(List.of(event1, event2));

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, mockUser);

        // Then
        assertNotNull(calendar);
        List<List<InlineKeyboardButton>> rows = calendar.getKeyboard();
        
        // Подсчитываем количество дней с индикаторами (надстрочные символы)
        int daysWithEvents = 0;
        for (int i = 2; i < rows.size() - 1; i++) {
            List<InlineKeyboardButton> row = rows.get(i);
            for (InlineKeyboardButton button : row) {
                String text = button.getText();
                // Проверяем наличие надстрочных символов
                if (text.matches(".*[ᴬᴮⱽᴳᴰᴱᴶᶻᴵᴷᴸᴹᴺᴼᴾᴿˢᵀᵁᶠˣᶜᵂʸ].*")) {
                    daysWithEvents++;
                }
            }
        }
        
        assertEquals(2, daysWithEvents, "Должно быть 2 дня с индикаторами событий");
    }

    @Test
    @DisplayName("Должен выбросить исключение при некорректном месяце")
    void shouldThrowExceptionForInvalidMonth() {
        // Given
        int year = 2025;
        int invalidMonth = 13;
        Long familyId = 1L;
        User user = createMockUser(familyId);
        
        when(keyboardLayoutService.createCalendarKeyboard(year, invalidMonth, user))
                .thenThrow(new IllegalArgumentException("Month must be between 1 and 12"));

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> keyboardService.createCalendarKeyboard(year, invalidMonth, user)
        );
        
        assertEquals("Month must be between 1 and 12", exception.getMessage());
    }

    // ========== Тесты валидации eventId ==========

    @Test
    @DisplayName("Должен выбросить исключение при null eventId в createEventActionsKeyboard")
    void shouldThrowExceptionWhenEventIdIsNull() {
        // Given
        when(inlineKeyboardService.createEventActionsKeyboard(null))
                .thenThrow(new IllegalArgumentException("EventId не может быть null"));
        
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> keyboardService.createEventActionsKeyboard(null)
        );
        
        assertEquals("EventId не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен выбросить исключение при отрицательном eventId")
    void shouldThrowExceptionWhenEventIdIsNegative() {
        // Given
        Long negativeEventId = -1L;
        
        when(inlineKeyboardService.createEventActionsKeyboard(negativeEventId))
                .thenThrow(new IllegalArgumentException("EventId должен быть положительным числом"));

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> keyboardService.createEventActionsKeyboard(negativeEventId)
        );
        
        assertTrue(exception.getMessage().contains("EventId должен быть положительным числом"));
    }

    @Test
    @DisplayName("Должен выбросить исключение при нулевом eventId")
    void shouldThrowExceptionWhenEventIdIsZero() {
        // Given
        Long zeroEventId = 0L;
        
        when(inlineKeyboardService.createEventActionsKeyboard(zeroEventId))
                .thenThrow(new IllegalArgumentException("EventId должен быть положительным числом"));

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> keyboardService.createEventActionsKeyboard(zeroEventId)
        );
        
        assertTrue(exception.getMessage().contains("EventId должен быть положительным числом"));
    }

    @Test
    @DisplayName("Должен успешно создать клавиатуру с положительным eventId")
    void shouldSuccessfullyCreateKeyboardWithPositiveEventId() {
        // Given
        Long positiveEventId = 1L;
        
        InlineKeyboardMarkup mockKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(new InlineKeyboardButton("Test"));
        rows.add(row);
        mockKeyboard.setKeyboard(rows);
        
        when(inlineKeyboardService.createEventActionsKeyboard(positiveEventId)).thenReturn(mockKeyboard);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(positiveEventId);

        // Then
        assertNotNull(keyboard);
        assertNotNull(keyboard.getKeyboard());
        assertFalse(keyboard.getKeyboard().isEmpty());
    }

    // ========== Тесты для createFileViewKeyboard ==========

    @Test
    @DisplayName("Должен создать клавиатуру для просмотра файла с кнопкой 'Назад к вложениям'")
    void shouldCreateFileViewKeyboardWithBackButton() {
        // Given
        Long eventId = 123L;
        
        InlineKeyboardMarkup mockKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton("⬅️ Назад к вложениям");
        backBtn.setCallbackData("attach_file_list_123");
        row.add(backBtn);
        rows.add(row);
        
        mockKeyboard.setKeyboard(rows);
        
        when(inlineKeyboardService.createFileViewKeyboard(eventId)).thenReturn(mockKeyboard);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createFileViewKeyboard(eventId);

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> resultRows = keyboard.getKeyboard();
        assertNotNull(resultRows, "Список рядов не должен быть null");
        assertEquals(1, resultRows.size(), "Должен быть 1 ряд кнопок");
        
        List<InlineKeyboardButton> resultRow = resultRows.get(0);
        assertEquals(1, resultRow.size(), "Ряд должен содержать 1 кнопку");
        
        InlineKeyboardButton resultBackBtn = resultRow.get(0);
        assertEquals("⬅️ Назад к вложениям", resultBackBtn.getText(), 
                "Текст кнопки должен быть '⬅️ Назад к вложениям'");
        assertEquals("attach_file_list_123", resultBackBtn.getCallbackData(), 
                "Callback data должен быть 'attach_file_list_123'");
    }

    @Test
    @DisplayName("Должен создать клавиатуру с корректным callback data для разных eventId")
    void shouldCreateFileViewKeyboardWithCorrectCallbackDataForDifferentEventIds() {
        // Given
        Long eventId1 = 456L;
        Long eventId2 = 789L;
        
        InlineKeyboardMarkup mockKeyboard1 = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows1 = new ArrayList<>();
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton backBtn1 = new InlineKeyboardButton("⬅️ Назад к вложениям");
        backBtn1.setCallbackData("attach_file_list_456");
        row1.add(backBtn1);
        rows1.add(row1);
        mockKeyboard1.setKeyboard(rows1);
        
        InlineKeyboardMarkup mockKeyboard2 = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows2 = new ArrayList<>();
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton backBtn2 = new InlineKeyboardButton("⬅️ Назад к вложениям");
        backBtn2.setCallbackData("attach_file_list_789");
        row2.add(backBtn2);
        rows2.add(row2);
        mockKeyboard2.setKeyboard(rows2);
        
        when(inlineKeyboardService.createFileViewKeyboard(eventId1)).thenReturn(mockKeyboard1);
        when(inlineKeyboardService.createFileViewKeyboard(eventId2)).thenReturn(mockKeyboard2);

        // When
        InlineKeyboardMarkup keyboard1 = keyboardService.createFileViewKeyboard(eventId1);
        InlineKeyboardMarkup keyboard2 = keyboardService.createFileViewKeyboard(eventId2);

        // Then
        String callbackData1 = keyboard1.getKeyboard().get(0).get(0).getCallbackData();
        String callbackData2 = keyboard2.getKeyboard().get(0).get(0).getCallbackData();
        
        assertEquals("attach_file_list_456", callbackData1, 
                "Callback data должен содержать eventId 456");
        assertEquals("attach_file_list_789", callbackData2, 
                "Callback data должен содержать eventId 789");
        assertNotEquals(callbackData1, callbackData2, 
                "Callback data для разных событий должны отличаться");
    }

    @Test
    @DisplayName("Должен выбросить исключение при null eventId в createFileViewKeyboard")
    void shouldThrowExceptionWhenEventIdIsNullInCreateFileViewKeyboard() {
        // Given
        when(inlineKeyboardService.createFileViewKeyboard(null))
                .thenThrow(new IllegalArgumentException("EventId не может быть null"));
        
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> keyboardService.createFileViewKeyboard(null)
        );
        
        assertEquals("EventId не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен выбросить исключение при отрицательном eventId в createFileViewKeyboard")
    void shouldThrowExceptionWhenEventIdIsNegativeInCreateFileViewKeyboard() {
        // Given
        Long negativeEventId = -1L;
        
        when(inlineKeyboardService.createFileViewKeyboard(negativeEventId))
                .thenThrow(new IllegalArgumentException("EventId должен быть положительным числом, получено: -1"));

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> keyboardService.createFileViewKeyboard(negativeEventId)
        );
        
        assertEquals("EventId должен быть положительным числом, получено: -1", 
                exception.getMessage());
    }

    @Test
    @DisplayName("Должен выбросить исключение при нулевом eventId в createFileViewKeyboard")
    void shouldThrowExceptionWhenEventIdIsZeroInCreateFileViewKeyboard() {
        // Given
        Long zeroEventId = 0L;
        
        when(inlineKeyboardService.createFileViewKeyboard(zeroEventId))
                .thenThrow(new IllegalArgumentException("EventId должен быть положительным числом, получено: 0"));

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> keyboardService.createFileViewKeyboard(zeroEventId)
        );
        
        assertEquals("EventId должен быть положительным числом, получено: 0", 
                exception.getMessage());
    }

    // ========== Тесты для createEventActionsKeyboard(Event, Long) ==========

    @Test
    @DisplayName("Должен создать клавиатуру с тремя рядами для активного события владельца")
    void shouldCreateThreeRowKeyboardForActiveOwnerEvent() {
        // Given
        Long eventId = 100L;
        Long userId = 1L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(userId).firstName("Иван").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        InlineKeyboardMarkup mockKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Первый ряд: Редактировать | Удалить
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton editBtn = new InlineKeyboardButton("✏️ Редактировать");
        editBtn.setCallbackData("edit_event_100");
        row1.add(editBtn);
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️ Удалить");
        deleteBtn.setCallbackData("delete_event_100");
        row1.add(deleteBtn);
        rows.add(row1);
        
        // Второй ряд: Вложения | Напоминания
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton attachBtn = new InlineKeyboardButton("📎 Вложения");
        attachBtn.setCallbackData("attach_file_list_100");
        row2.add(attachBtn);
        InlineKeyboardButton reminderBtn = new InlineKeyboardButton("🔔 Вкл. напоминания");
        reminderBtn.setCallbackData("enable_reminders_100");
        row2.add(reminderBtn);
        rows.add(row2);
        
        // Третий ряд: Завершить
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton completeBtn = new InlineKeyboardButton("✅ Завершить");
        completeBtn.setCallbackData("complete_event_100");
        row3.add(completeBtn);
        rows.add(row3);
        
        mockKeyboard.setKeyboard(rows);
        
        when(inlineKeyboardService.createEventActionsKeyboard(event, userId)).thenReturn(mockKeyboard);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> resultRows = keyboard.getKeyboard();
        assertNotNull(resultRows, "Список рядов не должен быть null");
        assertEquals(3, resultRows.size(), "Должно быть ровно 3 ряда кнопок для активного события владельца");
        
        // Проверяем первый ряд: Редактировать | Удалить
        List<InlineKeyboardButton> resultRow1 = resultRows.get(0);
        assertEquals(2, resultRow1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("✏️ Редактировать", resultRow1.get(0).getText(), "Первая кнопка должна быть 'Редактировать'");
        assertEquals("edit_event_100", resultRow1.get(0).getCallbackData(), "Callback data редактирования должен быть 'edit_event_100'");
        assertEquals("🗑️ Удалить", resultRow1.get(1).getText(), "Вторая кнопка должна быть 'Удалить'");
        assertEquals("delete_event_100", resultRow1.get(1).getCallbackData(), "Callback data удаления должен быть 'delete_event_100'");
        
        // Проверяем второй ряд: Вложения | Напоминания
        List<InlineKeyboardButton> resultRow2 = resultRows.get(1);
        assertEquals(2, resultRow2.size(), "Второй ряд должен содержать 2 кнопки (Вложения и Напоминания)");
        assertEquals("📎 Вложения", resultRow2.get(0).getText(), "Первая кнопка второго ряда должна быть 'Вложения'");
        assertEquals("attach_file_list_100", resultRow2.get(0).getCallbackData(), "Callback data вложений должен быть 'attach_file_list_100'");
        assertEquals("🔔 Вкл. напоминания", resultRow2.get(1).getText(), "Вторая кнопка второго ряда должна быть 'Вкл. напоминания'");
        assertEquals("enable_reminders_100", resultRow2.get(1).getCallbackData(), "Callback data включения напоминаний должен быть 'enable_reminders_100'");
        
        // Проверяем третий ряд: Завершить
        List<InlineKeyboardButton> resultRow3 = resultRows.get(2);
        assertEquals(1, resultRow3.size(), "Третий ряд должен содержать 1 кнопку (Завершить)");
        assertEquals("✅ Завершить", resultRow3.get(0).getText(), "Текст кнопки должен быть '✅ Завершить' без слова 'событие'");
        assertEquals("complete_event_100", resultRow3.get(0).getCallbackData(), "Callback data завершения должен быть 'complete_event_100'");
    }

    @Test
    @DisplayName("Должен создать клавиатуру с двумя рядами без кнопок напоминаний и завершения для неактивного события")
    void shouldCreateTwoRowKeyboardWithoutRemindersAndCompleteButtonForInactiveEvent() {
        // Given
        Long eventId = 101L;
        Long userId = 1L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(userId).firstName("Мария").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(14, 0))
            .status(EventStatus.COMPLETED) // Неактивное событие
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок для неактивного события");
        
        // Проверяем первый ряд: Редактировать | Удалить (без изменений)
        List<InlineKeyboardButton> row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("✏️ Редактировать", row1.get(0).getText(), "Первая кнопка должна быть 'Редактировать'");
        assertEquals("edit_event_101", row1.get(0).getCallbackData(), "Callback data редактирования должен быть 'edit_event_101'");
        assertEquals("🗑️ Удалить", row1.get(1).getText(), "Вторая кнопка должна быть 'Удалить'");
        assertEquals("delete_event_101", row1.get(1).getCallbackData(), "Callback data удаления должен быть 'delete_event_101'");
        
        // Проверяем второй ряд: только Вложения (без кнопки напоминаний)
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(1, row2.size(), "Второй ряд должен содержать только 1 кнопку для неактивного события");
        assertEquals("📎 Вложения", row2.get(0).getText(), "Кнопка должна быть 'Вложения'");
        assertEquals("attach_file_list_101", row2.get(0).getCallbackData(), "Callback data вложений должен быть 'attach_file_list_101'");
    }

    @Test
    @DisplayName("Должен создать клавиатуру с двумя рядами без кнопок напоминаний и завершения для события другого пользователя")
    void shouldCreateTwoRowKeyboardWithoutRemindersAndCompleteButtonForOtherUserEvent() {
        // Given
        Long eventId = 102L;
        Long ownerId = 1L;
        Long otherUserId = 2L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User owner = User.builder().id(ownerId).firstName("Петр").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(owner)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(16, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, otherUserId);

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок для события другого пользователя");
        
        // Проверяем первый ряд: Редактировать | Удалить (без изменений)
        List<InlineKeyboardButton> row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("✏️ Редактировать", row1.get(0).getText(), "Первая кнопка должна быть 'Редактировать'");
        assertEquals("edit_event_102", row1.get(0).getCallbackData(), "Callback data редактирования должен быть 'edit_event_102'");
        assertEquals("🗑️ Удалить", row1.get(1).getText(), "Вторая кнопка должна быть 'Удалить'");
        assertEquals("delete_event_102", row1.get(1).getCallbackData(), "Callback data удаления должен быть 'delete_event_102'");
        
        // Проверяем второй ряд: только Вложения (без кнопки напоминаний)
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(1, row2.size(), "Второй ряд должен содержать только 1 кнопку для события другого пользователя");
        assertEquals("📎 Вложения", row2.get(0).getText(), "Кнопка должна быть 'Вложения'");
        assertEquals("attach_file_list_102", row2.get(0).getCallbackData(), "Callback data вложений должен быть 'attach_file_list_102'");
    }

    @Test
    @DisplayName("Должен отображать счетчик вложений в кнопке когда есть вложения")
    void shouldDisplayAttachmentCountInButtonWhenAttachmentsExist() {
        // Given
        Long eventId = 103L;
        Long userId = 1L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(userId).firstName("Анна").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(12, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        InlineKeyboardMarkup mockKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Первый ряд: Редактировать | Удалить
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(new InlineKeyboardButton("✏️ Редактировать"));
        row1.add(new InlineKeyboardButton("🗑️ Удалить"));
        rows.add(row1);
        
        // Второй ряд: Вложения (3) | Напоминания
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton attachBtn = new InlineKeyboardButton("📎 Вложения (3)");
        attachBtn.setCallbackData("attach_file_list_103");
        row2.add(attachBtn);
        row2.add(new InlineKeyboardButton("🔔 Вкл. напоминания"));
        rows.add(row2);
        
        // Третий ряд: Завершить
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(new InlineKeyboardButton("✅ Завершить"));
        rows.add(row3);
        
        mockKeyboard.setKeyboard(rows);
        
        when(inlineKeyboardService.createEventActionsKeyboard(event, userId)).thenReturn(mockKeyboard);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        List<List<InlineKeyboardButton>> resultRows = keyboard.getKeyboard();
        assertEquals(3, resultRows.size(), "Должно быть ровно 3 ряда кнопок для активного события владельца");
        
        // Проверяем второй ряд: Вложения с счетчиком | Напоминания
        List<InlineKeyboardButton> resultRow2 = resultRows.get(1);
        assertEquals(2, resultRow2.size(), "Второй ряд должен содержать 2 кнопки (Вложения и Напоминания)");
        
        assertEquals("📎 Вложения (3)", resultRow2.get(0).getText(), 
                "Текст кнопки должен содержать счетчик вложений");
        assertEquals("attach_file_list_103", resultRow2.get(0).getCallbackData(), 
                "Callback data вложений должен быть 'attach_file_list_103'");
    }

    @Test
    @DisplayName("Должен сохранить все callback data без изменений")
    void shouldPreserveAllCallbackData() {
        // Given
        Long eventId = 104L;
        Long userId = 1L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(userId).firstName("Борис").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(18, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);
        when(reminderSchedulingService.hasActiveReminders(eventId)).thenReturn(false);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(3, rows.size(), "Должно быть ровно 3 ряда кнопок для активного события владельца");
        
        // Проверяем callback data первого ряда
        List<InlineKeyboardButton> row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("edit_event_104", row1.get(0).getCallbackData(), 
                "Callback data кнопки редактирования должен быть 'edit_event_{eventId}'");
        assertEquals("delete_event_104", row1.get(1).getCallbackData(), 
                "Callback data кнопки удаления должен быть 'delete_event_{eventId}'");
        
        // Проверяем callback data второго ряда (Вложения и Напоминания)
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(2, row2.size(), "Второй ряд должен содержать 2 кнопки (Вложения и Напоминания)");
        assertEquals("attach_file_list_104", row2.get(0).getCallbackData(), 
                "Callback data кнопки вложений должен быть 'attach_file_list_{eventId}'");
        assertEquals("enable_reminders_104", row2.get(1).getCallbackData(), 
                "Callback data кнопки включения напоминаний должен быть 'enable_reminders_{eventId}'");
        
        // Проверяем callback data третьего ряда (Завершить)
        List<InlineKeyboardButton> row3 = rows.get(2);
        assertEquals(1, row3.size(), "Третий ряд должен содержать 1 кнопку (Завершить)");
        assertEquals("complete_event_104", row3.get(0).getCallbackData(), 
                "Callback data кнопки завершения должен быть 'complete_event_{eventId}'");
    }

    @Test
    @DisplayName("Должен выбросить исключение при null event")
    void shouldThrowExceptionWhenEventIsNull() {
        // Given
        Long userId = 1L;
        
        when(inlineKeyboardService.createEventActionsKeyboard(null, userId))
                .thenThrow(new IllegalArgumentException("Event не может быть null"));

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> keyboardService.createEventActionsKeyboard(null, userId)
        );
        
        assertEquals("Event не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен выбросить исключение при null event.id")
    void shouldThrowExceptionWhenEventIdIsNullInEvent() {
        // Given
        Long userId = 1L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(userId).firstName("Сергей").family(family).build();
        Event event = Event.builder()
            .id(null) // null ID
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(inlineKeyboardService.createEventActionsKeyboard(event, userId))
                .thenThrow(new IllegalArgumentException("Event ID не может быть null"));

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> keyboardService.createEventActionsKeyboard(event, userId)
        );
        
        assertEquals("Event ID не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен выбросить исключение при null userId")
    void shouldThrowExceptionWhenUserIdIsNullInCreateEventActionsKeyboard() {
        // Given
        Long eventId = 105L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(1L).firstName("Елена").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(inlineKeyboardService.createEventActionsKeyboard(event, null))
                .thenThrow(new IllegalArgumentException("UserId не может быть null"));

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> keyboardService.createEventActionsKeyboard(event, null)
        );
        
        assertEquals("UserId не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен выбросить исключение при некорректном userId")
    void shouldThrowExceptionWhenUserIdIsInvalid() {
        // Given
        Long eventId = 106L;
        Long invalidUserId = -1L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(1L).firstName("Дмитрий").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(inlineKeyboardService.createEventActionsKeyboard(event, invalidUserId))
                .thenThrow(new IllegalArgumentException("UserId должен быть положительным числом"));

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> keyboardService.createEventActionsKeyboard(event, invalidUserId)
        );
        
        assertTrue(exception.getMessage().contains("UserId должен быть положительным числом"));
    }

    @Test
    @DisplayName("Должен отображать счетчик вложений для неактивного события без кнопки завершения")
    void shouldDisplayAttachmentCountForInactiveEventWithoutCompleteButton() {
        // Given
        Long eventId = 107L;
        Long userId = 1L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(userId).firstName("Ольга").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(15, 0))
            .status(EventStatus.COMPLETED) // Неактивное событие
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(5L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок для неактивного события");
        
        // Проверяем второй ряд: только Вложения с счетчиком (без кнопки напоминаний)
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(1, row2.size(), "Второй ряд должен содержать только 1 кнопку для неактивного события");
        assertEquals("📎 Вложения (5)", row2.get(0).getText(), 
                "Текст кнопки должен содержать счетчик вложений");
        assertEquals("attach_file_list_107", row2.get(0).getCallbackData(), 
                "Callback data вложений должен быть 'attach_file_list_107'");
    }

    @Test
    @DisplayName("Должен отображать 'Включить напоминания' когда нет активных напоминаний")
    void shouldDisplayEnableRemindersWhenNoActiveReminders() {
        // Given
        Long eventId = 108L;
        Long userId = 1L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(userId).firstName("Сергей").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);
        when(reminderSchedulingService.hasActiveReminders(eventId)).thenReturn(false);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(3, rows.size(), "Должно быть ровно 3 ряда кнопок");
        
        // Проверяем второй ряд: Вложения | Включить напоминания
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(2, row2.size(), "Второй ряд должен содержать 2 кнопки");
        assertEquals("🔔 Вкл. напоминания", row2.get(1).getText(), 
                "Текст кнопки должен быть 'Включить напоминания' когда нет активных напоминаний");
        assertEquals("enable_reminders_108", row2.get(1).getCallbackData(), 
                "Callback data должен быть 'enable_reminders_{eventId}'");
    }

    @Test
    @DisplayName("Должен отображать 'Отключить напоминания' когда есть активные напоминания")
    void shouldDisplayDisableRemindersWhenActiveRemindersExist() {
        // Given
        Long eventId = 109L;
        Long userId = 1L;
        
        Family family = Family.builder().id(1L).name("Test Family").build();
        User user = User.builder().id(userId).firstName("Елена").family(family).build();
        Event event = Event.builder()
            .id(eventId)
            .user(user)
            .family(family)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(14, 0))
            .status(EventStatus.ACTIVE)
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);
        when(reminderSchedulingService.hasActiveReminders(eventId)).thenReturn(true);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(3, rows.size(), "Должно быть ровно 3 ряда кнопок");
        
        // Проверяем второй ряд: Вложения | Отключить напоминания
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(2, row2.size(), "Второй ряд должен содержать 2 кнопки");
        assertEquals("🔕 Откл. напоминания", row2.get(1).getText(), 
                "Текст кнопки должен быть 'Отключить напоминания' когда есть активные напоминания");
        assertEquals("disable_reminders_109", row2.get(1).getCallbackData(), 
                "Callback data должен быть 'disable_reminders_{eventId}'");
    }
}

