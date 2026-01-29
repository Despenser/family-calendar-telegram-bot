# Implementation Plan: Callback Popup Messages Unification

## Overview

Данный план описывает пошаговую реализацию унификации всплывающих сообщений в Telegram боте. Реализация будет выполняться инкрементально: сначала создание инфраструктуры (классы констант), затем тестирование, и наконец миграция существующих callback handlers.

## Tasks

- [x] 1. Создать класс CallbackMessages с константами сообщений
  - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/util/CallbackMessages.java`
  - Добавить все константы для категорий: успех, ошибки, отмены, информация, подтверждения
  - Добавить специфичные константы (TOO_LATE_TODAY, REMINDER_NEEDS_TIME и т.д.)
  - Добавить JavaDoc комментарии для каждой константы
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.3, 3.1, 3.2, 3.4, 4.1, 4.2, 5.1, 6.2_

- [ ]* 1.1 Написать unit тесты для констант CallbackMessages
  - Создать файл `src/test/java/ru/golubyatnikov/family/calendar/bot/util/CallbackMessagesTest.java`
  - Проверить значения всех констант
  - Проверить, что константы не null и не пустые (кроме EMPTY)
  - _Requirements: 1.1, 2.3, 3.2, 3.4, 4.2, 6.2_

- [ ]* 1.2 Написать property тест для эмодзи в категориях
  - **Property 1: Категории сообщений используют правильные эмодзи**
  - **Validates: Requirements 1.5, 2.1, 3.1, 4.1, 5.1**
  - Использовать jqwik для генерации тестовых данных
  - Проверить, что все константы успеха начинаются с ✅
  - Проверить, что все константы ошибок начинаются с ❌
  - Проверить, что все константы отмены начинаются с 🚫
  - Проверить, что все информационные константы начинаются с ℹ️
  - _Requirements: 1.5, 2.1, 3.1, 4.1, 5.1_

- [ ]* 1.3 Написать property тест для непустых значений констант
  - **Property 3: Константы имеют непустые значения**
  - **Validates: Requirements 1.1**
  - Использовать рефлексию для получения всех публичных констант
  - Проверить, что каждая константа (кроме EMPTY) не null и не пустая
  - _Requirements: 1.1_

- [x] 2. Создать класс CallbackMessageFormatter с форматирующими методами
  - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/util/CallbackMessageFormatter.java`
  - Реализовать метод `notFound(String entityName)`
  - Реализовать метод `validationError(String reason)`
  - Реализовать метод `actionCancelled(String action)`
  - Реализовать метод `hint(String hint)`
  - Реализовать метод `selectPrompt(String item)`
  - Реализовать метод `itemSelected(String item)`
  - Добавить JavaDoc комментарии для каждого метода
  - _Requirements: 2.4, 3.3, 3.5, 4.3, 5.1, 6.3_

- [ ]* 2.1 Написать unit тесты для CallbackMessageFormatter
  - Создать файл `src/test/java/ru/golubyatnikov/family/calendar/bot/util/CallbackMessageFormatterTest.java`
  - Проверить форматирование для конкретных примеров
  - Проверить обработку null параметров
  - Проверить обработку пустых строк
  - Проверить обработку специальных символов
  - _Requirements: 2.4, 3.3, 3.5, 4.3, 6.3_

- [ ]* 2.2 Написать property тест для форматирующих методов
  - **Property 2: Форматирующие методы создают сообщения в правильном формате**
  - **Validates: Requirements 2.4, 3.3, 3.5, 4.3, 6.3**
  - Использовать jqwik для генерации случайных строк
  - Проверить формат для каждого метода с помощью regex
  - Проверить, что результат содержит входной параметр
  - Проверить, что результат содержит правильный префикс/суффикс
  - _Requirements: 2.4, 3.3, 3.5, 4.3, 6.3_

- [ ] 3. Checkpoint - Убедиться, что все тесты проходят
  - Запустить все unit и property тесты
  - Убедиться, что инфраструктура работает корректно
  - Спросить пользователя, если возникли вопросы

