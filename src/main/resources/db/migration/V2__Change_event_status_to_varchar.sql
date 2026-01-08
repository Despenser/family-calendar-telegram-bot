-- ============================================================================
-- Миграция V2: Изменение типа колонки status с ENUM на VARCHAR
-- ============================================================================
-- Описание: Преобразование event_status ENUM в VARCHAR для совместимости с Hibernate
-- Причина: Hibernate EnumType.STRING не работает корректно с PostgreSQL ENUM типами
-- ============================================================================

-- Шаг 1: Создаем временную колонку с типом VARCHAR
ALTER TABLE events 
    ADD COLUMN status_temp VARCHAR(50);

-- Шаг 2: Копируем данные из старой колонки в новую, преобразуя ENUM в текст
UPDATE events 
    SET status_temp = status::text;

-- Шаг 3: Удаляем старую колонку
ALTER TABLE events 
    DROP COLUMN status;

-- Шаг 4: Переименовываем временную колонку
ALTER TABLE events 
    RENAME COLUMN status_temp TO status;

-- Шаг 5: Устанавливаем NOT NULL constraint
ALTER TABLE events 
    ALTER COLUMN status SET NOT NULL;

-- Шаг 6: Устанавливаем значение по умолчанию
ALTER TABLE events 
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

-- Шаг 7: Добавляем CHECK constraint для валидации значений
ALTER TABLE events 
    ADD CONSTRAINT events_status_check CHECK (status IN ('DRAFT', 'ACTIVE'));

-- Шаг 8: Удаляем ENUM тип event_status, так как он больше не используется
DROP TYPE event_status;

-- Комментарий для колонки
COMMENT ON COLUMN events.status IS 'Статус события: DRAFT (черновик) или ACTIVE (активное)';

-- ============================================================================
-- Конец миграции V2
-- ============================================================================
