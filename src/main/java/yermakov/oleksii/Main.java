package yermakov.oleksii;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle; // <-- ДОБАВЬТЕ ЭТОТ ИМПОРТ
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Main extends Application {

    // --- КОНСТАНТЫ ИГРЫ ---
    private static final String CREATURES_FILE = "creatures.json";
    private static final String INFLUENCE_FILE = "influenceCards.json";
    private static final int STARTING_HAND_SIZE = 5;
    private static final int MAX_HAND_SIZE = 5;
    private static final int MAX_TURN_POINTS = 4;
    private static final int MAX_ROUNDS = 4;
    private int currentRound = 1;

    // --- КОНСТАНТЫ БОЯ (ЗАПРОС 1) ---
    private static final int ATTACK_TIER_1_MAX = 6;
    private static final int ATTACK_TIER_2_MAX = 14;

    // --- КОНСТАНТЫ НАГРАД (ЗАПРОС 2) ---
    private static final int BET_REWARD_GREEN_THRESHOLD = 4;
    private static final int BET_REWARD_RED_THRESHOLD = 8;
    private static final double REWARD_YELLOW_MULT = 1.0;
    private static final double REWARD_GREEN_MULT = 2.0;
    private static final double REWARD_RED_MULT = 3.0;

    // --- СПИСКИ КАРТ ---
    private final Map<String, CardData> allCards = new HashMap<>();
    private final List<CardData> creatureTemplates = new ArrayList<>();
    private final List<CardData> influenceDeck = new ArrayList<>();

    // --- СОСТОЯНИЕ (STATE) ---
    private CreatureState creature1State;
    private CreatureState creature2State;
    private enum Player { PLAYER_1, PLAYER_2 }
    private Player currentPlayer;
    private final List<CardData> player1Hand = new ArrayList<>();
    private final List<CardData> player2Hand = new ArrayList<>();
    private int currentTurnPointsUsed = 0;
    private int p1_BetsOn_C1 = 0;
    private int p2_BetsOn_C1 = 0;
    private int p1_BetsOn_C2 = 0;
    private int p2_BetsOn_C2 = 0;

    // --- UI REFERENCES ---
    private HBox handBox;
    private Text turnPointsText;
    private VBox creature1Pane;
    private VBox creature2Pane;
    private VBox centralDropZone1;
    private VBox centralDropZone2;
    private Text creature1BetText;
    private Text creature2BetText;

    // --- UI ШКАЛЫ (ЗАПРОС 1 и 2) ---
    private HBox attackScale1;
    private HBox attackScale2;
    private VBox betRewardScaleUI;
    private HBox betRewardScaleC1Row;
    private HBox betRewardScaleC2Row;


    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        try {
            loadDataWithJackson();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Критическая ошибка", "Не удалось загрузить данные из ресурсов: " + e.getMessage());
            return;
        }

        startGame();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));

        creature1Pane = createCreaturePane(creature1State);
        creature2Pane = createCreaturePane(creature2State);

        creature1BetText = new Text();
        creature1BetText.getStyleClass().add("bet-total-text");
        creature2BetText = new Text();
        creature2BetText.getStyleClass().add("bet-total-text");
        updateBetDisplays();

        // --- НОВЫЙ UI: (ЗАПРОС 1) Шкалы атаки ---
        attackScale1 = createAttackScale();
        attackScale2 = createAttackScale();

        VBox creature1Column = new VBox(5, creature1Pane, creature1BetText, attackScale1);
        creature1Column.setAlignment(Pos.CENTER);
        VBox creature2Column = new VBox(5, creature2Pane, creature2BetText, attackScale2);
        creature2Column.setAlignment(Pos.CENTER);
        // --- КОНЕЦ НОВОГО UI ---

        centralDropZone1 = createCentralDropZone(creature1State, creature1Pane, 1);
        centralDropZone2 = createCentralDropZone(creature2State, creature2Pane, 2);

        HBox centerCreatures = new HBox(15);
        centerCreatures.setAlignment(Pos.CENTER);
        centerCreatures.setPadding(new Insets(10));
        centerCreatures.getChildren().addAll(
                creature1Column,
                centralDropZone1,
                centralDropZone2,
                creature2Column
        );
        root.setTop(centerCreatures);

        VBox bottom = new VBox(10);
        bottom.setPadding(new Insets(8));
        bottom.setAlignment(Pos.CENTER);

        Button endTurnBtn = new Button("Завершить ход");
        endTurnBtn.setMinWidth(180);
        endTurnBtn.setOnAction(e -> endTurn());

        HBox buttonBar = new HBox(10, endTurnBtn);
        buttonBar.setAlignment(Pos.CENTER);

        // --- НОВЫЙ UI: (ЗАПРОС 2) Шкала наград ---
        betRewardScaleUI = createBetRewardScale();
        // --- КОНЕЦ НОВОГО UI ---

        turnPointsText = new Text();
        turnPointsText.getStyleClass().add("card-title");
        updateTurnPointsText();

        updateAllScales(); // Первоначальная отрисовка шкал

        handBox = new HBox(8);
        handBox.setPadding(new Insets(8));
        handBox.setAlignment(Pos.CENTER);

        ScrollPane handScroll = new ScrollPane(handBox);
        handScroll.setPrefHeight(170);
        handScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        handScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        handScroll.setFitToHeight(true);
        handScroll.setMaxWidth(800);

        bottom.getChildren().addAll(buttonBar, betRewardScaleUI, turnPointsText, handScroll);
        root.setBottom(bottom);

        updateHandDisplay();

        Scene scene = new Scene(root, 1200, 800); // Немного увеличил высоту
        scene.getStylesheets().add(makeCss());
        stage.setScene(scene);
        stage.setTitle("Dep or Dead — MVP (JavaFX)");
        stage.show();
    }

    private void startGame() {
        currentPlayer = Player.PLAYER_1;
        currentTurnPointsUsed = 0;
        player1Hand.clear();
        player2Hand.clear();

        Collections.shuffle(creatureTemplates);

        if (creatureTemplates.size() < 2) {
            showError("Ошибка данных", "Недостаточно шаблонов существ!");
            return;
        }
        creature1State = new CreatureState(creatureTemplates.get(0));
        creature2State = new CreatureState(creatureTemplates.get(1));

        for (int i = 0; i < STARTING_HAND_SIZE; i++) {
            drawCardToHandData(Player.PLAYER_1);
            drawCardToHandData(Player.PLAYER_2);
        }
    }

    private void endTurn() {
        if (currentPlayer == Player.PLAYER_1) {
            currentPlayer = Player.PLAYER_2;
            currentTurnPointsUsed = 0;
            drawCardsToMax(Player.PLAYER_2);
            updateTurnPointsText();
            updateHandDisplay();
        } else {
            if (currentRound >= MAX_ROUNDS) {
                startBattle();
            } else {
                currentRound++;
                currentPlayer = Player.PLAYER_1;
                currentTurnPointsUsed = 0;
                drawCardsToMax(Player.PLAYER_1);
                updateTurnPointsText();
                updateHandDisplay();
            }
        }
        updateAllScales(); // Обновляем шкалы при каждой смене хода
    }

    private void drawCardsToMax(Player player) {
        List<CardData> currentHand = (player == Player.PLAYER_1) ? player1Hand : player2Hand;
        int cardsToDraw = MAX_HAND_SIZE - currentHand.size();

        if (cardsToDraw > 0) {
            for (int i = 0; i < cardsToDraw; i++) {
                drawCardToHandData(player);
            }
        }
    }

    private void updateTurnPointsText() {
        String playerLabel = (currentPlayer == Player.PLAYER_1) ? "Игрок 1" : "Игрок 2";
        turnPointsText.setText(String.format("Раунд: %d / %d | Ход: %s | Очки: %d / %d",
                currentRound, MAX_ROUNDS, playerLabel, currentTurnPointsUsed, MAX_TURN_POINTS));
    }

    private void updateBetDisplays() {
        int totalOnC1 = p1_BetsOn_C1 + p2_BetsOn_C1;
        int totalOnC2 = p1_BetsOn_C2 + p2_BetsOn_C2;
        creature1BetText.setText("СТАВКИ: " + totalOnC1);
        creature2BetText.setText("СТАВКИ: " + totalOnC2);
    }

    // --- ИЗМЕНЕНИЕ: (ЗАПРОС 3) Логика боя с множителями наград ---
    private void startBattle() {
        StringBuilder battleLog = new StringBuilder("БИТВА НАЧИНАЕТСЯ!\n\n");
        CreatureState attacker, defender;

        if (creature1State.currentAttack > creature2State.currentAttack) {
            attacker = creature1State;
            defender = creature2State;
            battleLog.append(attacker.name).append(" (Атака: ").append(attacker.currentAttack)
                    .append(") ходит первым (Атака выше).\n");
        } else if (creature2State.currentAttack > creature1State.currentAttack) {
            attacker = creature2State;
            defender = creature1State;
            battleLog.append(attacker.name).append(" (Атака: ").append(attacker.currentAttack)
                    .append(") ходит первым (Атака выше).\n");
        } else {
            battleLog.append("Атака равна! Бросаем кубик...\n");
            int roll = DiceUtils.rollD6(1);
            if (roll % 2 == 0) {
                attacker = creature1State;
                defender = creature2State;
                battleLog.append("Выпало ").append(roll).append(" (Четное). ").append(attacker.name).append(" ходит первым!\n");
            } else {
                attacker = creature2State;
                defender = creature1State;
                battleLog.append("Выпало ").append(roll).append(" (Нечетное). ").append(attacker.name).append(" ходит первым!\n");
            }
        }

        while (creature1State.currentHealth > 0 && creature2State.currentHealth > 0) {
            int diceCount = getDiceCount(attacker.currentAttack);
            int damage = DiceUtils.rollD6(diceCount);
            defender.currentHealth -= damage;
            battleLog.append(String.format("-> %s (Атака: %d) бросает %d d6 и наносит %d урона!\n",
                    attacker.name, attacker.currentAttack, diceCount, damage));
            battleLog.append(String.format("   %s: %d HP осталось.\n",
                    defender.name, Math.max(0, defender.currentHealth)));
            if (defender.currentHealth <= 0) {
                break;
            }
            CreatureState temp = attacker;
            attacker = defender;
            defender = temp;
        }

        String winnerName;
        int player1Winnings;
        int player2Winnings;

        if (creature1State.currentHealth > 0) {
            winnerName = creature1State.name;
            // P1 поставил на C1 (победителя)
            player1Winnings = (int)(p1_BetsOn_C1 * getRewardMultiplier(creature1State, creature2State));
            // P2 поставил на C1 (победителя)
            player2Winnings = (int)(p2_BetsOn_C1 * getRewardMultiplier(creature1State, creature2State));
        } else {
            winnerName = creature2State.name;
            // P1 поставил на C2 (победителя)
            player1Winnings = (int)(p1_BetsOn_C2 * getRewardMultiplier(creature2State, creature1State));
            // P2 поставил на C2 (победителя)
            player2Winnings = (int)(p2_BetsOn_C2 * getRewardMultiplier(creature2State, creature1State));
        }

        battleLog.append("\n").append(winnerName).append(" ПОБЕЖДАЕТ!");

        String winningsMessage = String.format(
                "Игрок 1 выиграл: %d\nИгрок 2 выиграл: %d",
                player1Winnings,
                player2Winnings
        );

        showInfo(battleLog.toString());
        showEndGameSplashAndRestart(winnerName + " ПОБЕЖДАЕТ!", winningsMessage);
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    // --- НОВЫЙ МЕТОД: (ЗАПРОС 3) Расчет множителя награды ---
    private double getRewardMultiplier(CreatureState betOn, CreatureState opponent) {
        int rpDiff = betOn.currentRatePoints - opponent.currentRatePoints;

        if (rpDiff > 0) {
            // Ставка на фаворита
            return REWARD_YELLOW_MULT;
        } else {
            // Ставка на аутсайдера (rpDiff <= 0)
            int diff = Math.abs(rpDiff);
            if (diff < BET_REWARD_GREEN_THRESHOLD) {
                return REWARD_YELLOW_MULT; // Небольшая разница, все еще "желтая"
            }
            if (diff < BET_REWARD_RED_THRESHOLD) {
                return REWARD_GREEN_MULT; // Средняя разница
            }
            return REWARD_RED_MULT; // Большая разница
        }
    }
    // --- КОНЕЦ НОВОГО МЕТОДА ---

    private int getDiceCount(int attack) {
        if (attack <= ATTACK_TIER_1_MAX) {
            return 1;
        }
        if (attack <= ATTACK_TIER_2_MAX) {
            return 2;
        }
        return 3;
    }

    private void showEndGameSplashAndRestart(String winnerMessage, String winningsMessage) {
        Stage splashStage = new Stage();
        splashStage.initModality(Modality.APPLICATION_MODAL);
        splashStage.initOwner(creature1Pane.getScene().getWindow());

        Label label = new Label(winnerMessage);
        label.getStyleClass().add("card-title");
        Label labelWinnings = new Label(winningsMessage);
        labelWinnings.getStyleClass().add("card-stats");

        VBox splashRoot = new VBox(10, label, labelWinnings);
        splashRoot.setAlignment(Pos.CENTER);
        splashRoot.setPadding(new Insets(50));

        splashStage.setScene(new Scene(splashRoot));
        splashStage.setTitle("Конец игры");
        splashStage.show();

        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(e -> {
            splashStage.close();
            restartGame();
        });
        delay.play();
    }

    private void restartGame() {
        currentRound = 1;
        p1_BetsOn_C1 = 0;
        p2_BetsOn_C1 = 0;
        p1_BetsOn_C2 = 0;
        p2_BetsOn_C2 = 0;
        updateBetDisplays();

        startGame();

        creature1Pane.setUserData(creature1State);
        creature2Pane.setUserData(creature2State);

        clearDropZone(centralDropZone1);
        clearDropZone(centralDropZone2);

        refreshCreaturePane(creature1Pane, creature1State);
        refreshCreaturePane(creature2Pane, creature2State);

        updateHandDisplay();
        updateTurnPointsText();
        updateAllScales(); // Обновляем шкалы при рестарте
    }

    private void clearDropZone(VBox dropZone) {
        dropZone.getChildren().clear();
        if (dropZone.getUserData() instanceof List) {
            ((List<?>) dropZone.getUserData()).clear();
        }
    }

    private void updateHandDisplay() {
        handBox.getChildren().clear();
        List<CardData> currentHand = (currentPlayer == Player.PLAYER_1) ? player1Hand : player2Hand;

        for (CardData card : currentHand) {
            VBox cardNode = createHandCardNode(card);
            cardNode.getStyleClass().add("hand-card");
            cardNode.setPrefSize(150, 140);
            cardNode.setMinSize(150, 140);
            cardNode.setMaxSize(150, 140);
            handBox.getChildren().add(cardNode);
        }
    }

    private void drawCardToHandData(Player player) {
        if (influenceDeck.isEmpty()) {
            return;
        }
        int idx = ThreadLocalRandom.current().nextInt(influenceDeck.size());
        CardData card = influenceDeck.get(idx);

        if (player == Player.PLAYER_1) {
            player1Hand.add(card);
        } else {
            player2Hand.add(card);
        }
    }

    private VBox createCreaturePane(CreatureState state) {
        VBox creatureCardPane = createCardNode(state.baseCard, true, state);
        creatureCardPane.setPrefSize(260, 180);
        creatureCardPane.setMinSize(260, 180);
        creatureCardPane.setMaxSize(260, 180);
        creatureCardPane.setUserData(state);
        return creatureCardPane;
    }

    private VBox createCentralDropZone(CreatureState targetState, VBox targetPane, int targetBetId) {
        VBox dropArea = new VBox(4);
        dropArea.setPrefSize(220, 180);
        dropArea.setMinSize(220, 180);
        dropArea.setMaxSize(220, 180);
        dropArea.setAlignment(Pos.TOP_CENTER);
        dropArea.getStyleClass().add("drop-area");
        dropArea.setUserData(new ArrayList<CardData>());

        dropArea.setOnDragOver(ev -> {
            if (ev.getGestureSource() != dropArea && ev.getDragboard().hasString()) {
                ev.acceptTransferModes(TransferMode.MOVE);
                dropArea.getStyleClass().add("drop-area-hover");
            }
            ev.consume();
        });

        dropArea.setOnDragExited(ev -> {
            dropArea.getStyleClass().remove("drop-area-hover");
            ev.consume();
        });

        dropArea.setOnDragDropped(ev -> {
            Dragboard db = ev.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String content = db.getString();
                String[] parts = content.split(";");
                if (parts.length != 2) return;

                String cardId = parts[0];
                String mode = parts[1];

                CardData cd = allCards.get(cardId);
                if (cd == null) return;

                if (currentTurnPointsUsed + cd.cost <= MAX_TURN_POINTS) {
                    currentTurnPointsUsed += cd.cost;
                    updateTurnPointsText();
                    removeCardFromHandById(cardId);

                    if ("buff".equals(mode)) {
                        if (cd.effects != null) {
                            for (Effect effect : cd.effects) {
                                PatchUtils.applyEffect(targetState, effect);
                            }
                        }
                        refreshCreaturePane(targetPane, targetState);
                        VBox small = createCardNode(cd, false, null);
                        small.setPrefSize(200, 36);
                        small.setMinSize(200, 36);
                        small.setMaxSize(200, 36);
                        dropArea.getChildren().add(small);
                    }
                    else if ("bet".equals(mode)) {
                        if (targetBetId == 1) {
                            if (currentPlayer == Player.PLAYER_1) {
                                p1_BetsOn_C1 += cd.betAmount;
                            } else {
                                p2_BetsOn_C1 += cd.betAmount;
                            }
                        } else {
                            if (currentPlayer == Player.PLAYER_1) {
                                p1_BetsOn_C2 += cd.betAmount;
                            } else {
                                p2_BetsOn_C2 += cd.betAmount;
                            }
                        }
                        updateBetDisplays();

                        Text betText = new Text("СТАВКА: +" + cd.betAmount);
                        betText.getStyleClass().add(
                                (currentPlayer == Player.PLAYER_1) ? "bet-text-p1" : "bet-text-p2"
                        );
                        dropArea.getChildren().add(betText);
                    }

                    @SuppressWarnings("unchecked")
                    List<CardData> list = (List<CardData>) dropArea.getUserData();
                    list.add(cd);

                    success = true;
                    updateHandDisplay();
                    updateAllScales(); // Обновляем шкалы после применения эффекта
                } else {
                    showInfo(String.format(
                            "Недостаточно очков!\n\nСтоимость карты: %d\nУ вас есть: %d",
                            cd.cost,
                            (MAX_TURN_POINTS - currentTurnPointsUsed)
                    ));
                }
            }
            ev.setDropCompleted(success);
            ev.consume();
        });

        javafx.event.EventHandler<javafx.scene.input.MouseEvent> summaryClickHandler = ev -> {
            @SuppressWarnings("unchecked")
            List<CardData> list = (List<CardData>) dropArea.getUserData();
            if (list.isEmpty()) {
                showInfo("В этой стопке нет карт");
            } else {
                StringBuilder sb = new StringBuilder("Примененные карты и ставки:\n\n");
                for (int i = 0; i < list.size(); i++) {
                    CardData cd = list.get(i);
                    sb.append(i + 1).append(". ").append(cd.name)
                            .append(" (Стоимость: ").append(cd.cost).append(")");

                    List<String> effectStrings = new ArrayList<>();
                    int hp = cd.getStatChange("/health");
                    if (hp != 0) effectStrings.add("HP: " + (hp > 0 ? "+" : "") + hp);
                    int atk = cd.getStatChange("/attack");
                    if (atk != 0) effectStrings.add("ATK: " + (atk > 0 ? "+" : "") + atk);
                    int def = cd.getStatChange("/defense");
                    if (def != 0) effectStrings.add("DEF: " + (def > 0 ? "+" : "") + def);
                    int rp = cd.getStatChange("/ratePoints");
                    if (rp != 0) effectStrings.add("RP: " + (rp > 0 ? "+" : "") + rp);
                    if (cd.betAmount > 0) {
                        effectStrings.add("СТАВКА: +" + cd.betAmount);
                    }

                    if (!effectStrings.isEmpty()) {
                        sb.append(" [").append(String.join(", ", effectStrings)).append("]");
                    }
                    sb.append("\n");
                }
                showInfo(sb.toString());
            }
        };

        dropArea.setOnMouseClicked(summaryClickHandler);
        targetPane.setOnMouseClicked(summaryClickHandler);

        return dropArea;
    }

    private void refreshCreaturePane(VBox creaturePane, CreatureState state) {
        creaturePane.getChildren().clear();

        Text name = new Text(state.name);
        name.getStyleClass().add("card-title");

        String stats = String.format("HP: %d/%d | ATK: %d | DEF: %d | RP: %d",
                state.currentHealth, state.baseHealth,
                state.currentAttack, state.currentDefense,
                state.currentRatePoints);
        Text statsText = new Text(stats);
        statsText.getStyleClass().add("card-stats");

        Text desc = new Text(state.baseCard.text);
        desc.getStyleClass().add("card-text");
        desc.wrappingWidthProperty().bind(creaturePane.widthProperty().subtract(12));

        creaturePane.getChildren().addAll(name, statsText, desc);
    }

    private VBox createHandCardNode(CardData data) {
        VBox box = new VBox(4);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(6));

        Text name = new Text(data.name);
        name.getStyleClass().add("card-title");

        VBox buffView = new VBox(4);
        buffView.setPadding(new Insets(2, 0, 0, 0));

        if (data.cost > 0) {
            Text costText = new Text("Стоимость: " + data.cost);
            costText.getStyleClass().add("card-cost");
            buffView.getChildren().add(costText);
        }

        HBox statsBox = new HBox(8);
        addStatChangeText(statsBox, "HP", data.getStatChange("/health"), "hp");
        addStatChangeText(statsBox, "ATK", data.getStatChange("/attack"), "atk");
        addStatChangeText(statsBox, "DEF", data.getStatChange("/defense"), "def");
        addStatChangeText(statsBox, "RP", data.getStatChange("/ratePoints"), "rp");

        if (!statsBox.getChildren().isEmpty()) {
            buffView.getChildren().add(statsBox);
        }

        Text desc = new Text(data.text);
        desc.getStyleClass().add("card-text");
        desc.wrappingWidthProperty().bind(box.widthProperty().subtract(12));
        buffView.getChildren().add(desc);

        VBox betView = new VBox(4);
        betView.setAlignment(Pos.CENTER);

        Text betLabel = new Text("СТАВКА");
        betLabel.getStyleClass().add("card-cost");

        Text betAmountText = new Text("+" + data.betAmount);
        betAmountText.getStyleClass().add("bet-amount-text");

        betView.getChildren().addAll(betLabel, betAmountText);

        betView.setVisible(false);
        betView.setManaged(false);

        box.getChildren().addAll(name, buffView, betView);

        box.getProperties().put("isBet", false);
        box.setOnMouseClicked(ev -> {
            boolean isBet = (boolean) box.getProperties().get("isBet");
            box.getProperties().put("isBet", !isBet);

            buffView.setVisible(isBet);
            buffView.setManaged(isBet);
            betView.setVisible(!isBet);
            betView.setManaged(!isBet);
        });

        box.setOnDragDetected(ev -> {
            boolean isBet = (boolean) box.getProperties().get("isBet");
            String mode = isBet ? "bet" : "buff";

            Dragboard db = box.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(data.id + ";" + mode);
            db.setContent(content);
            ev.consume();
        });

        return box;
    }

    private VBox createCardNode(CardData data, boolean large, CreatureState state) {
        VBox box = new VBox(4);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(6));

        Text name = new Text(data.name);
        name.getStyleClass().add("card-title");

        Text desc = new Text(data.text);
        desc.getStyleClass().add("card-text");
        desc.wrappingWidthProperty().bind(box.widthProperty().subtract(12));

        if (large && state != null) {
            String stats = String.format("HP: %d/%d | ATK: %d | DEF: %d | RP: %d",
                    state.currentHealth, state.baseHealth,
                    state.currentAttack, state.currentDefense,
                    state.currentRatePoints);
            Text statsText = new Text(stats);
            statsText.getStyleClass().add("card-stats");
            box.getChildren().addAll(name, statsText, desc);
        } else {
            box.getChildren().addAll(name);
        }

        box.setUserData(data);
        return box;
    }

    private void addStatChangeText(HBox container, String prefix, int value, String styleType) {
        if (value == 0) {
            return;
        }
        String text = prefix + ": " + (value > 0 ? "+" : "") + value;
        Text statText = new Text(text);

        if (value > 0) {
            statText.getStyleClass().add("card-stat-" + styleType + "-pos");
        } else {
            statText.getStyleClass().add("card-stat-" + styleType + "-neg");
        }
        container.getChildren().add(statText);
    }

    // --- НОВЫЕ МЕТОДЫ ДЛЯ ШКАЛ (ЗАПРОС 1 и 2) ---

    /**
     * Создает UI-шкалу кубиков атаки
     */
    private HBox createAttackScale() {
        HBox scale = new HBox(5);
        scale.setAlignment(Pos.CENTER);
        scale.getStyleClass().add("dice-scale");

        Label d1 = new Label("1d6");
        d1.getStyleClass().add("dice-label");
        Label d2 = new Label("2d6");
        d2.getStyleClass().add("dice-label");
        Label d3 = new Label("3d6");
        d3.getStyleClass().add("dice-label");

        scale.getChildren().addAll(d1, d2, d3);
        return scale;
    }

    /**
     * Обновляет подсветку на шкале кубиков атаки
     */
    private void updateAttackScale(HBox scale, int currentAttack) {
        int diceCount = getDiceCount(currentAttack); // 1, 2, or 3
        for (int i = 0; i < scale.getChildren().size(); i++) {
            scale.getChildren().get(i).getStyleClass().remove("dice-label-active");
        }
        scale.getChildren().get(diceCount - 1).getStyleClass().add("dice-label-active");
    }

    /**
     * Создает UI-шкалу наград (Yellow/Green/Red)
     */
    private VBox createBetRewardScale() {
        VBox scaleVBox = new VBox(5);
        scaleVBox.setAlignment(Pos.CENTER);
        scaleVBox.setPadding(new Insets(5));

        Text title = new Text("Множители ставок (Ставка на C1 / Ставка на C2)");
        title.getStyleClass().add("card-text");

        betRewardScaleC1Row = createRewardRow("C1");
        betRewardScaleC2Row = createRewardRow("C2");

        scaleVBox.getChildren().addAll(title, betRewardScaleC1Row, betRewardScaleC2Row);
        return scaleVBox;
    }

    /**
     * Вспомогательный метод для создания одного ряда (Y/G/R)
     */
    private HBox createRewardRow(String label) {
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER);

        Label rowLabel = new Label(label + ":");
        rowLabel.getStyleClass().add("bet-scale-label");

        Rectangle yellow = new Rectangle(20, 20);
        yellow.getStyleClass().addAll("bet-scale-box", "bet-scale-yellow");

        Rectangle green = new Rectangle(20, 20);
        green.getStyleClass().addAll("bet-scale-box", "bet-scale-green");

        Rectangle red = new Rectangle(20, 20);
        red.getStyleClass().addAll("bet-scale-box", "bet-scale-red");

        row.getChildren().addAll(rowLabel, yellow, green, red);
        return row;
    }

    /**
     * Обновляет подсветку на шкале наград
     */
    private void updateBetRewardScale() {
        int rpDiff = creature1State.currentRatePoints - creature2State.currentRatePoints;

        // Обновляем ряд для ставок на C1
        // Является ли C1 фаворитом? (rpDiff > 0)
        updateRewardRow(betRewardScaleC1Row, rpDiff > 0, Math.abs(rpDiff));

        // Обновляем ряд для ставок на C2
        // Является ли C2 фаворитом? (rpDiff < 0)
        updateRewardRow(betRewardScaleC2Row, rpDiff < 0, Math.abs(rpDiff));
    }

    /**
     * Вспомогательный метод для подсветки одного ряда (Y/G/R)
     */
    private void updateRewardRow(HBox row, boolean isFavorite, int diff) {
        // 0=Label, 1=Y, 2=G, 3=R
        javafx.scene.Node yellow = row.getChildren().get(1);
        javafx.scene.Node green = row.getChildren().get(2);
        javafx.scene.Node red = row.getChildren().get(3);

        // Сброс
        yellow.setOpacity(0.2);
        green.setOpacity(0.2);
        red.setOpacity(0.2);

        if (isFavorite) {
            yellow.setOpacity(1.0); // Ставка на фаворита всегда Желтая
        } else {
            // Ставка на аутсайдера
            if (diff < BET_REWARD_GREEN_THRESHOLD) {
                yellow.setOpacity(1.0); // Небольшая разница
            } else if (diff < BET_REWARD_RED_THRESHOLD) {
                green.setOpacity(1.0); // Средняя разница
            } else {
                red.setOpacity(1.0); // Большая разница
            }
        }
    }

    /**
     * Обновляет все шкалы
     */
    private void updateAllScales() {
        updateAttackScale(attackScale1, creature1State.currentAttack);
        updateAttackScale(attackScale2, creature2State.currentAttack);
        updateBetRewardScale();
    }

    // --- КОНЕЦ НОВЫХ МЕТОДОВ ---

    private void removeCardFromHandById(String cardId) {
        List<CardData> currentHand = (currentPlayer == Player.PLAYER_1) ? player1Hand : player2Hand;
        currentHand.removeIf(card -> card.id.equals(cardId));
    }

    private void showInfo(String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setTitle("Содержимое");
        a.setContentText(text);
        a.getDialogPane().setPrefWidth(420);
        a.showAndWait();
    }

    private void showError(String title, String text) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(title);
        a.setTitle("Ошибка");
        a.setContentText(text);
        a.getDialogPane().setPrefWidth(420);
        a.showAndWait();
    }

    private void loadDataWithJackson() throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<CardData> creatureList = mapper.readValue(
                getResourceStream(CREATURES_FILE),
                new TypeReference<>() {}
        );

        List<CardData> influenceList = mapper.readValue(
                getResourceStream(INFLUENCE_FILE),
                new TypeReference<>() {}
        );

        creatureTemplates.addAll(creatureList);
        influenceDeck.addAll(influenceList);
        Collections.shuffle(influenceDeck);

        for (CardData c : creatureList) allCards.put(c.id, c);
        for (CardData c : influenceList) allCards.put(c.id, c);
    }


    private InputStream getResourceStream(String filename) throws IOException {
        InputStream is = Main.class.getResourceAsStream("/" + filename);
        if (is == null) {
            throw new IOException("Файл ресурса не найден: " + filename);
        }
        return is;
    }

    // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1 и 2) Добавлены стили для шкал ---
    private String makeCss() {
        return """
            data:,
            .card {
              -fx-background-color: linear-gradient(#ffffff, #f3f6ff);
              -fx-border-color: #cbd7ff;
              -fx-border-radius: 10;
              -fx-background-radius: 10;
              -fx-padding: 8;
              -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);
            }
            .creature-pane-hover {
              -fx-border-color: #8aa2ff;
              -fx-background-color: linear-gradient(#f5f8ff, #ffffff);
            }
            .card-title {
              -fx-font-weight: bold;
              -fx-font-size: 14px;
            }
            .card-cost {
              -fx-font-size: 11px;
              -fx-fill: #0066cc;
              -fx-font-weight: bold;
              -fx-padding: 2 0 0 0;
            }
            
            .bet-amount-text {
              -fx-font-size: 32px;
              -fx-font-weight: bold;
              -fx-fill: #008800;
            }
            .bet-total-text {
              -fx-font-size: 16px;
              -fx-font-weight: bold;
              -fx-fill: #333333;
            }
            .card-stat-bet {
              -fx-font-size: 12px;
              -fx-font-weight: bold;
              -fx-fill: #008800;
            }
            .bet-text-p1 {
              -fx-font-size: 12px;
              -fx-font-weight: bold;
              -fx-fill: #0078D7;
            }
            .bet-text-p2 {
              -fx-font-size: 12px;
              -fx-font-weight: bold;
              -fx-fill: #E50000;
            }
            
            .card-stat-hp-pos, .card-stat-hp-neg,
            .card-stat-atk-pos, .card-stat-atk-neg,
            .card-stat-def-pos, .card-stat-def-neg,
            .card-stat-rp-pos, .card-stat-rp-neg {
              -fx-font-size: 11px;
              -fx-font-weight: bold;
            }
            .card-stat-hp-pos  { -fx-fill: #00A800; }
            .card-stat-hp-neg  { -fx-fill: #597D35; }
            .card-stat-atk-pos { -fx-fill: #E50000; }
            .card-stat-atk-neg { -fx-fill: #B22222; }
            .card-stat-def-pos { -fx-fill: #0078D7; }
            .card-stat-def-neg { -fx-fill: #000080; }
            .card-stat-rp-pos  { -fx-fill: #FFA500; }
            .card-stat-rp-neg  { -fx-fill: #8B4513; }
            
            .card-stats {
              -fx-font-size: 13px;
              -fx-fill: #333333;
              -fx-font-weight: bold;
              -fx-padding: 2 0 0 0;
            }
            .card-text {
              -fx-font-size: 12px;
              -fx-fill: #333333;
              -fx-font-style: italic;
            }
            .hand-card {
              -fx-cursor: hand;
            }
            .drop-area {
              -fx-background-color: linear-gradient(#fafafa, #fff);
              -fx-border-color: #e0e0e0;
              -fx-border-style: dashed;
              -fx-border-radius: 8;
              -fx-background-radius: 8;
              -fx-padding: 8;
              -fx-alignment: top-center;
            }
            .drop-area-hover {
              -fx-border-color: #8aa2ff;
              -fx-background-color: linear-gradient(#f5f8ff, #ffffff);
            }
            
            /* --- НОВЫЕ СТИЛИ ДЛЯ ШКАЛ (ЗАПРОС 1 и 2) --- */
            .dice-scale {
              -fx-border-color: #e0e0e0;
              -fx-border-width: 1px;
              -fx-border-radius: 5px;
              -fx-padding: 3px;
            }
            .dice-label {
              -fx-padding: 5px 10px;
              -fx-font-size: 12px;
              -fx-font-weight: bold;
              -fx-background-color: #f0f0f0;
              -fx-border-radius: 3px;
              -fx-text-fill: #999999;
            }
            .dice-label-active {
              -fx-background-color: #E50000; /* Цвет Атаки */
              -fx-text-fill: white;
            }
            
            .bet-scale-label {
              -fx-font-size: 12px;
              -fx-font-weight: bold;
              -fx-min-width: 30px; /* Для выравнивания */
            }
            .bet-scale-box {
              -fx-stroke: #aaaaaa;
              -fx-stroke-width: 1px;
              -fx-opacity: 0.2; /* По умолчанию выключены */
            }
            .bet-scale-yellow { -fx-fill: #FFA500; }
            .bet-scale-green  { -fx-fill: #00A800; }
            .bet-scale-red    { -fx-fill: #E50000; }
            /* --- КОНЕЦ НОВЫХ СТИЛЕЙ --- */
            
            """;
    }

    // --- ВНУТРЕННИЕ КЛАССЫ (POJO) ---

    public static class CardData {
        public String id;
        public String name;
        public String text;
        public int cost;
        public int betAmount;
        public int health;
        public int attack;
        public int defense;
        public int ratePoints;
        public List<Effect> effects;

        public CardData() {
            this.effects = new ArrayList<>();
        }

        public int getStatChange(String path) {
            if (effects == null) {
                return 0;
            }
            int total = 0;
            for (Effect e : effects) {
                if (path.equals(e.path)) {
                    total += e.value;
                }
            }
            return total;
        }
    }

    public static class Effect {
        public String op;
        public String path;
        public int value;
        public Effect() {}
    }

    public static class CreatureState {
        public CardData baseCard;
        public String name;
        public int baseHealth;
        public int currentHealth;
        public int baseAttack;
        public int currentAttack;
        public int baseDefense;
        public int currentDefense;
        public int baseRatePoints;
        public int currentRatePoints;

        public CreatureState(CardData baseCard) {
            this.baseCard = baseCard;
            this.name = baseCard.name;
            this.baseHealth = baseCard.health;
            this.currentHealth = baseCard.health;
            this.baseAttack = baseCard.attack;
            this.currentAttack = baseCard.attack;
            this.baseDefense = baseCard.defense;
            this.currentDefense = baseCard.defense;
            this.baseRatePoints = baseCard.ratePoints;
            this.currentRatePoints = baseCard.ratePoints;
        }
    }
}