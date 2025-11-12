package yermakov.oleksii;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import javafx.animation.PauseTransition; // Для задержки
import javafx.stage.Modality; // Для сплэш-экрана
import javafx.util.Duration; // Для времени
import javafx.scene.control.Label; // Для текста на сплэш-экране

public class Main extends Application {

    private static final String CREATURES_FILE = "creatures.json";
    private static final String INFLUENCE_FILE = "influenceCards.json";

    private static final int STARTING_HAND_SIZE = 5;
    private static final int MAX_HAND_SIZE = 5;
    private static final int MAX_TURN_POINTS = 4;
    private static final int MAX_ROUNDS = 4;
    private int currentRound = 1;

    private static final int ATTACK_TIER_1_MAX = 6;
    private static final int ATTACK_TIER_2_MAX = 14;

    // --- ИЗМЕНЕНИЕ: Карта теперь по ID, так как парсер Jackson
    private final Map<String, CardData> allCards = new HashMap<>();
    // --- ИЗМЕНЕНИЕ: Это теперь "шаблоны", а не игровые объекты
    private final List<CardData> creatureTemplates = new ArrayList<>();
    private final List<CardData> influenceDeck = new ArrayList<>();

    // --- НОВЫЕ ПОЛЯ: Состояние (State) существ в игре ---
    private CreatureState creature1State;
    private CreatureState creature2State;
    // --- КОНЕЦ НОВЫХ ПОЛЕЙ ---

    private enum Player { PLAYER_1, PLAYER_2 }
    private Player currentPlayer;
    private final List<CardData> player1Hand = new ArrayList<>();
    private final List<CardData> player2Hand = new ArrayList<>();
    private int currentTurnPointsUsed = 0;

    private int p1_BetsOn_C1 = 0;
    private int p2_BetsOn_C1 = 0;
    private int p1_BetsOn_C2 = 0;
    private int p2_BetsOn_C2 = 0;

    // UI references
    private HBox handBox;
    private Text turnPointsText;
    // --- НОВЫЕ ПОЛЯ: Ссылки на UI существ для обновления ---
    private VBox creature1Pane;
    private VBox creature2Pane;

    private VBox centralDropZone1;
    private VBox centralDropZone2;

