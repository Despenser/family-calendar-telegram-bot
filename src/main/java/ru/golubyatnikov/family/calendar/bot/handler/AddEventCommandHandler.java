package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик команды /add_event для создания новых событий в семейном календаре.
 * 
 * <p>Команда /add_event реализует многошаговый диалог для сбора информации о событии:</p>
 * <ol>
 *   <li>Запрос даты события (формат: dd.MM.yyyy)</li>
 *   <li>Запрос времени события (формат: HH:mm)</li>
 *   <li>Запрос названия и описания события</li>
 *   <li>Создание события в базе данных</li>
 *   <li>Отправка подтверждения с деталями события</li>
 * </ol>
 * 
 * <p>Состояние диалога хранится в памяти в ConcurrentHashMap для поддержки
 * многопользовательского режима. Каждый пользователь имеет свое независимое состояние.</p>
 * 
 * <p>Команда требует авторизации - пользователь должен быть зарегистрирован
 * в системе и принадлежать семье.</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.3, 4.4, 4.5</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>
 * Пользователь: /add_event
 * Бот: Введите дату события в формате ДД.ММ.ГГГГ (например, 31.12.2025):
 * 
 * Пользователь: 31.12.2025
 * Бот: Введите время события в формате ЧЧ:ММ (например, 18:00):
 * 
 * Пользователь: 18:00
 * Бот: Введите название и описание события (можно в одном сообщении):
 * 
 * Пользователь: Новогодний ужин
 * Бот: ✅ Событие успешно создано!
 *      
 *      📅 Дата: 31.12.2025
 *      🕐 Время: 18:00
 *      📝 Название: Новогодний ужин
 * </pre>
 * 
 * @see CommandHandler
 * @see EventService
 * @see Event
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Component
@Slf4j
public class AddEventCommandHandler implements CommandHandler {

    private final EventService eventService;
    
    /**
     * Хранилище состояний диалога для каждого пользователя.
     * Ключ - Telegram ID пользователя, значение - состояние диалога.
     * Используется ConcurrentHashMap для потокобезопасности.
     */
    private final Map<Long, ConversationState> conversationStates = new ConcurrentHashMap<>();
    
    /**
     * Форматтер для парсинга даты в формате dd.MM.yyyy
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    
    /**
     * Форматтер для парсинга времени в формате HH:mm
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Конструктор для внедрения зависимостей.
     * 
     * @param eventService сервис для работы с событиями
     */
    public AddEventCommandHandler(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Обрабатывает команду /add_event и управляет многошаговым диалогом.
     * 
     * <p>Метод определяет текущий шаг диалога для пользователя и обрабатывает
     * соответствующий ввод. Если это первый вызов команды, начинается новый диалог.</p>
     * 
     * @param message входящее сообщение от Telegram
     * @param user пользователь из базы данных (не может быть null, так как команда требует авторизации)
     * @return текст ответа пользователю
     * @throws IllegalArgumentException если message или user равны null
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null) {
            log.error("Получено null сообщение в AddEventCommandHandler");
            throw new IllegalArgumentException("Сообщение не может быть null");
        }
        
        if (user == null) {
            log.error("Получен null пользователь в AddEventCommandHandler");
            throw new IllegalArgumentException("Пользователь не может быть null для команды /add_event");
        }
        
        Long telegramId = user.getTelegramId();
        String messageText = message.getText().trim();
        
        log.info("Обработка команды /add_event: telegramId={}, userId={}, text='{}'", 
                telegramId, user.getId(), messageText);
        
        // Проверяем, что пользователь принадлежит семье
        if (!user.hasFamily()) {
            log.warn("Пользователь без семьи попытался создать событие: userId={}, telegramId={}", 
                    user.getId(), telegramId);
            return "❌ Вы не принадлежите ни одной семье.\n\n" +
                   "Для создания событий необходимо быть членом семьи. " +
                   "Обратитесь к администратору для добавления в семью.";
        }
        
        // Получаем или создаем состояние диалога
        ConversationState state = conversationStates.get(telegramId);
        
        // Если это команда /add_event, начинаем новый диалог
        if (messageText.toLowerCase().startsWith("/add_event")) {
            log.info("Начало нового диалога создания события: telegramId={}, userId={}", 
                    telegramId, user.getId());
            state = new ConversationState();
            conversationStates.put(telegramId, state);
            return requestDate();
        }
        
        // Если состояния нет, значит пользователь отправил сообщение вне контекста
        if (state == null) {
            log.debug("Получено сообщение вне контекста диалога: telegramId={}, text='{}'", 
                    telegramId, messageText);
            return "Для создания события используйте команду /add_event";
        }
        
