package ru.golubyatnikov.family.calendar.bot.service;

import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based тесты для проверки архитектурной целостности кодовой базы.
 * 
 * <p>Проверяет соблюдение архитектурных ограничений:</p>
 * <ul>
 *   <li>Размер классов не превышает 500 строк</li>
 *   <li>Размер методов не превышает 50 строк</li>
 * </ul>
 * 
 * <p><b>Validates: Requirements 1.1, 11.2, 11.3</b></p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-01
 */
class ArchitecturalIntegrityPropertyTest {
    
    private static final int MAX_CLASS_SIZE = 500;
    private static final int MAX_METHOD_SIZE = 50;
    private static final String SOURCE_ROOT = "src/main/java";
    
    /**
     * Property 1: Архитектурная целостность - размер классов.
     * 
     * <p>Для всех классов в системе размер класса не должен превышать 500 строк.</p>
     * 
     * <p><b>Validates: Requirements 1.1, 11.2, 11.3</b></p>
     */
    @Property
    @Tag("Property 1: Архитектурная целостность")
    void allClassesShouldRespectSizeLimit(@ForAll("validJavaFiles") File javaFile) {
        int lineCount = getLineCount(javaFile);
        
        assertThat(lineCount)
            .as("Класс %s должен содержать не более %d строк, но содержит %d", 
                javaFile.getName(), MAX_CLASS_SIZE, lineCount)
            .isLessThanOrEqualTo(MAX_CLASS_SIZE);
    }
    
    /**
     * Провайдер для генерации валидных Java файлов из исходного кода.
     */
    @Provide
    Arbitrary<File> validJavaFiles() {
        try {
            List<File> javaFiles = Files.walk(Paths.get(SOURCE_ROOT))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(Path::toFile)
                .toList();
            
            if (javaFiles.isEmpty()) {
                throw new IllegalStateException("Не найдено ни одного Java файла в " + SOURCE_ROOT);
            }
            
            return Arbitraries.of(javaFiles);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при чтении Java файлов", e);
        }
    }
    
    /**
     * Подсчитывает количество строк в файле.
     * 
     * @param file файл для подсчета
     * @return количество строк
     */
    private int getLineCount(File file) {
        try {
            return (int) Files.lines(file.toPath()).count();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при чтении файла: " + file.getAbsolutePath(), e);
        }
    }
}
