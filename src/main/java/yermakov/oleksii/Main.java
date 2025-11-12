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
    private static final int MAX_ROUNDS = 7;
    private int currentRound = 1;

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

    // UI references
    private HBox handBox;
    private Text turnPointsText;
    // --- НОВЫЕ ПОЛЯ: Ссылки на UI существ для обновления ---
    private VBox creature1Pane;
    private VBox creature2Pane;
    // --- КОНЕЦ НОВЫХ ПОЛЕЙ ---


    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        try {
            // --- ИЗМЕНЕНИЕ: Полностью новый парсер на Jackson ---
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

        // --- ИЗМЕНЕНИЕ: Создаем UI на основе CreatureState ---
        creature1Pane = createCreaturePane(creature1State);
        creature2Pane = createCreaturePane(creature2State);
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        HBox centerCreatures = new HBox(40);
        centerCreatures.setAlignment(Pos.CENTER);
        centerCreatures.setPadding(new Insets(10));
        centerCreatures.getChildren().addAll(creature1Pane, creature2Pane);
        root.setTop(centerCreatures);

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
        handBox.setAlignment(Pos.CENTER_LEFT);

        ScrollPane handScroll = new ScrollPane(handBox);
        handScroll.setPrefHeight(140);
        handScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        handScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        handScroll.setFitToHeight(true);

        bottom.getChildren().addAll(buttonBar, turnPointsText, handScroll);
        root.setBottom(bottom);

        updateHandDisplay();

        Scene scene = new Scene(root, 900, 700); // Увеличил высоту для статов
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

        // --- НОВЫЙ КОД: Создаем "живых" существ из шаблонов ---
        if (creatureTemplates.size() < 2) {
            showError("Ошибка данных", "Недостаточно шаблонов существ!");
            return;
        }
        creature1State = new CreatureState(creatureTemplates.get(0));
        creature2State = new CreatureState(creatureTemplates.get(1));
        // --- КОНЕЦ НОВОГО КОДА ---


        for (int i = 0; i < STARTING_HAND_SIZE; i++) {
            drawCardToHandData(Player.PLAYER_1);
            drawCardToHandData(Player.PLAYER_2);
        }
    }

    private void endTurn() {
        // --- Логика для завершения хода Игрока 1 ---
        if (currentPlayer == Player.PLAYER_1) {
            currentPlayer = Player.PLAYER_2;
            currentTurnPointsUsed = 0;

            drawCardsToMax(Player.PLAYER_2);

            updateTurnPointsText();
            updateHandDisplay();
            showInfo("Ход переходит к: Игрок 2");
        }
        // --- Логика для завершения хода Игрока 2 (Конец раунда) ---
        else {
            if (currentRound >= MAX_ROUNDS) {
                // --- ИГРА ОКОНЧЕНА ---
                showEndGameSplashAndRestart();
            } else {
                // --- СЛЕДУЮЩИЙ РАУНД ---
                currentRound++;
                currentPlayer = Player.PLAYER_1;
                currentTurnPointsUsed = 0;

                drawCardsToMax(Player.PLAYER_1);

                updateTurnPointsText();
                updateHandDisplay();
                showInfo("Раунд " + currentRound + "! Ход переходит к: Игрок 1");
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
        // --- ИЗМЕНЕНИЕ: Добавлен счетчик раундов ---
        turnPointsText.setText(String.format("Раунд: %d / %d | Ход: %s | Очки: %d / %d",
                currentRound, MAX_ROUNDS, playerLabel, currentTurnPointsUsed, MAX_TURN_POINTS));
    }

    private void showEndGameSplashAndRestart() {
        Stage splashStage = new Stage();
        splashStage.initModality(Modality.APPLICATION_MODAL);
        // Получаем "родительское" окно, чтобы сплэш появился над ним
        splashStage.initOwner(creature1Pane.getScene().getWindow());

        Label label = new Label("БОЙ ОКОНЧЕН");
        label.getStyleClass().add("card-title"); // Используем стиль
        VBox splashRoot = new VBox(label);
        splashRoot.setAlignment(Pos.CENTER);
        splashRoot.setPadding(new Insets(50));

        splashStage.setScene(new Scene(splashRoot));
        splashStage.setTitle("Конец игры");
        splashStage.show();

        // Задержка в 2 секунды
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(e -> {
            splashStage.close();
            restartGame(); // Перезапускаем игру ПОСЛЕ закрытия
        });
        delay.play();
    }

    // --- НОВЫЙ МЕТОД: Сброс игры в начальное состояние ---
    private void restartGame() {
        // 1. Сбрасываем счетчик
        currentRound = 1;

        // 2. Сбрасываем все данные (руки, очки, создает НОВЫЕ state существ)
        startGame();

        // 3. Обновляем ССЫЛКИ на новые state в старом UI
        creature1Pane.setUserData(creature1State);
        creature2Pane.setUserData(creature2State);

        // 4. Очищаем контейнеры (drop-зоны)
        clearDropZone(creature1Pane);
        clearDropZone(creature2Pane);

        // 5. Обновляем UI существ (сброс HP/RP и т.д.)
        refreshCreaturePane(creature1Pane, creature1State);
        refreshCreaturePane(creature2Pane, creature2State);

        // 6. Обновляем руки и текст
        updateHandDisplay();
        updateTurnPointsText();
    }

    // --- НОВЫЙ МЕТОД: Вспомогательный, для очистки drop-зон ---
    private void clearDropZone(VBox creaturePane) {
        // 0-й элемент - VBox карты, 1-й элемент - VBox dropArea
        VBox dropArea = (VBox) creaturePane.getChildren().get(1);
        dropArea.getChildren().clear(); // Очистить UI

        // Очистить данные
        if (dropArea.getUserData() instanceof List) {
            ((List<?>) dropArea.getUserData()).clear();
        }
    }

    private void updateHandDisplay() {
        handBox.getChildren().clear();
        List<CardData> currentHand = (currentPlayer == Player.PLAYER_1) ? player1Hand : player2Hand;

        for (CardData card : currentHand) {
            // Передаем null в state, так как это карта влияния
            VBox cardNode = createCardNode(card, false, null);
            cardNode.getStyleClass().add("hand-card");

            cardNode.setPrefSize(220, 60);
            cardNode.setMinSize(220, 60);
            cardNode.setMaxSize(220, 60);

            cardNode.setOnMouseClicked(ev -> showInfo(card.name + " [" + card.cost + "]\n\n" + card.text));
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

    // --- ИЗМЕНЕНИЕ: Принимает CreatureState ---
    private VBox createCreaturePane(CreatureState state) {
        VBox container = new VBox(8);
        container.setAlignment(Pos.TOP_CENTER);
        container.setUserData(state);

        // Передаем state для отображения статов
        VBox card = createCardNode(state.baseCard, true, state);
        card.setPrefSize(260, 180);
        card.setMinSize(260, 180);
        card.setMaxSize(260, 180);

        VBox dropArea = new VBox(4);
        dropArea.setPrefSize(260, 110);
        dropArea.setMinSize(260, 110);
        dropArea.setMaxSize(260, 110);
        dropArea.setAlignment(Pos.TOP_CENTER);
        dropArea.getStyleClass().add("drop-area");
        dropArea.setUserData(new ArrayList<CardData>()); // Тут храним примененные карты

        container.setOnDragOver(ev -> {
            if (ev.getGestureSource() != container && ev.getDragboard().hasString()) {
                ev.acceptTransferModes(TransferMode.MOVE);
                dropArea.getStyleClass().add("drop-area-hover");
            }
            ev.consume();
        });

        container.setOnDragExited(ev -> {
            dropArea.getStyleClass().remove("drop-area-hover");
            ev.consume();
        });

        // --- ИЗМЕНЕНИЕ: Логика Drop (применение патча) ---
        container.setOnDragDropped(ev -> {
            Dragboard db = ev.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String cardId = db.getString();
                CardData cd = allCards.get(cardId); // Карта влияния

                if (cd != null && !"creature".equals(cd.type)) {
                    if (currentTurnPointsUsed + cd.cost <= MAX_TURN_POINTS) {

                        currentTurnPointsUsed += cd.cost;
                        updateTurnPointsText();
                        removeCardFromHandById(cardId);

                        // --- НОВЫЙ КОД: Применяем эффекты ---
                        // 1. Получаем state существа, на которое бросили
                        CreatureState targetState = (CreatureState) container.getUserData();

                        // 2. Применяем каждый эффект
                        if (cd.effects != null) {
                            for (Effect effect : cd.effects) {
                                PatchUtils.applyEffect(targetState, effect);
                            }
                        }

                        // 3. Обновляем UI существа
                        refreshCreaturePane(container, targetState);
                        // --- КОНЕЦ НОВОГО КОДА ---

                        // Добавляем карту в dropArea (визуально)
                        VBox small = createCardNode(cd, false, null);
                        small.setPrefSize(220, 36);
                        small.setMinSize(220, 36);
                        small.setMaxSize(220, 36);
                        dropArea.getChildren().add(small);

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
            }
            ev.setDropCompleted(success);
            ev.consume();
        });

        javafx.event.EventHandler<javafx.scene.input.MouseEvent> summaryClickHandler = ev -> {
            @SuppressWarnings("unchecked")
            List<CardData> list = (List<CardData>) dropArea.getUserData(); // Данные всегда в dropArea
            if (list.isEmpty()) {
                showInfo("На существе нет карт влияния");
            } else {
                StringBuilder sb = new StringBuilder("Примененные карты:\n\n");
                for (int i = 0; i < list.size(); i++) {
                    CardData cd = list.get(i);
                    sb.append(i + 1).append(". ").append(cd.name)
                            .append(" (Стоимость: ").append(cd.cost).append(")\n");
                }
                showInfo(sb.toString());
            }
        };

        // 2. Применяем его и к карте, и к зоне под ней
        dropArea.setOnMouseClicked(summaryClickHandler);
        card.setOnMouseClicked(summaryClickHandler);

        container.getChildren().addAll(card, dropArea);
        return container;
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    // --- НОВЫЙ МЕТОД: Обновляет UI карточки существа ---
    private void refreshCreaturePane(VBox creaturePane, CreatureState state) {
        VBox cardNode = (VBox) creaturePane.getChildren().get(0);
        cardNode.getChildren().clear(); // Очищаем старые Text ноды

        // Создаем заново UI, как в createCardNode
        Text name = new Text(state.name);
        name.getStyleClass().add("card-title");

        String stats = String.format("HP: %d/%d | ATK: %d | DEF: %d | RP: %d",
                state.currentHealth, state.baseHealth,
                state.currentAttack, state.currentDefense,
                state.currentRatePoints); // <-- Добавлено
        Text statsText = new Text(stats);
        statsText.getStyleClass().add("card-stats");

        Text desc = new Text(state.baseCard.text);
        desc.getStyleClass().add("card-text");
        desc.wrappingWidthProperty().bind(cardNode.widthProperty().subtract(12));

        cardNode.getChildren().addAll(name, statsText, desc);
    }
    // --- КОНЕЦ НОВОГО МЕТОДА ---

    // --- ИЗМЕНЕНИЕ: Отображает статы, если это карта существа ---
    private VBox createCardNode(CardData data, boolean large, CreatureState state) {
        VBox box = new VBox();
        box.getStyleClass().add("card");
        box.setPadding(new Insets(6));
        box.setSpacing(4);

        Text name = new Text(data.name);
        name.getStyleClass().add("card-title");

        Text desc = new Text(data.text);
        desc.getStyleClass().add("card-text");

        desc.wrappingWidthProperty().bind(box.widthProperty().subtract(12));

        if (large && state != null) {
            // --- ЭТО КАРТА СУЩЕСТВА ---
            // --- ИЗМЕНЕНИЕ: (ЗАПРОС 2) Добавлено RP в статы ---
            String stats = String.format("HP: %d/%d | ATK: %d | DEF: %d | RP: %d",
                    state.currentHealth, state.baseHealth,
                    state.currentAttack, state.currentDefense,
                    state.currentRatePoints); // <-- Добавлено
            Text statsText = new Text(stats);
            statsText.getStyleClass().add("card-stats");

            box.getChildren().addAll(name, statsText, desc);
            // --- ИЗМЕНЕНИЕ: (ЗАПРОС 3) Удален старый обработчик клика ---
            // box.setOnMouseClicked(ev -> showInfo(data.name + "\n\n" + stats + "\n" + data.text));
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        } else {
            Text costText = null;
            if (data.cost > 0) {
                costText = new Text("Стоимость: " + data.cost);
                costText.getStyleClass().add("card-cost");
            }

            // --- НОВЫЙ КОД: (ЗАПРОС 1) Отображение RP карты ---
            Text rpText = null;
            int rpChange = data.getRatePointsChange(); // Считаем RP
            if (rpChange != 0) {
                rpText = new Text("RP: " + (rpChange > 0 ? "+" : "") + rpChange);
                // Добавляем класс стиля
                rpText.getStyleClass().add(rpChange > 0 ? "card-rp-pos" : "card-rp-neg");
            }

            // Добавляем в UI (пропуская null)
            box.getChildren().add(name);
            if (costText != null) box.getChildren().add(costText);
            if (rpText != null) box.getChildren().add(rpText);
            box.getChildren().add(desc);
            // --- КОНЕЦ НОВОГО КОДА ---

            if (!large) { // Перетаскивание
                box.setOnDragDetected(ev -> {
                    Dragboard db = box.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(data.id);
                    db.setContent(content);
                    ev.consume();
                });
            }
        }

        box.setUserData(data);
        return box;
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

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

    // --- ИЗМЕНЕНИЕ: CSS для статов ---
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
            .card-rp-pos {
              -fx-font-size: 11px;
              -fx-fill: #008800; /* Зеленый */
              -fx-font-weight: bold;
            }
            .card-rp-neg {
              -fx-font-size: 11px;
              -fx-fill: #cc0000; /* Красный */
              -fx-font-weight: bold;
            }
            /* --- КОНЕЦ НОВЫХ СТИЛЕЙ --- */
            .card-stats {
              -fx-font-size: 13px;
              -fx-fill: #333333;
              -fx-font-weight: bold;
              -fx-padding: 2 0 0 0;
            }
            .card-text {
              -fx-font-size: 12px;
              -fx-fill: #333333;
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
            }
            .drop-area-hover {
              -fx-border-color: #8aa2ff;
              -fx-background-color: linear-gradient(#f5f8ff, #ffffff);
            }
            """;
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    // --- НОВЫЕ/ОБНОВЛЕННЫЕ POJO (внутренние классы) ---

    // Этот класс теперь универсален, Jackson сам разберется,
    // какие поля null (e.g. effects у существ, attack у карт)
    public static class CardData {
        public String id;
        public String name;
        public String text;
        public String type;
        public int cost;

        // Статы (для существ)
        public int health;
        public int attack;
        public int defense;
        public int ratePoints; // <-- НОВОЕ ПОЛЕ: Начальный RatePoints для существ

        // Эффекты (для карт влияния)
        public List<Effect> effects;

        // Пустой конструктор для Jackson
        public CardData() {
            this.effects = new ArrayList<>();
        }

        // --- НОВЫЙ КОД: (ЗАПРОС 1) Вспомогательный метод для UI ---
        public int getRatePointsChange() {
            if (effects == null) {
                return 0;
            }
            int totalRp = 0;
            for (Effect e : effects) {
                if ("/ratePoints".equals(e.path)) {
                    totalRp += e.value;
                }
            }
            return totalRp;
        }
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