package yermakov.oleksii;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Main extends Application {

    private static final String CREATURES_FILE = "creatures.json";
    private static final String INFLUENCE_FILE = "influenceCards.json";
    private static final String CONFIG_FILE = "config.json";
    private static GameConfig config;

    private final Map<String, CardData> allCards = new HashMap<>();
    private final List<CardData> creatureTemplates = new ArrayList<>();
    private final List<CardData> influenceCardTemplates = new ArrayList<>();
    private final List<CardData> influenceDeck = new ArrayList<>();

    public CreatureState creature1State;
    public CreatureState creature2State;
    public enum Player { PLAYER_1, PLAYER_2 }
    public Player currentPlayer;
    public final List<CardData> player1Hand = new ArrayList<>();
    public final List<CardData> player2Hand = new ArrayList<>();
    public int currentTurnPointsUsed = 0;
    public int p1_BetsOn_C1 = 0;
    public int p2_BetsOn_C1 = 0;
    public int p1_BetsOn_C2 = 0;
    public int p2_BetsOn_C2 = 0;

    public int player1TotalScore = 0;
    public int player2TotalScore = 0;

    public int currentRound = 1;
    public int currentBattle = 1;

    private enum RewardTier { YELLOW, GREEN, RED }

    private HBox handBox;
    private Text turnPointsText;
    private VBox creature1Pane;
    private VBox creature2Pane;
    private VBox centralDropZone1;
    private VBox centralDropZone2;
    private Text creature1BetText;
    private Text creature2BetText;
    private HBox attackScale1;
    private HBox attackScale2;
    private HBox betRewardScaleC1Row;
    private HBox betRewardScaleC2Row;
    private Text player1ScoreText;
    private Text player2ScoreText;
    private HBox defenseScale1;
    private HBox defenseScale2;

    private ScrollPane battleLogScroll;
    private VBox battleLogContainer;
    private CreatureState battleAttacker;
    private CreatureState battleDefender;
    private Alert battleDialog;
    private Text battleC1Stats;
    private Text battleC2Stats;
    private Button battleButton;
    private VBox urnPane;
    private static Path externalDataPath;


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        List<String> args = getParameters().getRaw();
        I18n.setLocale(args.isEmpty() ? "ru" : args.get(0));

        try {
            externalDataPath = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            loadDataWithJackson();
        } catch (Exception e) {
            e.printStackTrace();
            showError(I18n.getString("error.critical"), String.format(I18n.getString("error.dataLoad"), e.getMessage()));
            return;
        }

        startGame();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        creature1Pane = createCreaturePane(creature1State);
        creature2Pane = createCreaturePane(creature2State);

        creature1BetText = new Text();
        creature1BetText.getStyleClass().add("bet-total-text");
        creature2BetText = new Text();
        creature2BetText.getStyleClass().add("bet-total-text");
        updateBetDisplays();

        attackScale1 = createAttackScale();
        attackScale2 = createAttackScale();
        defenseScale1 = createDefenseScale();
        defenseScale2 = createDefenseScale();

        VBox betRewardScaleC1_UI = createBetRewardScale(I18n.getString("label.betMultiplier"));
        VBox betRewardScaleC2_UI = createBetRewardScale(I18n.getString("label.betMultiplier"));

        VBox creature1Column = new VBox(5, creature1Pane, creature1BetText, attackScale1, defenseScale1, betRewardScaleC1_UI);
        creature1Column.setAlignment(Pos.CENTER);
        VBox creature2Column = new VBox(5, creature2Pane, creature2BetText, attackScale2, defenseScale2, betRewardScaleC2_UI);
        creature2Column.setAlignment(Pos.CENTER);

        centralDropZone1 = createCentralDropZone(creature1Pane, 1);
        centralDropZone2 = createCentralDropZone(creature2Pane, 2);

        HBox topArea = new HBox(15);
        topArea.setAlignment(Pos.TOP_CENTER);
        topArea.setPadding(new Insets(10));
        topArea.getChildren().addAll(
                creature1Column,
                centralDropZone1,
                centralDropZone2,
                creature2Column
        );
        root.setTop(topArea);

        VBox mainBottomArea = new VBox(10);
        mainBottomArea.setPadding(new Insets(10, 5, 5, 5));
        mainBottomArea.setAlignment(Pos.CENTER);

        turnPointsText = new Text();
        turnPointsText.getStyleClass().add("card-title");
        updateTurnPointsText();
        updateAllScales();

        handBox = new HBox(10);
        handBox.setPadding(new Insets(10));
        handBox.setAlignment(Pos.CENTER);

        ScrollPane handScroll = new ScrollPane(handBox);
        handScroll.setPrefHeight(270);
        handScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        handScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        handScroll.setFitToHeight(true);
        handScroll.setMaxWidth(Double.MAX_VALUE);
        handScroll.getStyleClass().add("hand-scroll-pane");
        handBox.prefWidthProperty().bind(handScroll.widthProperty().subtract(20));

        Button endTurnBtn = new Button(I18n.getString("button.endTurn"));
        endTurnBtn.setMinWidth(180);
        endTurnBtn.setOnAction(e -> endTurn());

        player1ScoreText = new Text();
        player1ScoreText.getStyleClass().addAll("score-text", "text-p1");
        player2ScoreText = new Text();
        player2ScoreText.getStyleClass().addAll("score-text", "text-p2");
        updatePlayerTotalScores();

        Region spacerLeft = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);

        urnPane = createUrnDropZone();

        HBox bottomBar = new HBox(10, player1ScoreText, player2ScoreText, spacerLeft, urnPane, endTurnBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));

        mainBottomArea.getChildren().addAll(turnPointsText, handScroll, bottomBar);
        VBox.setVgrow(handScroll, Priority.ALWAYS);

        root.setBottom(mainBottomArea);

        updateHandDisplay();

        Scene scene = new Scene(root, 1300, 850);
        scene.getStylesheets().add(makeCss());
        stage.setScene(scene);
        stage.setTitle(I18n.getString("app.title"));

        stage.setMaximized(true);
        stage.show();
    }

    private void startGame() {
        currentPlayer = Player.PLAYER_1;
        currentTurnPointsUsed = 0;
        player1Hand.clear();
        player2Hand.clear();

        Collections.shuffle(creatureTemplates);

        if (creatureTemplates.size() < 2) {
            showError(I18n.getString("error.critical"), I18n.getString("error.noCreatures"));
            return;
        }
        creature1State = new CreatureState(creatureTemplates.get(0));
        creature2State = new CreatureState(creatureTemplates.get(1));

        // Инициализация динамических статов перед игрой (если вдруг старт с 15HP и т.д.)
        creature1State.recalculateDynamicStats();
        creature2State.recalculateDynamicStats();

        buildPlayableDeck();

        for (int i = 0; i < config.STARTING_HAND_SIZE; i++) {
            drawCardToHandData(Player.PLAYER_1);
            drawCardToHandData(Player.PLAYER_2);
        }
    }

    private void buildPlayableDeck() {
        influenceDeck.clear();
        for (CardData template : influenceCardTemplates) {
            int count = template.getCount();
            for (int i = 0; i < count; i++) {
                influenceDeck.add(template);
            }
        }
        Collections.shuffle(influenceDeck);
    }

    private void endTurn() {
        if (currentPlayer == Player.PLAYER_1) {
            currentPlayer = Player.PLAYER_2;
            currentTurnPointsUsed = 0;
            drawCardsToMax(Player.PLAYER_2);
        } else {
            if (currentRound >= config.MAX_ROUNDS_PER_BATTLE) {
                startBattle();
                return;
            } else {
                currentRound++;
                currentPlayer = Player.PLAYER_1;
                currentTurnPointsUsed = 0;
                drawCardsToMax(Player.PLAYER_1);
            }
        }
        updateTurnPointsText();
        updateHandDisplay();
        updateAllScales();
    }

    private void drawCardsToMax(Player player) {
        List<CardData> currentHand = (player == Player.PLAYER_1) ? player1Hand : player2Hand;
        int cardsToDraw = config.MAX_HAND_SIZE - currentHand.size();

        if (cardsToDraw > 0) {
            for (int i = 0; i < cardsToDraw; i++) {
                drawCardToHandData(player);
            }
        }
    }

    private void updateTurnPointsText() {
        String playerLabel = (currentPlayer == Player.PLAYER_1) ? I18n.getString("label.player1") : I18n.getString("label.player2");
        turnPointsText.setText(String.format(I18n.getString("label.turnInfo"),
                currentBattle, config.MAX_BATTLES,
                currentRound, config.MAX_ROUNDS_PER_BATTLE,
                playerLabel, currentTurnPointsUsed, config.MAX_TURN_POINTS));

        turnPointsText.getStyleClass().removeAll("text-p1", "text-p2");
        if (currentPlayer == Player.PLAYER_1) {
            turnPointsText.getStyleClass().add("text-p1");
        } else {
            turnPointsText.getStyleClass().add("text-p2");
        }
    }

    public void updateBetDisplays() {
        int totalOnC1 = p1_BetsOn_C1 + p2_BetsOn_C1;
        int totalOnC2 = p1_BetsOn_C2 + p2_BetsOn_C2;
        creature1BetText.setText(String.format(I18n.getString("label.bets"), totalOnC1));
        creature2BetText.setText(String.format(I18n.getString("label.bets"), totalOnC2));
    }

    private void updatePlayerTotalScores() {
        player1ScoreText.setText(String.format(I18n.getString("label.totalScore"), player1TotalScore));
        player2ScoreText.setText(String.format(I18n.getString("label.totalScore"), player2TotalScore));
    }

    private void startBattle() {
        if (creature1State.currentAttack > creature2State.currentAttack) {
            battleAttacker = creature1State;
            battleDefender = creature2State;
        } else if (creature2State.currentAttack > creature1State.currentAttack) {
            battleAttacker = creature2State;
            battleDefender = creature1State;
        } else {
            int roll = DiceUtils.rollD6(1);
            if (roll % 2 == 0) {
                battleAttacker = creature1State;
                battleDefender = creature2State;
            } else {
                battleAttacker = creature2State;
                battleDefender = creature1State;
            }
        }

        battleDialog = new Alert(Alert.AlertType.NONE);
        battleDialog.setTitle(I18n.getString("battle.dialogTitle"));
        battleDialog.getDialogPane().getStylesheets().add(makeCss());
        battleDialog.getDialogPane().setPrefSize(900, 600);

        battleDialog.getButtonTypes().add(ButtonType.CLOSE);
        battleDialog.getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);

        BorderPane battlePane = new BorderPane();
        battlePane.setPadding(new Insets(10));

        battleC1Stats = new Text(getCreatureBattleStats(creature1State));
        battleC1Stats.getStyleClass().add("battle-stat-text");
        battlePane.setLeft(battleC1Stats);
        BorderPane.setAlignment(battleC1Stats, Pos.TOP_LEFT);

        battleC2Stats = new Text(getCreatureBattleStats(creature2State));
        battleC2Stats.getStyleClass().add("battle-stat-text");
        battlePane.setRight(battleC2Stats);
        BorderPane.setAlignment(battleC2Stats, Pos.TOP_RIGHT);

        battleLogContainer = new VBox(5);
        battleLogContainer.setAlignment(Pos.TOP_CENTER);

        battleLogScroll = new ScrollPane(battleLogContainer);
        battleLogScroll.setPrefHeight(450);
        battleLogScroll.setFitToWidth(true);
        battleLogScroll.getStyleClass().add("battle-log-scroll");

        battleLogContainer.heightProperty().addListener((observable, oldValue, newValue) -> {
            battleLogScroll.setVvalue(1.0);
        });

        battleButton = new Button(I18n.getString("button.fight"));
        battleButton.setOnAction(e -> playBattleStep());

        VBox centerContent = new VBox(10, battleLogScroll, battleButton);
        centerContent.setAlignment(Pos.CENTER);
        battlePane.setCenter(centerContent);

        addBattleLog(I18n.getString("battle.start"));

        addBattleLog(String.format(I18n.getString("battle.initiative.attack"), battleAttacker.getLocalizedName(), battleAttacker.currentAttack));

        battleDialog.getDialogPane().setContent(battlePane);

        PauseTransition startDelay = new PauseTransition(Duration.seconds(1.0));
        startDelay.setOnFinished(e -> playBattleStep());
        startDelay.play();

        battleDialog.show();
    }

    private void playBattleStep() {
        // --- STUN CHECK (Пропуск хода) ---
        if (battleAttacker.isStunned) {
            addBattleLog(String.format(I18n.getString("battle.stun.skip"), battleAttacker.getLocalizedName()));
            battleAttacker.isStunned = false; // Оглушение снимается после пропуска
            swapTurn();
            return;
        }

        // --- БРОСОК КУБИКОВ ПО ОДНОМУ ---
        int diceCount = getDiceCount(battleAttacker.currentAttack);
        List<Integer> successfulDice = new ArrayList<>();
        List<Integer> rawRolls = new ArrayList<>();

        for (int i = 0; i < diceCount; i++) {
            int roll = DiceUtils.rollD6(1);
            rawRolls.add(roll);

            // 1. Промах (Орк)
            if (battleAttacker.missChance != null && battleAttacker.missChance.contains(roll)) {
                addBattleLog(String.format(I18n.getString("battle.miss"), (i+1), roll));
                continue;
            }

            // 2. Оглушающий удар (Дварф)
            if (battleAttacker.stunChance != null && battleAttacker.stunChance.contains(roll)) {
                addBattleLog(String.format(I18n.getString("battle.stun.trigger"), (i+1), roll));
                battleDefender.isStunned = true;
            }

            successfulDice.add(roll);
        }

        // --- МАГИЧЕСКИЙ БАРЬЕР (Поглощает один максимальный кубик) ---
        if (battleDefender.magicBarrier > 0 && !successfulDice.isEmpty()) {
            // Ищем максимальный кубик, чтобы его заблокировать
            int maxDie = Collections.max(successfulDice);
            successfulDice.remove(Integer.valueOf(maxDie));
            battleDefender.magicBarrier--;
            addBattleLog(String.format(I18n.getString("battle.barrier.absorb"),
                    battleDefender.getLocalizedName(), maxDie));
        }

        // --- СУММА УРОНА ---
        int rawDamage = successfulDice.stream().mapToInt(Integer::intValue).sum();

        // --- ЗАЩИТА ---
        int defenderDefense = battleDefender.currentDefense;
        int damageReduction = getDefenseBlock(defenderDefense);
        int finalDamage = Math.max(0, rawDamage - damageReduction);

        battleDefender.currentHealth -= finalDamage;
        // ВНИМАНИЕ: Здесь НЕ вызываем recalculateDynamicStats(), так как бонусы не должны меняться в бою

        addBattleLog("---");
        addBattleLog(String.format(I18n.getString("battle.log.attack"),
                battleAttacker.getLocalizedName(), rawRolls.toString(), rawDamage));

        if (damageReduction > 0) {
            addBattleLog(String.format(I18n.getString("battle.log.defense"),
                    battleDefender.getLocalizedName(), damageReduction));
        }

        addBattleLog(String.format(I18n.getString("battle.log.result"),
                finalDamage, battleDefender.getLocalizedName(), Math.max(0, battleDefender.currentHealth)));

        // --- ВАМПИРИЗМ ---
        if (battleAttacker.vampirism > 0 && finalDamage > 0) {
            battleAttacker.currentHealth += battleAttacker.vampirism;
            // Тут тоже не пересчитываем статы, даже если HP выросло
            addBattleLog(String.format(I18n.getString("battle.vampirism"),
                    battleAttacker.getLocalizedName(), battleAttacker.vampirism));
        }

        battleC1Stats.setText(getCreatureBattleStats(creature1State));
        battleC2Stats.setText(getCreatureBattleStats(creature2State));

        if (battleDefender.currentHealth <= 0) {
            battleButton.setText(I18n.getString("button.ok"));
            battleButton.setOnAction(e -> {
                battleDialog.close();
                Platform.runLater(() -> processBattleResults(battleAttacker));
            });
            return;
        }

        swapTurn();
    }

    private void swapTurn() {
        CreatureState temp = battleAttacker;
        battleAttacker = battleDefender;
        battleDefender = temp;

        PauseTransition stepDelay = new PauseTransition(Duration.seconds(config.BATTLE_STEP_DELAY));
        stepDelay.setOnFinished(e -> playBattleStep());
        stepDelay.play();
    }

    private void processBattleResults(CreatureState winner) {
        String winnerName = winner.getLocalizedName();
        RewardTier winnerTier;

        int p1WinningsFromWinner = 0;
        int p2WinningsFromWinner = 0;
        int p1LossesFromLoser = 0;
        int p2LossesFromLoser = 0;

        if (winner == creature1State) {
            winnerName = creature1State.getLocalizedName();
            winnerTier = getRewardTier(creature1State, creature2State);

            p1WinningsFromWinner = (int)(p1_BetsOn_C1 * getRewardMultiplier(winnerTier));
            p2WinningsFromWinner = (int)(p2_BetsOn_C1 * getRewardMultiplier(winnerTier));

            p1LossesFromLoser = p1_BetsOn_C2;
            p2LossesFromLoser = p2_BetsOn_C2;

        } else {
            winnerName = creature2State.getLocalizedName();
            winnerTier = getRewardTier(creature2State, creature1State);

            p1WinningsFromWinner = (int)(p1_BetsOn_C2 * getRewardMultiplier(winnerTier));
            p2WinningsFromWinner = (int)(p2_BetsOn_C2 * getRewardMultiplier(winnerTier));

            p1LossesFromLoser = p1_BetsOn_C1;
            p2LossesFromLoser = p2_BetsOn_C1;
        }

        int player1NetProfit = p1WinningsFromWinner - p1LossesFromLoser;
        int player2NetProfit = p2WinningsFromWinner - p2LossesFromLoser;

        // --- ЛОГИКА ВОРА ---
        // Если победитель - Вор (thief=true), он крадет 400 у самого прибыльного.
        if (winner.thief) {
            if (player1NetProfit > player2NetProfit) {
                player1NetProfit -= 400;
                // Показываем сообщение о краже? Можно в диалоге победы.
            } else if (player2NetProfit > player1NetProfit) {
                player2NetProfit -= 400;
            }
            // Если равны, никто не крадет? По логике "больше", да.
        }

        player1TotalScore += player1NetProfit;
        player2TotalScore += player2NetProfit;

        updatePlayerTotalScores();
        showEndGameDialog(winnerName, player1NetProfit, player2NetProfit, winnerTier, winner.thief);
    }

    private void addBattleLog(String message) {
        Text text = new Text(message);
        text.getStyleClass().add("battle-log-text");
        battleLogContainer.getChildren().add(text);
    }

    private String getCreatureBattleStats(CreatureState state) {
        int displayHealth = Math.max(0, state.currentHealth);

        String rpString = (state.bonusRatePoints > 0) ?
                String.format(I18n.getString("battle.statsHeader.bonus"), state.getLocalizedName(), displayHealth, state.currentRatePoints, state.bonusRatePoints) :
                String.format(I18n.getString("battle.statsHeader"), state.getLocalizedName(), displayHealth);

        String specials = "";
        if (state.magicBarrier > 0) specials += " [Barrier: " + state.magicBarrier + "]";
        if (state.isStunned) specials += " [STUN]";

        return rpString +
                "\n" +
                String.format(" ATK: %d (%s)", state.currentAttack, I18n.getString("label.diceLabel." + getDiceCount(state.currentAttack))) +
                "\n" +
                String.format(" DEF: %d (%s)", state.currentDefense, I18n.getString("label.defenseBlock." + getDefenseBlock(state.currentDefense))) +
                "\n" +
                String.format(" RP: %d (%s)", state.getTotalRP(), state.getLocalizedName()) + specials;
    }

    private RewardTier getRewardTier(CreatureState betOn, CreatureState opponent) {
        int rpDiff = betOn.getTotalRP() - opponent.getTotalRP();
        if (rpDiff > 0) {
            return RewardTier.YELLOW;
        } else {
            int diff = Math.abs(rpDiff);
            if (diff < config.BET_REWARD_GREEN_THRESHOLD) {
                return RewardTier.YELLOW;
            }
            if (diff < config.BET_REWARD_RED_THRESHOLD) {
                return RewardTier.GREEN;
            }
            return RewardTier.RED;
        }
    }

    private double getRewardMultiplier(RewardTier tier) {
        switch (tier) {
            case GREEN: return config.REWARD_GREEN_MULT;
            case RED: return config.REWARD_RED_MULT;
            case YELLOW:
            default: return config.REWARD_YELLOW_MULT;
        }
    }

    private int getDiceCount(int attack) {
        if (attack <= config.ATTACK_TIER_1_MAX) {
            return 1;
        }
        if (attack <= config.ATTACK_TIER_2_MAX) {
            return 2;
        }
        return 3;
    }

    private int getDefenseBlock(int defense) {
        if (defense <= 0) {
            return 0;
        }
        if (defense <= config.DEFENSE_TIER_1_MAX) {
            return 1;
        }
        if (defense <= config.DEFENSE_TIER_2_MAX) {
            return 2;
        }
        return 3;
    }

    private void showEndGameDialog(String winnerName, int p1NetProfit, int p2NetProfit, RewardTier tier, boolean thiefTriggered) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(I18n.getString("game.endTitle"));
            alert.setHeaderText(String.format(I18n.getString("battle.winner"), winnerName));

            Label p1Label = new Label(I18n.getString("label.player1") + " " + I18n.getString("game.winnings.simple") + ":");
            Label p1Amount = new Label("" + p1NetProfit);
            p1Amount.getStyleClass().add(p1NetProfit < 0 ? "reward-red" : "reward-" + tier.name().toLowerCase());

            Label p2Label = new Label(I18n.getString("label.player2") + " " + I18n.getString("game.winnings.simple") + ":");
            Label p2Amount = new Label("" + p2NetProfit);
            p2Amount.getStyleClass().add(p2NetProfit < 0 ? "reward-red" : "reward-" + tier.name().toLowerCase());

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.add(p1Label, 0, 0);
            grid.add(p1Amount, 1, 0);
            grid.add(p2Label, 0, 1);
            grid.add(p2Amount, 1, 1);

            if (thiefTriggered) {
                String victim = (p1NetProfit < p2NetProfit) ? I18n.getString("label.player2") : I18n.getString("label.player1");
                // Если прибыли равны, вор не сработал по логике "строго больше", но можно доработать.
                // Добавим сообщение
                Label thiefMsg = new Label(String.format(I18n.getString("battle.thief.effect"), victim));
                thiefMsg.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                grid.add(thiefMsg, 0, 2, 2, 1);
            }

            alert.getDialogPane().setContent(grid);
            alert.getDialogPane().getStylesheets().add(makeCss());

            alert.showAndWait();

            if (currentBattle >= config.MAX_BATTLES) {
                Alert matchOverAlert = new Alert(Alert.AlertType.INFORMATION);
                matchOverAlert.setTitle(I18n.getString("game.matchOver.title"));
                matchOverAlert.setHeaderText(null);
                matchOverAlert.setContentText(String.format(I18n.getString("game.matchOver.content"),
                        config.MAX_BATTLES, player1TotalScore, player2TotalScore));
                matchOverAlert.getButtonTypes().setAll(new ButtonType(I18n.getString("game.matchOver.newMatch")));
                matchOverAlert.showAndWait();

                player1TotalScore = 0;
                player2TotalScore = 0;
                currentBattle = 1;
                updatePlayerTotalScores();
            } else {
                currentBattle++;
            }

            restartGame();
        });
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
        clearDropZone(urnPane);

        refreshCreaturePane(creature1Pane, creature1State);
        refreshCreaturePane(creature2Pane, creature2State);

        updateHandDisplay();
        updateTurnPointsText();
        updateAllScales();
    }

    private void clearDropZone(VBox dropZone) {
        if (dropZone == null) return;

        if (dropZone.getChildren().size() > 1) {
            dropZone.getChildren().remove(1, dropZone.getChildren().size());
        }
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
            cardNode.setPrefSize(170, 240);
            cardNode.setMinSize(170, 240);
            cardNode.setMaxSize(170, 240);
            handBox.getChildren().add(cardNode);
        }
    }

    private void drawCardToHandData(Player player) {
        if (influenceDeck.isEmpty()) {
            triggerDeckEmptyGameOver();
            return;
        }

        CardData card = influenceDeck.remove(influenceDeck.size() - 1);

        if (player == Player.PLAYER_1) {
            player1Hand.add(card);
        } else {
            player2Hand.add(card);
        }
    }

    private void triggerDeckEmptyGameOver() {
        showError(I18n.getString("error.deckEmpty.title"), I18n.getString("error.deckEmpty.content"));
        Platform.exit();
    }

    private VBox createCreaturePane(CreatureState state) {
        VBox creatureCardPane = createCardNode(state.baseCard, true, state);
        creatureCardPane.setPrefSize(260, 180);
        creatureCardPane.setMinSize(260, 180);
        creatureCardPane.setMaxSize(260, 180);
        creatureCardPane.setUserData(state);
        return creatureCardPane;
    }

    private VBox createUrnDropZone() {
        VBox urn = new VBox(4);
        urn.setPrefSize(180, 50);
        urn.setMinSize(180, 50);
        urn.setAlignment(Pos.CENTER);
        urn.getStyleClass().add("drop-area");

        Text label = new Text(I18n.getString("label.urn"));
        label.getStyleClass().add("card-text");
        urn.getChildren().add(label);

        urn.setOnDragOver(ev -> {
            if (ev.getGestureSource() != urn && ev.getDragboard().hasString()) {
                ev.acceptTransferModes(TransferMode.MOVE);
                urn.getStyleClass().add("drop-area-hover");
            }
            ev.consume();
        });

        urn.setOnDragExited(ev -> {
            urn.getStyleClass().remove("drop-area-hover");
            ev.consume();
        });

        urn.setOnDragDropped(ev -> {
            Dragboard db = ev.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                int costToDiscard = 1;
                if (currentTurnPointsUsed + costToDiscard <= config.MAX_TURN_POINTS) {

                    String content = db.getString();
                    String cardId = content.split(";")[0];

                    currentTurnPointsUsed += costToDiscard;
                    updateTurnPointsText();
                    removeCardFromHandById(cardId);
                    updateHandDisplay();
                    success = true;
                } else {
                    showInfo(String.format(I18n.getString("error.notEnoughPoints"),
                            costToDiscard,
                            (config.MAX_TURN_POINTS - currentTurnPointsUsed)
                    ));
                }
            }
            ev.setDropCompleted(success);
            ev.consume();
        });

        return urn;
    }

    private VBox createCentralDropZone(VBox targetPane, int targetBetId) {
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

                int costToPlay = cd.cost;

                if (currentTurnPointsUsed + costToPlay <= config.MAX_TURN_POINTS) {

                    CreatureState currentState = (CreatureState) targetPane.getUserData();

                    if ("bet".equals(mode)) {
                        if (currentRound <= currentState.bettingBlockedUntilRound) {
                            showInfo(String.format(I18n.getString("error.bettingBlocked"), currentState.getLocalizedName()));
                            ev.setDropCompleted(false);
                            ev.consume();
                            return;
                        }
                    }

                    currentTurnPointsUsed += costToPlay;
                    updateTurnPointsText();
                    removeCardFromHandById(cardId);

                    if ("buff".equals(mode)) {
                        if (cd.effects != null) {
                            for (Effect effect : cd.effects) {
                                PatchUtils.applyEffect(this, currentState, effect, targetBetId);
                            }
                        }
                        refreshCreaturePane(targetPane, currentState);

                        VBox small = createCardNode(cd, false, null);
                        small.setPrefSize(200, 36);
                        small.setMinSize(200, 36);
                        small.setMaxSize(200, 36);
                        dropArea.getChildren().add(small);
                    }
                    else if ("bet".equals(mode)) {
                        int betAmount = cd.getBetAmount();
                        if (targetBetId == 1) {
                            if (currentPlayer == Player.PLAYER_1) {
                                p1_BetsOn_C1 += betAmount;
                            } else {
                                p2_BetsOn_C1 += betAmount;
                            }
                            int totalBet = p1_BetsOn_C1 + p2_BetsOn_C1;
                            currentState.bonusRatePoints = totalBet / config.BET_AMOUNT_PER_RP;
                        } else {
                            if (currentPlayer == Player.PLAYER_1) {
                                p1_BetsOn_C2 += betAmount;
                            } else {
                                p2_BetsOn_C2 += betAmount;
                            }
                            int totalBet = p1_BetsOn_C2 + p2_BetsOn_C2;
                            currentState.bonusRatePoints = totalBet / config.BET_AMOUNT_PER_RP;
                        }
                        updateBetDisplays();
                        refreshCreaturePane(targetPane, currentState);

                        Text betText = new Text(String.format(I18n.getString("label.betText"), betAmount));
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
                    updateAllScales();
                } else {
                    showInfo(String.format(I18n.getString("error.notEnoughPoints"),
                            costToPlay,
                            (config.MAX_TURN_POINTS - currentTurnPointsUsed)
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
                showInfo(I18n.getString("error.noCardsInStack"));
            } else {
                StringBuilder sb = new StringBuilder(I18n.getString("info.stackContents"));
                for (int i = 0; i < list.size(); i++) {
                    CardData cd = list.get(i);
                    sb.append(i + 1).append(". ").append(cd.getLocalizedName())
                            .append(" (").append(I18n.getString("label.cost").replace(":", "")).append(": ").append(cd.cost).append(")");

                    List<String> effectStrings = new ArrayList<>();
                    int hp = cd.getStatChange("/health");
                    if (hp != 0) effectStrings.add(String.format(I18n.getString("info.effect.hp"), (hp > 0 ? "+" : ""), hp));
                    int atk = cd.getStatChange("/attack");
                    if (atk != 0) effectStrings.add(String.format(I18n.getString("info.effect.atk"), (atk > 0 ? "+" : ""), atk));
                    int def = cd.getStatChange("/defense");
                    if (def != 0) effectStrings.add(String.format(I18n.getString("info.effect.def"), (def > 0 ? "+" : ""), def));
                    int rp = cd.getStatChange("/ratePoints");
                    if (rp != 0) effectStrings.add(String.format(I18n.getString("info.effect.rp"), (rp > 0 ? "+" : ""), rp));

                    int betDec = cd.getStatChange("/opponent_bets");
                    if (betDec > 0) effectStrings.add(String.format(I18n.getString("info.effect.betDec"), betDec));

                    if (cd.getBetAmount() > 0) {
                        effectStrings.add(String.format(I18n.getString("info.effect.bet"), cd.getBetAmount()));
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

        Text name = new Text(state.getLocalizedName());
        name.getStyleClass().add("card-title");
        name.wrappingWidthProperty().bind(creaturePane.widthProperty().subtract(16));

        String stats;
        if (state.bonusRatePoints > 0) {
            stats = String.format("HP: %d/%d | ATK: %d | DEF: %d | RP: %d (+%d)",
                    state.currentHealth, state.baseHealth,
                    state.currentAttack, state.currentDefense,
                    state.currentRatePoints, state.bonusRatePoints);
        } else {
            stats = String.format("HP: %d/%d | ATK: %d | DEF: %d | RP: %d",
                    state.currentHealth, state.baseHealth,
                    state.currentAttack, state.currentDefense,
                    state.currentRatePoints);
        }

        Text statsText = new Text(stats);
        statsText.getStyleClass().add("card-stats");

        Text desc = new Text(state.getLocalizedText());
        desc.getStyleClass().add("card-text");
        desc.wrappingWidthProperty().bind(creaturePane.widthProperty().subtract(16));

        creaturePane.getChildren().addAll(name, statsText, desc);
    }

    private VBox createHandCardNode(CardData data) {
        VBox box = new VBox(5);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(10));

        Text name = new Text(data.getLocalizedName());
        name.getStyleClass().add("card-title");
        name.wrappingWidthProperty().bind(box.widthProperty().subtract(16));

        VBox buffView = new VBox(5);
        buffView.setPadding(new Insets(2, 0, 0, 0));

        FlowPane statsBox = new FlowPane(8, 4);
        statsBox.setAlignment(Pos.CENTER_LEFT);
        addStatChangeText(statsBox, "HP", data.getStatChange("/health"), "hp");
        addStatChangeText(statsBox, "ATK", data.getStatChange("/attack"), "atk");
        addStatChangeText(statsBox, "DEF", data.getStatChange("/defense"), "def");
        addStatChangeText(statsBox, "RP", data.getStatChange("/ratePoints"), "rp");
        addStatChangeText(statsBox, "BET-", data.getStatChange("/opponent_bets"), "bet-dec");

        if (!statsBox.getChildren().isEmpty()) {
            buffView.getChildren().add(statsBox);
        }

        Text desc = new Text(data.getLocalizedText());
        desc.getStyleClass().add("card-text");
        desc.wrappingWidthProperty().bind(box.widthProperty().subtract(16));

        Region buffSpacer = new Region();
        VBox.setVgrow(buffSpacer, Priority.ALWAYS);

        BorderPane buffFooter = new BorderPane();
        if (data.cost > 0) {
            Text costText = new Text(String.format(I18n.getString("label.cost"), data.cost));
            costText.getStyleClass().add("card-cost");
            buffFooter.setLeft(costText);
        }

        if (data.getBetAmount() > 0) {
            Text betHint = new Text("$" + data.getBetAmount());
            betHint.getStyleClass().add("card-bet-hint");
            buffFooter.setRight(betHint);
        }

        buffView.getChildren().addAll(desc, buffSpacer, buffFooter);
        VBox.setVgrow(buffView, Priority.ALWAYS);

        VBox betView = new VBox(4);
        betView.setAlignment(Pos.CENTER);
        betView.setPadding(new Insets(10, 0, 0, 0));

        Region betSpacer = new Region();
        VBox.setVgrow(betSpacer, Priority.ALWAYS);

        Text betLabel = new Text(I18n.getString("label.bet"));
        betLabel.getStyleClass().add("card-cost");
        Text betAmountText = new Text(String.format(I18n.getString("label.betAmount"), data.getBetAmount()));
        betAmountText.getStyleClass().add("bet-amount-text");

        Region betSpacer2 = new Region();
        VBox.setVgrow(betSpacer2, Priority.ALWAYS);

        betView.getChildren().addAll(betSpacer, betLabel, betAmountText, betSpacer2);

        betView.setVisible(false);
        betView.setManaged(false);

        StackPane contentStack = new StackPane(buffView, betView);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        box.getChildren().addAll(name, contentStack);

        box.getProperties().put("isBet", false);

        if (data.getBetAmount() > 0) {
            box.setOnMouseClicked(ev -> {
                boolean isBet = (boolean) box.getProperties().get("isBet");
                box.getProperties().put("isBet", !isBet);
                buffView.setVisible(isBet);
                buffView.setManaged(isBet);
                betView.setVisible(!isBet);
                betView.setManaged(!isBet);
            });
        } else {
            box.setOnMouseClicked(ev -> {
                showInfo(I18n.getString("info.noBet"));
            });
        }

        box.setOnDragDetected(ev -> {
            boolean isBet = (data.getBetAmount() > 0) ? (boolean) box.getProperties().get("isBet") : false;
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

        Text name = new Text(data.getLocalizedName());
        name.getStyleClass().add("card-title");
        name.wrappingWidthProperty().bind(box.widthProperty().subtract(12));

        Text desc = new Text(data.getLocalizedText());
        desc.getStyleClass().add("card-text");
        desc.wrappingWidthProperty().bind(box.widthProperty().subtract(12));

        if (large && state != null) {
            String stats;
            if (state.bonusRatePoints > 0) {
                stats = String.format("HP: %d/%d | ATK: %d | DEF: %d | RP: %d (+%d)",
                        state.currentHealth, state.baseHealth,
                        state.currentAttack, state.currentDefense,
                        state.currentRatePoints, state.bonusRatePoints);
            } else {
                stats = String.format("HP: %d/%d | ATK: %d | DEF: %d | RP: %d",
                        state.currentHealth, state.baseHealth,
                        state.currentAttack, state.currentDefense,
                        state.currentRatePoints);
            }

            Text statsText = new Text(stats);
            statsText.getStyleClass().add("card-stats");
            box.getChildren().addAll(name, statsText, desc);
        } else {
            box.getChildren().addAll(name);
        }

        box.setUserData(data);
        return box;
    }

    private void addStatChangeText(FlowPane container, String prefix, int value, String styleType) {
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

    private HBox createAttackScale() {
        HBox scale = new HBox(5);
        scale.setAlignment(Pos.CENTER);
        scale.getStyleClass().add("dice-scale");

        Label d1 = new Label(I18n.getString("label.diceLabel.1"));
        d1.getStyleClass().add("dice-label");
        Label d2 = new Label(I18n.getString("label.diceLabel.2"));
        d2.getStyleClass().add("dice-label");
        Label d3 = new Label(I18n.getString("label.diceLabel.3"));
        d3.getStyleClass().add("dice-label");

        scale.getChildren().addAll(d1, d2, d3);
        return scale;
    }

    private void updateAttackScale(HBox scale, int currentAttack) {
        int diceCount = getDiceCount(currentAttack);
        for (int i = 0; i < scale.getChildren().size(); i++) {
            scale.getChildren().get(i).getStyleClass().remove("dice-label-active");
        }
        if (diceCount > 0) {
            scale.getChildren().get(diceCount - 1).getStyleClass().add("dice-label-active");
        }
    }

    private HBox createDefenseScale() {
        HBox scale = new HBox(5);
        scale.setAlignment(Pos.CENTER);
        scale.getStyleClass().add("defense-scale");

        Label d1 = new Label(I18n.getString("label.defenseBlock.1"));
        d1.getStyleClass().add("defense-label");
        Label d2 = new Label(I18n.getString("label.defenseBlock.2"));
        d2.getStyleClass().add("defense-label");
        Label d3 = new Label(I18n.getString("label.defenseBlock.3"));
        d3.getStyleClass().add("defense-label");

        scale.getChildren().addAll(d1, d2, d3);
        return scale;
    }

    private void updateDefenseScale(HBox scale, int currentDefense) {
        int blockCount = getDefenseBlock(currentDefense);
        for (int i = 0; i < scale.getChildren().size(); i++) {
            scale.getChildren().get(i).getStyleClass().remove("defense-label-active");
        }
        if (blockCount > 0) {
            scale.getChildren().get(blockCount - 1).getStyleClass().add("defense-label-active");
        }
    }

    private VBox createBetRewardScale(String creatureLabel) {
        VBox scaleVBox = new VBox(2);
        scaleVBox.setAlignment(Pos.CENTER);

        Text title = new Text(creatureLabel);
        title.getStyleClass().add("bet-scale-title");

        HBox row = createRewardRow();

        if (betRewardScaleC1Row == null) {
            betRewardScaleC1Row = row;
        } else {
            betRewardScaleC2Row = row;
        }

        scaleVBox.getChildren().addAll(title, row);
        return scaleVBox;
    }

    private HBox createRewardRow() {
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER);

        Rectangle yellow = new Rectangle(20, 20);
        yellow.getStyleClass().addAll("bet-scale-box", "bet-scale-yellow");
        Rectangle green = new Rectangle(20, 20);
        green.getStyleClass().addAll("bet-scale-box", "bet-scale-green");
        Rectangle red = new Rectangle(20, 20);
        red.getStyleClass().addAll("bet-scale-box", "bet-scale-red");

        row.getChildren().addAll(yellow, green, red);
        return row;
    }

    private void updateBetRewardScales() {
        if (creature1State == null || creature2State == null) return;
        int rpDiff = creature1State.getTotalRP() - creature2State.getTotalRP();
        updateRewardRow(betRewardScaleC1Row, rpDiff > 0, Math.abs(rpDiff));
        updateRewardRow(betRewardScaleC2Row, rpDiff < 0, Math.abs(rpDiff));
    }

    private void updateRewardRow(HBox row, boolean isFavorite, int diff) {
        javafx.scene.Node yellow = row.getChildren().get(0);
        javafx.scene.Node green = row.getChildren().get(1);
        javafx.scene.Node red = row.getChildren().get(2);

        yellow.setOpacity(0.2);
        green.setOpacity(0.2);
        red.setOpacity(0.2);

        if (isFavorite) {
            yellow.setOpacity(1.0);
        } else {
            if (diff < config.BET_REWARD_GREEN_THRESHOLD) {
                yellow.setOpacity(1.0);
            } else if (diff < config.BET_REWARD_RED_THRESHOLD) {
                green.setOpacity(1.0);
            } else {
                red.setOpacity(1.0);
            }
        }
    }

    private void updateAllScales() {
        if (attackScale1 != null) {
            updateAttackScale(attackScale1, creature1State.currentAttack);
            updateAttackScale(attackScale2, creature2State.currentAttack);
            updateDefenseScale(defenseScale1, creature1State.currentDefense);
            updateDefenseScale(defenseScale2, creature2State.currentDefense);
            updateBetRewardScales();
        }
    }

    private void removeCardFromHandById(String cardId) {
        List<CardData> currentHand = (currentPlayer == Player.PLAYER_1) ? player1Hand : player2Hand;

        for (int i = 0; i < currentHand.size(); i++) {
            if (currentHand.get(i).id.equals(cardId)) {
                currentHand.remove(i);
                return;
            }
        }
    }

    private void showInfo(String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setTitle(I18n.getString("info.title"));
        a.setContentText(text);
        a.getDialogPane().setPrefWidth(420);
        a.showAndWait();
    }

    private void showError(String title, String text) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(title);
        a.setTitle(I18n.getString("error.critical"));
        a.setContentText(text);
        a.getDialogPane().setPrefWidth(420);
        a.showAndWait();
    }

    private void loadDataWithJackson() throws IOException, URISyntaxException {
        createDefaultDataFileIfMissing(CONFIG_FILE);
        Path configPath = externalDataPath.resolve(CONFIG_FILE);

        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        config = mapper.readValue(Files.newInputStream(configPath), GameConfig.class);

        createDefaultDataFileIfMissing(CREATURES_FILE);
        createDefaultDataFileIfMissing(INFLUENCE_FILE);

        Path creaturesPath = externalDataPath.resolve(CREATURES_FILE);
        Path influencePath = externalDataPath.resolve(INFLUENCE_FILE);

        List<CardData> creatureList = mapper.readValue(
                Files.newInputStream(creaturesPath),
                new TypeReference<>() {}
        );

        List<CardData> influenceList = mapper.readValue(
                Files.newInputStream(influencePath),
                new TypeReference<>() {}
        );

        creatureTemplates.addAll(creatureList);
        influenceCardTemplates.addAll(influenceList);

        for (CardData c : creatureList) allCards.put(c.id, c);
        for (CardData c : influenceList) allCards.put(c.id, c);
    }

    private void createDefaultDataFileIfMissing(String fileName) throws IOException {
        Path externalPath = externalDataPath.resolve(fileName);

        if (Files.notExists(externalPath)) {
            if (fileName.equals(CONFIG_FILE)) {
                ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                GameConfig defaultConfig = new GameConfig();
                Files.writeString(externalPath, mapper.writeValueAsString(defaultConfig));
            } else {
                try (InputStream resourceStream = getResourceStream(fileName)) {
                    if (resourceStream == null) {
                        throw new IOException("Внутренний ресурс не найден: " + fileName);
                    }
                    Files.copy(resourceStream, externalPath);
                } catch (Exception e) {
                    throw new IOException("Не удалось создать внешний файл: " + fileName, e);
                }
            }
        }
    }

    private InputStream getResourceStream(String filename) throws IOException {
        InputStream is = Main.class.getResourceAsStream("/" + filename);
        if (is == null) {
            throw new IOException("Файл ресурса не найден: " + filename);
        }
        return is;
    }

    private String makeCss() {
        return """
            data:,
            .card {
              -fx-background-color: linear-gradient(#ffffff, #f3f6ff);
              -fx-border-color: #cbd7ff;
              -fx-border-radius: 10;
              -fx-background-radius: 10;
              -fx-padding: 10;
              -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 6, 0, 0, 2);
            }
            .creature-pane-hover {
              -fx-border-color: #8aa2ff;
              -fx-background-color: linear-gradient(#f5f8ff, #ffffff);
            }
            .card-title {
              -fx-font-weight: bold;
              -fx-font-size: 15px;
            }
            .card-cost {
              -fx-font-size: 13px;
              -fx-fill: #0066cc;
              -fx-font-weight: bold;
            }
            .card-bet-hint {
              -fx-font-size: 14px;
              -fx-fill: #008800;
              -fx-font-weight: bold;
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
            .card-stat-rp-pos, .card-stat-rp-neg,
            .card-stat-bet-dec-pos, .card-stat-bet-dec-neg {
              -fx-font-size: 13px;
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
            .card-stat-bet-dec-pos, .card-stat-bet-dec-neg { -fx-fill: #8A2BE2; }
            
            .card-stats {
              -fx-font-size: 14px;
              -fx-fill: #333333;
              -fx-font-weight: bold;
              -fx-padding: 2 0 0 0;
            }
            .card-text {
              -fx-font-size: 13px;
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
              -fx-background-color: #E50000;
              -fx-text-fill: white;
            }
            
            .defense-scale {
              -fx-border-color: #e0e0e0;
              -fx-border-width: 1px;
              -fx-border-radius: 5px;
              -fx-padding: 3px;
            }
            .defense-label {
              -fx-padding: 5px 10px;
              -fx-font-size: 12px;
              -fx-font-weight: bold;
              -fx-background-color: #f0f0f0;
              -fx-border-radius: 3px;
              -fx-text-fill: #999999;
            }
            .defense-label-active {
              -fx-background-color: #0078D7;
              -fx-text-fill: white;
            }
            
            .bet-scale-title {
                -fx-font-size: 11px;
                -fx-font-weight: bold;
                -fx-fill: #555555;
            }
            .bet-scale-label {
              -fx-font-size: 13px;
              -fx-font-weight: bold;
            }
            .bet-scale-box {
              -fx-stroke: #aaaaaa;
              -fx-stroke-width: 1px;
              -fx-opacity: 0.2;
            }
            .bet-scale-yellow { -fx-fill: #FFA500; }
            .bet-scale-green  { -fx-fill: #00A800; }
            .bet-scale-red    { -fx-fill: #E50000; }
            
            .score-text {
              -fx-font-size: 16px;
              -fx-font-weight: bold;
            }
            .text-p1 { -fx-fill: #0078D7; }
            .text-p2 { -fx-fill: #E50000; }

            .reward-yellow { -fx-fill: #FFA500; -fx-font-weight: bold; -fx-font-size: 14px; }
            .reward-green  { -fx-fill: #00A800; -fx-font-weight: bold; -fx-font-size: 14px; }
            .reward-red    { -fx-fill: #E50000; -fx-font-weight: bold; -fx-font-size: 14px; }
            
            .battle-stat-text {
              -fx-font-family: 'Consolas', 'Monospaced';
              -fx-font-size: 13px;
              -fx-line-spacing: 5px;
            }
            .battle-log-scroll {
                -fx-background-color: transparent;
                -fx-background-insets: 0;
                -fx-padding: 0;
            }
            .battle-log-text {
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-fill: #222222;
            }
            .hand-scroll-pane {
                -fx-background-color: transparent;
                -fx-background-insets: 0;
                -fx-padding: 0;
                -fx-border-color: transparent;
            }
            """;
    }

    public static class GameConfig {
        public int STARTING_HAND_SIZE = 5;
        public int MAX_HAND_SIZE = 5;
        public int MAX_TURN_POINTS = 4;
        public int MAX_ROUNDS_PER_BATTLE = 4;
        public int MAX_BATTLES = 3;
        public int ATTACK_TIER_1_MAX = 6;
        public int ATTACK_TIER_2_MAX = 14;
        public int DEFENSE_TIER_1_MAX = 3;
        public int DEFENSE_TIER_2_MAX = 8;
        public double BATTLE_STEP_DELAY = 1.0;
        public int BET_REWARD_GREEN_THRESHOLD = 4;
        public int BET_REWARD_RED_THRESHOLD = 8;
        public double REWARD_YELLOW_MULT = 1.0;
        public double REWARD_GREEN_MULT = 2.0;
        public double REWARD_RED_MULT = 3.0;
        public int BET_AMOUNT_PER_RP = 300;
    }

    public static class CardData {
        public String id;
        public Map<String, String> name;
        public Map<String, String> text;
        public int cost;
        public Integer betAmount;
        public int health;
        public int attack;
        public int defense;
        public int ratePoints;
        public Integer count;
        public List<Effect> effects;

        // --- Новые поля для свойств существ ---
        public Integer rpLimit;
        public Integer magicBarrier;
        public Integer vampirism;
        public List<Integer> missChance;
        public List<Integer> stunChance;
        public boolean thief;
        public List<DynamicStatConfig> dynamicStats;

        public CardData() {
            this.effects = new ArrayList<>();
            this.name = new HashMap<>();
            this.text = new HashMap<>();
        }

        public int getCount() {
            return (count == null || count <= 0) ? 1 : count;
        }

        public int getStatChange(String path) {
            if (effects == null) {
                return 0;
            }
            int total = 0;
            for (Effect e : effects) {
                if (path.equals(e.path) && e.value != null) {
                    total += e.value;
                }
            }
            return total;
        }

        public String getLocalizedName() {
            return name.getOrDefault(I18n.getLang(), name.get("en"));
        }

        public String getLocalizedText() {
            return text.getOrDefault(I18n.getLang(), text.get("en"));
        }

        public int getBetAmount() {
            return (betAmount != null) ? betAmount : 0;
        }
    }

    public static class DynamicStatConfig {
        public int thresholdHp;
        public List<Effect> effects;
    }

    public static class Effect {
        public String op;
        public String path;
        public Integer value;
        public Effect() {}
    }

    public static class CreatureState {
        public CardData baseCard;
        public String name;
        public String text;
        public int baseHealth;
        public int currentHealth;
        public int baseAttack;
        public int currentAttack;
        public int baseDefense;
        public int currentDefense;
        public int baseRatePoints;
        public int currentRatePoints;
        public int bonusRatePoints;
        public int bettingBlockedUntilRound = 0;

        // --- Поля для новых механик ---
        public int rpLimit = 0;
        public int magicBarrier = 0;
        public int vampirism = 0;
        public List<Integer> missChance;
        public List<Integer> stunChance;
        public boolean thief = false;
        public List<DynamicStatConfig> dynamicStats;
        public boolean isStunned = false;

        // Для отката динамических статов
        private int dynamicBonusAttack = 0;
        private int dynamicBonusDefense = 0;
        private int dynamicBonusRP = 0;

        public CreatureState(CardData baseCard) {
            this.baseCard = baseCard;
            this.name = baseCard.getLocalizedName();
            this.text = baseCard.getLocalizedText();
            this.baseHealth = baseCard.health;
            this.currentHealth = baseCard.health;
            this.baseAttack = baseCard.attack;
            this.currentAttack = baseCard.attack;
            this.baseDefense = baseCard.defense;
            this.currentDefense = baseCard.defense;
            this.baseRatePoints = baseCard.ratePoints;
            this.currentRatePoints = baseCard.ratePoints;
            this.bonusRatePoints = 0;

            // Загрузка особенностей
            this.rpLimit = (baseCard.rpLimit != null) ? baseCard.rpLimit : 0;
            this.magicBarrier = (baseCard.magicBarrier != null) ? baseCard.magicBarrier : 0;
            this.vampirism = (baseCard.vampirism != null) ? baseCard.vampirism : 0;
            this.missChance = baseCard.missChance;
            this.stunChance = baseCard.stunChance;
            this.thief = baseCard.thief;
            this.dynamicStats = baseCard.dynamicStats;
        }

        public void recalculateDynamicStats() {
            if (dynamicStats == null || dynamicStats.isEmpty()) return;

            // 1. Откат старых бонусов
            this.currentAttack -= dynamicBonusAttack;
            this.currentDefense -= dynamicBonusDefense;
            this.currentRatePoints -= dynamicBonusRP;

            dynamicBonusAttack = 0;
            dynamicBonusDefense = 0;
            dynamicBonusRP = 0;

            // 2. Расчет новых бонусов на основе текущего HP
            for (DynamicStatConfig cfg : dynamicStats) {
                if (this.currentHealth >= cfg.thresholdHp && cfg.effects != null) {
                    for (Effect e : cfg.effects) {
                        if ("inc".equals(e.op) && e.value != null) {
                            switch (e.path) {
                                case "/attack": dynamicBonusAttack += e.value; break;
                                case "/defense": dynamicBonusDefense += e.value; break;
                                case "/ratePoints": dynamicBonusRP += e.value; break;
                            }
                        }
                    }
                }
            }

            // 3. Применение
            this.currentAttack = Math.max(1, this.currentAttack + dynamicBonusAttack);
            this.currentDefense = Math.max(0, this.currentDefense + dynamicBonusDefense);

            int newRP = this.currentRatePoints + dynamicBonusRP;
            if (rpLimit > 0) newRP = Math.min(newRP, rpLimit);
            this.currentRatePoints = Math.max(1, newRP);
        }

        public String getLocalizedName() {
            return this.name;
        }
        public String getLocalizedText() {
            return this.text;
        }
        public int getTotalRP() {
            return currentRatePoints + bonusRatePoints;
        }
    }
}