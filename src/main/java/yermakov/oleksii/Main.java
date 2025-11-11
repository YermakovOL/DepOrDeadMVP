package yermakov.oleksii;

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

    // --- ИЗМЕНЕНИЕ: Пути к файлам ресурсов ---
    private static final String CREATURES_FILE = "creatures.json";
    private static final String INFLUENCE_FILE = "influenceCards.json";
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    private final Map<String, CardData> allCards = new HashMap<>();
    private final List<CardData> creatures = new ArrayList<>();
    private final List<CardData> influenceDeck = new ArrayList<>();

    // --- НОВЫЕ ПОЛЯ: КОНСТАНТЫ ИГРЫ ---
    private static final int STARTING_HAND_SIZE = 5;
    private static final int MAX_HAND_SIZE = 5;
    private static final int MAX_TURN_POINTS = 4;
    // --- КОНЕЦ НОВЫХ ПОЛЕЙ ---

    // --- НОВЫЕ ПОЛЯ: СОСТОЯНИЕ ИГРЫ (STATE) ---
    private enum Player { PLAYER_1, PLAYER_2 }
    private Player currentPlayer;
    private final List<CardData> player1Hand = new ArrayList<>();
    private final List<CardData> player2Hand = new ArrayList<>();
    private int currentTurnPointsUsed = 0;
    // --- КОНЕЦ НОВЫХ ПОЛЕЙ ---

    // UI references
    private HBox handBox;
    private Text turnPointsText;


    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        // --- ИЗМЕНЕНИЕ: Загрузка из ресурсов ---
        try {
            loadDataFromResources();
        } catch (Exception e) {
            showError("Критическая ошибка",
                    "Не удалось загрузить данные из ресурсов: " + e.getMessage() +
                            "\n\nУбедитесь, что JSON-файлы находятся в папке src/main/resources.");
            return;
        }
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        // --- НОВЫЙ КОД: Запуск игровой логики ---
        startGame();
        // --- КОНЕЦ НОВОГО КОДА ---

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));

        HBox centerCreatures = new HBox(40);
        centerCreatures.setAlignment(Pos.CENTER);
        centerCreatures.setPadding(new Insets(10));
        centerCreatures.getChildren().addAll(createCreaturePane(0), createCreaturePane(1));
        root.setTop(centerCreatures);

        VBox bottom = new VBox(10);
        bottom.setPadding(new Insets(8));
        bottom.setAlignment(Pos.CENTER);

        // --- ИЗМЕНЕНИЕ: Удалена кнопка "Взять карту" ---
        Button endTurnBtn = new Button("Завершить ход");
        endTurnBtn.setMinWidth(180);
        endTurnBtn.setOnAction(e -> endTurn());

        HBox buttonBar = new HBox(10, endTurnBtn); // Только кнопка завершения хода
        buttonBar.setAlignment(Pos.CENTER);
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        turnPointsText = new Text();
        turnPointsText.getStyleClass().add("card-title");
        updateTurnPointsText(); // Установить начальное значение

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

        // --- НОВЫЙ КОД: Отображаем руку первого игрока ---
        updateHandDisplay();
        // --- КОНЕЦ НОВОГО КОДА ---

        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(makeCss());
        stage.setScene(scene);
        stage.setTitle("Dep or Dead — MVP (JavaFX)");
        stage.show();
    }

    // --- НОВЫЙ МЕТОД: Начальная настройка игры ---
    private void startGame() {
        currentPlayer = Player.PLAYER_1;
        currentTurnPointsUsed = 0;
        player1Hand.clear();
        player2Hand.clear();

        // Раздаем стартовые руки
        for (int i = 0; i < STARTING_HAND_SIZE; i++) {
            drawCardToHandData(Player.PLAYER_1);
            drawCardToHandData(Player.PLAYER_2);
        }
    }

    // --- НОВЫЙ МЕТОД: Логика завершения хода ---
    private void endTurn() {
        // 1. Меняем игрока
        currentPlayer = (currentPlayer == Player.PLAYER_1) ? Player.PLAYER_2 : Player.PLAYER_1;

        // 2. Сбрасываем очки
        currentTurnPointsUsed = 0;

        // 3. Автоматический добор карт
        List<CardData> currentHand = (currentPlayer == Player.PLAYER_1) ? player1Hand : player2Hand;
        int cardsToDraw = MAX_HAND_SIZE - currentHand.size();

        if (cardsToDraw > 0) {
            for (int i = 0; i < cardsToDraw; i++) {
                drawCardToHandData(currentPlayer);
            }
        }

        // 4. Обновляем UI
        updateTurnPointsText();
        updateHandDisplay();

        // 5. Уведомляем
        String playerLabel = (currentPlayer == Player.PLAYER_1) ? "Игрок 1" : "Игрок 2";
        showInfo("Ход переходит к: " + playerLabel);
    }

    // --- НОВЫЙ МЕТОД: Обновление текста очков (теперь и игрока) ---
    private void updateTurnPointsText() {
        String playerLabel = (currentPlayer == Player.PLAYER_1) ? "Игрок 1" : "Игрок 2";
        turnPointsText.setText(String.format("Ход: %s | Очки: %d / %d", playerLabel, currentTurnPointsUsed, MAX_TURN_POINTS));
    }

    // --- НОВЫЙ МЕТОД: (UI) Обновляет handBox на основе данных ---
    private void updateHandDisplay() {
        handBox.getChildren().clear();

        List<CardData> currentHand = (currentPlayer == Player.PLAYER_1) ? player1Hand : player2Hand;

        for (CardData card : currentHand) {
            VBox cardNode = createCardNode(card, false);
            cardNode.getStyleClass().add("hand-card");

            // Фиксация размера
            cardNode.setPrefSize(220, 60);
            cardNode.setMinSize(220, 60);
            cardNode.setMaxSize(220, 60);

            // Клик для просмотра
            cardNode.setOnMouseClicked(ev -> showInfo(card.name + " [" + card.cost + "]\n\n" + card.text));

            handBox.getChildren().add(cardNode);
        }
    }

    // --- НОВЫЙ МЕТОД: (ДАННЫЕ) Берет карту из колоды и кладет в руку игрока ---
    // --- ИСПРАВЛЕННЫЙ МЕТОД: (ДАННЫЕ) Берет карту и кладет в руку ---
    private void drawCardToHandData(Player player) {
        if (influenceDeck.isEmpty()) {
            // Этого не должно случиться, если мы не удаляем карты,
            // но проверка на всякий случай.
            System.err.println("Ошибка: Колода влияния пуста!");
            return;
        }

        // Выбираем случайный индекс из *мастер-листа* карт
        int idx = ThreadLocalRandom.current().nextInt(influenceDeck.size());

        // --- ИСПРАВЛЕНИЕ: ---
        // Мы .get() (получаем) карту, а не .remove() (удаляем).
        // Это создает "бесконечную" колоду для MVP.
        CardData card = influenceDeck.get(idx);
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

        if (player == Player.PLAYER_1) {
            player1Hand.add(card);
        } else {
            player2Hand.add(card);
        }
    }

    // --- ИЗМЕНЕНИЕ: Логика Drop (добавлен вызов updateHandDisplay) ---
    private VBox createCreaturePane(int index) {
        CardData creature = creatures.get(index % creatures.size());

        VBox container = new VBox(8);
        container.setAlignment(Pos.TOP_CENTER);

        VBox card = createCardNode(creature, true);
        card.setPrefSize(260, 120);
        card.setMinSize(260, 120);
        card.setMaxSize(260, 120);

        VBox dropArea = new VBox(4);
        dropArea.setPrefSize(260, 110);
        dropArea.setMinSize(260, 110);
        dropArea.setMaxSize(260, 110);
        dropArea.setAlignment(Pos.TOP_CENTER);
        dropArea.getStyleClass().add("drop-area");
        dropArea.setUserData(new ArrayList<CardData>());

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

        container.setOnDragDropped(ev -> {
            Dragboard db = ev.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String cardId = db.getString();
                CardData cd = allCards.get(cardId);

                if (cd != null && !"creature".equals(cd.type)) {
                    int cost = cd.cost;
                    if (currentTurnPointsUsed + cost <= MAX_TURN_POINTS) {

                        currentTurnPointsUsed += cost;
                        updateTurnPointsText();

                        // --- ИЗМЕНЕНИЕ: Удаляем из ДАННЫХ, а не UI ---
                        removeCardFromHandById(cardId);
                        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

                        VBox small = createCardNode(cd, false);
                        small.setPrefSize(220, 36);
                        small.setMinSize(220, 36);
                        small.setMaxSize(220, 36);
                        dropArea.getChildren().add(small);

                        @SuppressWarnings("unchecked")
                        List<CardData> list = (List<CardData>) dropArea.getUserData();
                        list.add(cd);

                        success = true;

                        // --- НОВЫЙ КОД: Немедленно обновляем руку ---
                        updateHandDisplay();
                        // --- КОНЕЦ НОВОГО КОДА ---

                    } else {
                        showInfo(String.format(
                                "Недостаточно очков!\n\nСтоимость карты: %d\nУ вас есть: %d",
                                cost,
                                (MAX_TURN_POINTS - currentTurnPointsUsed)
                        ));
                    }
                }
            }
            ev.setDropCompleted(success);
            ev.consume();
        });

        dropArea.setOnMouseClicked(ev -> {
            @SuppressWarnings("unchecked")
            List<CardData> list = (List<CardData>) dropArea.getUserData();
            if (list.isEmpty()) {
                showInfo("В контейнере нет карт");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    CardData cd = list.get(i);
                    sb.append(i + 1).append(". ")
                            .append(cd.name)
                            .append(" (Стоимость: ").append(cd.cost).append(")")
                            .append(" — ").append(cd.text).append("\n");
                }
                showInfo(sb.toString());
            }
        });

        container.getChildren().addAll(card, dropArea);
        return container;
    }


    private VBox createCardNode(CardData data, boolean large) {
        VBox box = new VBox();
        box.getStyleClass().add("card");
        box.setPadding(new Insets(6));
        box.setSpacing(4);

        Text name = new Text(data.name);
        name.getStyleClass().add("card-title");

        Text desc = new Text(data.text);
        desc.getStyleClass().add("card-text");

        if (large) {
            desc.wrappingWidthProperty().bind(box.widthProperty().subtract(12));
        } else {
            desc.wrappingWidthProperty().bind(box.widthProperty().subtract(12));
        }

        if (data.cost > 0) {
            Text costText = new Text("Стоимость: " + data.cost);
            costText.getStyleClass().add("card-cost");
            box.getChildren().addAll(name, costText, desc);
        } else {
            box.getChildren().addAll(name, desc);
        }

        box.setUserData(data);

        if (!large) {
            box.setOnDragDetected(ev -> {
                Dragboard db = box.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(data.id);
                db.setContent(content);
                ev.consume();
            });
        } else {
            box.setOnMouseClicked(ev -> showInfo(data.name + "\n\n" + data.text));
        }

        return box;
    }

    // --- ИЗМЕНЕНИЕ: (ДАННЫЕ) Удаляет карту из листа текущего игрока ---
    private void removeCardFromHandById(String cardId) {
        List<CardData> currentHand = (currentPlayer == Player.PLAYER_1) ? player1Hand : player2Hand;

        // Используем лямбду для удаления по ID
        currentHand.removeIf(card -> card.id.equals(cardId));
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

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

    // --- НОВЫЙ МЕТОД: Читает файлы как ресурсы ---
    private void loadDataFromResources() throws IOException {
        String creaturesJson = readResourceFile(CREATURES_FILE);
        String influenceJson = readResourceFile(INFLUENCE_FILE);

        parseArrayIntoList(creaturesJson, "creature");
        parseArrayIntoList(influenceJson, "influence");

        for (CardData c : creatures) allCards.put(c.id, c);
        for (CardData c : influenceDeck) allCards.put(c.id, c);
    }

    private String readResourceFile(String filename) throws IOException {
        try (InputStream is = Main.class.getResourceAsStream("/" + filename)) {
            if (is == null) {
                throw new IOException("Файл ресурса не найден: " + filename);
            }
            try (Scanner scanner = new Scanner(is, "UTF-8")) {
                return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            }
        }
    }

    private void parseArrayIntoList(String arrayContent, String type) {
        String content = arrayContent.trim();
        if (content.startsWith("[")) {
            content = content.substring(1);
        }
        if (content.endsWith("]")) {
            content = content.substring(0, content.length() - 1);
        }
        content = content.trim();

        if (content.isEmpty()) {
            return;
        }

        String[] objs = content.split("\\},\\s*\\{");
        for (String raw : objs) {
            String s = raw.trim();
            if (!s.startsWith("{")) s = "{" + s;
            if (!s.endsWith("}")) s = s + "}";
            Map<String, String> map = parseObject(s);
            if (map.containsKey("id")) {

                int cost = 0;
                if ("influence".equals(type)) {
                    cost = ThreadLocalRandom.current().nextInt(1, 5);
                }

                CardData cd = new CardData(
                        map.get("id"),
                        map.getOrDefault("name", "—"),
                        map.getOrDefault("text", "—"),
                        type,
                        cost
                );

                if ("creature".equals(type)) creatures.add(cd);
                else influenceDeck.add(cd);
            }
        }
        if ("creature".equals(type) && creatures.size() < 2) {
            while (creatures.size() < 2) {
                creatures.add(new CardData("c-filler-" + creatures.size(), "Заглушка", "Автозаполнение", "creature", 0));
            }
        }
    }

    private Map<String, String> parseObject(String obj) {
        Map<String, String> map = new HashMap<>();
        int i = 0;
        while (i < obj.length()) {
            int q1 = obj.indexOf('"', i);
            if (q1 < 0) break;
            int q2 = obj.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            String key = obj.substring(q1 + 1, q2);
            int colon = obj.indexOf(':', q2 + 1);
            if (colon < 0) break;
            int valStart = obj.indexOf('"', colon + 1);
            if (valStart < 0) break;
            int valEnd = obj.indexOf('"', valStart + 1);
            if (valEnd < 0) break;
            String val = obj.substring(valStart + 1, valEnd);
            map.put(key.trim(), val.trim());
            i = valEnd + 1;
        }
        return map;
    }

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

    private static class CardData {
        final String id;
        final String name;
        final String text;
        final String type;
        final int cost;

        CardData(String id, String name, String text, String type, int cost) {
            this.id = id;
            this.name = name;
            this.text = text;
            this.type = type;
            this.cost = cost;
        }
    }
}