package ru.golubyatnikov.family.calendar.bot.model.dto;

/**
 * DTO для хранения информации о файле из Telegram.
 *
 * @param fileId   идентификатор файла в Telegram
 * @param fileName имя файла
 * @param fileType тип файла (document, photo, video, audio)
 * @param fileSize размер файла в байтах
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
public record FileInfo(String fileId, String fileName, String fileType, Long fileSize) { }
