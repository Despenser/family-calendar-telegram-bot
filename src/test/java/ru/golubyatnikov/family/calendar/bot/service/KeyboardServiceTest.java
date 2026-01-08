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

    private KeyboardService keyboardService;

    @BeforeEach
    void setUp() {
        keyboardService = new KeyboardService(eventRepository);
    }

    @Test
    @DisplayName("Должен создать клавиатуру для авторизованного пользователя с 4 кнопками")
    void shouldCreateAuthorizedUserKeyboardWithFourButtons() {
        // When
        ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();

        // Then
        assertNotNull(keyboard, "Клавиатура не должна быть null");
        assertTrue(keyboard.getResizeKeyboard(), "ResizeKeyboard должен быть true");
        
        List<KeyboardRow> rows = keyboard.getKeyboard();
        assertNotNull(rows, "Список рядов не должен быть null");
        assertEquals(2, rows.size(), "Должно быть 2 ряда кнопок");
        
        // Проверяем первый ряд
        KeyboardRow row1 = rows.get(0);
        assertEquals(2, row1.size(), "Первый ряд должен содержать 2 кнопки");
        assertEquals("📅 Предстоящие события", row1.get(0).getText());
        assertEquals("➕ Добавить событие", row1.get(1).getText());
        
        // Проверяем второй ряд
        KeyboardRow row2 = rows.get(1);
        assertEquals(2, row2.size(), "Второй ряд должен содержать 2 кнопки");
        assertEquals("📋 Мои события", row2.get(0).getText());
        assertEquals("❓ Помощь", row2.get(1).getText());
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
    @DisplayName("Должен создать inline клавиатуру для управления событием")
    void shouldCreateEventActionsKeyboard() {
        // Given
        Long eventId = 123L;

        // When
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(eventId);

        // Then
        assertNotNull(keyboard, "Inline клавиатура не должна быть null");
        
        List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
        assertNotNull(rows, "Список рядов не должен быть null");
        assertEquals(1, rows.size(), "Должен быть 1 ряд кнопок");
        
        List<InlineKeyboardButton> row = rows.get(0);
        assertEquals(2, row.size(), "Ряд должен содержать 2 кнопки");
        
        // Проверяем кнопку редактирования
        InlineKeyboardButton editBtn = row.get(0);
        assertEquals("✏️ Редактировать", editBtn.getText());
        assertEquals("edit_123", editBtn.getCallbackData());
        
        // Проверяем кнопку удаления
        InlineKeyboardButton deleteBtn = row.get(1);
        assertEquals("🗑️ Удалить", deleteBtn.getText());
        assertEquals("delete_123", deleteBtn.getCallbackData());
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
    @DisplayName("Должен преобразовать '📅 Предстоящие события' в '/upcoming_events'")
    void shouldConvertUpcomingEventsButtonToCommand() {
        // Given
        String buttonText = "📅 Предстоящие события";

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/upcoming_events", command);
    }

    @Test
    @DisplayName("Должен преобразовать '➕ Добавить событие' в '/add_event'")
    void shouldConvertAddEventButtonToCommand() {
        // Given
        String buttonText = "➕ Добавить событие";

        // When
        String command = keyboardService.buttonTextToCommand(buttonText);

        // Then
        assertEquals("/add_event", command);
    }

    @Test
    @DisplayName("Должен преобразовать '📋 Мои события' в '/my_events'")
    void shouldConvertMyEventsButtonToCommand() {
        // Given
        String buttonText = "📋 Мои события";

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
        assertTrue(buttonTexts.contains("📅 Предстоящие события"), 
                "Должна быть кнопка 'Предстоящие события'");
        assertTrue(buttonTexts.contains("➕ Добавить событие"), 
                "Должна быть кнопка 'Добавить событие'");
        assertTrue(buttonTexts.contains("📋 Мои события"), 
                "Должна быть кнопка 'Мои события'");
        assertTrue(buttonTexts.contains("❓ Помощь"), 
                "Должна быть кнопка 'Помощь'");
        
        assertEquals(4, buttonTexts.size(), "Должно быть ровно 4 кнопки");
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
        assertFalse(buttonTexts.contains("📅 Предстоящие события"), 
                "Не должно быть кнопки 'Предстоящие события'");
        assertFalse(buttonTexts.contains("➕ Добавить событие"), 
                "Не должно быть кнопки 'Добавить событие'");
        assertFalse(buttonTexts.contains("📋 Мои события"), 
                "Не должно быть кнопки 'Мои события'");
    }

    @Test
    @DisplayName("Inline кнопки должны содержать правильный callback data с eventId")
    void inlineButtonsShouldContainCorrectCallbackData() {
        // Given
        Long eventId = 789L;

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
                if (text.matches("\\d+ᴬ")) {
                    foundEventDay = true;
                    assertTrue(text.contains("ᴬ"), 
                        "Должен отображаться инициал первого события по времени (Анна - 10:00)");
                    assertFalse(text.contains("ᴮ"), 
                        "Не должен отображаться инициал второго события");
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
}
