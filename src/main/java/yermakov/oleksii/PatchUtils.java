package yermakov.oleksii;

public class PatchUtils {

    public static void applyEffect(Main.CreatureState creature, Main.Effect effect) {
        if (!"inc".equals(effect.op)) {
            System.err.println(String.format(I18n.getString("error.unsupportedOp"), effect.op));
            return;
        }

        switch (effect.path) {
            case "/health":
                creature.currentHealth += effect.value;
                break;
            // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1) Атака не ниже 1 ---
            case "/attack":
                creature.currentAttack = Math.max(1, creature.currentAttack + effect.value);
                break;
            // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1) Защита не ниже 0 ---
            case "/defense":
                creature.currentDefense = Math.max(0, creature.currentDefense + effect.value);
                break;
            case "/ratePoints":
                int newRp = creature.currentRatePoints + effect.value;
                creature.currentRatePoints = Math.max(1, newRp); // Оставляем 1, как было
                break;
            default:
                System.err.println(String.format(I18n.getString("error.unknownPath"), effect.path));
        }
    }
}