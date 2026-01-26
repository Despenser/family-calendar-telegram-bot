# Requirements Document

## Introduction

Исправление подсчета "Всего событий" в статистике команды /stats. В настоящее время счетчик показывает только активные события, но должен показывать сумму активных и завершенных событий.

## Glossary

- **Statistics_Service**: Сервис для получения статистики по событиям семьи
- **Stats_Command_Handler**: Обработчик команды /stats для отображения статистики
- **Total_Events**: Общее количество событий (активных + завершенных)
- **Active_Events**: События со статусом ACTIVE
- **Completed_Events**: События со статусом COMPLETED

## Requirements

### Requirement 1: Корректный подсчет общего количества событий

**User Story:** As a user, I want to see the correct total count of events in statistics, so that I can understand how many events I had in total (both active and completed).

#### Acceptance Criteria

1. WHEN Statistics_Service calculates monthly statistics, THEN Total_Events SHALL equal the sum of Active_Events and Completed_Events
2. WHEN Stats_Command_Handler displays statistics, THEN the "Всего событий" field SHALL show the sum of active and completed events
3. WHEN Stats_Command_Handler displays statistics, THEN the text "(только активные)" SHALL be removed from the "Всего событий" line
4. WHEN calculating completion percentage, THEN the system SHALL use Total_Events (active + completed) as the denominator
