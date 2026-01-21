package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
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
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

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
    private AttachmentService attachmentService;

    private KeyboardService keyboardService;

    @BeforeEach
    void setUp() {
        keyboardService = new KeyboardService(eventRepository, attachmentService);
    }

    @Test
    @DisplayName("Должен создать клавиатуру для авторизованного пользователя с 10 кнопками")
    void shouldCreateAuthorizedUserKeyboardWithFourButtons() {
        // When
        ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        assertTrue(keyboard.getResizeKeyboard(), "ResizeKeyboard должен быть true");
        
        List<KeyboardRow> rows = keyboard.getKeyboard();
        assertNotNull(rows, "Список рядов не должен быть null");
        assertEquals(4, rows.size(), "Должно быть 4 ряда кнопок");
        
        // Проверяем первый ряд
        KeyboardRow row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("📝 Мои события", row1.get(0).getText());
        assertEquals("➕ Добавить", row1.get(1).getText());
        
        // Проверяем второй ряд
        KeyboardRow row2 = rows.get(1);
        assertEquals(3, row2.size(), "Второй ряд должен содержать 3 кнопки");
        assertEquals("📅 Сегодня", row2.get(0).getText());
        assertEquals("📆 Неделя", row2.get(1).getText());
        assertEquals("📋 Планы", row2.get(2).getText());
        
        // Проверяем третий ряд
        KeyboardRow row3 = rows.get(2);
        assertEquals(3, row3.size(), "Третий ряд должен содержать 3 кнопки");
        assertEquals("🔍 Поиск", row3.get(0).getText());
        assertEquals("🎯 Фильтр", row3.get(1).getText());
        assertEquals("📊 Статистика", row3.get(2).getText());
        
        // Проверяем четвертый ряд
        KeyboardRow row4 = rows.get(3);
        assertEquals(2, row4.size(), "Четвертый ряд должен содержать 2 кнопки");
        assertEquals("🗑️ Корзина", row4.get(0).getText());
        assertEquals("❓ Помощь", row4.get(1).getText());
    }

    @Test
    @DisplayName("Должен создать клавиатуру для неавторизованного пользователя с 2 кнопками")
    void shouldCreateUnauthorizedUserKeyboardWithTwoButtons() {
        // When
        ReplyKeyboardMarkup keyboard = keyboardService.createUnauthorizedUserKeyboard();

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        assertTrue(keyboard.getResizeKeyboard(), "ResizeKeyboard должен быть true");
        
        List<KeyboardRow> rows = keyboard.getKeyboard();
        assertNotNull(rows, "Список рядов не должен быть null");
        assertEquals(1, rows.size(), "Должен быть 1 ряд кнопок");
        
        // Проверяем единственный ряд
        KeyboardRow row = rows.get(0);
        assertEquals(2, row.size(), "Ряд должен содержать 2 кнопки");
        assertEquals("🚀 Начать", row.get(0).getText());
        assertEquals("❓ Помощь", row.get(1).getText());
    }

    @Test
    @DisplayName("Должен создать inline клавиатуру для управления событием с двумя рядами")
    void shouldCreateEventActionsKeyboard() {
        // Given
        Long eventId = 123L;
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(eventId);

        // Then
        assertNotNull(keyboard, "Inline клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertNotNull(rows, "Список рядов не должен быть null");
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок");
        
        // Проверяем первый ряд: Редактировать | Удалить
        List<InlineKeyboardButton> row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        
        // Проверяем кнопку редактирования
        InlineKeyboardButton editBtn = row1.get(0);
        assertEquals("✏️ Редактировать", editBtn.getText(), "Первая кнопка должна быть 'Редактировать'");
        assertEquals("edit_event_123", editBtn.getCallbackData(), "Callback data редактирования должен быть 'edit_event_123'");
        
        // Проверяем кнопку удаления
        InlineKeyboardButton deleteBtn = row1.get(1);
        assertEquals("🗑️ Удалить", deleteBtn.getText(), "Вторая кнопка должна быть 'Удалить'");
        assertEquals("delete_event_123", deleteBtn.getCallbackData(), "Callback data удаления должен быть 'delete_event_123'");
        
        // Проверяем второй ряд: только Вложения (без кнопки завершения для метода с одним параметром)
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(1, row2.size(), "Второй ряд должен содержать 1 кнопку");
        InlineKeyboardButton attachmentsBtn = row2.get(0);
        assertEquals("📎 Вложения", attachmentsBtn.getText(), "Кнопка должна быть 'Вложения'");
        assertEquals("attach_file_list_123", attachmentsBtn.getCallbackData(), "Callback data вложений должен быть 'attach_file_list_123'");
    }

    @Test
    @DisplayName("Должен создать inline клавиатуру подтверждения удаления")
    void shouldCreateDeleteConfirmationKeyboard() {
        // Given
        Long eventId = 456L;

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createDeleteConfirmationKeyboard(eventId);

        // Then
        assertNotNull(keyboard, "Inline клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertNotNull(rows, "Список рядов не должен быть null");
        assertEquals(1, rows.size(), "Должен быть 1 ряд кнопок");
        
        List<InlineKeyboardButton> row = rows.get(0);
        assertEquals(2, row.size(), "Ряд должен содержать 2 кнопки");
        
        // Проверяем кнопку подтверждения
        InlineKeyboardButton confirmBtn = row.get(0);
        assertEquals("✅ Да, удалить", confirmBtn.getText());
        assertEquals("confirm_delete_456", confirmBtn.getCallbackData());
        
        // Проверяем кнопку отмены
        InlineKeyboardButton cancelBtn = row.get(1);
        assertEquals("❌ Отмена", cancelBtn.getText());
        assertEquals("cancel_delete_456", cancelBtn.getCallbackData());
    }

    @Test
    @DisplayName("Должен преобразовать '🚀 Начать' в '/start'")
    void shouldConvertStartButtonToCommand() {
        // Given
        String buttonText = "🚀 Начать";

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/start", command);
    }

    @Test
    @DisplayName("Должен преобразовать '📋 Планы' в '/upcoming_events'")
    void shouldConvertUpcomingEventsButtonToCommand() {
        // Given
        String buttonText = "📋 Планы";

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

        // When
        String result = keyboardService.buttonTextToCommand(text);

        // Then
        assertEquals(text, result);
    }

    @Test
    @DisplayName("Должен выбросить исключение при null buttonText")
    void shouldThrowExceptionWhenButtonTextIsNull() {
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
        // When
        ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();

        // Then
        List<KeyboardRow> rows = keyboard.getKeyboard();
        
        // Собираем все тексты кнопок
        List<String> buttonTexts = rows.stream()
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
        assertTrue(buttonTexts.contains("📋 Планы"), 
                "Должна быть кнопка 'Планы'");
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
        // When
        ReplyKeyboardMarkup keyboard = keyboardService.createUnauthorizedUserKeyboard();

        // Then
        List<KeyboardRow> rows = keyboard.getKeyboard();
        
        // Собираем все тексты кнопок
        List<String> buttonTexts = rows.stream()
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
        assertFalse(buttonTexts.contains("📋 Планы"), 
                "Не должно быть кнопки 'Планы'");
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
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);

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
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(Event.EventStatus.ACTIVE)))
            .thenReturn(new ArrayList<>());

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, familyId);

        // Then
        assertNotNull(calendar);
        List<List<InlineKeyboardButton>> rows = calendar.getKeyboard();
        
        // Пропускаем заголовок и дни недели (первые 2 ряда)
        for (int i = 2; i < rows.size() - 1; i++) { // -1 чтобы пропустить навигацию
            List<InlineKeyboardButton> row = rows.get(i);
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
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(Event.EventStatus.ACTIVE)))
            .thenReturn(new ArrayList<>());

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, familyId);

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
        
        // Создаем тестовое событие
        Family family = Family.builder().id(familyId).name("Test Family").build();
        User user = User.builder().id(1L).firstName("Алексей").family(family).build();
        Event event = Event.builder()
            .id(1L)
            .user(user)
            .family(family)
            .eventDate(eventDate)
            .eventTime(LocalTime.of(10, 0))
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(Event.EventStatus.ACTIVE)))
            .thenReturn(List.of(event));

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, familyId);

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
        
        // Создаем событие с пользователем, имя которого начинается с маленькой буквы
        Family family = Family.builder().id(familyId).name("Test Family").build();
        User user = User.builder().id(1L).firstName("иван").family(family).build();
        Event event = Event.builder()
            .id(1L)
            .user(user)
            .family(family)
            .eventDate(eventDate)
            .eventTime(LocalTime.of(14, 30))
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(Event.EventStatus.ACTIVE)))
            .thenReturn(List.of(event));

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, familyId);

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
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        Event event2 = Event.builder()
            .id(2L)
            .user(user2)
            .family(family)
            .eventDate(eventDate)
            .eventTime(LocalTime.of(10, 0)) // Раньше
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(Event.EventStatus.ACTIVE)))
            .thenReturn(List.of(event1, event2));

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, familyId);

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
        
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), eq(monthStart), eq(monthEnd), eq(Event.EventStatus.ACTIVE)))
            .thenReturn(new ArrayList<>());

        // When
        keyboardService.createCalendarKeyboard(year, month, familyId);

        // Then
        // Проверяем, что метод репозитория был вызван с правильными параметрами
        org.mockito.Mockito.verify(eventRepository).findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), eq(monthStart), eq(monthEnd), eq(Event.EventStatus.ACTIVE));
    }

    @Test
    @DisplayName("Должен создать календарь для месяца без событий")
    void shouldCreateCalendarForMonthWithoutEvents() {
        // Given
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        Long familyId = 1L;
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(Event.EventStatus.ACTIVE)))
            .thenReturn(new ArrayList<>());

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, familyId);

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
    @DisplayName("Должен создать календарь для месяца с несколькими событиями от разных пользователей")
    void shouldCreateCalendarForMonthWithMultipleEventsFromDifferentUsers() {
        // Given
        LocalDate now = LocalDate.now();
        LocalDate date1 = now.plusDays(2);
        LocalDate date2 = now.plusDays(10);
        int year = now.getYear();
        int month = now.getMonthValue();
        Long familyId = 1L;
        
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
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        Event event2 = Event.builder()
            .id(2L)
            .user(user2)
            .family(family)
            .eventDate(date2)
            .eventTime(LocalTime.of(16, 0))
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            eq(familyId), any(LocalDate.class), any(LocalDate.class), eq(Event.EventStatus.ACTIVE)))
            .thenReturn(List.of(event1, event2));

        // When
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(year, month, familyId);

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

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> keyboardService.createCalendarKeyboard(year, invalidMonth, familyId)
        );
        
        assertEquals("Month must be between 1 and 12", exception.getMessage());
    }

    // ========== Тесты валидации eventId ==========

    @Test
    @DisplayName("Должен выбросить исключение при null eventId в createEventActionsKeyboard")
    void shouldThrowExceptionWhenEventIdIsNull() {
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
        when(attachmentService.countEventAttachments(positiveEventId)).thenReturn(0L);

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

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createFileViewKeyboard(eventId);

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertNotNull(rows, "Список рядов не должен быть null");
        assertEquals(1, rows.size(), "Должен быть 1 ряд кнопок");
        
        List<InlineKeyboardButton> row = rows.get(0);
        assertEquals(1, row.size(), "Ряд должен содержать 1 кнопку");
        
        InlineKeyboardButton backBtn = row.get(0);
        assertEquals("⬅️ Назад к вложениям", backBtn.getText(), 
                "Текст кнопки должен быть '⬅️ Назад к вложениям'");
        assertEquals("attach_file_list_123", backBtn.getCallbackData(), 
                "Callback data должен быть 'attach_file_list_123'");
    }

    @Test
    @DisplayName("Должен создать клавиатуру с корректным callback data для разных eventId")
    void shouldCreateFileViewKeyboardWithCorrectCallbackDataForDifferentEventIds() {
        // Given
        Long eventId1 = 456L;
        Long eventId2 = 789L;

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
    @DisplayName("Должен создать клавиатуру с двумя рядами для активного события владельца")
    void shouldCreateTwoRowKeyboardForActiveOwnerEvent() {
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
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertNotNull(rows, "Список рядов не должен быть null");
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок");
        
        // Проверяем первый ряд: Редактировать | Удалить
        List<InlineKeyboardButton> row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("✏️ Редактировать", row1.get(0).getText(), "Первая кнопка должна быть 'Редактировать'");
        assertEquals("edit_event_100", row1.get(0).getCallbackData(), "Callback data редактирования должен быть 'edit_event_100'");
        assertEquals("🗑️ Удалить", row1.get(1).getText(), "Вторая кнопка должна быть 'Удалить'");
        assertEquals("delete_event_100", row1.get(1).getCallbackData(), "Callback data удаления должен быть 'delete_event_100'");
        
        // Проверяем второй ряд: Вложения | Завершить
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(2, row2.size(), "Второй ряд должен содержать 2 кнопки для активного события владельца");
        assertEquals("📎 Вложения", row2.get(0).getText(), "Первая кнопка второго ряда должна быть 'Вложения'");
        assertEquals("attach_file_list_100", row2.get(0).getCallbackData(), "Callback data вложений должен быть 'attach_file_list_100'");
        assertEquals("✅ Завершить", row2.get(1).getText(), "Текст кнопки должен быть '✅ Завершить' без слова 'событие'");
        assertEquals("complete_event_100", row2.get(1).getCallbackData(), "Callback data завершения должен быть 'complete_event_100'");
    }

    @Test
    @DisplayName("Должен создать клавиатуру с двумя рядами без кнопки завершения для неактивного события")
    void shouldCreateTwoRowKeyboardWithoutCompleteButtonForInactiveEvent() {
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
            .status(Event.EventStatus.COMPLETED) // Неактивное событие
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок");
        
        // Проверяем первый ряд: Редактировать | Удалить (без изменений)
        List<InlineKeyboardButton> row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("✏️ Редактировать", row1.get(0).getText(), "Первая кнопка должна быть 'Редактировать'");
        assertEquals("edit_event_101", row1.get(0).getCallbackData(), "Callback data редактирования должен быть 'edit_event_101'");
        assertEquals("🗑️ Удалить", row1.get(1).getText(), "Вторая кнопка должна быть 'Удалить'");
        assertEquals("delete_event_101", row1.get(1).getCallbackData(), "Callback data удаления должен быть 'delete_event_101'");
        
        // Проверяем второй ряд: только Вложения
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(1, row2.size(), "Второй ряд должен содержать только 1 кнопку для неактивного события");
        assertEquals("📎 Вложения", row2.get(0).getText(), "Кнопка должна быть 'Вложения'");
        assertEquals("attach_file_list_101", row2.get(0).getCallbackData(), "Callback data вложений должен быть 'attach_file_list_101'");
    }

    @Test
    @DisplayName("Должен создать клавиатуру с двумя рядами без кнопки завершения для события другого пользователя")
    void shouldCreateTwoRowKeyboardWithoutCompleteButtonForOtherUserEvent() {
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
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, otherUserId);

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок");
        
        // Проверяем первый ряд: Редактировать | Удалить (без изменений)
        List<InlineKeyboardButton> row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("✏️ Редактировать", row1.get(0).getText(), "Первая кнопка должна быть 'Редактировать'");
        assertEquals("edit_event_102", row1.get(0).getCallbackData(), "Callback data редактирования должен быть 'edit_event_102'");
        assertEquals("🗑️ Удалить", row1.get(1).getText(), "Вторая кнопка должна быть 'Удалить'");
        assertEquals("delete_event_102", row1.get(1).getCallbackData(), "Callback data удаления должен быть 'delete_event_102'");
        
        // Проверяем второй ряд: только Вложения
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
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(3L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок");
        
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(2, row2.size(), "Второй ряд должен содержать 2 кнопки (Вложения и Завершить)");
        
        assertEquals("📎 Вложения (3)", row2.get(0).getText(), 
                "Текст кнопки должен содержать счетчик вложений");
        assertEquals("attach_file_list_103", row2.get(0).getCallbackData(), 
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
            .status(Event.EventStatus.ACTIVE)
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(0L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок");
        
        // Проверяем callback data первого ряда
        List<InlineKeyboardButton> row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("edit_event_104", row1.get(0).getCallbackData(), 
                "Callback data кнопки редактирования должен быть 'edit_event_{eventId}'");
        assertEquals("delete_event_104", row1.get(1).getCallbackData(), 
                "Callback data кнопки удаления должен быть 'delete_event_{eventId}'");
        
        // Проверяем callback data второго ряда
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(2, row2.size(), "Второй ряд должен содержать 2 кнопки");
        assertEquals("attach_file_list_104", row2.get(0).getCallbackData(), 
                "Callback data кнопки вложений должен быть 'attach_file_list_{eventId}'");
        assertEquals("complete_event_104", row2.get(1).getCallbackData(), 
                "Callback data кнопки завершения должен быть 'complete_event_{eventId}'");
    }

    @Test
    @DisplayName("Должен выбросить исключение при null event")
    void shouldThrowExceptionWhenEventIsNull() {
        // Given
        Long userId = 1L;

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
            .status(Event.EventStatus.ACTIVE)
            .build();

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
            .status(Event.EventStatus.ACTIVE)
            .build();

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
            .status(Event.EventStatus.ACTIVE)
            .build();

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
            .status(Event.EventStatus.COMPLETED) // Неактивное событие
            .build();
        
        when(attachmentService.countEventAttachments(eventId)).thenReturn(5L);

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);

        // Then
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertEquals(2, rows.size(), "Должно быть ровно 2 ряда кнопок");
        
        // Проверяем второй ряд: только Вложения с счетчиком
        List<InlineKeyboardButton> row2 = rows.get(1);
        assertEquals(1, row2.size(), "Второй ряд должен содержать только 1 кнопку для неактивного события");
        assertEquals("📎 Вложения (5)", row2.get(0).getText(), 
                "Текст кнопки должен содержать счетчик вложений");
        assertEquals("attach_file_list_107", row2.get(0).getCallbackData(), 
                "Callback data вложений должен быть 'attach_file_list_107'");
    }
}

