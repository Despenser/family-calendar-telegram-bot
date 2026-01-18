package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;

import java.util.List;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /upcoming_events для Telegram бота семейного календаря.
 * 
 * <p>Команда /upcoming_events позволяет пользователям просматривать все предстоящие
 * события их семьи на ближайшие 30 дней. Она выполняет следующие функции:</p>
 * <ul>
 *   <li>Получает список предстоящих событий семьи пользователя</li>
 *   <li>Форматирует события с использованием Markdown для улучшения читаемости</li>
 *   <li>Отображает дату, время, название, описание и автора каждого события</li>
 *   <li>Сортирует события по дате и времени</li>
 *   <li>Отправляет соответствующее сообщение, если событий нет</li>
 * </ul>
 * 
 * <p>Команда требует авторизации - пользователь должен быть зарегистрирован
 * в системе и принадлежать семье.</p>
 * 
 * <p><b>Требования:</b> 5.1, 5.2, 5.3, 5.4, 5.5</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>
 * Пользователь отправляет: /upcoming_events
 * 
 * Если есть события:
 * Бот отвечает: "📅 *Предстоящие события семьи*
 *                
 *                📌 *День рождения мамы*
 *                📅 Дата: 31.12.2025
 *                🕐 Время: 18:00
 *                📝 Описание: Празднование дня рождения
 *                👤 Создал: Иван Иванов
 *                
 *                📌 *Поход в кино*
 *                📅 Дата: 02.01.2026
 *                🕐 Время: 20:00
 *                📝 Описание: Смотрим новый фильм
 *                👤 Создал: Мария Петрова"
 * 
 * Если событий нет:
 * Бот отвечает: "📅 *Предстоящие события семьи*
 *                
 *                На ближайшие 7 дней событий не запланировано.
 *                
 *                Используйте /add_event для добавления нового события."
 * </pre>
 * 
 * @see CommandHandler
 * @see EventService
 * @see Event
 * @see User
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Component
@Slf4j
public class UpcomingEventsCommandHandler implements CommandHandler {

    private static final int DEFAULT_DAYS = 30;
    
    private final EventService eventService;

    /**
     * Конструктор для внедрения зависимостей.
     * 
     * @param eventService сервис для работы с событиями
     */
    public UpcomingEventsCommandHandler(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Обрабатывает команду /upcoming_events от пользователя.
     * 
     * <p>Метод получает список предстоящих событий семьи пользователя
     * на ближайшие 30 дней и форматирует их в читаемый вид с использованием
     * Markdown разметки.</p>
     * 
     * <p>Если у пользователя нет семьи, возвращается сообщение об ошибке.
     * Если событий нет, возвращается соответствующее информационное сообщение.</p>
     * 
     * @param message входящее сообщение от Telegram, содержащее команду /upcoming_events
     * @param user пользователь из базы данных, запросивший список событий.
     *             Не может быть null, так как команда требует авторизации.
     * @return текст со списком предстоящих событий или сообщение об их отсутствии
     * @throws IllegalArgumentException если message равен null
     * @throws IllegalStateException если пользователь не принадлежит ни одной семье
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null) {
            log.error("Получено null сообщение в UpcomingEventsCommandHandler");
            throw new IllegalArgumentException("Сообщение не может быть null");
        }

        if (user == null) {
            log.error("Получен null пользователь в UpcomingEventsCommandHandler");
            throw new IllegalArgumentException("Пользователь не может быть null");
        }

        Long telegramId = message.getFrom().getId();
        String username = message.getFrom().getUserName();

        log.info("Обработка команды /upcoming_events: telegramId={}, username={}, userId={}", 
                telegramId, username, user.getId());

        // Проверяем наличие семьи у пользователя
        if (!user.hasFamily()) {
            log.warn("Пользователь ID={} не принадлежит ни одной семье", user.getId());
            return buildNoFamilyMessage();
        }

        Long familyId = user.getFamily().getId();
        log.debug("Получение предстоящих событий для семьи ID={}", familyId);

        // Получаем предстоящие события семьи
        List<Event> upcomingEvents = eventService.getUpcomingEvents(familyId, DEFAULT_DAYS);

        log.debug("Найдено {} событий до фильтрации для семьи ID={}", 
                upcomingEvents.size(), familyId);

        // ========== ФИЛЬТРАЦИЯ ПЕРСОНАЛЬНЫХ СОБЫТИЙ ==========
        // Применяется единая логика фильтрации для обеспечения корректного отображения событий:
        //
        // Правила видимости:
        // 1. Семейные события (isPersonal = false) - видны ВСЕМ членам семьи
        // 2. Персональные события (isPersonal = true) - видны ТОЛЬКО создателю
        //
        // Логика фильтра: !event.getIsPersonal() || event.belongsToUser(user.getId())
        // - Если событие НЕ персональное (!event.getIsPersonal()) -> показываем
        // - ИЛИ если событие принадлежит текущему пользователю (event.belongsToUser(user.getId())) -> показываем
        // - В остальных случаях (персональное событие другого пользователя) -> скрываем
        //
        // Требования: 1.4, 4.1, 4.2, 5.4
        // =====================================================
        List<Event> filteredEvents = upcomingEvents.stream()
                .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
                .collect(Collectors.toList());

