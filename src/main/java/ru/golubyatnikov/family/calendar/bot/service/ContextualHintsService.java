package ru.golubyatnikov.family.calendar.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для предоставления контекстных подсказок при создании событий.
 * 
 * <p>Анализирует название события и предлагает релевантные действия на основе
 * ключевых слов. Например, для события "День рождения" предлагает создать список подарков,
 * для "Поездка" - прикрепить билеты.</p>
 * 
 * <p><b>Требования:</b> 24.1, 24.2, 24.3, 24.4</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Service
@Slf4j
public class ContextualHintsService {

    /**
     * Типы контекстных подсказок.
     */
    public enum HintType {
        /** Предложение создать список подарков */
        GIFT_LIST("🎁 Добавить список подарков", "gift_list"),
        
        /** Предложение прикрепить билеты */
        ATTACH_TICKETS("🎫 Прикрепить билеты", "attach_tickets"),
        
        /** Предложение создать повестку дня */
        CREATE_AGENDA("📋 Создать повестку дня", "create_agenda"),
        
        /** Предложение добавить список покупок */
        SHOPPING_LIST("🛒 Добавить список покупок", "shopping_list"),
        
        /** Предложение добавить маршрут */
        ADD_ROUTE("🗺️ Добавить маршрут", "add_route");
        
        private final String displayText;
        private final String callbackData;
        
        HintType(String displayText, String callbackData) {
            this.displayText = displayText;
            this.callbackData = callbackData;
        }
        
        public String getDisplayText() {
            return displayText;
        }
        
        public String getCallbackData() {
            return "hint_" + callbackData;
        }
    }

    /**
     * Словари ключевых слов для различных типов событий.
     */
    private static final Map<HintType, Set<String>> KEYWORDS = new HashMap<>();
    
    static {
        // Ключевые слова для списка подарков
        KEYWORDS.put(HintType.GIFT_LIST, Set.of(
            "день рождения", "др", "birthday", "юбилей", "праздник", "новый год",
            "рождество", "8 марта", "23 февраля", "годовщина"
        ));
        
        // Ключевые слова для прикрепления билетов
        KEYWORDS.put(HintType.ATTACH_TICKETS, Set.of(
            "поездка", "путешествие", "полет", "рейс", "самолет", "поезд",
            "trip", "flight", "travel", "концерт", "театр", "кино", "спектакль",
            "выставка", "музей", "мероприятие"
        ));
        
        // Ключевые слова для повестки дня
        KEYWORDS.put(HintType.CREATE_AGENDA, Set.of(
            "встреча", "собрание", "совещание", "meeting", "конференция",
            "презентация", "переговоры", "обсуждение", "планерка"
        ));
        
        // Ключевые слова для списка покупок
        KEYWORDS.put(HintType.SHOPPING_LIST, Set.of(
            "покупки", "магазин", "shopping", "супермаркет", "рынок",
            "закупка", "шопинг", "продукты"
        ));
        
        // Ключевые слова для маршрута
        KEYWORDS.put(HintType.ADD_ROUTE, Set.of(
            "экскурсия", "прогулка", "поход", "маршрут", "тур",
            "путь", "дорога", "навигация"
        ));
    }

    /**
     * Анализирует название события и возвращает список релевантных подсказок.
     * 
     * <p>Метод ищет ключевые слова в названии события (без учета регистра)
     * и возвращает соответствующие типы подсказок.</p>
     * 
     * @param eventTitle название события для анализа
     * @return список типов подсказок, отсортированных по релевантности
     */
    public List<HintType> analyzeEventTitle(String eventTitle) {
        if (eventTitle == null || eventTitle.trim().isEmpty()) {
            log.debug("Пустое название события для анализа");
            return Collections.emptyList();
        }
        
        String normalizedTitle = eventTitle.toLowerCase().trim();
        log.debug("Анализ названия события: '{}'", normalizedTitle);
        
        List<HintType> hints = new ArrayList<>();
        
        // Проверяем каждый тип подсказки
        for (Map.Entry<HintType, Set<String>> entry : KEYWORDS.entrySet()) {
            HintType hintType = entry.getKey();
            Set<String> keywords = entry.getValue();
            
            // Проверяем, содержит ли название хотя бы одно ключевое слово
            for (String keyword : keywords) {
                if (normalizedTitle.contains(keyword)) {
                    hints.add(hintType);
                    log.debug("Найдено совпадение: keyword='{}', hintType={}", keyword, hintType);
                    break; // Достаточно одного совпадения для этого типа
                }
            }
        }
        
        if (hints.isEmpty()) {
            log.debug("Подсказки не найдены для названия: '{}'", eventTitle);
        } else {
            log.info("Найдено {} подсказок для события '{}'", hints.size(), eventTitle);
        }
        
        return hints;
    }

