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
            case "/attack":
                creature.currentAttack += effect.value;
                break;
            case "/defense":
                creature.currentDefense += effect.value;
                break;
            case "/ratePoints":
                creature.currentRatePoints += effect.value;
                break;
            default:
                System.err.println(String.format(I18n.getString("error.unknownPath"), effect.path));
        }
    }
}