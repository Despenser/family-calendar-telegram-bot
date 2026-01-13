-- Изменение типа колонки action_type с ENUM на VARCHAR
-- Это необходимо для корректной работы с Hibernate

-- Изменяем тип колонки action_type на VARCHAR
ALTER TABLE event_history ALTER COLUMN action_type TYPE VARCHAR(20);

-- Удаляем старый ENUM тип
DROP TYPE IF EXISTS action_type;
