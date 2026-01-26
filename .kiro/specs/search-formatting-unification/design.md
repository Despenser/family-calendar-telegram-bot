# Дизайн: Расширенное форматирование результатов поиска

## Обзор

Этот дизайн описывает добавление специального формата отображения событий для команды `/search`. В отличие от команд `/today` и `/week`, которые показывают события в контексте определенной даты, результаты поиска требуют более полной информации о каждом событии, включая дату и тип события. Это обеспечит пользователям лучшую ориентацию в найденных событиях.

## Архитектура

### Текущая архитектура

```
SearchCommandHandler
├── handle() - обработка команды /search
├── performSearch() - выполнение поиска
└── использует EventFormatter.formatEvent() - базовый формат (без даты и типа)
```

### Целевая архитектура

```
SearchCommandHandler
├── handle() - обработка команды /search
└── performSearch() - выполнение поиска
    └── использует EventFormatter.formatSearchResult() - расширенный формат с датой и типом
```

## Компоненты и интерфейсы

### EventFormatter

**Новый метод:**

```java
/**
 * Форматирует событие для результатов поиска с полной информацией.
 * 
 * <p>Формат вывода:</p>
 * <pre>
 * 📌 [название]
 * 📅 Дата: DD.MM.YYYY
 * 🕐 Время: [время]
 * [иконка типа] Тип: [Семейное/Личное]
 * 📝 Описание: [описание]
 * 👤 Создал: [имя]
 * </pre>
 * 
 * @param event событие для форматирования
 * @param currentUser текущий пользователь
 * @return отформатированная строка с информацией о событии
 */
public static String formatSearchResult(Event event, User currentUser) {
    if (event == null) {
        throw new IllegalArgumentException("Событие не может быть null");
    }
    if (currentUser == null) {
        throw new IllegalArgumentException("Текущий пользователь не может быть null");
    }
    
    StringBuilder sb = new StringBuilder();
    
    // Эмодзи 📌 и название события
    sb.append(escape("📌 "));
    sb.append(bold(event.getTitle()));
    sb.append(escape("\n"));
    
    // Дата события
    sb.append(escape("📅 Дата: "));
    sb.append(escape(event.getEventDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))));
    sb.append(escape("\n"));
    
    // Время события (если есть)
    if (event.getEventTime() != null) {
        sb.append(escape("🕐 Время: "));
        if (event.getEndTime() != null) {
            sb.append(escape(event.getEventTime().format(TIME_FORMATTER) + " - " + event.getEndTime().format(TIME_FORMATTER)));
        } else {
            sb.append(escape(event.getEventTime().format(TIME_FORMATTER)));
        }
        sb.append(escape("\n"));
    }
    
    // Тип события
    if (event.getIsPersonal()) {
        sb.append(escape("👤 Тип: Личное"));
    } else {
        sb.append(escape("👨‍👩‍👧‍👦 Тип: Семейное"));
    }
    sb.append(escape("\n"));
    
    // Описание события (если есть)
    if (event.getDescription() != null && !event.getDescription().isBlank()) {
        sb.append(escape("📝 Описание: "));
        sb.append(escape(event.getDescription()));
        sb.append(escape("\n"));
    }
    
    // Создатель события (если не текущий пользователь)
    if (!event.belongsToUser(currentUser.getId())) {
        sb.append(escape("👤 Создал: " + event.getUser().getFirstName()));
        sb.append(escape("\n"));
    }
    
    return sb.toString();
}
```

### SearchCommandHandler

**Изменения:**
- Изменить метод `performSearch()` для использования `EventFormatter.formatSearchResult()`
- Добавить разделитель `EventFormatter.formatDaySeparator()` между событиями

**Метод performSearch() - новая реализация:**

