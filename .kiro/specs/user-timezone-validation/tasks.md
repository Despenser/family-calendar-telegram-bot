# План реализации: Валидация дат с учетом таймзоны пользователя

## Overview

Реализация поддержки таймзон пользователей для корректной валидации дат и времени событий. Задачи организованы в логическом порядке: сначала изменения БД и модели, затем сервисы, затем обработчики, и наконец тестирование.

## Tasks

- [x] 1. Создать миграцию базы данных для добавления поля timezone
  - Создать файл V19__Add_timezone_to_users.sql
  - Добавить колонку timezone VARCHAR(50) с default значением 'Europe/Moscow'
  - Добавить комментарий к колонке
  - Убедиться, что миграция идемпотентна
  - _Requirements: 5.1, 5.2, 5.3_

- [x] 2. Обновить модель User для поддержки timezone
  - [x] 2.1 Добавить поле timezone в класс User
    - Добавить поле timezone типа String с default значением "Europe/Moscow"
    - Добавить аннотацию @Column с параметрами
    - Добавить JavaDoc комментарий
    - _Requirements: 1.1_

  - [x] 2.2 Добавить вспомогательные методы для работы с timezone
    - Добавить метод getZoneId() для получения ZoneId
    - Добавить метод getCurrentDate() для получения текущей даты в timezone пользователя
    - Добавить метод getCurrentDateTime() для получения текущего времени в timezone пользователя
    - Добавить JavaDoc комментарии
    - _Requirements: 1.1, 2.1, 4.1_

  - [ ]* 2.3 Написать unit тесты для методов User
    - Тест для getZoneId() с разными timezone
    - Тест для getCurrentDate() с разными timezone
    - Тест для getCurrentDateTime() с разными timezone
    - _Requirements: 1.1, 2.1_

- [x] 3. Обновить UserService для работы с timezone
  - [x] 3.1 Добавить валидацию timezone
    - Создать метод validateAndNormalizeTimezone()
    - Обработать null, пустые строки и невалидные timezone
    - Возвращать default timezone при ошибках
    - Добавить логирование
    - _Requirements: 1.3_

  - [x] 3.2 Обновить метод createUser для сохранения timezone
    - Добавить параметр timezone в сигнатуру метода
    - Вызвать validateAndNormalizeTimezone()
    - Установить timezone при создании User
    - Обновить логирование
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 3.3 Добавить метод updateTimezone
    - Создать метод updateTimezone(Long userId, String timezone)
    - Валидировать timezone
    - Обновить User в БД
    - Добавить логирование
    - _Requirements: 1.4_

  - [ ]* 3.4 Написать property тест для валидации timezone
    - **Property 2: Default timezone fallback**
    - **Validates: Requirements 1.3**
    - Генерировать невалидные timezone строки
    - Проверять, что используется default timezone
    - _Requirements: 1.3_

  - [ ]* 3.5 Написать property тест для сохранения timezone
    - **Property 1: Timezone persistence**
    - **Validates: Requirements 1.1**
    - Генерировать валидные timezone
    - Создавать пользователя, сохранять, извлекать
    - Проверять, что timezone совпадает
    - _Requirements: 1.1_

  - [ ]* 3.6 Написать property тест для обновления timezone
    - **Property 3: Timezone update**
    - **Validates: Requirements 1.4**
    - Генерировать пользователей и timezone
    - Обновлять timezone, извлекать
    - Проверять, что timezone обновлена
    - _Requirements: 1.4_

- [x] 4. Checkpoint - Убедиться, что базовая функциональность timezone работает
  - Запустить все тесты
  - Проверить, что миграция применяется корректно
  - Убедиться, что пользователи создаются с timezone
  - Спросить пользователя, если возникли вопросы

- [x] 5. Обновить KeyboardService для использования timezone пользователя
  - [x] 5.1 Изменить сигнатуру createCalendarKeyboard
    - Изменить параметр с (int year, int month, Long familyId) на (int year, int month, User user)
    - Извлекать familyId из user.getFamily()
    - Получать ZoneId из user.getZoneId()
    - _Requirements: 2.1_

  - [x] 5.2 Обновить логику определения текущей даты
    - Заменить LocalDate.now() на user.getCurrentDate()
    - Обновить сравнение дат для определения прошлых дат
    - Обновить логирование с информацией о timezone
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 5.3 Обновить отображение текущей даты
    - Убедиться, что индикатор 📍 добавляется для user.getCurrentDate()
    - _Requirements: 2.5_

  - [ ]* 5.4 Написать property тест для валидации дат в календаре
    - **Property 4: Calendar date validation**
    - **Validates: Requirements 2.1, 2.2, 2.3**
    - Генерировать пользователей с разными timezone
    - Создавать календари
    - Проверять, что прошлые даты пустые, будущие - кнопки
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ]* 5.5 Написать property тест для индикатора текущей даты
    - **Property 5: Current date indicator**
    - **Validates: Requirements 2.5**
    - Генерировать пользователей с разными timezone
    - Создавать календари
    - Проверять, что текущая дата содержит 📍
    - _Requirements: 2.5_

- [x] 6. Обновить вызовы createCalendarKeyboard в обработчиках
  - [x] 6.1 Обновить EventTypeCallbackHandler
    - Изменить вызов createCalendarKeyboard для передачи user вместо familyId
    - _Requirements: 2.1_

  - [x] 6.2 Обновить EventCallbackHandler
    - Изменить вызов createCalendarKeyboard для передачи user вместо familyId
    - _Requirements: 2.1_

  - [x] 6.3 Обновить NavigationCallbackHandler
    - Изменить вызов createCalendarKeyboard для передачи user вместо familyId
    - _Requirements: 2.1_

