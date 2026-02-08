package ru.golubyatnikov.family.calendar.bot.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.Event;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Централизованный компонент для формирования сообщений бота.
 * 
 * <p>Обеспечивает консистентный стиль сообщений и корректное экранирование
 * специальных символов MarkdownV2. Все методы автоматически применяют
 * экранирование через {@link MarkdownFormatter}.</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.4</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * @Component
 * public class DateTimeCallbackHandler {
 *     private final BotMessageBuilder messageBuilder;
 *     
 *     public void handleDateSelection(LocalDate date) {
 *         String message = messageBuilder.buildDateSelectedMessage(date);
 *         // Отправка сообщения...
 *     }
 * }
 * }</pre>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 * @see MarkdownFormatter
 */
@Component
@RequiredArgsConstructor
public class BotMessageBuilder {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String EVENT_CREATION_HEADER = "📋 Создание нового события";
    private static final String FAMILY_EVENT_TYPE = "👨‍👩‍👧‍👦 Тип: Семейное";
    private static final String PERSONAL_EVENT_TYPE = "👤 Тип: Персональное";
    
    // ===== Сообщения о создании события =====
    
    /**
     * Формирует сообщение об успешном создании события.
     * 
     * @param event созданное событие
     * @return отформатированное сообщение
     */
    public String buildEventCreatedMessage(Event event) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ ").append(bold("Событие успешно создано!")).append("\n\n");
        sb.append("📅 Дата: ").append(escape(formatDate(event.getEventDate()))).append("\n");
        sb.append("🕐 Время: ").append(escape(formatTime(event.getEventTime()))).append("\n");
        sb.append("📝 Название: ").append(escape(event.getTitle()));
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            sb.append("\n📄 Описание: ").append(escape(event.getDescription()));
        }
        
        return sb.toString();
    }
    
    /**
     * Формирует сообщение об отмене создания события.
     * 
     * @return отформатированное сообщение
     */
    public String buildEventCancelledMessage() {
        return "❌ Создание события отменено";
    }
    
    // ===== Сообщения о выборе даты и времени =====
    
    /**
     * Формирует сообщение о выборе даты.
     * 
     * @param date выбранная дата
     * @return отформатированное сообщение
     */
    public String buildDateSelectedMessage(LocalDate date) {
        return bold(EVENT_CREATION_HEADER) + "\n\n" +
               formatMessage("✅ Дата выбрана: %s\n\nТеперь выберите час:", formatDate(date));
    }
    
    /**
     * Формирует сообщение о выборе даты (строковый формат).
     * 
     * @param formattedDate отформатированная дата
     * @return отформатированное сообщение
     */
    public String buildDateSelectedMessage(String formattedDate) {
        return bold(EVENT_CREATION_HEADER) + "\n\n" +
               formatMessage("✅ Дата выбрана: %s\n\nТеперь выберите час:", formattedDate);
    }
    
    /**
     * Формирует сообщение об обновлении даты события.
     * 
     * @param formattedDate отформатированная дата
     * @return отформатированное сообщение
     */
    public String buildDateUpdatedMessage(String formattedDate) {
        return formatMessage("✅ Дата события обновлена: %s", formattedDate);
    }
    
    /**
     * Формирует сообщение об обновлении времени события.
     * 
     * @param formattedTime отформатированное время
     * @return отформатированное сообщение
     */
    public String buildTimeUpdatedMessage(String formattedTime) {
        return formatMessage("✅ Время события обновлено: %s", formattedTime);
    }
    
    /**
     * Формирует сообщение о выборе часа.
     * 
     * @param hour выбранный час
     * @return отформатированное сообщение
     */
    public String buildHourSelectedMessage(int hour) {
        return bold(EVENT_CREATION_HEADER) + "\n\n" +
               formatMessage("✅ Час выбран: %02d:00\n\nТеперь выберите минуты:", hour);
    }
    
    /**
     * Формирует сообщение о выборе часа при редактировании времени события.
     * Включает шапку "🕐 Редактирование времени".
     * 
     * @param hour выбранный час
     * @return отформатированное сообщение с шапкой
     */
    public String buildEditTimeHourSelectedMessage(int hour) {
        return "🕐 Редактирование времени\n\n" +
               formatMessage("✅ Час выбран: %02d:00\n\nТеперь выберите минуты:", hour);
    }
    
    /**
     * Формирует сообщение о выборе времени.
     * 
     * @param time выбранное время
     * @return отформатированное сообщение
     */
    public String buildTimeSelectedMessage(LocalTime time) {
        return bold(EVENT_CREATION_HEADER) + "\n\n" +
                formatMessage("✅ Время выбрано: %s\n\nТеперь отправьте название события:", 
                formatTime(time));
    }
    
    /**
     * Формирует сообщение о выборе времени (строковый формат).
     * 
     * @param formattedTime отформатированное время
     * @return отформатированное сообщение
     */
    public String buildTimeSelectedMessage(String formattedTime) {
        return bold(EVENT_CREATION_HEADER) + "\n\n" +
                formatMessage("✅ Время выбрано: %s\n\nТеперь отправьте название события:", 
                formattedTime);
    }
    
    /**
     * Формирует сообщение для выбора часа.
     * 
     * @return отформатированное сообщение
     */
    public String buildSelectHourMessage() {
        return "🕐 Выберите час:";
    }
    
    /**
     * Формирует сообщение для выбора часа при редактировании времени события.
     * Включает шапку "🕐 Редактирование времени".
     * 
     * @return отформатированное сообщение с шапкой
     */
    public String buildEditTimeSelectHourMessage() {
        return "🕐 Редактирование времени\n\nВыберите новое время:";
    }
    
    /**
     * Формирует сообщение для выбора даты.
     * 
     * @return отформатированное сообщение
     */
    public String buildSelectDateMessage() {
        return "📅 Выберите дату события:";
    }
    
    /**
     * Формирует сообщение для выбора даты при создании нового события.
     * Включает шапку "📋 Создание нового события".
     * 
     * @return отформатированное сообщение с шапкой
     */
    public String buildSelectDateMessageWithHeader() {
        return bold(EVENT_CREATION_HEADER) + "\n\nВыберите дату события:";
    }
    
    /**
     * Формирует сообщение для выбора даты при повторении события.
     * Включает информацию о скопированных данных из исходного события.
     * 
     * @param originalEvent исходное событие, которое повторяется
     * @return отформатированное сообщение с информацией о повторении
     */
    public String buildRepeatEventSelectDateMessage(Event originalEvent) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔄 ").append(bold("Повторение события")).append("\n\n");
        sb.append("📝 ").append(bold("Название: ")).append(escape(originalEvent.getTitle())).append("\n");
        
        if (originalEvent.getDescription() != null && !originalEvent.getDescription().isBlank()) {
            sb.append("📄 ").append(bold("Описание: ")).append(escape(originalEvent.getDescription())).append("\n");
        }
        
        sb.append("👤 ").append(bold("Тип: "))
          .append(originalEvent.getIsPersonal() ? "Персональное" : "Семейное").append("\n\n");
        sb.append("Выберите новую дату для события:");
        
        return sb.toString();
    }
    
    /**
     * Формирует сообщение о невозможности создания события на сегодня.
     * 
     * <p>Используется когда текущее время >= 23:46 и пользователь выбирает сегодняшнюю дату.
     * В этом случае не остается достаточно времени для создания события с минимальным
     * интервалом 15 минут.</p>
     * 
     * <p><b>Требования:</b> 1.3, 3.3</p>
     * 
     * @return отформатированное сообщение
     */
    public String buildTooLateForTodayMessage() {
        return "⏰ " + bold("Слишком поздно создавать события на сегодня") + "\n\n" +
               escape("Выберите завтрашний день или другую дату.");
    }
    
    /**
     * Формирует сообщение с предложением выбрать следующий час.
     * 
     * <p>Используется когда текущие минуты >= 46 и пользователь выбирает текущий час.
     * В этом случае все минутные интервалы (00, 15, 30, 45) уже прошли.</p>
     * 
     * <p><b>Требования:</b> 2.3, 3.4</p>
     * 
     * @param currentHour текущий выбранный час (0-23)
     * @return отформатированное сообщение
     */
    public String buildSelectNextHourMessage(int currentHour) {
        return "⏰ " + bold("Все минуты для этого часа уже прошли") + "\n\n" +
               formatMessage("Час %02d:XX недоступен. Выберите следующий час:", currentHour);
    }
    
    // ===== Сообщения о типе события =====
    
    /**
     * Формирует сообщение о выборе типа события.
     * 
     * @param isPersonal true если персональное событие
     * @return отформатированное сообщение
     */
    public String buildEventTypeSelectedMessage(boolean isPersonal) {
        if (isPersonal) {
            return bold(EVENT_CREATION_HEADER) + "\n\n" +
                   "✅ " + escape("Выбрано: Персональное событие") + "\n\n" +
                   italic("Только вы будете видеть это событие.");
        } else {
            return bold(EVENT_CREATION_HEADER) + "\n\n" +
                   "✅ " + escape("Выбрано: Семейное событие") + "\n\n" +
                   italic("Все члены семьи будут видеть это событие.");
        }
    }
    
    // ===== Сообщения об ошибках =====
    
    /**
     * Формирует сообщение об ошибке.
     * 
     * @param errorText текст ошибки
     * @return отформатированное сообщение
     */
    public String buildErrorMessage(String errorText) {
        return "❌ " + bold("Произошла ошибка") + "\\. " + italic(escape(errorText));
    }
    
    /**
     * Формирует сообщение об ошибке с предложением действия.
     * 
     * @param errorText текст ошибки
     * @param actionHint подсказка о действии
     * @return отформатированное сообщение
     */
    public String buildErrorMessageWithAction(String errorText, String actionHint) {
        return buildErrorMessage(errorText) + "\n\n" + italic(escape(actionHint));
    }
    
    /**
     * Формирует простое сообщение об ошибке для callback query.
     * 
     * @return отформатированное сообщение
     */
    public String buildCallbackErrorMessage() {
        return "❌ Произошла ошибка. Попробуйте еще раз.";
    }
    
    
    // ===== Сообщения о файлах =====
    
    /**
     * Формирует сообщение о прикреплении файла.
     * 
     * @param fileName имя файла
     * @param fileSizeMb размер файла в МБ
     * @return отформатированное сообщение
     */
    public String buildFileAttachedMessage(String fileName, double fileSizeMb) {
        return "✅ " + bold("Файл успешно прикреплен!") + "\n\n" +
                "📎 Название: " + escape(fileName) + "\n" +
                "📊 Размер: " + escape(String.format("%.2f МБ", fileSizeMb)) + "\n\n" +
                escape("Вы можете продолжить прикреплять файлы или завершить создание события.");
    }
    
    // ===== Сообщения о событиях из текста =====
    
    /**
     * Формирует сообщение предпросмотра события из текста.
     * 
     * @param title название события
     * @param date дата события
     * @param time время события
     * @return отформатированное сообщение
     */
    public String buildTextEventPreviewMessage(String title, String date, String time) {
        return "✅ " + bold("Распознано событие из текста:") + "\n\n" +
                "📝 Название: " + escape(title) + "\n" +
                "📅 Дата: " + escape(date) + "\n" +
                "🕐 Время: " + escape(time) + "\n\n" +
                escape("Подтвердите создание события:");
    }
    
    /**
     * Формирует сообщение об отмене создания события из текста.
     * 
     * @return отформатированное сообщение
     */
    public String buildTextEventCancelledMessage() {
        return "❌ Создание события из текста отменено";
    }
    
    // ===== Сообщения о пропуске описания =====
    
    /**
     * Формирует сообщение о пропуске описания.
     * 
     * @return отформатированное сообщение
     */
    public String buildDescriptionSkippedMessage() {
        return "✅ Описание пропущено\\. Событие создано\\!";
    }
    
    // ===== Централизованное форматирование события =====
    
    /**
     * Формирует заголовок для списка "Мои события".
     * 
     * <p>Заголовок включает:</p>
     * <ul>
     *   <li>Эмодзи 📋 и название "Мои события" (выделено жирным)</li>
     *   <li>Информацию о количестве событий в формате "Всего событий: N"</li>
     * </ul>
     * 
     * <p>Все специальные символы MarkdownV2 корректно экранированы с помощью
     * метода {@link MarkdownFormatter#escape(String)}.</p>
     * 
     * <p>Формат заголовка соответствует команде /trash для единообразия интерфейса.</p>
     * 
     * <p><b>Пример вывода:</b></p>
     * <pre>
     * 📋 *Мои события*
     * 
     * Всего событий: 5
     * </pre>
     * 
     * <p><b>Требования:</b> 2.1, 2.2, 2.3, 4.1, 4.2, 4.3</p>
     * 
     * @param eventCount количество событий пользователя (должно быть больше 0)
     * @return отформатированный заголовок с использованием MarkdownV2
     */
    public String buildMyEventsHeader(int eventCount) {
        return "📋 " + bold("Мои события") + "\n\n" +
                escape("Всего событий: ") + escape(String.valueOf(eventCount)) + "\n";
    }
    
    /**
     * Формирует сообщение о пустом состоянии списка "Мои события".
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Заголовок "📝 Мои события" (выделено жирным)</li>
     *   <li>Информацию об отсутствии событий</li>
     *   <li>Подсказку о команде для добавления нового события</li>
     * </ul>
     * 
     * <p>Все специальные символы MarkdownV2 корректно экранированы с помощью
     * метода {@link MarkdownFormatter#escape(String)}.</p>
     * 
     * <p><b>Пример вывода:</b></p>
     * <pre>
     * 📝 *Мои события*
     * 
     * У вас пока нет созданных событий.
     * 
     * Используйте ➕ /add_event для добавления нового события.
     * </pre>
     * 
     * <p><b>Требования:</b> 3.1, 3.2, 3.3</p>
     * 
     * @return отформатированное сообщение о пустом состоянии
     */
    public String buildEmptyMyEventsMessage() {
        return "📝 " + bold("Мои события") + "\n\n" +
                escape("У вас пока нет созданных событий.\n\n") +
                escape("Используйте ") + escape("➕ /add_event") + escape(" для добавления нового события.");
    }
    
    /**
     * Формирует заголовок для корзины удаленных событий.
     * 
     * <p>Заголовок включает:</p>
     * <ul>
     *   <li>Эмодзи 🗑️ и название "Корзина" (выделено жирным)</li>
     *   <li>Информацию о сроке хранения событий (italic текст)</li>
     *   <li>Информацию о количестве событий в формате "Всего событий: N"</li>
     * </ul>
     * 
     * <p>Все специальные символы MarkdownV2 корректно экранированы с помощью
     * метода {@link MarkdownFormatter#escape(String)}.</p>
     * 
     * <p>Формат заголовка соответствует команде /my_events для единообразия интерфейса.</p>
     * 
     * <p><b>Пример вывода:</b></p>
     * <pre>
     * 🗑️ *Корзина*
     * 
     * _Удаленные события хранятся 30 дней_
     * 
     * Всего событий: 3
     * </pre>
     * 
     * <p><b>Требования:</b> 4.1, 3.2</p>
     * 
     * @param eventCount количество событий в корзине (должно быть больше 0)
     * @return отформатированный заголовок с использованием MarkdownV2
     */
    public String buildTrashHeader(int eventCount) {
        return "🗑️ " + bold("Корзина") + "\n\n" +
                italic("Удаленные события хранятся 30 дней") + "\n\n" +
                escape("Всего событий: ") + escape(String.valueOf(eventCount)) + escape("\n");
    }
    
    /**
     * Формирует сообщение о событии для отображения в списке "Мои события".
     * 
     * <p>Этот метод обеспечивает централизованное и консистентное форматирование
     * сообщений о событиях во всех частях приложения. Автоматически применяет
     * экранирование специальных символов MarkdownV2 для всех пользовательских данных.</p>
     * 
     * <p><b>Формат сообщения:</b></p>
     * <ul>
     *   <li>Заголовок с эмодзи 📌 и названием события (выделено жирным)</li>
     *   <li>Дата события в формате "📅 Дата: DD.MM.YYYY"</li>
     *   <li>Время события в формате "🕐 Время: HH:MM"</li>
     *   <li>Тип события: "👨‍👩‍👧‍👦 Тип: Семейное" или "👤 Тип: Персональное"</li>
     *   <li>Описание события (если указано) в формате "📝 Описание: текст"</li>
     * </ul>
     * 
     * <p>Использует единый формат для всех случаев отображения событий:
     * первоначальный список, обновление после редактирования, отдельное сообщение.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3</p>
     * 
     * @param event событие для форматирования
     * @return отформатированное сообщение с MarkdownV2 экранированием
     * @throws IllegalArgumentException если event равен null
     */
    public String buildEventMessage(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        StringBuilder formatted = new StringBuilder();
        
        // Используем escape() для эмодзи и bold() для названия
        formatted.append(escape("📌 ")).append(bold(event.getTitle())).append(escape("\n"));
        formatted.append(escape("📅 Дата: ")).append(escape(event.getFormattedDate())).append(escape("\n"));
        formatted.append(escape("🕐 Время: ")).append(escape(event.getFormattedTime()));
        
        // Добавляем тип события
        formatted.append(escape("\n"));
        Boolean isPersonalValue = event.getIsPersonal() != null ? event.getIsPersonal() : false;
        if (isPersonalValue) {
            formatted.append(escape(PERSONAL_EVENT_TYPE));
        } else {
            formatted.append(escape(FAMILY_EVENT_TYPE));
        }
        
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            formatted.append(escape("\n📝 Описание: ")).append(escape(event.getDescription()));
        }
        
        return formatted.toString();
    }
    
    /**
     * Формирует текст сообщения о событии с учетом флага isMyEventsHeader.
     * 
     * <p>Если событие помечено как первое в списке "Мои события" (isMyEventsHeader = true),
     * добавляет шапку списка перед текстом события. Это обеспечивает сохранение шапки
     * при редактировании первого события через обработчики callback.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 3.5</p>
     * 
     * @param event событие для форматирования
     * @param eventCount количество активных событий пользователя (используется только если isMyEventsHeader = true)
     * @return отформатированный текст сообщения, возможно с шапкой
     * @throws IllegalArgumentException если event равен null
     */
    public String buildEventMessageWithHeader(Event event, int eventCount) {
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        String messageText = buildEventMessage(event);
        
        // Если это первое событие в списке "Мои события", добавляем шапку
        if (Boolean.TRUE.equals(event.getIsMyEventsHeader())) {
            String header = buildMyEventsHeader(eventCount);
            messageText = header + "\n" + messageText;
        }
        
        return messageText;
    }
    
    // ===== Сообщения о завершении события =====
    
    /**
     * Формирует сообщение о завершенном событии с предложением добавить заметку.
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Заголовок "✅ Событие завершено!" (выделено жирным)</li>
     *   <li>Карточку события с использованием {@link #buildEventMessage(Event)}</li>
     *   <li>Предложение добавить заметку о том, как прошло событие</li>
     * </ul>
     * 
     * <p>Используется при ручном завершении события пользователем через кнопку "✅ Завершить".
     * После отображения этого сообщения пользователю предлагаются кнопки "📝 Добавить заметку"
     * и "⏭️ Пропустить".</p>
     * 
     * <p><b>Требования:</b> 1.1, 2.1</p>
     * 
     * @param event завершенное событие
     * @return отформатированное сообщение с использованием MarkdownV2
     * @throws IllegalArgumentException если event равен null
     */
    public String buildCompletionMessage(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }

        String message = "✅ " + bold("Событие завершено!") + "\n\n" +
                buildEventMessage(event) +
                "\n\n" + escape("Хотите добавить заметку о том, как прошло событие?");
        
        return message;
    }
    
    /**
     * Формирует сообщение о завершенном событии с заметкой.
     * 
     * <p>Сообщение включает:</p>
     * <ul>
     *   <li>Заголовок "✅ Событие завершено" (выделено жирным)</li>
     *   <li>Карточку события с использованием {@link #buildEventMessage(Event)}</li>
     *   <li>Секцию заметки с эмодзи "📝" (если заметка присутствует)</li>
     * </ul>
     * 
     * <p>Текст заметки корректно экранируется для Telegram MarkdownV2 с помощью
     * метода {@link MarkdownFormatter#escape(String)}.</p>
     * 
     * <p>Используется для финального отображения завершенного события после того,
     * как пользователь добавил заметку или пропустил её добавление.</p>
     * 
     * <p><b>Требования:</b> 4.1, 4.2, 4.3, 4.4</p>
     * 
     * @param event завершенное событие (может содержать заметку или нет)
     * @return отформатированное сообщение с использованием MarkdownV2
     * @throws IllegalArgumentException если event равен null
     */
    public String buildCompletedEventMessage(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        StringBuilder message = new StringBuilder();
        message.append("✅ ").append(bold("Событие завершено")).append("\n\n");
        message.append(buildEventMessage(event));
        
        // Добавляем секцию заметки, если она присутствует
        if (event.getCompletionNote() != null && !event.getCompletionNote().isBlank()) {
            message.append("\n\n📝 ").append(bold("Заметка:")).append("\n");
            message.append(escape(event.getCompletionNote()));
        }
        
        return message.toString();
    }
    
    // ===== Вспомогательные методы =====
    
    /**
     * Форматирует дату в строку.
     * 
     * @param date дата
     * @return отформатированная строка
     */
    private String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FORMATTER);
    }
    
    /**
     * Форматирует время в строку.
     * 
     * @param time время
     * @return отформатированная строка
     */
    private String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(TIME_FORMATTER);
    }
    
    // ===== Методы для календаря просмотра =====
    
    /**
     * Формирует сообщение со списком событий на дату (для прошлых дат).
     * 
     * @param date дата
     * @param events список событий
     * @return отформатированное сообщение
     */
    public String buildDateEventsListMessage(LocalDate date, java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> events) {
        StringBuilder sb = new StringBuilder();
        sb.append(escape("📅 События на ")).append(bold(date.format(DATE_FORMATTER))).append("\n\n");
        sb.append(escape("Выберите событие для просмотра:"));
        return sb.toString();
    }
    
    /**
     * Формирует сообщение для создания события на дату (для будущих дат без событий).
     * 
     * @param date дата
     * @return отформатированное сообщение
     */
    public String buildCreateEventOnDateMessage(LocalDate date) {
        StringBuilder sb = new StringBuilder();
        sb.append(escape("📅 ")).append(bold(date.format(DATE_FORMATTER))).append("\n\n");
        sb.append(escape("На эту дату нет событий.")).append("\n\n");
        sb.append(escape("Хотите создать событие?"));
        return sb.toString();
    }
    
    /**
     * Формирует сообщение для управления событиями на дату (для будущих дат с событиями).
     * 
     * @param date дата
     * @param events список событий
     * @return отформатированное сообщение
     */
    public String buildDateEventsManagementMessage(LocalDate date, java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> events) {
        StringBuilder sb = new StringBuilder();
        sb.append(escape("📅 События на ")).append(bold(date.format(DATE_FORMATTER))).append("\n\n");
        sb.append(escape("Всего событий: ")).append(bold(String.valueOf(events.size()))).append("\n\n");
        sb.append(escape("Выберите действие:"));
        return sb.toString();
    }
    
    /**
     * Формирует сообщение для выбора времени события.
     * 
     * @param date выбранная дата
     * @return отформатированное сообщение
     */
    public String buildSelectTimeMessage(LocalDate date) {
        return escape("📋 Создание нового события") + "\n\n" +
               escape("✅ Дата: ") + bold(date.format(DATE_FORMATTER)) + "\n\n" +
               escape("Выберите время события:");
    }
    
    /**
     * Формирует сообщение для календаря просмотра событий.
     * Включает шапку "📅 Календарь событий".
     * 
     * @return отформатированное сообщение с шапкой
     */
    public String buildCalendarViewMessage() {
        return "📅 " + bold("Календарь событий") + "\n\n" +
               escape("Выберите дату для просмотра, создания или редактирования событий:");
    }
}