    /**
     * Возвращает список предлагаемых действий на основе анализа названия события.
     * 
     * <p>Этот метод является удобной оберткой над {@link #analyzeEventTitle(String)},
     * возвращающей готовые к отображению строки действий.</p>
     * 
     * @param eventTitle название события для анализа
     * @return список предлагаемых действий с эмодзи
     */
    public List<String> getSuggestedActions(String eventTitle) {
        List<HintType> hints = analyzeEventTitle(eventTitle);
        
        return hints.stream()
                .map(HintType::getDisplayText)
                .toList();
    }

    /**
     * Проверяет, есть ли подсказки для данного названия события.
     * 
     * @param eventTitle название события
     * @return true если есть хотя бы одна подсказка
     */
    public boolean hasHints(String eventTitle) {
        return !analyzeEventTitle(eventTitle).isEmpty();
    }

    /**
     * Возвращает описание действия для данного типа подсказки.
     * 
     * @param hintType тип подсказки
     * @return описание действия
     */
    public String getHintDescription(HintType hintType) {
        return switch (hintType) {
            case GIFT_LIST -> "Создайте чек-лист с идеями подарков для именинника";
            case ATTACH_TICKETS -> "Прикрепите билеты, бронирования или подтверждения";
            case CREATE_AGENDA -> "Создайте чек-лист с пунктами повестки дня";
            case SHOPPING_LIST -> "Создайте чек-лист с товарами для покупки";
            case ADD_ROUTE -> "Добавьте описание маршрута или прикрепите карту";
        };
    }

    /**
     * Обрабатывает принятие подсказки пользователем.
     * 
     * <p>Возвращает инструкции для пользователя о том, что делать дальше.
     * Команды отображаются в кликабельном формате для удобства пользователя.</p>
     * 
     * @param hintType тип принятой подсказки
     * @return инструкции для пользователя
     */
    public String handleHintAccepted(HintType hintType) {
        log.info("Пользователь принял подсказку: {}", hintType);
        
        return switch (hintType) {
            case GIFT_LIST -> 
                "🎁 " + bold("Список подарков") + "\n\n" +
                "Отправьте список идей подарков \\(по одной на строку\\)\\.\n\n" +
                italic("Например:") + "\n" +
                "• Книга\n" +
                "• Сертификат в магазин\n" +
                "• Билеты в театр\n\n" +
                "После создания используйте " + escape("/today") + " или " + escape("/week") + " для просмотра события\\.";
                
            case ATTACH_TICKETS -> 
                "🎫 " + bold("Прикрепление билетов") + "\n\n" +
                "Отправьте файлы с билетами, бронированиями или подтверждениями\\.\n\n" +
                italic("Максимальный размер файла: 20 МБ") + "\n\n" +
                "Используйте " + escape("/my_events") + " для просмотра всех ваших событий\\.";
                
            case CREATE_AGENDA -> 
                "📋 " + bold("Повестка дня") + "\n\n" +
                "Отправьте пункты повестки дня \\(по одному на строку\\)\\.\n\n" +
                italic("Например:") + "\n" +
                "• Обсуждение бюджета\n" +
                "• Планирование проекта\n" +
                "• Вопросы и ответы\n\n" +
                "Для поиска событий используйте " + escape("/search") + "\\.";
                
            case SHOPPING_LIST -> 
                "🛒 " + bold("Список покупок") + "\n\n" +
                "Отправьте список товаров для покупки \\(по одному на строку\\)\\.\n\n" +
                italic("Например:") + "\n" +
                "• Молоко\n" +
                "• Хлеб\n" +
                "• Яйца\n\n" +
                "Просмотрите события на сегодня с помощью " + escape("/today") + "\\.";
                
            case ADD_ROUTE -> 
                "🗺️ " + bold("Маршрут") + "\n\n" +
                "Отправьте описание маршрута или прикрепите файл с картой\\.\n\n" +
                italic("Например:") + "\n" +
                "Старт: Красная площадь\n" +
                "Остановка 1: Кремль\n" +
                "Остановка 2: Парк Горького\n" +
                "Финиш: Воробьевы горы\n\n" +
                "Используйте " + escape("/week") + " для просмотра событий на неделю\\.";
        };
    }

    /**
     * Обрабатывает отклонение подсказки пользователем.
     * 
     * @param hintType тип отклоненной подсказки
     */
    public void handleHintDeclined(HintType hintType) {
        log.info("Пользователь отклонил подсказку: {}", hintType);
        // В будущем здесь можно добавить аналитику или обучение модели
    }
}
