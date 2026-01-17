# План реализации: Исправление обработки callback-запросов корзины

## Обзор

Данный план описывает пошаговую реализацию исправления обработки callback-запросов от inline-кнопок корзины. Основная задача - создать `TrashCallbackHandler`, который будет обрабатывать нажатия на кнопки "Восстановить" и "Удалить навсегда" в корзине.

## Задачи

- [x] 1. Создать TrashCallbackHandler
  - Создать новый класс `TrashCallbackHandler` в пакете `handler.callback`
  - Реализовать интерфейс `CallbackHandler`
  - Добавить аннотации `@Component`, `@RequiredArgsConstructor`, `@Slf4j`
  - Внедрить зависимости `TrashService` и `TelegramMessageService`
  - Реализовать метод `getPrefix()` для возврата `CallbackPrefix.TRASH`
  - _Requirements: 1.5, 2.1, 2.3_

- [x] 2. Реализовать основную логику обработки callback
  - [x] 2.1 Реализовать метод `handle(CallbackQuery, User)`
    - Добавить аннотацию `@HandleCallbackErrors`
    - Извлечь `callbackData`, `chatId` из `callbackQuery`
    - Определить тип операции (restore или delete) по префиксу
    - Вызвать соответствующий приватный метод
    - Добавить логирование на уровне DEBUG
    - _Requirements: 1.1, 1.2, 2.2, 2.4_

  - [x] 2.2 Реализовать метод `extractEventId(String callbackData)`
    - Определить префикс callback data (trash_restore_ или trash_delete_)
    - Извлечь eventId из строки после префикса
    - Обработать `NumberFormatException` с логированием
    - Вернуть Long eventId
    - _Requirements: 1.3_

  - [ ]* 2.3 Написать unit-тест для extractEventId
    - Тест с валидным callback data "trash_restore_123"
    - Тест с валидным callback data "trash_delete_456"
    - Тест с невалидным callback data (NumberFormatException)
    - Тест с неизвестным префиксом
    - _Requirements: 1.3_

- [x] 3. Реализовать восстановление события
  - [x] 3.1 Реализовать метод `handleRestore(Long chatId, User user, Long eventId)`
    - Вызвать `trashService.restoreEvent(eventId, user.getId())`
    - Получить восстановленное событие
    - Сформировать сообщение с использованием `MarkdownFormatter`
    - Отправить сообщение через `messageService.sendMessage()`
    - Обработать исключения (`EventNotFoundException`, `UnauthorizedAccessException`)
    - Добавить логирование на уровне INFO при успехе и ERROR при ошибке
    - _Requirements: 1.1, 1.3_

  - [ ]* 3.2 Написать unit-тест для handleRestore
    - Тест успешного восстановления события
    - Тест обработки EventNotFoundException
    - Тест обработки UnauthorizedAccessException
    - Проверка вызова TrashService.restoreEvent
    - Проверка отправки сообщения пользователю
    - _Requirements: 1.1, 1.3, 1.6_

  - [ ]* 3.3 Написать property-тест для восстановления
    - **Property 1: Восстановление события из корзины**
    - **Validates: Requirements 1.1**
    - Генератор случайных удалённых событий
    - Проверка, что для любого события восстановление работает корректно
    - Минимум 100 итераций
    - _Requirements: 1.1_

- [x] 4. Реализовать окончательное удаление события
  - [x] 4.1 Реализовать метод `handlePermanentDelete(Long chatId, User user, Long eventId)`
    - Вызвать `trashService.permanentlyDelete(eventId, user.getId())`
    - Сформировать сообщение с использованием `MarkdownFormatter`
    - Отправить сообщение через `messageService.sendMessage()`
    - Обработать исключения (`EventNotFoundException`, `UnauthorizedAccessException`)
    - Добавить логирование на уровне INFO при успехе и ERROR при ошибке
    - _Requirements: 1.2, 1.3_

  - [ ]* 4.2 Написать unit-тест для handlePermanentDelete
    - Тест успешного удаления события
    - Тест обработки EventNotFoundException
    - Тест обработки UnauthorizedAccessException
    - Проверка вызова TrashService.permanentlyDelete
    - Проверка отправки сообщения пользователю
    - _Requirements: 1.2, 1.3, 1.6_

  - [ ]* 4.3 Написать property-тест для удаления
    - **Property 2: Окончательное удаление события**
    - **Validates: Requirements 1.2**
    - Генератор случайных удалённых событий
    - Проверка, что для любого события удаление работает корректно
    - Минимум 100 итераций
    - _Requirements: 1.2_

