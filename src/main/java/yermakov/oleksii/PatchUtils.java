package yermakov.oleksii;

public class PatchUtils {

    public static void applyEffect(Main.CreatureState creature, Main.Effect effect) {
        if (!"inc".equals(effect.op)) {
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
            case "/ratePoints":
                // --- ИЗМЕНЕНИЕ: (ЗАПРОС 2) RP не может быть < 1 ---
                int newRp = creature.currentRatePoints + effect.value;
                creature.currentRatePoints = Math.max(1, newRp);
                // --- КОНЕЦ ИЗМЕНЕНИЯ ---
                break;
            default:
                System.err.println("Неизвестный путь: " + effect.path);
        }
    }
}