    private Text creature1BetText;
    private Text creature2BetText;


    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        try {
            loadDataWithJackson();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Критическая ошибка",
                    "Не удалось загрузить данные из ресурсов: " + e.getMessage() +
                            "\n\nУбедитесь, что JSON-файлы верны и библиотека Jackson подключена.");
            return;
        }

        startGame();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));

        // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1) Новая компоновка ---

        // 1. Панели существ (только карта)
        creature1Pane = createCreaturePane(creature1State);
        creature2Pane = createCreaturePane(creature2State);

        // 2. Текст ставок
        creature1BetText = new Text();
        creature1BetText.getStyleClass().add("bet-total-text");
        creature2BetText = new Text();
        creature2BetText.getStyleClass().add("bet-total-text");
        updateBetDisplays(); // Установить "Ставки: 0"

        // 3. Колонки для Существ + Ставок
        VBox creature1Column = new VBox(5, creature1Pane, creature1BetText);
        creature1Column.setAlignment(Pos.CENTER);
        VBox creature2Column = new VBox(5, creature2Pane, creature2BetText);
        creature2Column.setAlignment(Pos.CENTER);

        // 4. Центральные стопки (для баффов и ставок)
        centralDropZone1 = createCentralDropZone(creature1State, creature1Pane, 1);
        centralDropZone2 = createCentralDropZone(creature2State, creature2Pane, 2);

        // 5. Собираем верхний ряд
        HBox centerCreatures = new HBox(15);
        centerCreatures.setAlignment(Pos.CENTER);
        centerCreatures.setPadding(new Insets(10));
        centerCreatures.getChildren().addAll(
                creature1Column, // Колонка 1 (Существо + Ставки)
                centralDropZone1,
                centralDropZone2,
                creature2Column // Колонка 2 (Существо + Ставки)
        );
        root.setTop(centerCreatures);
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        VBox bottom = new VBox(10);
        bottom.setPadding(new Insets(8));
        bottom.setAlignment(Pos.CENTER);

        Button endTurnBtn = new Button("Завершить ход");
        endTurnBtn.setMinWidth(180);
        endTurnBtn.setOnAction(e -> endTurn());

        HBox buttonBar = new HBox(10, endTurnBtn);
        buttonBar.setAlignment(Pos.CENTER);

        turnPointsText = new Text();
        turnPointsText.getStyleClass().add("card-title");
        updateTurnPointsText();

        handBox = new HBox(8);
        handBox.setPadding(new Insets(8));
        handBox.setAlignment(Pos.CENTER);

        ScrollPane handScroll = new ScrollPane(handBox);
        handScroll.setPrefHeight(170);
        handScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        handScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        handScroll.setFitToHeight(true);
        handScroll.setMaxWidth(800);

        bottom.getChildren().addAll(buttonBar, turnPointsText, handScroll);
        root.setBottom(bottom);

        updateHandDisplay();

        Scene scene = new Scene(root, 1200, 730);
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

    // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1) Логика боя с подсчетом выигрыша ---
    private void startBattle() {
        StringBuilder battleLog = new StringBuilder("БИТВА НАЧИНАЕТСЯ!\n\n");

        CreatureState attacker;
        CreatureState defender;

        // 1. Определяем инициативу (кто ходит первым)
        // ... (логика инициативы без изменений) ...
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

        // 2. Боевой цикл (без изменений)
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

        // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1) Определение победителя и выигрыша ---
        String winnerName;
        int player1Winnings;
        int player2Winnings;

        if (creature1State.currentHealth > 0) {
            winnerName = creature1State.name;
            player1Winnings = p1_BetsOn_C1; // P1 выигрывает то, что поставил на C1
            player2Winnings = p2_BetsOn_C1; // P2 выигрывает то, что поставил на C1
        } else {
            winnerName = creature2State.name;
            player1Winnings = p1_BetsOn_C2; // P1 выигрывает то, что поставил на C2
            player2Winnings = p2_BetsOn_C2; // P2 выигрывает то, что поставил на C2
        }

        battleLog.append("\n").append(winnerName).append(" ПОБЕЖДАЕТ!");

        // 4. Формируем сообщение о выигрыше
        String winningsMessage = String.format(
                "Игрок 1 выиграл: %d\nИгрок 2 выиграл: %d",
                player1Winnings,
                player2Winnings
        );
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        // Показываем лог боя
        showInfo(battleLog.toString());

        // 5. Показываем сплэш-экран и перезапускаем
        showEndGameSplashAndRestart(winnerName + " ПОБЕЖДАЕТ!", winningsMessage);
    }

    // --- НОВЫЙ МЕТОД: Определяет кол-во кубиков по атаке ---
    private int getDiceCount(int attack) {
        if (attack <= ATTACK_TIER_1_MAX) {
            return 1; // 1-6 атаки
        }
        if (attack <= ATTACK_TIER_2_MAX) {
            return 2; // 7-14 атаки
        }
        return 3; // 15+ атаки
    }

    // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1) Показывает победителя и выигрыш ---
    private void showEndGameSplashAndRestart(String winnerMessage, String winningsMessage) {
        Stage splashStage = new Stage();
        splashStage.initModality(Modality.APPLICATION_MODAL);
        splashStage.initOwner(creature1Pane.getScene().getWindow());

        // 1. Текст победителя
        Label label = new Label(winnerMessage);
        label.getStyleClass().add("card-title");

        // 2. Текст выигрыша
        Label labelWinnings = new Label(winningsMessage);
        labelWinnings.getStyleClass().add("card-stats"); // Используем стиль статов

        // 3. VBox для обоих текстов
        VBox splashRoot = new VBox(10, label, labelWinnings); // VBox с отступом 10
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

        // --- НОВЫЙ КОД: Сброс ставок ---
        p1_BetsOn_C1 = 0;
        p2_BetsOn_C1 = 0;
        p1_BetsOn_C2 = 0;
        p2_BetsOn_C2 = 0;
        updateBetDisplays();
        // --- КОНЕЦ НОВОГО КОДА ---

        startGame();

        creature1Pane.setUserData(creature1State);
        creature2Pane.setUserData(creature2State);

        clearDropZone(centralDropZone1);
        clearDropZone(centralDropZone2);

        refreshCreaturePane(creature1Pane, creature1State);
        refreshCreaturePane(creature2Pane, creature2State);

        updateHandDisplay();
        updateTurnPointsText();
    }
    // --- ИЗМЕНЕНИЕ: (ИСПРАВЛЕН БАГ) ---
    private void clearDropZone(VBox dropZone) {
        dropZone.getChildren().clear(); // Очистить UI

        if (dropZone.getUserData() instanceof List) {
            ((List<?>) dropZone.getUserData()).clear();
        }
    }
    // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1) Логика клика/состояния переехала в createHandCardNode ---
    private void updateHandDisplay() {
        handBox.getChildren().clear();
        List<CardData> currentHand = (currentPlayer == Player.PLAYER_1) ? player1Hand : player2Hand;

        for (CardData card : currentHand) {
            // --- ИЗМЕНЕНИЕ: Используем новый метод для карт в руке ---
            VBox cardNode = createHandCardNode(card);
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---

            cardNode.getStyleClass().add("hand-card");
            cardNode.setPrefSize(150, 140);
            cardNode.setMinSize(150, 140);
            cardNode.setMaxSize(150, 140);

            // Клик для просмотра (если это не переключатель)
            // cardNode.setOnMouseClicked(ev -> showInfo(card.name + " [" + card.cost + "]\n\n" + card.text));
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

    // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1) Принимает CreatureState и целевую стопку ---
    private VBox createCreaturePane(CreatureState state) {

        VBox creatureCardPane = createCardNode(state.baseCard, true, state);
        creatureCardPane.setPrefSize(260, 180);
        creatureCardPane.setMinSize(260, 180);
        creatureCardPane.setMaxSize(260, 180);

        // Вся карта является контейнером
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
        dropArea.setUserData(new ArrayList<CardData>()); // Здесь хранятся карты

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
                String mode = parts[1]; // "buff" или "bet"

                CardData cd = allCards.get(cardId);
                if (cd == null) return;

                if (currentTurnPointsUsed + cd.cost <= MAX_TURN_POINTS) {
                    currentTurnPointsUsed += cd.cost;
                    updateTurnPointsText();
                    removeCardFromHandById(cardId);

                    if ("buff".equals(mode)) {
                        // ... (Логика "БАФФ" без изменений) ...
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
                    // --- ИЗМЕНЕНИЕ: (ЗАПРОС 1) Логика "СТАВКА" ---
                    else if ("bet".equals(mode)) {
                        // 1. Применяем ставку к ЦЕЛЕВОМУ СУЩЕСТВУ от ТЕКУЩЕГО ИГРОКА
                        if (targetBetId == 1) { // Ставка на C1
                            if (currentPlayer == Player.PLAYER_1) {
                                p1_BetsOn_C1 += cd.betAmount;
                            } else {
                                p2_BetsOn_C1 += cd.betAmount;
                            }
                        } else { // Ставка на C2
                            if (currentPlayer == Player.PLAYER_1) {
                                p1_BetsOn_C2 += cd.betAmount;
                            } else {
                                p2_BetsOn_C2 += cd.betAmount;
                            }
                        }
                        updateBetDisplays(); // Обновляем общий банк

                        // 2. Добавляем карту (СТАВКА) визуально В ЭТУ СТОПКУ
                        Text betText = new Text("СТАВКА: +" + cd.betAmount);
                        // Добавляем цвет в зависимости от игрока
                        betText.getStyleClass().add(
                                (currentPlayer == Player.PLAYER_1) ? "bet-text-p1" : "bet-text-p2"
                        );
                        dropArea.getChildren().add(betText);
                    }
                    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

                    @SuppressWarnings("unchecked")
                    List<CardData> list = (List<CardData>) dropArea.getUserData();
                    list.add(cd);

                    success = true;
                    updateHandDisplay();
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
            List<CardData> list = (List<CardData>) dropArea.getUserData(); // Читаем из этой стопки
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

                    // --- НОВЫЙ КОД: Показ ставки в истории ---
                    if (cd.betAmount > 0) {
                        effectStrings.add("СТАВКА: +" + cd.betAmount);
                    }
                    // --- КОНЕЦ НОВОГО КОДА ---

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
    // --- НОВЫЙ МЕТОД: Обновляет UI карточки существа ---
    // --- ИСПРАВЛЕННЫЙ МЕТОД: Обновляет UI карточки существа ---
    private void refreshCreaturePane(VBox creaturePane, CreatureState state) {

        // --- ИСПРАВЛЕНИЕ: (Удалена строка с ошибкой ClassCastException) ---
        // VBox cardNode = (VBox) creaturePane.getChildren().get(0); (ЭТО БЫЛО НЕПРАВИЛЬНО)

        // Мы очищаем саму панель (которая и есть карточка),
        // чтобы добавить в нее обновленный текст
        creaturePane.getChildren().clear();
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

        // Создаем заново UI
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

        // --- ИСПРАВЛЕНИЕ: Привязываем к ширине самой creaturePane ---
        desc.wrappingWidthProperty().bind(creaturePane.widthProperty().subtract(12));
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

        // --- ИСПРАВЛЕНИЕ: Добавляем детей (Text) прямо в creaturePane (VBox) ---
        creaturePane.getChildren().addAll(name, statsText, desc);
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---
    }
    // --- КОНЕЦ НОВОГО МЕТОДА ---

    // --- НОВЫЙ МЕТОД: (ЗАПРОС 1) Для создания карт в руке (с 2 состояниями) ---
    private VBox createHandCardNode(CardData data) {
        VBox box = new VBox(4); // 4px spacing
        box.getStyleClass().add("card");
        box.setPadding(new Insets(6));

        // --- 1. Элементы, видимые всегда ---
        Text name = new Text(data.name);
        name.getStyleClass().add("card-title");

        // --- 2. Элементы "Режима БАФФ" (по умолчанию) ---
        VBox buffView = new VBox(4);
        buffView.setPadding(new Insets(2, 0, 0, 0));

        // Стоимость
        if (data.cost > 0) {
            Text costText = new Text("Стоимость: " + data.cost);
            costText.getStyleClass().add("card-cost");
            buffView.getChildren().add(costText);
        }

        // Статы (HP, ATK, DEF, RP)
        HBox statsBox = new HBox(8);
        addStatChangeText(statsBox, "HP", data.getStatChange("/health"), "hp");
        addStatChangeText(statsBox, "ATK", data.getStatChange("/attack"), "atk");
        addStatChangeText(statsBox, "DEF", data.getStatChange("/defense"), "def");
        addStatChangeText(statsBox, "RP", data.getStatChange("/ratePoints"), "rp");

        if (!statsBox.getChildren().isEmpty()) {
            buffView.getChildren().add(statsBox);
        }

        // Описание
        Text desc = new Text(data.text);
        desc.getStyleClass().add("card-text");
        desc.wrappingWidthProperty().bind(box.widthProperty().subtract(12));
        buffView.getChildren().add(desc);

        // --- 3. Элементы "Режима СТАВКА" (скрыты) ---
        VBox betView = new VBox(4);
        betView.setAlignment(Pos.CENTER);

        Text betLabel = new Text("СТАВКА");
        betLabel.getStyleClass().add("card-cost");

        Text betAmountText = new Text("+" + data.betAmount);
        betAmountText.getStyleClass().add("bet-amount-text"); // Новый стиль

        betView.getChildren().addAll(betLabel, betAmountText);

        // Скрываем режим "Ставка"
        betView.setVisible(false);
        betView.setManaged(false);

        // --- 4. Сборка ---
        box.getChildren().addAll(name, buffView, betView);

        // --- 5. Логика переключения (Клик) ---
        box.getProperties().put("isBet", false); // Начальное состояние
        box.setOnMouseClicked(ev -> {
            boolean isBet = (boolean) box.getProperties().get("isBet");
            // Инвертируем состояние
            box.getProperties().put("isBet", !isBet);

            // Переключаем видимость
            buffView.setVisible(isBet);
            buffView.setManaged(isBet);
            betView.setVisible(!isBet);
            betView.setManaged(!isBet);
        });

        // --- 6. Логика D&D (Drag Detected) ---
        box.setOnDragDetected(ev -> {
            boolean isBet = (boolean) box.getProperties().get("isBet");
            String mode = isBet ? "bet" : "buff";

            Dragboard db = box.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();

            // --- ИЗМЕНЕНИЕ: Передаем ID и Режим ---
            content.putString(data.id + ";" + mode);
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---

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
            // --- ЭТО КАРТА СУЩЕСТВА ---
            String stats = String.format("HP: %d/%d | ATK: %d | DEF: %d | RP: %d",
                    state.currentHealth, state.baseHealth,
                    state.currentAttack, state.currentDefense,
                    state.currentRatePoints);
            Text statsText = new Text(stats);
            statsText.getStyleClass().add("card-stats");
            box.getChildren().addAll(name, statsText, desc);

        } else {
            // --- ЭТО КАРТА ВЛИЯНИЯ (в стопке сброса) ---
            // (Больше не отображаем статы в стопке, только имя, для простоты)
            box.getChildren().addAll(name);
        }

        box.setUserData(data);
        return box;
    }

    // --- ИЗМЕНЕНИЕ: (ЗАПРОС 3) Создает Text для 8 цветов ---
    private void addStatChangeText(HBox container, String prefix, int value, String styleType) {
        if (value == 0) {
            return; // Не показываем, если нет изменений
        }

        String text = prefix + ": " + (value > 0 ? "+" : "") + value;
        Text statText = new Text(text);

        // --- ИЗМЕНЕНИЕ: Генерируем 1 из 8 классов (н.п. card-stat-hp-pos) ---
        if (value > 0) {
            statText.getStyleClass().add("card-stat-" + styleType + "-pos");
        } else {
            statText.getStyleClass().add("card-stat-" + styleType + "-neg");
        }
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        container.getChildren().add(statText);
    }

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

    // --- НОВЫЙ МЕТОД: Парсер на Jackson ---
    private void loadDataWithJackson() throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                // Игнорируем поля, которых нет в POJO
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 1. Читаем существ
        List<CardData> creatureList = mapper.readValue(
                getResourceStream(CREATURES_FILE),
                new TypeReference<>() {}
        );

        // 2. Читаем карты влияния
        List<CardData> influenceList = mapper.readValue(
                getResourceStream(INFLUENCE_FILE),
                new TypeReference<>() {}
        );

        // 3. Заполняем наши списки
        creatureTemplates.addAll(creatureList);

        influenceDeck.addAll(influenceList);
        Collections.shuffle(influenceDeck);

        // 4. Заполняем allCards для быстрого доступа
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
    // --- КОНЕЦ НОВЫХ МЕТОДОВ ---

    // --- УДАЛЕНЫ МЕТОДЫ: ---
    // loadDataFromResources()
    // readResourceFile()
    // parseArrayIntoList()
    // parseObject()

    // --- ИЗМЕНЕНИЕ: CSS для 8 цветов ---
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
            
            /* --- НОВЫЕ СТИЛИ (ЗАПРОС 1) --- */
            .bet-amount-text {
              -fx-font-size: 32px;
              -fx-font-weight: bold;
              -fx-fill: #008800; /* Зеленый */
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
            .bet-amount-text {
              -fx-font-size: 32px;
              -fx-font-weight: bold;
              -fx-fill: #008800; /* Зеленый */
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
                                                        /* Стили для ставок в стопке */
                                                        .bet-text-p1 {
                                                          -fx-font-size: 12px;
                                                          -fx-font-weight: bold;
                                                          -fx-fill: #0078D7; /* Синий P1 */
                                                        }
                                                        .bet-text-p2 {
                                                          -fx-font-size: 12px;
                                                          -fx-font-weight: bold;
                                                          -fx-fill: #E50000; /* Красный P2 */
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
              -fx-alignment: top-center; /* Изменено */
            }
            .drop-area-hover {
              -fx-border-color: #8aa2ff;
              -fx-background-color: linear-gradient(#f5f8ff, #ffffff);
            }
            """;
    }

    // --- НОВЫЕ/ОБНОВЛЕННЫЕ POJO (внутренние классы) ---

    // Этот класс теперь универсален, Jackson сам разберется,
    // какие поля null (e.g. effects у существ, attack у карт)
    public static class CardData {
        public String id;
        public String name;
        public String text; // (ЗАПРОС 2) Теперь это короткое описание
        public String type;
        public int cost;
        public int betAmount;

        // Статы (для существ)
        public int health;
        public int attack;
        public int defense;
        public int ratePoints;

        // Эффекты (для карт влияния)
        public List<Effect> effects;

        // Пустой конструктор для Jackson
        public CardData() {
            this.effects = new ArrayList<>();
        }

        // --- ИЗМЕНЕНИЕ: (ЗАПРОС 3) Умный метод для получения всех статов ---
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
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---
    }

    // POJO для эффекта
    public static class Effect {
        public String op;   // "inc"
        public String path; // "/health"
        public int value; // 10

        public Effect() {}
    }

    // POJO для "живого" существа
    public static class CreatureState {
        public CardData baseCard; // Шаблон
        public String name;
        public int baseHealth;
        public int currentHealth;
        public int baseAttack;
        public int currentAttack;
        public int baseDefense;
        public int currentDefense;
        public int baseRatePoints; // <-- НОВОЕ ПОЛЕ
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
    // --- КОНЕЦ POJO ---
}