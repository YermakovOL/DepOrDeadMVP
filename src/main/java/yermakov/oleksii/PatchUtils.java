package yermakov.oleksii;

public class PatchUtils {

    public static void applyEffect(Main mainApp, Main.CreatureState creature, Main.Effect effect, int targetBetId) {

        if (effect.op == null || effect.path == null || effect.value == null) {
            return;
        }

        switch (effect.op) {
            case "inc":
                applyInc(creature, effect.path, effect.value);
                break;

            case "dec_bet":
                applyDecBet(mainApp, effect.path, effect.value, targetBetId);
                break;

            default:
                // Игнорируем пустые "op"
                if (!effect.op.isEmpty()) {
                    System.err.println(String.format(I18n.getString("error.unsupportedOp"), effect.op));
                }
        }
    }

    private static void applyInc(Main.CreatureState creature, String path, int value) {
        switch (path) {
            case "/health":
                creature.currentHealth += value;
                break;
            case "/attack":
                creature.currentAttack = Math.max(1, creature.currentAttack + value);
                break;
            case "/defense":
                creature.currentDefense = Math.max(0, creature.currentDefense + value);
                break;
            case "/ratePoints":
                creature.currentRatePoints = Math.max(1, creature.currentRatePoints + value);
                break;
            default:
                if (!path.isEmpty()) {
                    System.err.println(String.format(I18n.getString("error.unknownPath"), path));
                }
        }
    }

    private static void applyDecBet(Main mainApp, String path, int value, int targetBetId) {
        if (!"/opponent_bets".equals(path)) {
            System.err.println(String.format(I18n.getString("error.unknownPath"), path));
            return;
        }

        int amountToRemove = value;
        Main.Player currentPlayer = mainApp.currentPlayer;

        if (targetBetId == 1) { // Цель - Существо 1
            if (currentPlayer == Main.Player.PLAYER_1) {
                mainApp.p2_BetsOn_C1 = Math.max(0, mainApp.p2_BetsOn_C1 - amountToRemove);
            } else {
                mainApp.p1_BetsOn_C1 = Math.max(0, mainApp.p1_BetsOn_C1 - amountToRemove);
            }
        } else { // Цель - Существо 2
            if (currentPlayer == Main.Player.PLAYER_1) {
                mainApp.p2_BetsOn_C2 = Math.max(0, mainApp.p2_BetsOn_C2 - amountToRemove);
            } else {
                mainApp.p1_BetsOn_C2 = Math.max(0, mainApp.p1_BetsOn_C2 - amountToRemove);
            }
        }

        mainApp.updateBetDisplays();
    }
}