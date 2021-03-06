/*******************************************************************************
 * Copyright 2019 Viridian Software Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.mini2dx.breakout;

import org.mini2Dx.core.Mdx;
import org.mini2Dx.core.assets.AssetManager;
import org.mini2Dx.core.files.FallbackFileHandleResolver;
import org.mini2Dx.core.files.FileHandleResolver;
import org.mini2Dx.core.files.InternalFileHandleResolver;
import org.mini2Dx.core.font.FontGlyphLayout;
import org.mini2Dx.core.game.GameContainer;
import org.mini2Dx.core.Graphics;
import org.mini2Dx.core.graphics.viewport.FitViewport;
import org.mini2Dx.core.graphics.viewport.Viewport;
import org.mini2Dx.core.screen.BasicGameScreen;
import org.mini2Dx.core.screen.ScreenManager;
import org.mini2Dx.core.screen.transition.NullTransition;
import org.mini2Dx.core.exception.SerializationException;
import org.mini2Dx.ui.UiContainer;
import org.mini2Dx.ui.UiThemeLoader;
import org.mini2Dx.ui.element.Button;
import org.mini2Dx.ui.element.Container;
import org.mini2Dx.ui.element.TextBox;
import org.mini2Dx.ui.event.ActionEvent;
import org.mini2Dx.ui.listener.ActionListener;
import org.mini2Dx.ui.style.UiTheme;

import java.io.IOException;
import java.util.Objects;

import java.util.Random;

public class BreakoutGame extends BasicGameScreen {
    public static final int ID = 2;

    public static final int DEBUG_INPUT = 1, DEBUG_COLLISION_DRAW_COLLISION_BOXES = 2, DEBUG_COLLISION_PRINT = 4, DEBUG_BALL_SPEEDUP = 8;
    public static final int DEBUG_MODE = 0;

    public static final int gridSizeX = 10, gridSizeY = 6;
    public static final float gameWidth = gridSizeX * Brick.width, gameHeight = gridSizeY * Brick.height * 3;

    private static final String GAME_OVER_STRING = "GAME OVER!";
    private static final String UI_ASK_NAME_LAYOUT_XML = "ui/askname_ui.xml";

    private static Brick.Color brickColors[] = {Brick.Color.RED, Brick.Color.PURPLE, Brick.Color.BLUE, Brick.Color.GREEN, Brick.Color.YELLOW, Brick.Color.GREY, Brick.Color.VIR };

    private GameState gameState;

    private Viewport viewport;
    private Background background;
    private Paddle paddle;
    private Ball ball;
    private final Brick[][] bricks = new Brick[gridSizeX][gridSizeY];
    private ScoreCounter score;
    private LivesHandler lives;
    private UiContainer uiContainer;

    @Override
    public void initialise(GameContainer gameContainer) {
        //noinspection ConstantConditions
        assert gridSizeY > brickColors.length; //there should be at least a color for every row of bricks

        viewport = new FitViewport(gameWidth, gameHeight);
        background = new Background();
        initialiseGame();
        //Create fallback file resolver so we can use the default mini2Dx-ui theme
        FileHandleResolver fileHandleResolver = new FallbackFileHandleResolver(new InternalFileHandleResolver());

        //Create asset manager for loading resources
        AssetManager assetManager = new AssetManager(fileHandleResolver);

        //Add mini2Dx-ui theme loader
        assetManager.setAssetLoader(UiTheme.class, new UiThemeLoader(fileHandleResolver));

        //Load default theme
        assetManager.load(UiTheme.DEFAULT_THEME_FILENAME, UiTheme.class);

        uiContainer = new UiContainer(gameContainer, assetManager);

    }

    private final FontGlyphLayout glyphLayout = Mdx.fonts.defaultFont().newGlyphLayout();

    public void initialiseGame() {
        gameState = GameState.RUNNING;
        paddle = new Paddle();
        ball = new Ball();
        initialiseBricks();
        CollisionHandler.getInstance().setPaddle(paddle);
        CollisionHandler.getInstance().setBall(ball);
        score = new ScoreCounter();
        lives = new LivesHandler();
    }

    @Override
    public void update(GameContainer gameContainer, ScreenManager screenManager, float delta) {

        InputHandler.update();
        switch (gameState) {
            case RUNNING:
                if (InputHandler.getInstance().isQuitPressed()) {
                    gameState = GameState.EXITING;
                } else if (InputHandler.getInstance().isRestartPressed()) {
                    gameState = GameState.RESTARTED;
                }
                if (CollisionHandler.getInstance().getAliveBricks() == 0) {
                    initialiseBricks();
                    ball.returnToDefaultPosition();
                }
                if (lives.isDead()) {
                    gameState = GameState.ENDING_GAME;
                } else {
                    paddle.update(delta);
                    CollisionHandler.update();
                    ball.update(delta);
                    for (int i = 0; i < gridSizeX; i++) {
                        for (int j = 0; j < gridSizeY; j++) {
                            bricks[i][j].update();
                        }
                    }
                    // TODO: Adding if ball touching a special brick
//                    if (CollisionHandler.getInstance().isBallTouchingAny)
//                        lives.increase();
                        //if(...)
                        //score.superupdate...
                    //else
                    score.update();
                    if (ball.getCollisionBox().getY() > gameHeight) {
                        lives.decrease();
                        if (!lives.isDead())
                            ball.returnToDefaultPosition();
                    }
                }
                break;
            case ENDING_GAME:
                if (LeaderboardHandler.getInstance().willBeInLeaderboard(ScoreCounter.getInstance().getScore())) {
                    gameState = GameState.ASK_NAME;
                } else {
                    gameState = GameState.WAITING_FOR_ANY_KEY;
                }
                break;
            case ASK_NAME:
                Mdx.input.setInputProcessor(uiContainer);

                Container temp_askNameContainer = null;
                try {
                    temp_askNameContainer = Mdx.xml.fromXml(Mdx.files.internal(UI_ASK_NAME_LAYOUT_XML).reader(), Container.class);
                } catch (SerializationException | IOException e) {
                    e.printStackTrace();
                }
                final Container askNameContainer = Objects.requireNonNull(temp_askNameContainer);
                askNameContainer.setXY((BreakoutGame.gameWidth - askNameContainer.getWidth()) / 2, (BreakoutGame.gameHeight - askNameContainer.getHeight()) / 2);
                final Button confirmButton = (Button) askNameContainer.getElementById("confirmButton");
                final TextBox playerNameText = (TextBox) askNameContainer.getElementById("playerNameText");
                confirmButton.addActionListener(new ActionListener() {
                    @Override
                    public void onActionBegin(ActionEvent event) {

                    }

                    @Override
                    public void onActionEnd(ActionEvent event) {
                        LeaderboardHandler.getInstance().addScore(new Score(playerNameText.getValue(), ScoreCounter.getInstance().getScore()));
                        confirmButton.setEnabled(false);
                        gameState = GameState.WAITING_FOR_ANY_KEY;
                    }
                });
                uiContainer.add(askNameContainer);
                gameState = GameState.WAITING_FOR_NAME;
                break;
            case WAITING_FOR_NAME:
                uiContainer.update(delta);
                break;
            case WAITING_FOR_ANY_KEY:
                if (InputHandler.getInstance().isAnyKeyPressed()) {
                    gameState = GameState.EXITING;
                }
                break;
            case EXITING:
                screenManager.enterGameScreen(MainMenu.ID, new NullTransition(),
                        new NullTransition());
                gameState = GameState.RESTARTED;
                break;
            case RESTARTED:
                initialiseGame();
                break;
        }
    }

    private void initialiseBricks() {

        for (int j = 0; j < gridSizeY; j++) {
            for (int i = 0; i < gridSizeX; i++) {
                bricks[i][j] = new Brick(brickColors[j], i * Brick.width, j * Brick.height);
//                Random rand= new Random();
                //random position
//                int rand_x= rand.nextInt(9);
//                int rand_y=rand.nextInt(5);
//                System.out.println(rand_x );
//                System.out.println(rand_y );

//                if(i == rand_x && j == rand_y) {
//                int rando = rand.nextInt(17);
//                if(rando < 1){
//                     Item item1 = new Item(brickColors[6],i * Brick.width, j * Brick.height);
//                }
//                else{
//                    bricks[i][j] = new Brick(brickColors[j], i * Brick.width, j * Brick.height);
//                }

            }
            CollisionHandler.getInstance().setBricks(bricks);
        }
        bricks[1][5] = new Brick(brickColors[6], 1 * Brick.width, 5 * Brick.height, true);
        bricks[3][5] = new Brick(brickColors[6], 3 * Brick.width, 5 * Brick.height, true);
        bricks[5][5] = new Brick(brickColors[6], 5 * Brick.width, 5 * Brick.height, true);
    }

    @Override
    public void render(GameContainer gameContainer, Graphics g) {
        viewport.apply(g);
        background.render(g);
        if (gameState == GameState.WAITING_FOR_NAME) {
            uiContainer.render(g);
        } else {
            if (lives.isDead()) {
                drawCenterAlignedString(g, GAME_OVER_STRING);
            } else {
                paddle.render(g);
                ball.render(g);
                for (int i = 0; i < gridSizeX; i++)
                    for (int j = 0; j < gridSizeY; j++)
                        bricks[i][j].render(g);
                lives.render(g);
            }
        }
        score.render(g);
    }

    protected enum GameState {
        RUNNING,
        ENDING_GAME,
        ASK_NAME,
        WAITING_FOR_NAME,
        WAITING_FOR_ANY_KEY,
        EXITING,
        RESTARTED
    }

    @Override
    public int getId() {
        return ID;
    }

    public void drawCenterAlignedString(Graphics g, String s) {
        glyphLayout.setText(s);
        int renderX = Math.round((gameWidth / 2f) - (glyphLayout.getWidth() / 2f));
        int renderY = Math.round((gameHeight / 2f) - (glyphLayout.getHeight() / 2f));
        g.drawString(s, renderX, renderY);
    }
}