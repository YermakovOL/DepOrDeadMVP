package yermakov.oleksii;

import java.util.concurrent.ThreadLocalRandom;

public class DiceUtils {

    private static final int DICE_SIDES = 6;

    /**
     * Бросает указанное количество 6-гранных кубиков.
     * @param diceCount Количество кубиков (н.п. 1, 2, 3)
     * @return Сумма результатов
     */
    public static int rollD6(int diceCount) {
        if (diceCount < 1) {
            return 0;
        }

        int total = 0;
        for (int i = 0; i < diceCount; i++) {
            // nextInt(min, max) -> min (включительно), max (исключительно)
            total += ThreadLocalRandom.current().nextInt(1, DICE_SIDES + 1);
        }
        return total;
    }
}