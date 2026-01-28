-- ============================================================================
-- Миграция V22: Изменение типа колонки reminder_type с ENUM на VARCHAR
-- ============================================================================
-- Описание: Преобразование reminder_type ENUM в VARCHAR для совместимости с Hibernate
-- Причина: Hibernate EnumType.STRING не работает корректно с PostgreSQL ENUM типами
-- Требования: Исправление ошибки создания событий с напоминаниями
-- ============================================================================

-- Шаг 1: Удаляем constraint, который использует reminder_type
ALTER TABLE reminders DROP CONSTRAINT IF EXISTS reminders_custom_logic;

-- Шаг 2: Изменяем тип колонки reminder_type на VARCHAR
ALTER TABLE reminders ALTER COLUMN reminder_type TYPE VARCHAR(50);

-- Шаг 3: Пересоздаём constraint с новым типом данных
ALTER TABLE reminders ADD CONSTRAINT reminders_custom_logic CHECK (
    (reminder_type = 'CUSTOM' AND custom_minutes IS NOT NULL) OR
    (reminder_type != 'CUSTOM' AND custom_minutes IS NULL)
);

-- Шаг 4: Удаляем старый ENUM тип
DROP TYPE IF EXISTS reminder_type;

-- ============================================================================
-- Примечания:
-- 
-- 1. VARCHAR(50) достаточно для хранения всех значений enum ReminderType
-- 2. Существующие данные автоматически преобразуются в VARCHAR
-- 3. Hibernate будет корректно работать с VARCHAR типом через EnumType.STRING
-- 4. Миграции V20 и V21 уже обновили данные на UPPER_CASE значения
-- 5. Constraint обновлён для работы с UPPER_CASE значениями ('CUSTOM')
-- ============================================================================
-- Конец миграции V22
-- ============================================================================
