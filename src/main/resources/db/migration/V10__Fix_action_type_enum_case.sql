-- ============================================================================
-- Миграция V10: Исправление регистра значений ENUM action_type
-- ============================================================================
-- Описание: Изменение значений ENUM action_type на верхний регистр для
--           соответствия Java ENUM
-- ============================================================================

-- Шаг 1: Добавляем временную колонку с VARCHAR типом
ALTER TABLE event_history 
    ADD COLUMN action_type_temp VARCHAR(20);

-- Шаг 2: Копируем данные с преобразованием в верхний регистр
UPDATE event_history 
    SET action_type_temp = UPPER(action_type::text);

-- Шаг 3: Удаляем старую колонку
ALTER TABLE event_history 
    DROP COLUMN action_type;

-- Шаг 4: Удаляем старый ENUM тип
DROP TYPE action_type;

-- Шаг 5: Создаем новый ENUM с правильным регистром
CREATE TYPE action_type AS ENUM ('CREATED', 'UPDATED', 'DELETED', 'RESTORED');

-- Шаг 6: Добавляем новую колонку с ENUM типом
ALTER TABLE event_history 
    ADD COLUMN action_type action_type;

-- Шаг 7: Копируем данные из временной колонки
UPDATE event_history 
    SET action_type = action_type_temp::action_type;

-- Шаг 8: Делаем колонку NOT NULL
ALTER TABLE event_history 
    ALTER COLUMN action_type SET NOT NULL;

-- Шаг 9: Удаляем временную колонку
ALTER TABLE event_history 
    DROP COLUMN action_type_temp;

-- Обновляем комментарий
COMMENT ON TYPE action_type IS 
    'Тип действия с событием: CREATED (создано), UPDATED (обновлено), DELETED (удалено), RESTORED (восстановлено)';

-- ============================================================================
-- Конец миграции V10
-- ============================================================================
