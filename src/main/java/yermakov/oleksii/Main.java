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

public class Main extends Application {

    private static final String CREATURES_FILE = "creatures.json";
    private static final String INFLUENCE_FILE = "influenceCards.json";

    private static final int STARTING_HAND_SIZE = 5;
    private static final int MAX_HAND_SIZE = 5;
    private static final int MAX_TURN_POINTS = 4;

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
        currentPlayer = (currentPlayer == Player.PLAYER_1) ? Player.PLAYER_2 : Player.PLAYER_1;
        currentTurnPointsUsed = 0;

        List<CardData> currentHand = (currentPlayer == Player.PLAYER_1) ? player1Hand : player2Hand;
        int cardsToDraw = MAX_HAND_SIZE - currentHand.size();

        if (cardsToDraw > 0) {
            for (int i = 0; i < cardsToDraw; i++) {
                drawCardToHandData(currentPlayer);
            }
        }

        updateTurnPointsText();
        updateHandDisplay();

        String playerLabel = (currentPlayer == Player.PLAYER_1) ? "Игрок 1" : "Игрок 2";
        showInfo("Ход переходит к: " + playerLabel);
    }

    private void updateTurnPointsText() {
        String playerLabel = (currentPlayer == Player.PLAYER_1) ? "Игрок 1" : "Игрок 2";
        turnPointsText.setText(String.format("Ход: %s | Очки: %d / %d", playerLabel, currentTurnPointsUsed, MAX_TURN_POINTS));
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
        // --- НОВЫЙ КОД: Сохраняем State в UI ---
        container.setUserData(state);
        // --- КОНЕЦ НОВОГО КОДА ---

        // Передаем state для отображения статов
        VBox card = createCardNode(state.baseCard, true, state);
        card.setPrefSize(260, 180); // Увеличил высоту
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

        // (Клик по drop-зоне остался без изменений)
        dropArea.setOnMouseClicked(ev -> {
            // ... (старый код) ...
        });

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

        // --- НОВЫЙ UI: Отображение статов ---
        String stats = String.format("HP: %d/%d | ATK: %d | DEF: %d",
                state.currentHealth, state.baseHealth,
                state.currentAttack, state.currentDefense);
        Text statsText = new Text(stats);
        statsText.getStyleClass().add("card-stats");
        // --- КОНЕЦ НОВОГО UI ---

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
            String stats = String.format("HP: %d/%d | ATK: %d | DEF: %d",
                    state.currentHealth, state.baseHealth,
                    state.currentAttack, state.currentDefense);
            Text statsText = new Text(stats);
            statsText.getStyleClass().add("card-stats");

            box.getChildren().addAll(name, statsText, desc);
            box.setOnMouseClicked(ev -> showInfo(data.name + "\n\n" + stats + "\n" + data.text));

        } else {
            // --- ЭТО КАРТА ВЛИЯНИЯ ---
            if (data.cost > 0) {
                Text costText = new Text("Стоимость: " + data.cost);
                costText.getStyleClass().add("card-cost");
                box.getChildren().addAll(name, costText, desc);
            } else {
                box.getChildren().addAll(name, desc);
            }

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

        // Эффекты (для карт влияния)
        public List<Effect> effects;

        // Пустой конструктор для Jackson
        public CardData() {
            this.effects = new ArrayList<>();
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

        public CreatureState(CardData baseCard) {
            this.baseCard = baseCard;
            this.name = baseCard.name;
            this.baseHealth = baseCard.health;
            this.currentHealth = baseCard.health;
            this.baseAttack = baseCard.attack;
            this.currentAttack = baseCard.attack;
            this.baseDefense = baseCard.defense;
            this.currentDefense = baseCard.defense;
        }
    }
    // --- КОНЕЦ POJO ---
}