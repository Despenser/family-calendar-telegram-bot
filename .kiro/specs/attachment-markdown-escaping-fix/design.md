# Дизайн: Исправление экранирования Markdown в сообщениях о вложениях

## 1. Обзор решения

### 1.1 Архитектурный подход
Решение заключается в рефакторинге метода `handleAttachmentList()` класса `AttachmentCallbackHandler` для использования централизованного метода экранирования `MarkdownFormatter.escapeMarkdownV2()` вместо локального метода `escapeMarkdown()`.

### 1.2 Ключевые изменения
1. Удаление локального метода `escapeMarkdown()`
2. Использование `MarkdownFormatter.escapeMarkdownV2()` для всех строк, добавляемых в сообщение
3. Экранирование результата метода `formatFileSize()`
4. Экранирование отформатированной даты загрузки

## 2. Детальный дизайн

### 2.1 Изменения в методе `handleAttachmentList()`

**Текущая реализация (проблемная):**
```java
// Имя файла
String fileName = attachment.getFileName() != null ? 
        attachment.getFileName() : "Без названия";
message.append("*").append(escapeMarkdown(fileName)).append("*\n");

// Размер файла (НЕ экранирован!)
message.append("📊 Размер: ").append(formatFileSize(attachment.getFileSize())).append("\n");

// Дата загрузки (НЕ экранирована!)
message.append("📅 Загружено: ")
       .append(attachment.getUploadedAt().format(DATE_TIME_FORMATTER));
```

**Новая реализация:**
```java
// Имя файла
String fileName = attachment.getFileName() != null ? 
        attachment.getFileName() : "Без названия";
message.append("*").append(MarkdownFormatter.escapeMarkdownV2(fileName)).append("*\n");

// Размер файла (экранирован)
message.append("📊 Размер: ")
       .append(MarkdownFormatter.escapeMarkdownV2(formatFileSize(attachment.getFileSize())))
       .append("\n");

// Дата загрузки (экранирована)
String formattedDate = attachment.getUploadedAt().format(DATE_TIME_FORMATTER);
message.append("📅 Загружено: ")
       .append(MarkdownFormatter.escapeMarkdownV2(formattedDate));
```

### 2.2 Удаление локального метода `escapeMarkdown()`

**Метод для удаления:**
```java
private String escapeMarkdown(String text) {
    if (text == null) {
        return "";
    }
    
    return text.replace("_", "\\_")
               .replace("*", "\\*")
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
```