```java
public void performSearch(Long chatId, User user, String query) {
    log.debug("Выполнение поиска для пользователя ID={} по запросу: '{}'", user.getId(), query);
    
    try {
        // Валидация запроса
        if (query == null || query.trim().length() < 2) {
            messageService.sendMessage(chatId, 
                "❌ " + escape("Поисковый запрос должен содержать минимум 2 символа."));
            return;
        }
        
        // Выполнение поиска
        List<Event> results = searchService.searchEvents(
            user.getFamily().getId(), 
            user.getId(), 
            query.trim()
        );
        
        if (results.isEmpty()) {
            String responseMessage = "🔍 " + bold("Результаты поиска") + "\n\n" +
                                   escape("По запросу \"") + escape(query) + escape("\" ничего не найдено.") + "\n\n" +
                                   italic("Попробуйте изменить запрос или использовать другие ключевые слова.") + "\n\n" +
                                   escape("Вы можете использовать ") + escape("/today") + escape(" или ") + escape("/week") + 
                                   escape(" для просмотра событий.");
            messageService.sendMessage(chatId, responseMessage);
            log.info("Поиск для пользователя ID={} не дал результатов", user.getId());
            return;
        }
        
        // Формирование сообщения с результатами
        StringBuilder messageBuilder = new StringBuilder();
        
        // Заголовок результатов поиска
        messageBuilder.append("🔍 ").append(bold("Результаты поиска")).append("\n");
        messageBuilder.append(italic("Запрос: " + escape("\"") + query + escape("\""))).append("\n\n");
        
        // Форматирование событий с использованием EventFormatter.formatSearchResult()
        for (int i = 0; i < results.size(); i++) {
            Event event = results.get(i);
            messageBuilder.append(EventFormatter.formatSearchResult(event, user));
            
            // Добавляем разделитель между событиями (но не после последнего)
            if (i < results.size() - 1) {
                messageBuilder.append(EventFormatter.formatDaySeparator());
                messageBuilder.append(escape("\n"));
            }
        }
        
        // Пустая строка перед счетчиком
        messageBuilder.append(escape("\n"));
        
        // Счетчик результатов
        messageBuilder.append(italic("Найдено событий: " + results.size()));
        
        messageService.sendMessage(chatId, messageBuilder.toString());
        log.info("Пользователю ID={} отправлено {} результатов поиска", user.getId(), results.size());
        
    } catch (Exception e) {
        log.error("Ошибка при выполнении поиска для пользователя ID={}", user.getId(), e);
        try {
            messageService.sendMessage(chatId, 
                "❌ " + escape("Произошла ошибка при поиске событий. Попробуйте позже."));
        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage(), ex);
        }
    }
}
```

## Модели данных

Изменений в моделях данных не требуется. Используются существующие модели:
- `Event` - модель события
- `User` - модель пользователя

## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться во всех допустимых выполнениях системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Полнота информации в результатах поиска

*Для любого* события в результатах поиска, форматирование должно включать дату события, тип события (Семейное/Личное), время (если есть), описание (если есть) и создателя (если не текущий пользователь).

**Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7**

### Property 2: Разделители между событиями

*Для любых* двух последовательных событий в результатах поиска, между ними должен быть разделитель "─────────────────────".

**Validates: Requirements 1.8**

### Property 2: Сохранение структуры заголовка

*Для любого* поискового запроса с результатами, сообщение должно содержать заголовок "🔍 **Результаты поиска**", строку с запросом в формате "_Запрос: \"текст\"_", список событий и счетчик в указанном порядке.

**Validates: Requirements 2.1, 2.2, 2.3, 3.1, 3.2, 3.3**

### Property 3: Централизация логики форматирования

*Для любой* реализации SearchCommandHandler, класс не должен содержать собственный метод formatEvent() и должен использовать EventFormatter.formatSearchResult() для форматирования результатов поиска.

**Validates: Requirements 4.1, 4.2, 4.3**

### Property 4: Сохранение функциональности пустых результатов

*Для любого* поискового запроса без результатов, система должна отображать информативное сообщение с предложениями альтернативных действий.

**Validates: Requirements 5.1, 5.2, 5.3, 5.4**

## Обработка ошибок

Обработка ошибок остается без изменений:
- Валидация длины поискового запроса (минимум 2 символа)
- Обработка исключений при выполнении поиска
- Обработка исключений при отправке сообщений

## Стратегия тестирования

### Unit-тесты

1. **Тест использования EventFormatter:**
   - Проверить, что `performSearch()` использует `EventFormatter.formatEvent()` для каждого события
   - Проверить, что собственный метод `formatEvent()` удален из класса

2. **Тест структуры сообщения:**
   - Проверить наличие заголовка "🔍 **Результаты поиска**"
   - Проверить наличие строки с запросом
   - Проверить наличие счетчика результатов

3. **Тест сообщения об отсутствии результатов:**
   - Проверить корректность сообщения при пустых результатах
   - Проверить наличие предложений альтернативных команд

### Property-based тесты

Для этого рефакторинга property-based тесты не требуются, так как:
- Мы используем существующий проверенный метод `EventFormatter.formatEvent()`
- Изменения касаются только структуры кода, а не логики форматирования
- Unit-тесты достаточны для проверки корректности интеграции

### Конфигурация тестов

- Использовать JUnit 5 для unit-тестов
- Использовать Mockito для мокирования зависимостей
- Минимальное покрытие кода: 80%
