# Implementation Plan: Reminder Type ENUM Case Fix

## Overview

Исправление несоответствия регистра значений ENUM reminder_type путем создания миграции базы данных, которая добавит UPPER_CASE значения и обновит существующие данные.

## Tasks

- [x] 1. Создать миграцию для обновления ENUM reminder_type
  - Создать файл V20__Fix_reminder_type_enum_case.sql
  - Добавить новые значения ENUM в UPPER_CASE формате
  - Обновить существующие записи в таблице reminders
  - Добавить комментарии к миграции
  - _Requirements: 1.1, 1.2, 1.3, 2.1_

- [x] 2. Checkpoint - Проверить работу миграции
  - Перезапустить Docker контейнеры
  - Проверить, что миграция выполнилась успешно
  - Проверить логи на отсутствие ошибок
  - Создать тестовое событие с напоминанием через UI
  - Убедиться, что напоминание сохраняется без ошибок

- [ ]* 3. Написать property test для round-trip consistency
  - **Property 1: Round-trip consistency for ReminderType**
  - **Validates: Requirements 3.3**
  - Генерировать случайные Reminder объекты
  - Сохранять в базу и читать обратно
  - Проверять, что ReminderType не изменился
  - Минимум 100 итераций

- [ ]* 4. Написать unit test для всех ReminderType значений
  - Тестировать сохранение Reminder с каждым ReminderType
  - Тестировать чтение Reminder с каждым ReminderType
  - Проверять отсутствие database errors
  - _Requirements: 1.1, 1.2, 2.1_

- [x] 5. Final checkpoint - Убедиться, что все работает
  - Запустить все тесты
  - Проверить работу в Docker
  - Убедиться, что ошибка "column reminder_type is of type reminder_type but expression is of type character varying" больше не возникает

## Notes

- Задачи, помеченные `*`, являются опциональными и могут быть пропущены для быстрого MVP
- Миграция должна быть безопасной и не ломать существующие данные
- Старые значения ENUM остаются в базе для обратной совместимости, но не используются приложением