**Обоснование удаления:**
- Дублирует функциональность `MarkdownFormatter.escapeMarkdownV2()`
- Нарушает принцип DRY (Don't Repeat Yourself)
- Усложняет поддержку кода

### 2.3 Изменения в методе `handleDeleteFile()`

**Текущая реализация:**
```java
String fileName = attachment.getFileName() != null ? 
        attachment.getFileName() : "Без названия";
String message = "⚠️ *Подтверждение удаления*\n\n" +
               "Вы действительно хотите удалить вложение?\n\n" +
               "📎 " + escapeMarkdown(fileName);
```

**Новая реализация:**
```java
String fileName = attachment.getFileName() != null ? 
        attachment.getFileName() : "Без названия";
String message = "⚠️ *Подтверждение удаления*\n\n" +
               "Вы действительно хотите удалить вложение?\n\n" +
               "📎 " + MarkdownFormatter.escapeMarkdownV2(fileName);
```

### 2.4 Изменения в методе `handleViewFile()`

**Текущая реализация (проблемная):**
```java
// Формируем caption с именем файла (НЕ экранирован!)
String caption = attachment.getFileName() != null ? 
        attachment.getFileName() : "Вложение";

// Отправляем файл через TelegramMessageService
messageService.sendFile(chatId, attachment.getFileId(), 
        attachment.getFileType(), caption);
```

**Проблема:** В `TelegramMessageService.sendPhoto()` (и аналогичных методах) caption используется с `parseMode="MarkdownV2"`:
```java
if (caption != null && !caption.isBlank()) {
    sendPhoto.setCaption(caption);
    sendPhoto.setParseMode("MarkdownV2");
}
```

**Новая реализация:**
```java
// Формируем caption с именем файла (экранирован)
String fileName = attachment.getFileName() != null ? 
        attachment.getFileName() : "Вложение";
String caption = MarkdownFormatter.escapeMarkdownV2(fileName);

// Отправляем файл через TelegramMessageService
messageService.sendFile(chatId, attachment.getFileId(), 
        attachment.getFileType(), caption);
```

**Обоснование:**
- Caption отправляется с `parseMode="MarkdownV2"` в `TelegramMessageService`
- Все специальные символы в caption должны быть экранированы
- Имена файлов часто содержат точки (расширения, даты и т.д.)

## 3. Свойства корректности

### 3.1 Property: Все специальные символы экранированы
**Описание:** Любая строка, добавленная в сообщение о вложениях, должна иметь все специальные символы MarkdownV2 экранированными.

**Формальное определение:**
```
∀ text ∈ MessageParts:
  containsSpecialChars(text) ⟹ isEscaped(text)
```

**Тестовая стратегия:**
- Генерировать случайные имена файлов со специальными символами
- Генерировать различные размеры файлов (КБ и МБ)
- Генерировать различные даты
- Проверять, что все специальные символы экранированы

### 3.2 Property: Экранирование не изменяет отображаемый текст
**Описание:** Экранированный текст должен отображаться в Telegram так же, как и оригинальный текст (без управляющих символов).

**Формальное определение:**
```
∀ text ∈ Strings:
  displayedText(escapeMarkdownV2(text)) = text
```

**Тестовая стратегия:**
- Проверять, что точки в датах отображаются как точки
- Проверять, что точки в размерах отображаются как точки
- Проверять, что двоеточия в времени отображаются как двоеточия

### 3.3 Property: Сообщение успешно парсится Telegram API
**Описание:** Любое сообщение о вложениях должно успешно парситься Telegram API без ошибок.

**Формальное определение:**
```
∀ attachments ∈ AttachmentLists:
  message = buildAttachmentMessage(attachments)
  ⟹ telegramAPI.parse(message) = Success
```

**Тестовая стратегия:**
- Интеграционные тесты с реальным Telegram API (или mock)
- Проверка различных комбинаций вложений
- Проверка граничных случаев (пустой список, один элемент, много элементов)

## 4. Структура данных

### 4.1 Входные данные
```java
class Attachment {
    String fileName;        // Может содержать любые символы
    Long fileSize;          // В байтах
    LocalDateTime uploadedAt; // Дата и время загрузки
    String fileType;        // photo, video, audio, document
}
```

### 4.2 Выходные данные
```java
String message; // Экранированное сообщение в формате MarkdownV2
```

### 4.3 Примеры трансформации

**Пример 1: Имя файла с точками**
- Вход: `"photo_1769005286492.jpg"`
- Выход: `"photo\\_1769005286492\\.jpg"`

**Пример 2: Размер файла**
- Вход: `53299` (байт)
- Промежуточный результат: `"52.05 КБ"`
- Выход: `"52\\.05 КБ"`

**Пример 3: Дата загрузки**
- Вход: `LocalDateTime.of(2026, 1, 21, 14, 21)`
- Промежуточный результат: `"21.01.2026 14:21"`
- Выход: `"21\\.01\\.2026 14:21"` (двоеточие не экранируется, так как не является специальным символом в MarkdownV2)

**Исправление:** Двоеточие НЕ является специальным символом в MarkdownV2, поэтому не требует экранирования.

## 5. Алгоритм

### 5.1 Алгоритм формирования сообщения о вложениях

```
function buildAttachmentMessage(attachments: List<Attachment>): String
    message = "📎 *Вложения события*\n\n"
    
    if attachments.isEmpty():
        message += "_У этого события пока нет вложений_"
        return message
    
    for i = 0 to attachments.size() - 1:
        attachment = attachments[i]
        
        if i > 0:
            message += "\n━━━━━━━━━━━━━━━━━━━━\n\n"
        
        // Эмодзи для типа файла
        emoji = getFileTypeEmoji(attachment.fileType)
        message += emoji + " "
        
        // Имя файла (экранировано)
        fileName = attachment.fileName != null ? attachment.fileName : "Без названия"
        message += "*" + escapeMarkdownV2(fileName) + "*\n"
        
        // Размер файла (экранирован)
        fileSize = formatFileSize(attachment.fileSize)
        message += "📊 Размер: " + escapeMarkdownV2(fileSize) + "\n"
        
        // Дата загрузки (экранирована)
        formattedDate = attachment.uploadedAt.format(DATE_TIME_FORMATTER)
        message += "📅 Загружено: " + escapeMarkdownV2(formattedDate)
    
    return message
```

### 5.2 Сложность алгоритма
- **Временная сложность:** O(n × m), где n - количество вложений, m - средняя длина строк
- **Пространственная сложность:** O(n × m) для хранения результирующего сообщения

## 6. Обработка ошибок

### 6.1 Null-безопасность
- Метод `MarkdownFormatter.escapeMarkdownV2()` корректно обрабатывает null (возвращает пустую строку)
- Имя файла проверяется на null перед экранированием
- Размер файла проверяется на null в методе `formatFileSize()`

### 6.2 Ошибки форматирования
- Если дата не может быть отформатирована, будет выброшено исключение `DateTimeException`
- Обработка происходит на уровне аспекта `@HandleCallbackErrors`

## 7. Тестирование

### 7.1 Unit-тесты

**Тест 1: Экранирование имени файла**
```java
@Test
void testFileNameEscaping() {
    String fileName = "photo.2026.01.21.jpg";
    String escaped = MarkdownFormatter.escapeMarkdownV2(fileName);
    assertEquals("photo\\.2026\\.01\\.21\\.jpg", escaped);
}
```

**Тест 2: Экранирование размера файла**
```java
@Test
void testFileSizeEscaping() {
    String fileSize = "52.05 КБ";
    String escaped = MarkdownFormatter.escapeMarkdownV2(fileSize);
    assertEquals("52\\.05 КБ", escaped);
}
```

**Тест 3: Экранирование даты**
```java
@Test
void testDateEscaping() {
    String date = "21.01.2026 14:21";
    String escaped = MarkdownFormatter.escapeMarkdownV2(date);
    assertEquals("21\\.01\\.2026 14:21", escaped);
}
```

### 7.2 Property-based тесты

**Property Test 1: Все специальные символы экранированы**
```java
@Property
void allSpecialCharsAreEscaped(@ForAll String text) {
    String escaped = MarkdownFormatter.escapeMarkdownV2(text);
    
    // Проверяем, что все специальные символы экранированы
    char[] specialChars = {'_', '*', '[', ']', '(', ')', '~', '`', 
                          '>', '#', '+', '-', '=', '|', '{', '}', '.', '!'};
    
    for (char c : specialChars) {
        if (text.contains(String.valueOf(c))) {
            assertTrue(escaped.contains("\\" + c),
                "Символ '" + c + "' должен быть экранирован");
        }
    }
}
```

**Property Test 2: Сообщение о вложениях корректно формируется**
```java
@Property
void attachmentMessageIsWellFormed(@ForAll List<@From("validAttachments") Attachment> attachments) {
    String message = buildAttachmentMessage(attachments);
    
    // Проверяем, что сообщение не содержит неэкранированных специальных символов
    // (кроме тех, что находятся внутри форматирования)
    assertDoesNotThrow(() -> validateMarkdownV2(message));
}
```

### 7.3 Интеграционные тесты

**Тест 1: Отправка сообщения с вложениями в Telegram**
```java
@Test
void testSendAttachmentMessageToTelegram() {
    // Создаем тестовые вложения
    List<Attachment> attachments = createTestAttachments();
    
    // Формируем сообщение
    String message = buildAttachmentMessage(attachments);
    
    // Отправляем в Telegram (mock или реальный API)
    assertDoesNotThrow(() -> 
        telegramMessageService.sendMessage(chatId, message));
}
```

## 8. Миграция и развертывание

### 8.1 План миграции
1. Внести изменения в `AttachmentCallbackHandler`
2. Запустить все тесты
3. Провести ручное тестирование с реальными вложениями
4. Развернуть в production

### 8.2 Откат изменений
В случае проблем можно откатить изменения через Git:
```bash
git revert <commit-hash>
```

### 8.3 Мониторинг
- Отслеживать логи на наличие ошибок парсинга MarkdownV2
- Мониторить метрики успешности отправки сообщений

## 9. Альтернативные решения

### 9.1 Альтернатива 1: Использование HTML вместо MarkdownV2
**Плюсы:**
- Меньше проблем с экранированием
- Более предсказуемое поведение

**Минусы:**
- Требует изменения всех сообщений в приложении
- Большой объем работы
- Риск регрессии

**Решение:** Отклонено из-за большого объема изменений

### 9.2 Альтернатива 2: Отключение форматирования для проблемных частей
**Плюсы:**
- Простое решение
- Минимальные изменения

**Минусы:**
- Ухудшение пользовательского опыта
- Непоследовательное форматирование

**Решение:** Отклонено из-за ухудшения UX

## 10. Зависимости

### 10.1 Существующие компоненты
- `MarkdownFormatter.escapeMarkdownV2()` - используется для экранирования
- `TelegramMessageService` - используется для отправки сообщений
- `AttachmentService` - используется для получения вложений

### 10.2 Новые зависимости
Нет новых зависимостей

## 11. Документация

### 11.1 Обновление JavaDoc
Обновить JavaDoc метода `handleAttachmentList()` с указанием, что все строки экранируются через `MarkdownFormatter.escapeMarkdownV2()`.

### 11.2 Обновление README
Не требуется, так как это внутреннее исправление.

## 12. Метрики успеха

### 12.1 Функциональные метрики
- ✅ 0 ошибок парсинга MarkdownV2 при отображении вложений
- ✅ 100% успешных отправок сообщений о вложениях

### 12.2 Технические метрики
- ✅ Удаление дублирующего кода (метод `escapeMarkdown()`)
- ✅ Использование централизованного метода экранирования
- ✅ 100% покрытие тестами измененного кода
