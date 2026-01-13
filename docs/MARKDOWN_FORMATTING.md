# Руководство по форматированию сообщений MarkdownV2

## Содержание

1. [Введение](#введение)
2. [Специальные символы MarkdownV2](#специальные-символы-markdownv2)
3. [Класс MarkdownFormatter](#класс-markdownformatter)
4. [Примеры использования](#примеры-использования)
5. [Правила и лучшие практики](#правила-и-лучшие-практики)
6. [Частые ошибки](#частые-ошибки)
7. [Отладка проблем](#отладка-проблем)
8. [Тестирование](#тестирование)

---

## Введение

Telegram Bot API использует формат **MarkdownV2** для форматирования текста в сообщениях. Этот формат позволяет создавать жирный текст, курсив, моноширинный шрифт и другие стили.

Однако MarkdownV2 имеет набор специальных символов, которые **обязательно** должны быть экранированы обратным слешем (`\`). Неэкранированные символы приводят к ошибкам парсинга и сообщения не отправляются.

### Пример проблемы

```java
// ❌ НЕПРАВИЛЬНО
String message = "Дата выбрана: 12.01.2026";
telegramMessageService.sendMessage(chatId, message);
// Ошибка: Bad Request: can't parse entities: Character '.' is reserved
```

```java
// ✅ ПРАВИЛЬНО
String message = MarkdownFormatter.formatMessage("Дата выбрана: %s", "12.01.2026");
telegramMessageService.sendMessage(chatId, message);
// Результат: "Дата выбрана: 12\\.01\\.2026"
```

---

## Специальные символы MarkdownV2

Следующие символы имеют особое значение в MarkdownV2 и **должны быть экранированы**:

```
_  *  [  ]  (  )  ~  `  >  #  +  -  =  |  {  }  .  !
```

### Таблица специальных символов

| Символ | Назначение в MarkdownV2 | Экранированный вид |
|--------|-------------------------|-------------------|
| `_` | Курсив | `\_` |
| `*` | Жирный текст | `\*` |
| `[` `]` | Ссылки | `\[` `\]` |
| `(` `)` | Ссылки | `\(` `\)` |
| `~` | Зачеркнутый текст | `\~` |
| `` ` `` | Моноширинный текст | `` \` `` |
| `>` | Цитата | `\>` |
| `#` | Заголовок | `\#` |
| `+` `-` `=` | Списки | `\+` `\-` `\=` |
| `\|` | Таблицы | `\|` |
| `{` `}` | Группировка | `\{` `\}` |
| `.` | Списки | `\.` |
| `!` | Восклицание | `\!` |

### Символы, НЕ требующие экранирования

- **Эмодзи**: 👋 ✅ 📅 🔔 и т.д.
- **Кириллица**: все русские буквы
- **Латиница**: все английские буквы
- **Цифры**: 0-9
- **Пробелы и переносы строк**: ` ` `\n` `\t`
- **Другие Unicode символы**: большинство не требуют экранирования

---

## Класс MarkdownFormatter

Класс `MarkdownFormatter` предоставляет набор утилитных методов для безопасного форматирования сообщений.

### Основные методы

#### 1. `escape(String text)`

Экранирует все специальные символы в строке.

```java
String escaped = MarkdownFormatter.escape("Цена: 100$! Скидка 50%.");
// Результат: "Цена: 100\\$\\! Скидка 50%\\."
```

**Когда использовать:**
- Для экранирования отдельных строк
- Когда нужно экранировать только часть сообщения
- Для подготовки данных перед конкатенацией

#### 2. `formatMessage(String template, Object... args)`

Форматирует сообщение с автоматическим экранированием всех частей.

```java
String message = MarkdownFormatter.formatMessage(
    "Событие: %s\nДата: %s",
    eventTitle,
    eventDate
);
```

**Когда использовать:**
- Для формирования сообщений с переменными (рекомендуется)
- Когда нужно экранировать и шаблон, и аргументы
- Для замены `String.format()` в коде бота

**Важно:** Метод поддерживает только плейсхолдер `%s`. Все аргументы преобразуются в строки через `toString()`.

#### 3. Методы стилизации

```java
// Жирный текст
String bold = MarkdownFormatter.bold("Важное сообщение");
// Результат: "*Важное сообщение*"

// Курсив
String italic = MarkdownFormatter.italic("Примечание");
// Результат: "_Примечание_"

// Моноширинный шрифт
String code = MarkdownFormatter.code("/add_event");
// Результат: "`/add_event`"

// Жирный курсив
String boldItalic = MarkdownFormatter.boldItalic("Очень важно");
// Результат: "*_Очень важно_*"
```

**Когда использовать:**
- Для выделения важных частей сообщения
- Для форматирования команд и кода
- Для создания заголовков и акцентов

---

## Примеры использования

### Пример 1: Простое сообщение с датой

```java
// Дата содержит точки, которые нужно экранировать
String date = "12.01.2026";

// ❌ НЕПРАВИЛЬНО
String message = "Дата: " + date;

// ✅ ПРАВИЛЬНО - вариант 1
String message = "Дата: " + MarkdownFormatter.escape(date);

// ✅ ПРАВИЛЬНО - вариант 2 (рекомендуется)
String message = MarkdownFormatter.formatMessage("Дата: %s", date);
```

### Пример 2: Сообщение с несколькими переменными

```java
String eventTitle = "Встреча с врачом!";
String eventDate = "15.01.2026";
String eventTime = "14:30";

// ✅ ПРАВИЛЬНО
String message = MarkdownFormatter.formatMessage(
    "📅 Событие создано!\n\n" +
    "Название: %s\n" +
    "Дата: %s\n" +
    "Время: %s",
    eventTitle,
    eventDate,
    eventTime
);
```

### Пример 3: Сообщение со стилизацией

```java
String eventTitle = "Важная встреча";
String date = "20.01.2026";

String header = MarkdownFormatter.bold("Напоминание");
String title = MarkdownFormatter.italic(eventTitle);
String dateFormatted = MarkdownFormatter.escape(date);

String message = header + "\n\n" +
                "Событие: " + title + "\n" +
                "Дата: " + dateFormatted;
```

### Пример 4: Список событий

```java
List<Event> events = eventService.getUpcomingEvents();

StringBuilder message = new StringBuilder();
message.append(MarkdownFormatter.bold("Предстоящие события:")).append("\n\n");

for (Event event : events) {
    String line = MarkdownFormatter.formatMessage(
        "• %s - %s в %s\n",
        event.getTitle(),
        event.getDate(),
        event.getTime()
    );
    message.append(line);
}
```

### Пример 5: Сообщение с командой

```java
String command = MarkdownFormatter.code("/add_event");
String message = MarkdownFormatter.formatMessage(
    "Для создания события используйте команду %s",
    command
);
// Результат: "Для создания события используйте команду `/add_event`"
```

### Пример 6: Обработка callback query

```java
@Override
public void handleCallback(CallbackQuery callbackQuery) {
    String selectedDate = callbackQuery.getData().split(":")[1];
    
    String message = MarkdownFormatter.formatMessage(
        "✅ Дата выбрана: %s\n\nТеперь выберите час:",
        selectedDate
    );
    
    telegramMessageService.editMessage(
        callbackQuery.getMessage().getChatId(),
        callbackQuery.getMessage().getMessageId(),
        message,
        keyboardService.createHourKeyboard()
    );
}
```

---

## Правила и лучшие практики

### ✅ Правильные практики

1. **Всегда используйте `formatMessage()` для сообщений с переменными**

```java
// Хорошо
String message = MarkdownFormatter.formatMessage(
    "Событие: %s на %s в %s",
    title, date, time
);
```

2. **Используйте `escape()` для отдельных строк**

```java
// Хорошо
String escapedTitle = MarkdownFormatter.escape(event.getTitle());
String message = "Событие: " + escapedTitle;
```

3. **Используйте методы стилизации для форматирования**

```java
// Хорошо
String header = MarkdownFormatter.bold("Важно");
String note = MarkdownFormatter.italic("Примечание");
```

4. **Экранируйте данные из базы данных**

```java
// Хорошо - данные из БД могут содержать специальные символы
String title = event.getTitle(); // может быть "Встреча!"
String message = MarkdownFormatter.formatMessage("Событие: %s", title);
```

5. **Экранируйте пользовательский ввод**

```java
// Хорошо - пользователь может ввести любые символы
String userInput = update.getMessage().getText();
String message = MarkdownFormatter.formatMessage("Вы ввели: %s", userInput);
```

### ❌ Неправильные практики

1. **НЕ используйте `String.format()` напрямую для MarkdownV2 сообщений**

```java
// Плохо - специальные символы не экранированы
String message = String.format("Дата: %s", date);

// Плохо - даже с escape() для аргументов, статический текст не экранирован
String message = String.format("Событие создано! Дата: %s", escape(date));
```

**⚠️ ВАЖНО:** Использование `String.format()` для формирования MarkdownV2 сообщений **ЗАПРЕЩЕНО**. Это приводит к ошибкам парсинга в Telegram API. Всегда используйте `formatMessage()` из `MarkdownFormatter`.

2. **НЕ забывайте экранировать статический текст**

```java
// Плохо - восклицательный знак и точки не экранированы
String message = "Событие создано! Дата: " + MarkdownFormatter.escape(date);

// Хорошо
String message = MarkdownFormatter.formatMessage("Событие создано! Дата: %s", date);
```

3. **НЕ применяйте двойное экранирование**

```java
// Плохо - двойное экранирование
String escaped = MarkdownFormatter.escape(date);
String message = MarkdownFormatter.formatMessage("Дата: %s", escaped);

// Хорошо
String message = MarkdownFormatter.formatMessage("Дата: %s", date);
```

4. **НЕ конкатенируйте неэкранированные строки**

```java
// Плохо
String message = "Цена: 100$! " + "Скидка: 50%.";

// Хорошо
String message = MarkdownFormatter.escape("Цена: 100$! Скидка: 50%.");
```

5. **НЕ используйте сырые строки для сообщений**

```java
// Плохо
telegramMessageService.sendMessage(chatId, "Дата: 12.01.2026");

// Хорошо
String message = MarkdownFormatter.formatMessage("Дата: %s", "12.01.2026");
telegramMessageService.sendMessage(chatId, message);
```

---

## Частые ошибки

### Ошибка 1: Неэкранированные точки в датах

**Проблема:**
```java
String date = "12.01.2026";
String message = "Дата: " + date;
// Ошибка: Character '.' is reserved
```

**Решение:**
```java
String message = MarkdownFormatter.formatMessage("Дата: %s", date);
```

### Ошибка 2: Неэкранированные восклицательные знаки

**Проблема:**
```java
String message = "Событие создано!";
// Ошибка: Character '!' is reserved
```

**Решение:**
```java
String message = MarkdownFormatter.escape("Событие создано!");
```

### Ошибка 3: Неэкранированные дефисы в списках

**Проблема:**
```java
String message = "События:\n- Событие 1\n- Событие 2";
// Ошибка: Character '-' is reserved
```

**Решение:**
```java
String message = MarkdownFormatter.formatMessage(
    "События:\n- %s\n- %s",
    event1,
    event2
);
```

### Ошибка 4: Смешивание экранированных и неэкранированных частей

**Проблема:**
```java
String escapedDate = MarkdownFormatter.escape(date);
String message = "Дата: " + escapedDate + ". Время: " + time;
// Точка после даты не экранирована
```

**Решение:**
```java
String message = MarkdownFormatter.formatMessage("Дата: %s. Время: %s", date, time);
```

### Ошибка 5: Использование неподдерживаемых плейсхолдеров

**Проблема:**
```java
String message = MarkdownFormatter.formatMessage("Событий: %d", count);
// IllegalArgumentException: только %s поддерживается
```

**Решение:**
```java
String message = MarkdownFormatter.formatMessage("Событий: %s", String.valueOf(count));
// или
String message = MarkdownFormatter.formatMessage("Событий: %s", count);
```

---

## Отладка проблем

### Шаг 1: Проверьте логи

Когда Telegram возвращает ошибку парсинга, она логируется с превью текста:

```bash
docker-compose logs app | grep -i "Bad Request"
docker-compose logs app | grep -i "can't parse entities"
```

Пример лог-записи:
```
Bad Request (400): Ошибка парсинга MarkdownV2. 
telegramId=526536667, textPreview=✅ Дата выбрана: 12.01.2026
Теперь выберите час:, response=Bad Request: can't parse entities: 
Character '.' is reserved and must be escaped with the preceding '\'
```

### Шаг 2: Найдите проблемный код

Используйте превью текста из логов для поиска места в коде:

```bash
# Поиск по тексту сообщения
grep -r "Дата выбрана" src/main/java
```

### Шаг 3: Проверьте форматирование

Убедитесь, что используется `MarkdownFormatter`:

```java
// Найдите код вроде этого
String message = String.format("✅ Дата выбрана: %s", date);

// Замените на
String message = MarkdownFormatter.formatMessage("✅ Дата выбрана: %s", date);
```

### Шаг 4: Тестируйте локально

Создайте unit тест для проверки форматирования:

```java
@Test
void testDateFormatting() {
    String date = "12.01.2026";
    String message = MarkdownFormatter.formatMessage("Дата: %s", date);
    
    // Проверяем, что точки экранированы
    assertTrue(message.contains("\\."));
    
    // Проверяем, что нет неэкранированных точек
    assertFalse(message.matches(".*[^\\\\]\\..*"));
}
```

### Инструменты отладки

#### 1. Визуализация экранирования

```java
public static void debugEscape(String text) {
    String escaped = MarkdownFormatter.escape(text);
    System.out.println("Исходный текст: " + text);
    System.out.println("Экранированный: " + escaped);
    System.out.println("Длина: " + text.length() + " -> " + escaped.length());
}
```

#### 2. Проверка специальных символов

```java
public static void checkSpecialChars(String text) {
    char[] specialChars = {'_', '*', '[', ']', '(', ')', '~', '`', 
                          '>', '#', '+', '-', '=', '|', '{', '}', '.', '!'};
    
    for (char c : specialChars) {
        if (text.indexOf(c) >= 0) {
            System.out.println("Найден специальный символ: " + c);
        }
    }
}
```

---

## Тестирование

### Unit тесты

Всегда пишите тесты для сообщений с форматированием:

```java
@Test
void testEventCreationMessage() {
    String title = "Встреча!";
    String date = "15.01.2026";
    String time = "14:30";
    
    String message = MarkdownFormatter.formatMessage(
        "Событие: %s\nДата: %s\nВремя: %s",
        title, date, time
    );
    
    // Проверяем, что восклицательный знак экранирован
    assertTrue(message.contains("Встреча\\!"));
    
    // Проверяем, что точки в дате экранированы
    assertTrue(message.contains("15\\.01\\.2026"));
    
    // Проверяем, что двоеточие в времени экранировано
    assertTrue(message.contains("14:30")); // двоеточие не требует экранирования
}
```

### Property-based тесты

Используйте property-based тестирование для проверки на случайных данных:

```java
@Property
void allSpecialCharactersAreEscaped(@ForAll String text) {
    String escaped = MarkdownFormatter.escape(text);
    
    char[] specialChars = {'_', '*', '[', ']', '(', ')', '~', '`', 
                          '>', '#', '+', '-', '=', '|', '{', '}', '.', '!'};
    
    for (char special : specialChars) {
        int index = text.indexOf(special);
        if (index >= 0) {
            int escapedIndex = escaped.indexOf(special);
            assertTrue(escapedIndex > 0 && escaped.charAt(escapedIndex - 1) == '\\',
                "Символ '" + special + "' не экранирован");
        }
    }
}
```

### Integration тесты

Тестируйте отправку сообщений с реальным форматированием:

```java
@Test
void testSendFormattedMessage() {
    String date = "12.01.2026";
    String message = MarkdownFormatter.formatMessage("Дата: %s", date);
    
    // Отправляем сообщение (с моком TelegramMessageService)
    telegramMessageService.sendMessage(chatId, message);
    
    // Проверяем, что сообщение отправлено без ошибок
    verify(telegramMessageService).sendMessage(eq(chatId), contains("\\."));
}
```

---

## Примеры реальных исправлений

### Исправление 1: Сообщение об удалении события (MyEventsCommandHandler)

**Было (неправильно):**
```java
return String.format("✅ %s\n\n" +
       "Событие успешно удалено из календаря.\n\n" +
       "Используйте %s для просмотра оставшихся событий.",
       bold("Событие удалено"), escape("/my_events"));
```

**Проблема:** Статический текст "Событие успешно удалено из календаря." содержит точки, которые не экранированы.

**Стало (правильно):**
```java
return formatMessage("✅ %s\n\n" +
       "Событие успешно удалено из календаря.\n\n" +
       "Используйте %s для просмотра оставшихся событий.",
       bold("Событие удалено"), code("/my_events"));
```

**Результат:** Все точки в статическом тексте автоматически экранируются методом `formatMessage()`.

### Исправление 2: Сообщение корзины (TrashCommandHandler)

**Было (неправильно):**
```java
messageBuilder.append(formatMessage("🗑️ %s\n\n", bold("Корзина")));
messageBuilder.append(italic("Удаленные события хранятся 30 дней")).append("\n\n");
```

**Проблема:** Текст "Удаленные события хранятся 30 дней" передается в `italic()` без экранирования, а затем конкатенируется с неэкранированными символами переноса строки.

**Стало (правильно):**
```java
messageBuilder.append(formatMessage("🗑️ %s\n\n", bold("Корзина")));
messageBuilder.append(formatMessage("%s\n\n", italic("Удаленные события хранятся 30 дней")));
```

**Результат:** Весь текст, включая статические части, корректно экранируется.

### Исправление 3: Список событий с датами

**Было (неправильно):**
```java
String header = String.format("📋 %s\n\nВсего событий: %d\n", 
        bold("Мои события"), userEvents.size());
```

**Проблема:** Использование `String.format` с `%d` для числа, статический текст не экранирован.

**Стало (правильно):**
```java
String header = formatMessage("📋 %s\n\nВсего событий: %s\n", 
        bold("Мои события"), userEvents.size());
```

**Результат:** Используется `%s` вместо `%d`, все части сообщения экранируются.

### Исправление 4: Детали события

**Было (неправильно):**
```java
details.append(String.format("📅 Дата: %s\n", escape(event.getFormattedDate())));
details.append(String.format("🕐 Время: %s\n", escape(event.getFormattedTime())));
```

**Проблема:** Статический текст "Дата:" и "Время:" содержит двоеточия, которые не экранированы.

**Стало (правильно):**
```java
details.append(formatMessage("📅 Дата: %s\n", escape(event.getFormattedDate())));
details.append(formatMessage("🕐 Время: %s\n", escape(event.getFormattedTime())));
```

**Результат:** Все специальные символы в статическом тексте экранируются.

---

## Дополнительные ресурсы

- [Telegram Bot API - MarkdownV2 style](https://core.telegram.org/bots/api#markdownv2-style)
- [Javadoc MarkdownFormatter](../src/main/java/ru/golubyatnikov/family/calendar/bot/util/MarkdownFormatter.java)
- [Тесты MarkdownFormatter](../src/test/java/ru/golubyatnikov/family/calendar/bot/util/MarkdownFormatterTest.java)
- [SETUP.md - Работа с MarkdownV2](../SETUP.md#работа-с-markdownv2-форматированием)

---

## Чеклист для разработчиков

Перед коммитом кода, проверьте:

- [ ] Все сообщения с переменными используют `formatMessage()`
- [ ] Все статические сообщения со специальными символами экранированы
- [ ] Нет использования `String.format()` для формирования сообщений
- [ ] Нет двойного экранирования
- [ ] Написаны unit тесты для новых сообщений
- [ ] Проверено локально с реальными данными
- [ ] Логи не содержат ошибок парсинга MarkdownV2

---

**Последнее обновление:** 12 января 2026

**Версия:** 1.0
