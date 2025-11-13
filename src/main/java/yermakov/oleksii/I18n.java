package yermakov.oleksii;

import java.util.Locale;
import java.util.ResourceBundle;

public class I18n {

    private static Locale currentLocale = Locale.of("ru");
    private static ResourceBundle messages = ResourceBundle.getBundle("messages", currentLocale);

    public static void setLocale(String language) {
        currentLocale = Locale.of(language);    
        messages = ResourceBundle.getBundle("messages", currentLocale);
    }

    public static String getString(String key) {
        try {
            return messages.getString(key);
        } catch (Exception e) {
            return "!" + key + "!"; // Показать ошибку, если ключ не найден
        }
    }

    public static String getLang() {
        return currentLocale.getLanguage();
    }
}