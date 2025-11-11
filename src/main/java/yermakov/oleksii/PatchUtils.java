package yermakov.oleksii;

// Этот класс применяет наши кастомные эффекты ("inc")
public class PatchUtils {

    public static void applyEffect(Main.CreatureState creature, Main.Effect effect) {
        if (!"inc".equals(effect.op)) {
            // Мы пока поддерживаем только "inc"
            System.err.println("Неподдерживаемая операция: " + effect.op);
            return;
        }

        switch (effect.path) {
            case "/health":
                creature.currentHealth += effect.value;
                break;
            case "/attack":
                creature.currentAttack += effect.value;
                break;
            case "/defense":
                creature.currentDefense += effect.value;
                break;
            default:
                System.err.println("Неизвестный путь: " + effect.path);
        }
    }
}
