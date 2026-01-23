# Документ проектирования: Отображение типа события

## Обзор

Данная функция добавляет визуальное отображение типа события (семейное или персональное) в сообщениях о событиях Telegram-бота. Это улучшит понимание пользователями видимости каждого события без необходимости проверять дополнительную информацию.

Изменения затрагивают только один компонент - `BotMessageBuilder`, который отвечает за формирование всех текстовых сообщений бота. Модификация минимальна и не влияет на другие части системы.

## Архитектура

### Текущая архитектура

```
┌─────────────────────────────────────────┐
│         Event Handlers                  │
│  (MyEventsCommandHandler, etc.)         │
└──────────────┬──────────────────────────┘
               │
               │ вызывает
               ▼
┌─────────────────────────────────────────┐
│      BotMessageBuilder                  │
│  - buildEventMessage(Event)             │
│  - buildEventMessageWithHeader(...)     │
└──────────────┬──────────────────────────┘
               │
               │ использует
               ▼
┌─────────────────────────────────────────┐
│         Event Model                     │
│  - title, description                   │
│  - eventDate, eventTime                 │
│  - isPersonal (Boolean)                 │
└─────────────────────────────────────────┘
```

### Изменения

Модификация затрагивает только метод `buildEventMessage(Event event)` в классе `BotMessageBuilder`. Добавляется одна дополнительная строка в формируемое сообщение между временем и описанием.

## Компоненты и интерфейсы

### BotMessageBuilder

**Изменяемый метод:**

```java
public String buildEventMessage(Event event)
```

**Текущая логика:**
1. Проверка event на null
2. Формирование строки с названием (эмодзи 📌 + жирный текст)
3. Добавление строки с датой (эмодзи 📅)
4. Добавление строки с временем (эмодзи 🕐)
5. Добавление строки с описанием (эмодзи 📝), если описание присутствует

**Новая логика:**
1. Проверка event на null
2. Формирование строки с названием (эмодзи 📌 + жирный текст)
3. Добавление строки с датой (эмодзи 📅)
4. Добавление строки с временем (эмодзи 🕐)
5. **Добавление строки с типом события (эмодзи 👨‍👩‍👧‍👦 или 👤)**
6. Добавление строки с описанием (эмодзи 📝), если описание присутствует

**Псевдокод новой реализации:**

```
function buildEventMessage(event):
    if event is null:
        throw IllegalArgumentException("Event не может быть null")
    
    formatted = new StringBuilder()
    
    // Название
    formatted.append(escape("📌 "))
    formatted.append(bold(event.title))
    formatted.append(escape("\n"))
    
    // Дата
    formatted.append(escape("📅 Дата: "))
    formatted.append(escape(event.formattedDate))
    formatted.append(escape("\n"))
    
    // Время
    formatted.append(escape("🕐 Время: "))
    formatted.append(escape(event.formattedTime))
    
    // *** НОВАЯ ЛОГИКА: Тип события ***
    formatted.append(escape("\n"))
    
    isPersonalValue = event.isPersonal != null ? event.isPersonal : false
    
    if isPersonalValue:
        formatted.append(escape("👤 Тип: Персональное"))
    else:
        formatted.append(escape("👨‍👩‍👧‍👦 Тип: Семейное"))
    // *** КОНЕЦ НОВОЙ ЛОГИКИ ***
    
    // Описание (опционально)
    if event.description is not null and event.description is not blank:
        formatted.append(escape("\n📝 Описание: "))
        formatted.append(escape(event.description))
    
    return formatted.toString()
```

### Event Model

Модель `Event` не требует изменений. Используется существующее поле:

```java
@Column(name = "is_personal", nullable = false)
@Builder.Default
private Boolean isPersonal = false;
```

**Обработка значений:**
- `null` → обрабатывается как `false` (семейное событие по умолчанию)
- `true` → персональное событие
- `false` → семейное событие

## Модели данных

### Формат сообщения о событии

**Текущий формат:**
```
📌 Название события
📅 Дата: DD.MM.YYYY
🕐 Время: HH:MM
📝 Описание: текст (если есть)
```

**Новый формат:**
```
📌 Название события
📅 Дата: DD.MM.YYYY
🕐 Время: HH:MM
👨‍👩‍👧‍👦 Тип: Семейное
📝 Описание: текст (если есть)
```

