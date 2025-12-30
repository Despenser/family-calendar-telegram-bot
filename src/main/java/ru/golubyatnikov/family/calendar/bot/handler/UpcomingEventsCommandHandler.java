package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Обработчик команды /upcoming_events для Telegram бота семейного календаря.
 * 
 * <p>Команда /upcoming_events позволяет пользователям просматривать все предстоящие
 * события их семьи на ближайшие 7 дней. Она выполняет следующие функции:</p>
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

    private static final int DEFAULT_DAYS = 7;
    
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
     * на ближайшие 7 дней и форматирует их в читаемый вид с использованием
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

        log.info("Найдено {} предстоящих событий для семьи ID={}", 
                upcomingEvents.size(), familyId);

        if (upcomingEvents.isEmpty()) {
            return buildNoEventsMessage();
        }

        return buildEventsListMessage(upcomingEvents);
    }

    /**
     * Формирует сообщение об отсутствии семьи у пользователя.
     * 
     * @return сообщение с инструкциями для пользователя без семьи
     */
    private String buildNoFamilyMessage() {
        return "❌ *Ошибка*\n\n" +
               "Вы не принадлежите ни одной семье.\n\n" +
               "Для просмотра событий необходимо быть членом семьи. " +
               "Обратитесь к администратору для добавления в семью.";
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
        return String.format(
                "📅 *Предстоящие события семьи*\n\n" +
                "На ближайшие %d дней событий не запланировано.\n\n" +
                "Используйте /add_event для добавления нового события.",
                DEFAULT_DAYS
        );
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
     * @param events список событий для форматирования
     * @return отформатированное сообщение со списком событий
     */
    private String buildEventsListMessage(List<Event> events) {
        String eventsList = events.stream()
                .map(this::formatEvent)
                .collect(Collectors.joining("\n\n"));

        return String.format(
                "📅 *Предстоящие события семьи*\n\n" +
                "%s\n\n" +
                "Всего событий: %d",
                eventsList,
                events.size()
        );
    }

    /**
     * Форматирует одно событие в читаемый вид.
     * 
     * <p>Использует эмодзи для визуального выделения различных полей события.
     * Название события выделяется жирным шрифтом с помощью Markdown.</p>
     * 
     * <p>Если у события нет описания, поле "Описание" не отображается.</p>
     * 
     * @param event событие для форматирования
     * @return отформатированная строка с информацией о событии
     */
    private String formatEvent(Event event) {
        StringBuilder formatted = new StringBuilder();
        
        formatted.append(String.format("📌 *%s*\n", escapeMarkdown(event.getTitle())));
        formatted.append(String.format("📅 Дата: %s\n", event.getFormattedDate()));
        formatted.append(String.format("🕐 Время: %s\n", event.getFormattedTime()));
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            formatted.append(String.format("📝 Описание: %s\n", 
                    escapeMarkdown(event.getDescription())));
        }
        
        formatted.append(String.format("👤 Создал: %s", 
                escapeMarkdown(event.getUser().getFullName())));
        
        return formatted.toString();
    }

    /**
     * Экранирует специальные символы Markdown для безопасного отображения.
     * 
     * <p>Telegram Bot API использует Markdown для форматирования текста.
     * Некоторые символы имеют специальное значение и должны быть экранированы,
     * чтобы отображаться корректно.</p>
     * 
     * <p>Экранируются следующие символы: * _ [ ] ( ) ~ ` > # + - = | { } . !</p>
     * 
     * @param text текст для экранирования
     * @return текст с экранированными специальными символами
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        
        return text.replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("~", "\\~")
                   .replace("`", "\\`")
                   .replace(">", "\\>")
                   .replace("#", "\\#")
                   .replace("+", "\\+")
                   .replace("-", "\\-")
                   .replace("=", "\\=")
                   .replace("|", "\\|")
                   .replace("{", "\\{")
                   .replace("}", "\\}")
                   .replace(".", "\\.")
                   .replace("!", "\\!");
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
        return "Показать предстоящие события семьи";
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
