# План реализации: Исправление LazyInitializationException при возврате к напоминанию

## Обзор

Данный план описывает шаги для исправления `LazyInitializationException`, возникающей при нажатии кнопки "Назад к напоминанию". Решение включает создание метода с `@EntityGraph` для eager загрузки связанных сущностей и упрощение логики форматирования.

## Задачи

- [x] 1. Создать метод в ReminderRepository с @EntityGraph
  - Добавить метод `findWithEventAndUserById(Long id)` в `ReminderRepository`
  - Использовать `@EntityGraph(attributePaths = {"event", "event.user"})` для eager загрузки
  - Добавить JavaDoc с описанием метода и загружаемых связей
  - _Требования: 1.1, 2.1_

- [x] 2. Создать метод в ReminderService для получения напоминания с eager загрузкой
  - Добавить метод `getReminderWithEventAndUser(Long reminderId)` в `ReminderService`
  - Использовать `@Transactional(readOnly = true)` для оптимизации
  - Вызывать `reminderRepository.findWithEventAndUserById(reminderId)`
  - Выбрасывать `ReminderNotFoundException` если напоминание не найдено
  - Добавить JavaDoc с описанием метода
  - _Требования: 1.1, 2.1_

- [x] 3. Изменить EventCallbackHandler.handleBackToReminder() для использования нового метода
  - Заменить вызов `reminderService.getReminderWithEventById(reminderId)` на `reminderService.getReminderWithEventAndUser(reminderId)`
  - Удалить загрузку пользователя через `userService.findById(userId)`
  - Получать timezone из `reminder.getEvent().getUser().getTimezone()` вместо `eventOwner.getTimezone()`
  - Обновить логирование для отражения изменений
  - _Требования: 1.2, 1.3, 3.1, 3.2, 3.3_

- [x] 4. Упростить ReminderService.formatShortReminderMessage()
  - Удалить строку `ZoneId creatorTimezone = getUserTimezone(event.getUser());`
  - Заменить все использования `creatorTimezone` на `recipientTimezone`
  - Удалить создание `eventInCreatorTZ` - создавать `eventInRecipientTZ` напрямую
  - Обновить JavaDoc для отражения изменений (указать, что метод не обращается к event.user)
  - Обновить логирование - удалить упоминания `creatorTimezone` и `eventInCreatorTZ`
  - _Требования: 2.1, 2.2, 2.3_

- [ ] 5. Checkpoint - Проверить работу возврата к напоминанию
  - Запустить приложение
  - Создать событие с напоминанием
  - Нажать "Посмотреть детали" в уведомлении о напоминании
  - Нажать "Назад к напоминанию"
  - Убедиться, что LazyInitializationException не возникает
  - Убедиться, что короткое сообщение отображается корректно
  - Убедиться, что время отформатировано правильно

- [ ]* 6. Написать unit-тест для ReminderRepository.findWithEventAndUserById()
  - Создать тест в `ReminderRepositoryTest`
  - Проверить, что метод загружает напоминание с событием и пользователем
  - Проверить, что можно обратиться к `reminder.getEvent().getUser()` без LazyInitializationException
  - Использовать `@DataJpaTest` для изоляции теста
  - _Требования: 1.1, 2.3_

- [ ]* 7. Написать unit-тест для ReminderService.getReminderWithEventAndUser()
  - Создать тест в `ReminderServiceTest`
  - Проверить успешное получение напоминания
  - Проверить выброс `ReminderNotFoundException` для несуществующего ID
  - Проверить, что можно обратиться к `reminder.getEvent().getUser()` без исключения
  - _Требования: 1.1, 2.3_

- [ ]* 8. Написать unit-тест для ReminderService.formatShortReminderMessage()
  - Создать тест в `ReminderServiceTest`
  - Проверить форматирование для каждого типа напоминания (EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE)
  - Проверить, что метод не обращается к `event.getUser()` (использовать mock с lazy proxy)
  - Проверить корректное форматирование времени в разных timezone
  - Проверить fallback при ошибках
  - _Требования: 2.1, 2.2, 3.4, 4.1, 4.2, 4.3, 4.4_

- [ ]* 9. Написать интеграционный тест для возврата к напоминанию
  - Создать тест в `EventCallbackHandlerIntegrationTest`
  - Создать событие, напоминание и пользователя в тестовой БД
  - Симулировать callback query "back_to_reminder_{eventId}_{reminderId}"
  - Проверить, что метод выполняется без LazyInitializationException
  - Проверить, что сообщение отформатировано корректно
  - Проверить, что клавиатура создана корректно
  - _Требования: 3.1, 3.2, 3.3_

- [x] 10. Финальный checkpoint - Убедиться, что все работает корректно
  - Запустить все тесты проекта
  - Убедиться, что все тесты проходят
  - Проверить логи на отсутствие LazyInitializationException
  - Проверить работу возврата к напоминанию в реальном приложении

## Примечания

- Задачи, отмеченные `*`, являются опциональными и могут быть пропущены для быстрого MVP
- Каждая задача ссылается на конкретные требования для трассируемости
- Checkpoint задачи обеспечивают инкрементальную валидацию
- Тесты помогают предотвратить регрессию в будущем