или

```
📌 Название события
📅 Дата: DD.MM.YYYY
🕐 Время: HH:MM
👤 Тип: Персональное
📝 Описание: текст (если есть)
```

### Текстовые константы

```java
private static final String FAMILY_EVENT_TYPE = "👨‍👩‍👧‍👦 Тип: Семейное";
private static final String PERSONAL_EVENT_TYPE = "👤 Тип: Персональное";
```

## Свойства корректности


*Свойство - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы - по сути, формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Свойство 1: Отображение типа семейного события

*Для любого* события с полем `isPersonal = false` или `isPersonal = null`, сформированное сообщение должно содержать строку "👨‍👩‍👧‍👦 Тип: Семейное"

**Validates: Requirements 1.2, 4.1, 4.3**

### Свойство 2: Отображение типа персонального события

*Для любого* события с полем `isPersonal = true`, сформированное сообщение должно содержать строку "👤 Тип: Персональное"

**Validates: Requirements 1.3, 4.2**

### Свойство 3: Корректный порядок элементов сообщения

*Для любого* события, в сформированном сообщении индекс строки с названием должен быть меньше индекса строки с датой, индекс строки с датой должен быть меньше индекса строки с временем, индекс строки с временем должен быть меньше индекса строки с типом, и если описание присутствует, индекс строки с типом должен быть меньше индекса строки с описанием

**Validates: Requirements 1.4, 3.2**

### Свойство 4: Корректное экранирование специальных символов

*Для любого* события, все специальные символы MarkdownV2 в строке с типом события (включая двоеточие в "Тип:") должны быть экранированы обратным слешем

**Validates: Requirements 1.5, 2.1, 2.3**

### Свойство 5: Сохранение существующих элементов сообщения

*Для любого* события, сформированное сообщение должно содержать все обязательные элементы: эмодзи 📌 с названием, эмодзи 📅 с датой, эмодзи 🕐 с временем, и если описание не пустое - эмодзи 📝 с описанием

**Validates: Requirements 3.1, 3.4**

### Свойство 6: Отсутствие исключений при любом значении isPersonal

*Для любого* события с любым значением поля `isPersonal` (null, true, false), метод `buildEventMessage` должен успешно завершиться без выброса исключений

**Validates: Requirements 4.4**

## Обработка ошибок

### Существующая обработка

Метод `buildEventMessage` уже содержит проверку на null:

```java
if (event == null) {
    throw new IllegalArgumentException("Event не может быть null");
}
```

Эта проверка сохраняется без изменений.

### Обработка null значения isPersonal

Поле `isPersonal` может быть null для старых событий в базе данных. Обработка:

```java
Boolean isPersonalValue = event.getIsPersonal() != null ? event.getIsPersonal() : false;
```

Значение по умолчанию - `false` (семейное событие), что соответствует значению по умолчанию в модели `Event`.

### Обработка специальных символов

Все текстовые данные, включая новую строку с типом события, обрабатываются через `MarkdownFormatter.escape()`, что обеспечивает корректное экранирование специальных символов MarkdownV2.

## Стратегия тестирования

### Двойной подход к тестированию

Для обеспечения полного покрытия функциональности используется комбинация unit-тестов и property-based тестов:

- **Unit-тесты**: Проверяют конкретные примеры и граничные случаи
- **Property-тесты**: Проверяют универсальные свойства на множестве сгенерированных входных данных

### Unit-тесты

**Конкретные примеры:**
1. Тест с семейным событием (isPersonal = false)
2. Тест с персональным событием (isPersonal = true)
3. Тест с null значением isPersonal
4. Тест с событием без описания
5. Тест с событием с описанием

**Граничные случаи:**
- Событие с null значением isPersonal (должно отображаться как семейное)
- Событие с пустым описанием (тип должен быть последней строкой)
- Событие с описанием, содержащим специальные символы

**Проверки в unit-тестах:**
- Наличие правильного текста типа события
- Наличие правильного эмодзи
- Правильный порядок строк
- Корректное экранирование двоеточия в "Тип:"
- Сохранение всех существующих элементов сообщения

### Property-Based тесты

**Библиотека:** jqwik (уже используется в проекте)

**Конфигурация:**
- Минимум 100 итераций на каждый property-тест
- Каждый тест помечается комментарием с ссылкой на свойство из design.md

