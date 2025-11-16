package yermakov.oleksii;

public class PatchUtils {

    /**
     * Применяет эффект карты к состоянию существа или состоянию игры.
     * @param mainApp Ссылка на главный класс Main для доступа к ставкам (p1_BetsOn_C1 и т.д.)
     * @param creature Целевое существо
     * @param effect Эффект для применения
     * @param targetBetId ID цели (1 для C1, 2 для C2)
     */
    public static void applyEffect(Main mainApp, Main.CreatureState creature, Main.Effect effect, int targetBetId) {

        // Пропускаем пустые эффекты из JSON
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
                System.err.println(String.format(I18n.getString("error.unsupportedOp"), effect.op));
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
                System.err.println(String.format(I18n.getString("error.unknownPath"), path));
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
                // Игрок 1 атакует ставки Игрока 2 на Существо 1
                mainApp.p2_BetsOn_C1 = Math.max(0, mainApp.p2_BetsOn_C1 - amountToRemove);
            } else {
                // Игрок 2 атакует ставки Игрока 1 на Существо 1
                mainApp.p1_BetsOn_C1 = Math.max(0, mainApp.p1_BetsOn_C1 - amountToRemove);
            }
        } else { // Цель - Существо 2
            if (currentPlayer == Main.Player.PLAYER_1) {
                // Игрок 1 атакует ставки Игрока 2 на Существо 2
                mainApp.p2_BetsOn_C2 = Math.max(0, mainApp.p2_BetsOn_C2 - amountToRemove);
            } else {
                // Игрок 2 атакует ставки Игрока 1 на Существо 2
                mainApp.p1_BetsOn_C2 = Math.max(0, mainApp.p1_BetsOn_C2 - amountToRemove);
            }
        }

        // Немедленно обновляем UI ставок
        mainApp.updateBetDisplays();
    }
}