- [x] 4. Мигрировать ReminderCallbackHandler на новые константы
  - Открыть файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/ReminderCallbackHandler.java`
  - Заменить все строковые литералы на константы из CallbackMessages
  - Использовать CallbackMessageFormatter для форматируемых сообщений
  - Сохранить семантическую эквивалентность сообщений
  - _Requirements: 2.2, 2.5, 3.7, 4.4, 5.4, 6.4, 7.1, 7.2, 7.3_

- [x] 5. Мигрировать ReminderCallbackHandlerImpl на новые константы
  - Открыть файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/ReminderCallbackHandlerImpl.java`
  - Заменить все строковые литералы на константы из CallbackMessages
  - Использовать CallbackMessageFormatter для форматируемых сообщений
  - _Requirements: 2.2, 2.5, 3.7, 4.4, 5.4, 6.4, 7.1, 7.2, 7.3_

- [x] 6. Мигрировать TextEventCallbackHandler на новые константы
  - Открыть файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/TextEventCallbackHandler.java`
  - Заменить все строковые литералы на константы из CallbackMessages
  - _Requirements: 2.2, 2.5, 3.7, 4.4, 7.1, 7.2, 7.3_

- [x] 7. Мигрировать RecurrenceCallbackHandler на новые константы
  - Открыть файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/RecurrenceCallbackHandler.java`
  - Заменить все строковые литералы на константы из CallbackMessages
  - _Requirements: 2.2, 2.5, 7.1, 7.2, 7.3_

- [x] 8. Мигрировать ChecklistCallbackHandler на новые константы
  - Открыть файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/ChecklistCallbackHandler.java`
  - Заменить все строковые литералы на константы из CallbackMessages
  - _Requirements: 2.2, 2.5, 3.7, 7.1, 7.2, 7.3_

- [x] 9. Мигрировать AttachmentCallbackHandler на новые константы
  - Открыть файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/AttachmentCallbackHandler.java`
  - Заменить все строковые литералы на константы из CallbackMessages
  - Использовать CallbackMessageFormatter для форматируемых сообщений
  - _Requirements: 2.2, 2.5, 3.7, 4.4, 7.1, 7.2, 7.3_

- [x] 10. Мигрировать EventCallbackHandler на новые константы
  - Открыть файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/EventCallbackHandler.java`
  - Заменить все строковые литералы на константы из CallbackMessages
  - Использовать CallbackMessageFormatter для форматируемых сообщений
  - _Requirements: 2.2, 2.5, 3.7, 4.4, 7.1, 7.2, 7.3_

- [x] 11. Мигрировать DateTimeCallbackHandler на новые константы
  - Открыть файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/DateTimeCallbackHandler.java`
  - Заменить все строковые литералы на константы из CallbackMessages
  - Использовать CallbackMessageFormatter для форматируемых сообщений
  - _Requirements: 2.2, 2.5, 6.4, 7.1, 7.2, 7.3_

- [x] 12. Мигрировать CommentCallbackHandler на новые константы
  - Открыть файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/CommentCallbackHandler.java`
  - Заменить все строковые литералы на константы из CallbackMessages
  - _Requirements: 2.2, 2.5, 7.1, 7.2, 7.3_

- [ ] 13. Checkpoint - Убедиться, что все тесты проходят после миграции
  - Запустить все существующие тесты callback handlers
  - Запустить все unit и property тесты для CallbackMessages
  - Убедиться, что миграция не нарушила функциональность
  - Спросить пользователя, если возникли вопросы

- [ ]* 14. Создать документацию маппинга старых и новых сообщений
  - Создать файл `docs/CALLBACK_MESSAGES_MIGRATION.md`
  - Добавить таблицу маппинга из design.md
  - Добавить список всех затронутых callback handlers
  - Добавить примеры использования новых констант
  - Добавить обоснование решений по унификации
  - _Requirements: 8.1, 8.2, 8.3, 8.4_

- [x] 15. Финальный checkpoint - Проверка завершения
  - Убедиться, что все callback handlers мигрированы
  - Убедиться, что все тесты проходят
  - Убедиться, что документация создана
  - Провести code review изменений
  - Спросить пользователя о готовности к деплою

## Notes

- Задачи, помеченные `*`, являются опциональными и могут быть пропущены для более быстрого MVP
- Каждая задача ссылается на конкретные требования для отслеживаемости
- Checkpoint задачи обеспечивают инкрементальную валидацию
- Property тесты валидируют универсальные свойства корректности
- Unit тесты валидируют конкретные примеры и граничные случаи
- Миграция выполняется по одному handler за раз для минимизации рисков