- [x] 5. Checkpoint - Проверка базовой функциональности
  - Убедиться, что все тесты проходят
  - Проверить, что TrashCallbackHandler корректно обрабатывает callback
  - Спросить пользователя, если возникли вопросы

- [ ] 6. Тестирование интеграции с CallbackQueryDispatcher
  - [ ]* 6.1 Написать unit-тест для маршрутизации
    - Создать callback с префиксом "trash_restore_"
    - Проверить, что `CallbackQueryDispatcher.findHandler()` возвращает `TrashCallbackHandler`
    - Создать callback с префиксом "trash_delete_"
    - Проверить, что `CallbackQueryDispatcher.findHandler()` возвращает `TrashCallbackHandler`
    - _Requirements: 1.4, 2.3_

  - [ ]* 6.2 Написать property-тест для маршрутизации
    - **Property 4: Маршрутизация callback-запросов**
    - **Validates: Requirements 1.4, 2.3**
    - Генератор случайных callback data с префиксом "trash_"
    - Проверка, что для любого callback с префиксом "trash_" находится TrashCallbackHandler
    - Минимум 100 итераций
    - _Requirements: 1.4, 2.3_

- [x] 7. Рефакторинг TrashCommandHandler
  - [x] 7.1 Удалить дублирующие методы из TrashCommandHandler
    - Удалить метод `handleTrashCallback(CallbackQuery, User)`
    - Удалить метод `handleRestore(Long, User, Long)`
    - Удалить метод `handlePermanentDelete(Long, User, Long)`
    - Сохранить методы `handle()`, `createEventActionsKeyboard()`, `formatEvent()`
    - _Requirements: 3.1, 3.2, 3.3_

  - [ ]* 7.2 Написать unit-тест для TrashCommandHandler
    - Проверить, что метод `handle()` отображает список удалённых событий
    - Проверить, что метод `createEventActionsKeyboard()` создаёт кнопки с правильным callback data
    - _Requirements: 3.3, 3.4_

  - [ ]* 7.3 Написать property-тест для формата callback data
    - **Property 5: Формат callback data для кнопок корзины**
    - **Validates: Requirements 3.4**
    - Генератор случайных событий
    - Проверка, что для любого события создаются кнопки с форматом "trash_restore_{eventId}" и "trash_delete_{eventId}"
    - Минимум 100 итераций
    - _Requirements: 3.4_

- [ ] 8. Тестирование обработки ошибок
  - [ ]* 8.1 Написать property-тест для обработки ошибок
    - **Property 3: Обработка ошибок при невалидных данных**
    - **Validates: Requirements 1.3**
    - Генератор невалидных callback data (несуществующие eventId, некорректный формат)
    - Проверка, что для любых невалидных данных система отправляет сообщение об ошибке
    - Минимум 100 итераций
    - _Requirements: 1.3_

- [x] 9. Final checkpoint - Проверка всей функциональности
  - Убедиться, что все тесты проходят
  - Проверить работу в Docker-контейнере
  - Проверить логи на отсутствие ошибок
  - Спросить пользователя, если возникли вопросы

## Примечания

- Задачи, отмеченные `*`, являются опциональными и могут быть пропущены для более быстрого MVP
- Каждая задача ссылается на конкретные требования для отслеживаемости
- Checkpoints обеспечивают инкрементальную валидацию
- Property-тесты валидируют универсальные свойства корректности
- Unit-тесты валидируют конкретные примеры и граничные случаи
