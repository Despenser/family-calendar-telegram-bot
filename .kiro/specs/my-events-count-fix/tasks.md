# План реализации: Исправление подсчета событий в команде /my_events

- [x] 1. Добавить метод подсчета активных событий в EventRepository




  - Добавить метод `countByUserIdAndStatus(Long userId, Event.EventStatus status)` в интерфейс EventRepository
  - Метод должен использовать Spring Data JPA naming convention для автоматической генерации запроса
  - Добавить JavaDoc с описанием метода
  - _Requirements: 1.1_

- [ ]* 1.1 Написать unit-тест для метода countByUserIdAndStatus
  - **Property 1: Подсчет соответствует отображению**
  - **Validates: Requirements 1.1, 1.2**


- [x] 2. Изменить метод getActiveEventsCount в EventService




  - Изменить реализацию метода `getActiveEventsCount` для использования нового метода репозитория
  - Заменить вызов `countByUserIdAndStatusNot(userId, Event.EventStatus.DELETED)` на `countByUserIdAndStatus(userId, Event.EventStatus.ACTIVE)`
  - Обновить JavaDoc с уточнением, что метод считает только события со статусом ACTIVE
  - Обновить log-сообщение для ясности
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ]* 2.1 Написать unit-тест для метода getActiveEventsCount
  - **Property 2: Исключение неактивных событий**
  - **Validates: Requirements 1.1**

- [ ]* 3. Написать интеграционный тест для команды /my_events
  - Создать пользователя с событиями разных статусов (2 ACTIVE, 1 DRAFT, 1 COMPLETED, 1 DELETED)
  - Вызвать команду /my_events
  - Проверить, что в шапке отображается "Всего событий: 2"
  - Проверить, что отображается ровно 2 события
  - **Property 3: Консистентность после редактирования**
  - **Validates: Requirements 1.2, 1.3, 1.4**


- [x] 4. Checkpoint - Убедиться, что все тесты проходят





  - Ensure all tests pass, ask the user if questions arise.