        // Обрабатываем текущий шаг диалога
        try {
            return processConversationStep(state, messageText, user);
        } catch (Exception e) {
            log.error("Ошибка при обработке шага диалога: telegramId={}, step={}, error={}", 
                    telegramId, state.getStep(), e.getMessage(), e);
            conversationStates.remove(telegramId);
            return "❌ Произошла ошибка при создании события: " + e.getMessage() + "\n\n" +
                   "Попробуйте снова, используя команду /add_event";
        }
    }

    /**
     * Обрабатывает текущий шаг диалога в зависимости от состояния.
     * 
     * @param state текущее состояние диалога
     * @param input ввод пользователя
     * @param user пользователь
     * @return ответ для пользователя
     */
    private String processConversationStep(ConversationState state, String input, User user) {
        switch (state.getStep()) {
            case WAITING_FOR_DATE:
                return handleDateInput(state, input, user.getTelegramId());
            
            case WAITING_FOR_TIME:
                return handleTimeInput(state, input, user.getTelegramId());
            
            case WAITING_FOR_TITLE:
                return handleTitleInput(state, input, user);
            
            default:
                log.warn("Неизвестный шаг диалога: step={}, telegramId={}", 
                        state.getStep(), user.getTelegramId());
                conversationStates.remove(user.getTelegramId());
                return "Произошла ошибка. Начните заново с команды /add_event";
        }
    }

    /**
     * Обрабатывает ввод даты события.
     * 
     * @param state состояние диалога
     * @param input ввод пользователя
     * @param telegramId ID пользователя в Telegram
     * @return ответ для пользователя
     */
    private String handleDateInput(ConversationState state, String input, Long telegramId) {
        log.debug("Обработка ввода даты: telegramId={}, input='{}'", telegramId, input);
        
        try {
            LocalDate date = LocalDate.parse(input.trim(), DATE_FORMATTER);
            
            // Проверяем, что дата не в прошлом
            if (date.isBefore(LocalDate.now())) {
                log.warn("Попытка ввести дату в прошлом: telegramId={}, date={}", telegramId, date);
                return "❌ Дата не может быть в прошлом.\n\n" +
                       "Пожалуйста, введите дату в будущем в формате ДД.ММ.ГГГГ:";
            }
            
            state.setEventDate(date);
            state.setStep(ConversationStep.WAITING_FOR_TIME);
            
            log.info("Дата события сохранена: telegramId={}, date={}", telegramId, date);
            return requestTime();
            
        } catch (DateTimeParseException e) {
            log.warn("Неверный формат даты: telegramId={}, input='{}', error={}", 
                    telegramId, input, e.getMessage());
            return "❌ Неверный формат даты.\n\n" +
                   "Пожалуйста, введите дату в формате ДД.ММ.ГГГГ (например, 31.12.2025):";
        }
    }

    /**
     * Обрабатывает ввод времени события.
     * 
     * @param state состояние диалога
     * @param input ввод пользователя
     * @param telegramId ID пользователя в Telegram
     * @return ответ для пользователя
     */
    private String handleTimeInput(ConversationState state, String input, Long telegramId) {
        log.debug("Обработка ввода времени: telegramId={}, input='{}'", telegramId, input);
        
        try {
            LocalTime time = LocalTime.parse(input.trim(), TIME_FORMATTER);
            
            // Проверяем, что дата-время не в прошлом
            LocalDateTime eventDateTime = LocalDateTime.of(state.getEventDate(), time);
            if (eventDateTime.isBefore(LocalDateTime.now())) {
                log.warn("Попытка ввести время, которое в прошлом: telegramId={}, dateTime={}", 
                        telegramId, eventDateTime);
                return "❌ Дата и время события не могут быть в прошлом.\n\n" +
                       "Пожалуйста, введите время в формате ЧЧ:ММ (например, 18:00):";
            }
            
            state.setEventTime(time);
            state.setStep(ConversationStep.WAITING_FOR_TITLE);
            
            log.info("Время события сохранено: telegramId={}, time={}", telegramId, time);
            return requestTitle();
            
        } catch (DateTimeParseException e) {
            log.warn("Неверный формат времени: telegramId={}, input='{}', error={}", 
                    telegramId, input, e.getMessage());
            return "❌ Неверный формат времени.\n\n" +
                   "Пожалуйста, введите время в формате ЧЧ:ММ (например, 18:00):";
        }
    }

    /**
     * Обрабатывает ввод названия и описания события.
     * 
     * @param state состояние диалога
     * @param input ввод пользователя
     * @param user пользователь
     * @return ответ для пользователя
     */
    private String handleTitleInput(ConversationState state, String input, User user) {
        log.debug("Обработка ввода названия: telegramId={}, userId={}, input='{}'", 
                user.getTelegramId(), user.getId(), input);
        
        String trimmedInput = input.trim();
        
        if (trimmedInput.isEmpty()) {
            log.warn("Попытка создать событие с пустым названием: telegramId={}", user.getTelegramId());
            return "❌ Название события не может быть пустым.\n\n" +
                   "Пожалуйста, введите название и описание события:";
        }
        
        // Разделяем ввод на название и описание
        // Если есть перенос строки, первая строка - название, остальное - описание
        // Если нет переноса, весь текст - это название
        String title;
        String description = null;
        
        int newlineIndex = trimmedInput.indexOf('\n');
        if (newlineIndex > 0) {
            title = trimmedInput.substring(0, newlineIndex).trim();
            description = trimmedInput.substring(newlineIndex + 1).trim();
            if (description.isEmpty()) {
                description = null;
            }
        } else {
            title = trimmedInput;
        }
        
        try {
            // Создаем событие
            LocalDateTime eventDateTime = LocalDateTime.of(state.getEventDate(), state.getEventTime());
            Event event = eventService.createEvent(user.getId(), title, description, eventDateTime);
            
            log.info("Событие успешно создано: eventId={}, userId={}, telegramId={}, title='{}'", 
                    event.getId(), user.getId(), user.getTelegramId(), title);
            
            // Удаляем состояние диалога
            conversationStates.remove(user.getTelegramId());
            
            return buildSuccessMessage(event);
            
        } catch (InvalidDateException e) {
            log.error("Ошибка валидации даты при создании события: telegramId={}, error={}", 
                    user.getTelegramId(), e.getMessage());
            conversationStates.remove(user.getTelegramId());
            return "❌ " + e.getMessage() + "\n\n" +
                   "Попробуйте снова, используя команду /add_event";
        }
    }

    /**
     * Формирует запрос даты события.
     * 
     * @return текст запроса
     */
    private String requestDate() {
        return "📅 *Создание нового события*\n\n" +
               "Введите дату события в формате ДД.ММ.ГГГГ (например, 31.12.2025):";
    }

    /**
     * Формирует запрос времени события.
     * 
     * @return текст запроса
     */
    private String requestTime() {
        return "🕐 Отлично!\n\n" +
               "Теперь введите время события в формате ЧЧ:ММ (например, 18:00):";
    }

    /**
     * Формирует запрос названия и описания события.
     * 
     * @return текст запроса
     */
    private String requestTitle() {
        return "📝 Отлично!\n\n" +
               "Теперь введите название события.\n" +
               "Если хотите добавить описание, введите его со следующей строки:\n\n" +
               "*Пример:*\n" +
               "Новогодний ужин\n" +
               "Встречаемся у бабушки в 18:00";
    }

    /**
     * Формирует сообщение об успешном создании события.
     * 
     * @param event созданное событие
     * @return текст сообщения
     */
    private String buildSuccessMessage(Event event) {
        StringBuilder message = new StringBuilder();
        message.append("✅ *Событие успешно создано!*\n\n");
        message.append(String.format("📅 *Дата:* %s\n", event.getFormattedDate()));
        message.append(String.format("🕐 *Время:* %s\n", event.getFormattedTime()));
        message.append(String.format("📝 *Название:* %s\n", event.getTitle()));
        
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            message.append(String.format("📄 *Описание:* %s\n", event.getDescription()));
        }
        
        message.append("\n");
        message.append("Все члены вашей семьи получат уведомление за 1 час до события.\n");
        message.append("Используйте /my_events для управления своими событиями.");
        
        return message.toString();
    }

    @Override
    public String getCommand() {
        return "/add_event";
    }

    @Override
    public String getDescription() {
        return "Добавить новое событие в календарь";
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    /**
     * Перечисление шагов диалога создания события.
     */
    private enum ConversationStep {
        WAITING_FOR_DATE,
        WAITING_FOR_TIME,
        WAITING_FOR_TITLE
    }

    /**
     * Класс для хранения состояния диалога пользователя.
     */
    private static class ConversationState {
        private ConversationStep step = ConversationStep.WAITING_FOR_DATE;
        private LocalDate eventDate;
        private LocalTime eventTime;

        public ConversationStep getStep() {
            return step;
        }

        public void setStep(ConversationStep step) {
            this.step = step;
        }

        public LocalDate getEventDate() {
            return eventDate;
        }

        public void setEventDate(LocalDate eventDate) {
            this.eventDate = eventDate;
        }

        public LocalTime getEventTime() {
            return eventTime;
        }

        public void setEventTime(LocalTime eventTime) {
            this.eventTime = eventTime;
        }
    }
}
