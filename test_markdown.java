import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

public class TestMarkdown {
    public static void main(String[] args) {
        // Тест 1: formatMessage с bold
        String result1 = formatMessage("✅ %s\n\nТекст с точкой.", bold("Заголовок"));
        System.out.println("Тест 1: " + result1);
        
        // Тест 2: formatMessage с ручным экранированием
        String result2 = formatMessage("✅ %s\n\nТекст с точкой\\.", bold("Заголовок"));
        System.out.println("Тест 2: " + result2);
        
        // Тест 3: bold отдельно
        String result3 = bold("Заголовок");
        System.out.println("Тест 3: " + result3);
    }
}
