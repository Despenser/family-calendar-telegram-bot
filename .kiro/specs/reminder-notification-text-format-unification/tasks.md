# Implementation Plan: Reminder Notification Text Format Unification

## Overview

Реализация унификации формата уведомлений о напоминаниях и удаление функционала кастомных напоминаний. Изменения включают обновление форматирования текста, deprecation метода создания кастомных напоминаний и обновление тестов.

## Tasks

- [x] 1. Обновить метод formatReminderMessageByType
  - Изменен для показа полной версии с эмодзи 🔔
  - Добавлено название события, дата, время, описание, тип события и тип напоминания
  - Используется единый формат для всех типов напоминаний
  - Описание события показывается полностью без обрезки
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 2. Обновить метод formatShortReminderMessage
  - Реализована короткая версия с эмодзи типа напоминания (🔥/⚡/🌙)
  - Используется тире " - " перед названием события
  - Удален эмодзи типа события (👤/👨‍👩‍👧‍👦) из заголовка
  - Добавлен fallback для старых типов напоминаний
  - _Requirements: 1.1, 1.2, 1.3, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 2. Пометить метод createCustomReminder как deprecated
  - Добавить аннотацию @Deprecated
  - Изменить реализацию для выброса UnsupportedOperationException
  - Добавить информативное сообщение об ошибке
  - Добавить логирование попыток создания кастомных напоминаний
  - _Requirements: 3.1, 3.2_

- [x] 3. Обновить метод getReminderTimeInfo
  - Изменен формат текста для соответствия требованиям
  - Удалено слово "Напоминание:" из начала строк
  - Используется формат "за X минут/час до события" и "накануне вечером"
  - _Requirements: 2.2_

- [x] 4. Обновить метод handleBackToReminder в EventCallbackHandler
  - Метод использует formatShortReminderMessage для возврата к короткой версии
  - Реализована логика восстановления короткого текста напоминания
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ]* 6. Обновить unit-тесты для formatReminderMessageByType
  - [ ]* 6.1 Обновить тесты для EVENING_BEFORE (проверка эмодзи 🌙 и тире)
    - _Requirements: 1.3, 2.2, 2.3_
  
  - [ ]* 6.2 Обновить тесты для ONE_HOUR_BEFORE (проверка эмодзи ⚡ и тире)
    - _Requirements: 1.1, 2.2, 2.3_
  
  - [ ]* 6.3 Обновить тесты для FIFTEEN_MINUTES_BEFORE (проверка эмодзи 🔥 и тире)
    - _Requirements: 1.2, 2.2, 2.3_
  
  - [ ]* 6.4 Добавить тест отсутствия эмодзи типа события в заголовке
    - _Requirements: 2.1_
  
  - [ ]* 6.5 Добавить тест сохранения жирного форматирования
    - _Requirements: 4.4_

- [ ]* 7. Добавить unit-тесты для deprecated функционала
  - [ ]* 7.1 Тест выброса UnsupportedOperationException в createCustomReminder
    - _Requirements: 3.1_
  
  - [ ]* 7.2 Тест выброса IllegalArgumentException для MORNING_OF_DAY
    - _Requirements: 3.2_
  
  - [ ]* 7.3 Тест выброса IllegalArgumentException для TEN_MINUTES_BEFORE
    - _Requirements: 3.2_
  
  - [ ]* 7.4 Тест выброса IllegalArgumentException для CUSTOM
    - _Requirements: 3.2_
  
  - [ ]* 7.5 Тест fallback для старых типов в formatReminderMessageByType
    - _Requirements: 5.1, 5.2_

- [ ]* 8. Написать property-based тесты
  - [ ]* 8.1 Property test для правильных эмодзи
    - **Property 1: Правильные эмодзи для поддерживаемых типов напоминаний**
    - **Validates: Requirements 1.1, 1.2, 1.3, 2.2**
  
  - [ ]* 8.2 Property test для отсутствия эмодзи типа события
    - **Property 2: Отсутствие эмодзи типа события в заголовке**
    - **Validates: Requirements 2.1**
  
  - [ ]* 8.3 Property test для наличия тире
    - **Property 3: Наличие тире в заголовке**
    - **Validates: Requirements 2.3**
  
  - [ ]* 8.4 Property test для жирного форматирования
    - **Property 4: Сохранение жирного форматирования**
    - **Validates: Requirements 4.4**
  
  - [ ]* 8.5 Property test для информации о дате и времени
    - **Property 5: Сохранение информации о дате и времени**
    - **Validates: Requirements 4.1, 4.4**
  
  - [ ]* 8.6 Property test для информации о создателе
    - **Property 6: Сохранение информации о создателе для семейных событий**
    - **Validates: Requirements 4.3**
  
  - [ ]* 8.7 Property test для отклонения кастомных напоминаний
    - **Property 7: Отклонение создания кастомных напоминаний**
    - **Validates: Requirements 3.1, 3.2**
  
  - [ ]* 8.8 Property test для поддержки только трех типов
    - **Property 8: Поддержка только трех типов напоминаний**
    - **Validates: Requirements 3.2, 3.3**

- [x] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Задачи, помеченные `*`, являются опциональными и могут быть пропущены для более быстрого MVP
- Каждая задача ссылается на конкретные требования для отслеживаемости
- Checkpoint задачи обеспечивают инкрементальную валидацию
- Property tests валидируют универсальные свойства корректности
- Unit tests валидируют конкретные примеры и граничные случаи
- Метод createCustomReminder помечается @Deprecated вместо удаления для обратной совместимости на уровне компиляции
- Старые типы напоминаний в БД обрабатываются через fallback в default case