**Property-тесты для реализации:**

1. **Property 1: Отображение типа семейного события**
   ```java
   @Property(tries = 100)
   void familyEventDisplaysCorrectType(@ForAll("familyEvents") Event event)
   ```
   - Генератор: события с isPersonal = false или null
   - Проверка: сообщение содержит "👨‍👩‍👧‍👦 Тип: Семейное"
   - **Feature: event-type-display, Property 1**

2. **Property 2: Отображение типа персонального события**
   ```java
   @Property(tries = 100)
   void personalEventDisplaysCorrectType(@ForAll("personalEvents") Event event)
   ```
   - Генератор: события с isPersonal = true
   - Проверка: сообщение содержит "👤 Тип: Персональное"
   - **Feature: event-type-display, Property 2**

3. **Property 3: Корректный порядок элементов**
   ```java
   @Property(tries = 100)
   void eventMessageMaintainsCorrectOrder(@ForAll("validEvents") Event event)
   ```
   - Генератор: любые валидные события
   - Проверка: индексы подстрок в правильном порядке
   - **Feature: event-type-display, Property 3**

4. **Property 4: Корректное экранирование**
   ```java
   @Property(tries = 100)
   void eventTypeLineIsProperlyEscaped(@ForAll("validEvents") Event event)
   ```
   - Генератор: любые валидные события
   - Проверка: двоеточие в "Тип:" экранировано как "\\:"
   - **Feature: event-type-display, Property 4**

5. **Property 5: Сохранение существующих элементов**
   ```java
   @Property(tries = 100)
   void eventMessageContainsAllRequiredElements(@ForAll("validEvents") Event event)
   ```
   - Генератор: любые валидные события
   - Проверка: наличие всех эмодзи и обязательных полей
   - **Feature: event-type-display, Property 5**

6. **Property 6: Отсутствие исключений**
   ```java
   @Property(tries = 100)
   void buildEventMessageDoesNotThrowForAnyIsPersonalValue(@ForAll("validEvents") Event event)
   ```
   - Генератор: события с различными значениями isPersonal
   - Проверка: метод не выбрасывает исключений
   - **Feature: event-type-display, Property 6**

### Генераторы данных для property-тестов

```java
@Provide
Arbitrary<Event> familyEvents() {
    return validEvents()
        .map(event -> {
            event.setIsPersonal(Arbitraries.of(false, null).sample());
            return event;
        });
}

@Provide
Arbitrary<Event> personalEvents() {
    return validEvents()
        .map(event -> {
            event.setIsPersonal(true);
            return event;
        });
}

@Provide
Arbitrary<Event> validEvents() {
    return Combinators.combine(
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),
        Arbitraries.strings().alpha().ofMaxLength(500),
        validDates(),
        validTimes()
    ).as((title, description, date, time) -> 
        Event.builder()
            .title(title)
            .description(description.isEmpty() ? null : description)
            .eventDate(date)
            .eventTime(time)
            .build()
    );
}
```

### Обновление существующих тестов

Класс `BotMessageBuilderPropertyTest` уже существует и содержит property-тесты для других методов. Новые property-тесты будут добавлены в этот же класс для консистентности.

Существующие unit-тесты для `buildEventMessage` (если есть) должны быть обновлены для проверки наличия строки с типом события.

### Баланс между unit и property тестами

- **Unit-тесты** фокусируются на конкретных примерах и edge cases (null значения, пустые описания)
- **Property-тесты** обеспечивают широкое покрытие через рандомизацию входных данных
- Вместе они обеспечивают комплексную проверку: unit-тесты ловят конкретные баги, property-тесты проверяют общую корректность

## Влияние на производительность

Изменения минимальны и не влияют на производительность:
- Добавляется одна дополнительная операция `append()` в StringBuilder
- Одна дополнительная проверка значения Boolean
- Один дополнительный вызов `escape()` для константной строки

Все операции выполняются за O(1) и не добавляют заметных накладных расходов.

## Обратная совместимость

Изменения полностью обратно совместимы:
- Не меняется сигнатура метода `buildEventMessage`
- Не меняется модель `Event`
- Не требуется миграция базы данных
- Старые события с null значением isPersonal корректно обрабатываются

Единственное видимое изменение - добавление одной строки в сообщения о событиях, что является ожидаемым улучшением функциональности.