- [x] 7. Обновить DateTimeCallbackHandler для валидации времени
  - [x] 7.1 Добавить валидацию времени для сегодняшнего дня
    - В методе handleTimeSelection получить User
    - Получить дату события из черновика
    - Если дата == user.getCurrentDate(), проверить время
    - Если время < user.getCurrentDateTime().toLocalTime(), показать ошибку
    - Вернуть пользователя к выбору часа
    - _Requirements: 3.1, 3.3_

  - [x] 7.2 Создать метод в BotMessageBuilder для сообщения об ошибке
    - Создать метод buildPastTimeErrorMessage(LocalTime selected, LocalTime current)
    - Вернуть сообщение "Нельзя выбрать время в прошлом. Текущее время: HH:mm, выбрано: HH:mm"
    - _Requirements: 3.3_

  - [ ]* 7.3 Написать property тест для валидации времени
    - **Property 6: Time validation for today**
    - **Validates: Requirements 3.1, 3.2**
    - Генерировать пользователей с разными timezone
    - Генерировать времена (прошлые и будущие)
    - Проверять, что прошлые времена отклоняются, будущие принимаются
    - _Requirements: 3.1, 3.2_

  - [ ]* 7.4 Написать unit тест для сообщения об ошибке времени
    - Тест для buildPastTimeErrorMessage с разными временами
    - Проверить формат сообщения
    - _Requirements: 3.3_

- [x] 8. Checkpoint - Убедиться, что валидация дат и времени работает
  - Запустить все тесты
  - Проверить, что календарь показывает правильные даты
  - Проверить, что валидация времени работает
  - Спросить пользователя, если возникли вопросы

- [x] 9. Обновить EventService для форматирования с учетом timezone
  - [x] 9.1 Добавить метод isToday с учетом timezone
    - Создать метод isToday(LocalDate eventDate, User user)
    - Сравнить eventDate с user.getCurrentDate()
    - _Requirements: 4.2_

  - [x] 9.2 Добавить метод isTomorrow с учетом timezone
    - Создать метод isTomorrow(LocalDate eventDate, User user)
    - Сравнить eventDate с user.getCurrentDate().plusDays(1)
    - _Requirements: 4.2_

  - [x] 9.3 Обновить методы форматирования событий
    - Использовать isToday() и isTomorrow() вместо прямого сравнения с LocalDate.now()
    - Обновить все места, где используется LocalDate.now() для сравнения дат
    - _Requirements: 4.1, 4.2_

  - [ ]* 9.4 Написать property тест для относительных меток дат
    - **Property 8: Relative date labels**
    - **Validates: Requirements 4.2**
    - Генерировать события с датами сегодня/завтра/другие
    - Проверять правильность меток
    - _Requirements: 4.2_

  - [ ]* 9.5 Написать property тест для группировки событий
    - **Property 9: Event grouping by date**
    - **Validates: Requirements 4.3**
    - Генерировать события и пользователей с разными timezone
    - Группировать события
    - Проверять, что группировка использует user timezone
    - _Requirements: 4.3_

- [x] 10. Обновить StartCommandHandler для извлечения timezone
  - [x] 10.1 Добавить метод extractTimezoneFromMessage
    - Создать метод для извлечения timezone из Telegram Message
    - Вернуть null (Telegram API не предоставляет timezone)
    - Добавить комментарий о возможных улучшениях
    - _Requirements: 1.2_

  - [x] 10.2 Обновить метод handle для передачи timezone
    - Вызвать extractTimezoneFromMessage()
    - Передать timezone в userService.createUser()
    - Обновить логирование
    - _Requirements: 1.2_

  - [ ]* 10.3 Написать unit тест для регистрации с timezone
    - Тест для создания пользователя с timezone
    - Тест для создания пользователя без timezone (default)
    - _Requirements: 1.2, 1.3_

- [x] 11. Обновить существующие тесты
  - [x] 11.1 Обновить KeyboardServiceTest
    - Обновить вызовы createCalendarKeyboard для передачи User
    - Создать mock User с timezone для тестов
    - _Requirements: 2.1_

  - [x] 11.2 Обновить DateTimeCallbackHandlerTest
    - Добавить тесты для валидации времени
    - Создать mock User с timezone
    - _Requirements: 3.1_

  - [x] 11.3 Обновить EventServiceTest
    - Обновить тесты для методов isToday/isTomorrow
    - Создать mock User с timezone
    - _Requirements: 4.2_

- [x] 12. Checkpoint - Финальная проверка
  - Запустить все тесты (unit, property, integration)
  - Проверить, что все property тесты проходят минимум 100 итераций
  - Убедиться, что нет регрессий в существующей функциональности
  - Проверить логирование миграции
  - Спросить пользователя, если возникли вопросы

- [x] 13. Документация и финализация
  - [x] 13.1 Обновить JavaDoc комментарии
    - Добавить комментарии к новым методам
    - Обновить комментарии к измененным методам
    - Указать требования в комментариях
    - _Requirements: All_

  - [x] 13.2 Обновить README (если необходимо)
    - Добавить информацию о поддержке timezone
    - Описать default timezone
    - _Requirements: All_

## Notes

- Все задачи являются обязательными для полного покрытия функциональности
- Каждая задача ссылается на конкретные требования для отслеживаемости
- Checkpoint задачи обеспечивают инкрементальную валидацию
- Property тесты валидируют универсальные свойства корректности
- Unit тесты валидируют конкретные примеры и граничные случаи
- Все property тесты должны выполняться минимум 100 итераций