        log.info("После фильтрации осталось {} предстоящих событий для пользователя ID={}, семья ID={}", 
                filteredEvents.size(), user.getId(), familyId);

        if (filteredEvents.isEmpty()) {
            return buildNoEventsMessage();
        }

        return buildEventsListMessage(filteredEvents);
    }

    /**
     * Формирует сообщение об отсутствии семьи у пользователя.
     * 
     * @return сообщение с инструкциями для пользователя без семьи
     */
    private String buildNoFamilyMessage() {
        return String.format("""
                        ❌ %s
                        
                        Вы не принадлежите ни одной семье.
                        
                        Для просмотра событий необходимо быть членом семьи. \
                        Обратитесь к администратору для добавления в семью.""",
               bold("Ошибка"));
    }

    /**
     * Формирует сообщение об отсутствии предстоящих событий.
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Заголовок с эмодзи</li>
     *   <li>Информацию об отсутствии событий</li>
     *   <li>Подсказку о добавлении нового события</li>
     * </ul>
     * 
     * @return отформатированное сообщение об отсутствии событий
     */
    private String buildNoEventsMessage() {
        return escape("📅 ") + bold("Предстоящие события семьи (30 дней)") + escape("\n\n") +
                escape("На ближайшие " + DEFAULT_DAYS + " дней событий не запланировано.\n\n") +
                escape("Используйте /add_event для добавления нового события.");
    }

    /**
     * Формирует список предстоящих событий с форматированием.
     * 
     * <p>Каждое событие отображается в следующем формате:</p>
     * <pre>
     * 📌 *Название события*
     * 📅 Дата: dd.MM.yyyy
     * 🕐 Время: HH:mm
     * 📝 Описание: текст описания (если есть)
     * 👤 Создал: Имя Фамилия
     * </pre>
     * 
     * <p>События разделяются пустой строкой для улучшения читаемости.</p>
     * 
     * @param filteredEvents список отфильтрованных событий для форматирования
     * @return отформатированное сообщение со списком событий
     */
    private String buildEventsListMessage(List<Event> filteredEvents) {
        String eventsList = filteredEvents.stream()
                .map(this::formatEvent)
                .collect(Collectors.joining("\n\n"));

        return escape("📅 ") + bold("Предстоящие события (30 дней)") + escape("\n\n") +
                eventsList + escape("\n\n") +
                escape("Всего событий: ") + escape(String.valueOf(filteredEvents.size()));
    }

    /**
     * Форматирует одно событие в читаемый вид с детальной информацией.
     * 
     * <p>Использует эмодзи для визуального выделения различных полей события.
     * Название события выделяется жирным шрифтом с помощью Markdown.</p>
     * 
     * <p>Формат вывода включает:</p>
     * <ul>
     *   <li>Иконку типа события (🔒 для персональных, 👨‍👩‍👧‍👦 для семейных)</li>
     *   <li>Название события (жирным шрифтом)</li>
     *   <li>Дату события в формате dd.MM.yyyy</li>
     *   <li>Время события (с интервалом, если указано время окончания)</li>
     *   <li>Описание события (если заполнено)</li>
     *   <li>Имя создателя события</li>
     * </ul>
     * 
     * <p>Если у события нет описания, поле "Описание" не отображается.</p>
     * 
     * <p><b>Требования:</b> 2.2, 2.4, 4.3, 4.4</p>
     * 
     * @param event событие для форматирования
     * @return отформатированная строка с информацией о событии
     */
    private String formatEvent(Event event) {
        StringBuilder formatted = new StringBuilder();
        
        // Иконка типа события (персональное или семейное)
        String eventTypeIcon = event.getIsPersonal() ? "🔒" : "👨‍👩‍👧‍👦";
        formatted.append(escape(eventTypeIcon)).append(bold(event.getTitle())).append(escape("\n"));
        
        // Дата события
        formatted.append(escape("📅 Дата: ")).append(escape(event.getFormattedDate())).append(escape("\n"));
        
        // Время события (с интервалом, если указано)
        if (event.hasTimeInterval()) {
            formatted.append(escape("🕐 Время: ")).append(escape(event.getFormattedTimeInterval())).append(escape("\n"));
        } else {
            formatted.append(escape("🕐 Время: ")).append(escape(event.getFormattedTime())).append(escape("\n"));
        }
        
        // Описание события (опционально)
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            formatted.append(escape("📝 Описание: ")).append(escape(event.getDescription())).append(escape("\n"));
        }
        
        // Создатель события
        formatted.append(escape("👤 Создал: ")).append(escape(event.getUser().getFullName()));
        
        return formatted.toString();
    }

    /**
     * Возвращает команду, которую обрабатывает этот handler.
     * 
     * @return строка "/upcoming_events"
     */
    @Override
    public String getCommand() {
        return "/upcoming_events";
    }

    /**
     * Возвращает описание команды для отображения в справке.
     * 
     * @return описание команды
     */
    @Override
    public String getDescription() {
        return "Показать все предстоящие события (30 дней)";
    }

    /**
     * Определяет, требуется ли авторизация для выполнения этой команды.
     * 
     * <p>Команда /upcoming_events требует авторизации, так как она отображает
     * события семьи, к которой принадлежит пользователь.</p>
     * 
     * @return true, так как команда требует авторизации
     */
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
