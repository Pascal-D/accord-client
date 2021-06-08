package de.uniks.stp.controller.snake;

import de.uniks.stp.builder.ModelBuilder;
import de.uniks.stp.controller.snake.model.Food;
import de.uniks.stp.controller.snake.model.Snake;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import static de.uniks.stp.controller.snake.Constants.*;

public class SnakeGameController {

    private Parent view;
    private ModelBuilder builder;
    private Scene scene;
    private Label scoreLabel;
    private Label highScoreLabel;
    private Canvas gameField;
    private Direction currentDirection;
    private Snake snake;

    private enum Direction {
        RIGHT,
        LEFT,
        UP,
        DOWN
    }

    public SnakeGameController(Scene scene, Parent view, ModelBuilder builder) {
        this.scene = scene;
        this.view = view;
        this.builder = builder;
    }

    public void init() throws InterruptedException {
        scoreLabel = (Label) view.lookup("#label_score");
        highScoreLabel = (Label) view.lookup("#label_highscore");
        gameField = (Canvas) view.lookup("#gameField");
        GraphicsContext gc = gameField.getGraphicsContext2D();
        currentDirection = Direction.RIGHT; // TODO


        scene.setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.RIGHT || key.getCode() == KeyCode.D) {
                System.out.println("RIGHT");
                this.currentDirection = Direction.RIGHT;
            } else if (key.getCode() == KeyCode.LEFT || key.getCode() == KeyCode.A) {
                System.out.println("LEFT");
                this.currentDirection = Direction.LEFT;
            } else if (key.getCode() == KeyCode.UP || key.getCode() == KeyCode.W) {
                System.out.println("UP");
                this.currentDirection = Direction.UP;
            } else if (key.getCode() == KeyCode.DOWN || key.getCode() == KeyCode.S) {
                System.out.println("DOWN");
                this.currentDirection = Direction.DOWN;
            }
        });

        drawFieldMap(gc);
        spawnFood(gc);
        spawnSnake(gc);
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(500), run -> main(gc)));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void main(GraphicsContext gc) {
        System.out.println("RUN");
        drawFieldMap(gc);
        moveSnake(gc);
    }


    private void spawnSnake(GraphicsContext gc) {
        snake = new Snake();
        gc.setFill(Color.web("000000"));
        gc.fillRect(snake.getPosX(), snake.getPosY(), FIELD_SIZE, FIELD_SIZE);
    }

    private void moveSnake(GraphicsContext gc) {
        switch (currentDirection) {
            case UP:
                snake.addPosY(-FIELD_SIZE);
                break;

            case DOWN:
                snake.addPosY(FIELD_SIZE);
                break;

            case LEFT:
                snake.addPosX(-FIELD_SIZE);
                break;

            case RIGHT:
                snake.addPosX(FIELD_SIZE);
                break;
        }
        gc.setFill(Color.web("000000"));
        gc.fillRect(snake.getPosX(), snake.getPosY(), FIELD_SIZE, FIELD_SIZE);
    }

    private void spawnFood(GraphicsContext gc) {
        Food food = new Food();
        gc.setFill(Color.web("FFFFFF"));
        gc.fillRect(food.getPosX() * FIELD_SIZE, food.getPosY() * FIELD_SIZE, FIELD_SIZE, FIELD_SIZE);
    }

    private void drawFieldMap(GraphicsContext gc) {
        for (int row = 0; row < ROW; row++) {
            for (int column = 0; column < COLUMN; column++) {
                if (row % 2 == 0) {
                    if (column % 2 == 0) {
                        gc.setFill(Color.web("8FDD37"));
                    } else {
                        gc.setFill(Color.web("6DCC01"));
                    }
                } else {
                    if (column % 2 == 1) {
                        gc.setFill(Color.web("8FDD37"));
                    } else {
                        gc.setFill(Color.web("6DCC01"));
                    }
                }
                gc.fillRect(column * FIELD_SIZE, row * FIELD_SIZE, FIELD_SIZE, FIELD_SIZE);
            }
        }
    }

    public void stop() {

    }
}
