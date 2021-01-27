import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import static java.awt.Image.SCALE_SMOOTH;


public class CE203_1903165_Ass2 extends JFrame {

    // Starting point of the program
    public static void main(String[] args) {
        new CE203_1903165_Ass2();
    }

    /* Constructor sets characteristics of the JFrame
       and creates the game panel.  */
    CE203_1903165_Ass2() {
        // Title is stored in the Constants class
        setTitle(Constants.TITLE);
        setResizable(false);
        setVisible(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        GamePanel gp = new GamePanel();
        add(gp);

        gp.setFocusable(true);
        gp.requestFocusInWindow();

        pack();
    }
}

class Constants {
    /*  Constants class stores key values used by the game.  */
    public static final String TITLE = "covid_game";
    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;
    public static final int UPDATE_FREQUENCY_MS = 16; // (roughly 60 frames per second)
    public static final int GRASS_Y_POS = 553; // Vertical position of the land the player walks on
}


/*  In this file (GamePanel.java before merging) the state of the game gets updated and repainted.
    Thanks to polymorphism and the use of abstract "State" class,
    updating and repainting is done using 2 same method calls regardless
    of the currently active state, which could be for example:
        - IntroState    (intro image that lasts 2 seconds and shows game logo/graphic)
        - PlayState     (it ends when player HP goes down to 0)
        - GameOverState (top 5 scores are shown in this state, it ends when user presses any button)

    Transition between states occurs whenever "State.change(some_state)" static method is called.  */

class GamePanel extends JPanel {
    GamePanel() {
        setPreferredSize(new Dimension(Constants.WIDTH, Constants.HEIGHT));

        /* Each state implements its own key and mouse listener.
           Whenever a state is changed, the panel calls:
                - panel.removeMouseListener(old_state);
                - panel.removeKeyListener(old_state);
                - panel.addKeyListener(new_state);
                - panel.addMouseListener(new_state);
           So that's why reference to the panel is passed to the state.
           ("panel" is a static member of State class)  */
        State.panel = this;
        State.change(new IntroState());

        /* This timer is responsible for updating the state of the game
           It updates the game state and repaints it around 60 times per second.  */
        Timer update_timer = new Timer(Constants.UPDATE_FREQUENCY_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                /* This is where action upon every frame is performed
                  (State is an abstract class, "current()" is a static method) */
                State.current().advance();

                // repaint calls "paintComponent" where "draw" of current state is called
                repaint();
            }
        });
        update_timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        State.current().draw(g);
    }
}


abstract class State implements KeyListener, MouseInputListener {
    /*  Each State class derived from this abstract class will have to implement
        its own set of KeyListener and MouseInputListener methods such as:
            keyTyped
            keyPressed
            keyReleased

            mouseClicked
            mousePressed
            mouseReleased
            mouseEntered
            mouseExited
            mouseDragged
            mouseMoved
        This way each state will be able to implement its own controls in a clear way
        without interfering with each other, and without introducing "type" variable
        and explicit conditional mechanism.

        Each state class is in separate file with separate set of these methods. See:
            - IntroState.java
            - PlayState.java
            - GameOverState.java  */


    /*  If I wanted to implement some states like "EquipmentViewState" or "TemporaryPopupState", or "PauseMenuState"
        where the previous state would have to be preserved, then it would be a good idea to use Stack
        data structure, however in this simple game the transitions between all states are known from
        the beginning:
            - "IntroState" changes to "PlayState"
            - "PlayState" changes to "GameOverState"
            - "GameOverState" changes to "IntroState"
        So using Stack is not needed. Only the reference to current state has to be kept.  */
    //protected static Stack<State> states = new Stack<>();

    // static variables
    private static State current_state = null;
    public static JPanel panel;

    /*  Each state is going to have a collection of simple graphics items, which may be:
            - TextItem    (e.g. score, student ID, owned powerups list)
            - RectItem
            - ImageItem   (e.g. player, virus, projectile, powerups, background)
            - ProgressBar (e.g. HP bar, and "time before next shot possible" bar)  */
    protected java.util.List<GraphicsItem> graphics_items;

    /*  Entities are models of game objects like:
            - Player
            - Projectile
            - Virus
        These can consist of multiple graphics items and tend to implement more
        complex behaviour than GraphicsItems.  */
    protected java.util.List<Entity> entities;

    // Source of background image: https://wallpapercave.com/wp/bwNMtPe.jpg
    protected static ImageItem background_item = new ImageItem(0, 0, Constants.WIDTH, Constants.HEIGHT,"background.png", false);

    State() {
        graphics_items = new ArrayList<>();
        entities = new ArrayList<>();
    }

    protected abstract void onEnter();
    protected abstract void onExit();
    public abstract void advance();
    public abstract void draw(Graphics g);

    /*  "change" method performs cleanup of previous state (onExit call)
        and initializes the new state (onEnter call). It also ensures that
        key and mouse events of previous state are not passed to panel
        anymore (remove_x_Listener calls) and the new state passes such
        events instead (add_x_Listener calls).
     */
    static public void change(State new_state) {
        /*if (!states.isEmpty()) {
             State old_state = states.pop();
             old_state.onExit();*/

        if (current_state != null) {
            current_state.onExit();
            panel.removeMouseListener(current_state);
            panel.removeKeyListener(current_state);
        }

        //states.push(new_state);

        new_state.onEnter();
        panel.addKeyListener(new_state);
        panel.addMouseListener(new_state);
        current_state = new_state;
    }

    public static State current() {
        //return states.peek();

        return current_state;
    }
}

class PlayState extends State {
    /*  In this state the gameplay behaviour is defined.
        It has and manages 3 key members:
            - player
            - viruses list (enemies)
            - projectiles list ("bullet-like" objects shot by player)

        In the "advance" method it checks for collisions between:
            - player and viruses      (to decrease HP if that happens)
            - projectiles and viruses (to destroy viruses if that happens)

        It displays GUI items such as:
            - HP bar
            - "Time till next shot available" bar
            - Score
            - Student ID
            - Owned powerups list
    */

    private Player player;
    private List<Projectile> projectiles;
    private List<Virus> viruses;

    /* Bunch of hard-coded positions/sizes and declarations of GUI elements */

    // HP bar
    private static final int HP_BAR_X = 10;
    private static final int HP_BAR_Y = 10;
    private static final int HP_BAR_WIDTH = 200;
    private static final int HP_BAR_HEIGHT = 20;
    private static final Color HP_BAR_COLOR = Color.RED;
    private ProgressBar hp_bar;

    // "Time till next shot available" bar
    private static final int SHOOTING_BAR_X = HP_BAR_X;
    private static final int SHOOTING_BAR_Y = HP_BAR_Y + (int)(HP_BAR_HEIGHT *1.3);
    private static final int SHOOTING_BAR_WIDTH = HP_BAR_WIDTH;
    private static final int SHOOTING_BAR_HEIGHT = HP_BAR_HEIGHT;
    private static final Color SHOOTING_BAR_COLOR = Color.WHITE;
    private ProgressBar shooting_bar;

    // score TextItem
    private static final int SCORE_X = HP_BAR_X;
    private static final int SCORE_Y = SHOOTING_BAR_Y + (int)(HP_BAR_HEIGHT *2);
    private static final int SCORE_FONT_SIZE = 20;
    private static final Color SCORE_COLOR = Color.WHITE;
    private int score;
    private TextItem score_item;

    // student ID TextItem
    private static final String STUDENT_ID = "1903165";
    private static final int STUDENT_ID_X = HP_BAR_X;
    private static final int STUDENT_ID_Y = SCORE_Y + (int)(SCORE_FONT_SIZE *1.3);
    private static final int STUDENT_ID_FONT_SIZE = SCORE_FONT_SIZE;
    private static final Color STUDENT_ID_COLOR = Color.WHITE;

    // initial virus
    private static final int VIRUS_START_X = Constants.WIDTH/2;
    private static final int VIRUS_START_Y = Constants.HEIGHT/5;
    private static final int VIRUS_START_SIZE_TIER = 4;

    private int level;

    // green "Powerups:" label in to top-right corner (for showing currently owned powerups)
    private static TextItem powerups_label = new TextItem(Constants.WIDTH/10*7,  Constants.HEIGHT/25, SCORE_FONT_SIZE, "Powerups:", Color.GREEN, false);

    @Override
    protected void onEnter() {
        // Initialize collections and items, add GUI items to "graphics_items" list
        // so these can be displayed all using "forEach"

        player = new Player();
        viruses = new ArrayList<>();
        viruses.add( new Virus(VIRUS_START_X, VIRUS_START_Y, VIRUS_START_SIZE_TIER) );
        projectiles = new ArrayList<>();

        // Create score and student ID text items, then add them to graphics_items collection
        score = 0;
        score_item = new TextItem(SCORE_X, SCORE_Y, SCORE_FONT_SIZE, "Score = 0", SCORE_COLOR, false);
        graphics_items.add(score_item);
        graphics_items.add(new TextItem(STUDENT_ID_X, STUDENT_ID_Y, STUDENT_ID_FONT_SIZE, "Student ID = " + STUDENT_ID, STUDENT_ID_COLOR, false));

        // Create HP and shooting bar (displayed in top-left corner)
        hp_bar = new ProgressBar(HP_BAR_X, HP_BAR_Y, HP_BAR_WIDTH, HP_BAR_HEIGHT, HP_BAR_COLOR, Player.MAX_HP);
        shooting_bar = new ProgressBar(SHOOTING_BAR_X, SHOOTING_BAR_Y, SHOOTING_BAR_WIDTH, SHOOTING_BAR_HEIGHT, SHOOTING_BAR_COLOR, Player.BASE_SHOOTING_TIME_LIMIT);
        graphics_items.add(hp_bar);
        graphics_items.add(shooting_bar);

        Powerup.resetAll();
    }

    @Override
    protected void onExit() {
    }

    @Override
    public void advance() {
        // Update the state of all game objects (move what supposed to move, react to input)

        player.advance();
        viruses.forEach(v -> v.advance());
        projectiles.forEach(p -> p.advance());

        // Shooting rate is limited, a projectile is not created upon every button press.
        // Instead the check for shooting ability is done here. If player can shoot,
        // then a projectile is created and added to list.
        if (player.isShooting()) {
            player.isShooting(false);
            int px = player.getX();
            int py = player.getY();
            int pw = player.getWidth();
            int ph = player.getHeight();
            projectiles.add(new Projectile(px + pw/2, py - ph/2));
        }

        // Check if player is colliding with a virus. If that's the case, then
        // decrease HP accordingly and update the hp_bar object. Additionally,
        // check if the player is dead, in such case change the state to GameOverState.
        Virus colliding_virus = player.collidingVirus(viruses);
        if (colliding_virus != null) {
            player.onHit();
            hp_bar.setValue(player.getHealth());
            if (player.isDead()) {
                ScoreRecords.getInstance().add(
                        String.format("Student (%s)", STUDENT_ID),
                        score);
                State.change(new GameOverState());
            }
        }

        // Check if the player is colliding with a powerup,
        // in such case add it to currently owned powerups of the player.
        Powerup colliding_powerup = player.collidingPowerup();
        if (colliding_powerup != null) {
            player.addPowerup(colliding_powerup);
            colliding_powerup.isActive(false);
        }

        // Advance all powerups (can't use "forEach" because "all_powerups" is a static array of Powerup class)
        for (int i = 0; i < Powerup.all_powerups.length; i++)
            Powerup.all_powerups[i].advance();

        /*  Loop over each virus and check if it was hit by a projectile.
            If that's the case then:
                - add +1 to score
                - remove virus that got hit
                - if the virus size tier wasn't the smallest
                  then produce 2 additional viruses, 1 moving right, 1 moving left
                - if all viruses were eliminated then increase level and spawn a new, bigger virus
                  (so the game never ends unless the player dies)
        */
        for (int i = 0; i < viruses.size(); i++) {
            Virus v = viruses.get(i);
            if (!v.isActive())
                continue;
            Projectile colliding_projectile = v.collidingProjectile(projectiles);
            if (colliding_projectile != null) {
                colliding_projectile.deactivate();
                projectiles.remove(colliding_projectile);
                viruses.remove(v);
                for (Virus new_v : v.pop())
                    viruses.add(new_v);
                score++;
                score_item.setText("Score = " + score);

                if (viruses.isEmpty()) {
                    // next level
                    level++;
                    viruses.add(new Virus(VIRUS_START_X, VIRUS_START_Y, level + VIRUS_START_SIZE_TIER) );
                }
                Powerup.spawnIfLucky(v.getCenterX(), v.getCenterY());
            }
        }

        // update the "time till next shot available" bar
        shooting_bar.setValue((int) (Player.BASE_SHOOTING_TIME_LIMIT - player.timeTillNextShot()));
    }

    @Override
    public void draw(Graphics g) {
        // paint all game objects including player, projectiles, viruses and remaining GUI items

        background_item.draw(g); // draw background first (that's why it's not added to "graphics_items" collection)

        player.draw(g);
        projectiles.forEach(p -> p.draw(g));

        if (player.hasPowerup())
            powerups_label.draw(g);

        for (int i = 0; i < Powerup.all_powerups.length; i++)
            Powerup.all_powerups[i].draw(g);

        viruses.forEach(v -> v.draw(g));
        graphics_items.forEach(item -> item.draw(g));
        entities.forEach(e -> e.draw(g));

        // Draw list of all currently owned powerups and their remaining duration.
        // It's not optimal to format the string and perform arithmetic operations here,
        // however it works and seems to not cause performance issues so I
        // just left it the way it is.
        int y_offset = 0;
        long current_time = System.currentTimeMillis();
        for (Map.Entry e : player.getOwnedPowerups().entrySet()) {
            Powerup p = (Powerup)e.getKey();
            Long start_time = (Long)e.getValue();
            p.draw(g);
            y_offset += 30;
            g.setColor(Color.WHITE);
            g.drawString(String.format("%s %ds", p.getName(), (p.getDuration() - (current_time - start_time))/1000 + 1), powerups_label.getX(), powerups_label.getY() + y_offset);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) { }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A)
            player.movesLeft(true);
        else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D)
            player.movesRight(true);
        else if (key == KeyEvent.VK_SPACE) {
            player.isShooting(true);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A)
            player.movesLeft(false);
        else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D)
            player.movesRight(false);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        //System.out.println("PlayState::mouseClicked, getButton = " + e.getButton());
    }

    @Override
    public void mousePressed(MouseEvent e) { }
    @Override
    public void mouseReleased(MouseEvent e) { }
    @Override
    public void mouseEntered(MouseEvent e) { }
    @Override
    public void mouseExited(MouseEvent e) { }
    @Override
    public void mouseDragged(MouseEvent e) { }
    @Override
    public void mouseMoved(MouseEvent e) { }
}

class IntroState extends State {
    /*  This is the initial state of the game, it shows an image
        for 1 second and changes into PlayState.  */

    private static final int INTRO_LENGTH = 1000;

    private static ImageItem intro_image = new ImageItem(0,0, Constants.WIDTH, Constants.HEIGHT,"intro.png", false);

    public IntroState() {
        graphics_items.add(intro_image);
    }

    @Override
    protected void onEnter() {
        // It sets timer to change the state into PlayState after 1 second.
        Timer timer = new Timer(INTRO_LENGTH, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                State.change(new PlayState());
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    @Override
    protected void onExit() {
    }

    @Override
    public void advance() {
    }

    @Override
    public void draw(Graphics g) {
        background_item.draw(g);
        graphics_items.forEach(item -> item.draw(g));
    }

    @Override
    public void keyTyped(KeyEvent e) { }
    @Override
    public void keyPressed(KeyEvent e) { }
    @Override
    public void keyReleased(KeyEvent e) { }
    @Override
    public void mouseClicked(MouseEvent e) {
        //System.out.println("IntroState::mouseClicked, getButton = " + e.getButton());
    }
    @Override
    public void mousePressed(MouseEvent e) { }
    @Override
    public void mouseReleased(MouseEvent e) { }
    @Override
    public void mouseEntered(MouseEvent e) { }
    @Override
    public void mouseExited(MouseEvent e) { }
    @Override
    public void mouseDragged(MouseEvent e) { }
    @Override
    public void mouseMoved(MouseEvent e) { }
}

class GameOverState extends State {
    /*  This state displays:
            - large "Game over" label
            - "press any key..." label underneath
            - Achieved score
            - Top 5 of all time scores   */
    private static final int GAME_OVER_LENGTH = 1000;
    private static final int GAME_OVER_SIZE = 50;
    private static final int PRESS_ANY_KEY_SIZE = 25;
    private static final int HIGH_SCORES_SIZE = 20;

    /* this boolean will prevent from exiting this state too quickly
       it's needed because the game may end while user is about to press a key
       it prevents exiting GameOverState by accident before being able to see it properly
       press_any_key_text_shown becomes true after a second  */
    private boolean press_any_key_text_shown;

    public GameOverState() {
        // Create the large "Game Over" label and add it to graphics_items list
        graphics_items.add(new TextItem(Constants.WIDTH/2, Constants.HEIGHT/6, GAME_OVER_SIZE,"Game Over", Color.RED, true));

        ScoreRecords sr = ScoreRecords.getInstance();
        Record last_record = sr.lastAdded();

        // Create "You scored <number>" item and add it to graphics_items list
        graphics_items.add(new TextItem(Constants.WIDTH/2, Constants.HEIGHT/2, GAME_OVER_SIZE,"You scored " + last_record.score, Color.WHITE, true));


        int y_pos = Constants.HEIGHT / 5 * 3;

        // Create "Top 5 scores of all time:" label item and add it to graphics_items list
        graphics_items.add(new TextItem(Constants.WIDTH / 10, y_pos, HIGH_SCORES_SIZE *2,"Top 5 scores of all time:", Color.GREEN, false));
        y_pos += HIGH_SCORES_SIZE *2;

        /*  Loop over each of top 5 scores, create TextItem for each and add them to graphics_items list
            If the player just achieved top 5 scores it marks it with green colour and appends (NEW) at the end.  */
        int i = 1;
        for (Record record : sr.topFive()) {
            String text_format = "%d. %s - %d";
            Color clr = Color.WHITE;
            if (record == last_record) {
                text_format += " (NEW)";
                clr = Color.GREEN;
            }
            graphics_items.add(
                    new TextItem(
                            Constants.WIDTH / 10,
                            y_pos,
                            HIGH_SCORES_SIZE,
                            String.format(text_format, i++, record.name, record.score),
                            clr, false)
            );
            y_pos += HIGH_SCORES_SIZE * 3/2;
        }
    }

    @Override
    protected void onEnter() {
        // It's called when State.current_state (static member) is set to GameOverState

        press_any_key_text_shown = false;
        Timer timer = new Timer(GAME_OVER_LENGTH, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                graphics_items.add(new TextItem(Constants.WIDTH/2, Constants.HEIGHT/6 +  GAME_OVER_SIZE, PRESS_ANY_KEY_SIZE,"Press any key...", Color.RED, true));
                press_any_key_text_shown = true;
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    @Override
    protected void onExit() {
    }

    @Override
    public void advance() {
    }

    @Override
    public void draw(Graphics g) {
        background_item.draw(g);
        graphics_items.forEach(item -> item.draw(g));
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Proceed to a new game when player types any key

        if (press_any_key_text_shown)
            State.change(new IntroState());
    }

    @Override
    public void keyPressed(KeyEvent e) { }
    @Override
    public void keyReleased(KeyEvent e) { }

    @Override
    public void mouseClicked(MouseEvent e) {
        //System.out.println("GameOverState::mouseClicked, getButton = " + e.getButton());
    }

    @Override
    public void mousePressed(MouseEvent e) { }
    @Override
    public void mouseReleased(MouseEvent e) { }
    @Override
    public void mouseEntered(MouseEvent e) { }
    @Override
    public void mouseExited(MouseEvent e) { }
    @Override
    public void mouseDragged(MouseEvent e) { }
    @Override
    public void mouseMoved(MouseEvent e) { }
}

abstract class GraphicsItem {
    /*  This game needs to display different kind of graphical items.
        GraphicsItem abstract class provides framework for all of them.
        That is to avoid code duplication and allow using polymorphism
        to simplify the code.  */

    protected int x, y, width, height;

    /*  If the supplied "centered" is "true"  then the center of the item is placed at x_ and y_ values.
        If the supplied "centered" is "false" then the top-left corner of the item is placed at x_ and y_ values.   */
    public GraphicsItem(int x_, int y_, int w, int h, boolean centered) {
        x = x_ - (centered ? w/2 : 0);
        y = y_ - (centered ? h/2 : 0);
        width = w;
        height = h;
    }

    public abstract void draw(Graphics g);
    public abstract void drawAt(Graphics g, int x, int y, boolean centered);

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setCenterX(int x) { this.x = x - width/2; }
    public void setCenterY(int y) { this.y = y - height/2; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}

class ImageItem extends GraphicsItem {
    /*  All loaded images are stored in a hashmap (key=filename, value=Image),
        that is to avoid loading them multiple times (which could be inefficient).  */
    private static HashMap<String, BufferedImage> all_loaded_images = new HashMap<>();
    private static final String IMAGES_PATH = "src\\ImageFiles\\";

    private BufferedImage image;

    private boolean is_active;

    /*  Before loading an image, the constructor checks if the specified image was
        previously loaded, in such case it uses previously used image (and resizes
        it if the size does not match).  */
    public ImageItem(int x_, int y_, int w, int h, String file_name, boolean centered) {
        super(x_, y_, w, h, centered);

        if (!all_loaded_images.containsKey(file_name)) {
            try {
                image = ImageIO.read(new File(IMAGES_PATH + file_name));
                all_loaded_images.put(file_name, image);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            image = all_loaded_images.get(file_name);
        }

        // resize the image if its dimensions don't match the supplied width and height
        if (w != image.getWidth() || h != image.getHeight())
            scaleImage();

        is_active = true;
    }

    /*  Inactive images are not drawn  */
    public boolean isActive() {
        return is_active;
    }

    public void isActive(boolean state) {
        is_active = state;
    }

    /*  "scaleImage" resizes the image to have width and height specified in the constructor.  */
    private void scaleImage() {
        Image temp = image.getScaledInstance(width, height, SCALE_SMOOTH);
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        g.drawImage(temp, 0, 0, null);
        g.dispose();
    }

    @Override
    public void draw(Graphics g) {
        if (is_active)
            g.drawImage(image, x, y, null);
    }

    @Override
    public void drawAt(Graphics g, int x_, int y_, boolean centered) {
        if (is_active) {
            if (centered)
                g.drawImage(image, x_ - width / 2, y_ - height / 2, null);
            else
                g.drawImage(image, x_, y_, null);
        }
    }
}


class ProgressBar extends RectItem {
    /*  ProgressBar class can be used to conveniently create HP bar.
        It is also used in this game to create "time till next shot" bar.  */

    private int value;
    private int max_value;
    private Color color, bg_color;
    private static final int MARGIN = 2;
    private static final Color MARGIN_COLOR = Color.GRAY;

    public ProgressBar(int x_, int y_, int w, int h, Color clr_, int max_value_) {
        super(x_, y_, w, h, MARGIN_COLOR);
        color = clr_;

        /*  bg_color = background color
            using clr_.darker() didn't work well when the color was already dark,
            so I decided to divide each of RGB values 'manually'  */
        bg_color = new Color(clr_.getRed()/5, clr_.getGreen()/5, clr_.getBlue()/5);

        value = max_value_;
        max_value = max_value_;
    }

    public void setValue(int v) {
        value = v;
    }

    @Override
    public void draw(Graphics g) {
        super.draw(g);
        g.setColor(bg_color);

        /*  ProgressBar really draws 3 rectangles:
                - 1 which serves as border, painted by "super.draw" (because this class extends RectItem)
                - 1 which indicates the actual progress (e.g. HP level)
                - 1 which serves as the background (using color that is darker than actual progress rect
            MARGIN is added/subtracted below to avoid painting over the 1st rectangle (border)  */
        int x_ = super.x + MARGIN;
        int y_ = super.y + MARGIN;
        int w_ = super.width - MARGIN*2;
        int h_ = super.height - MARGIN*2;

        // draw background rect (darker than actual progress rect)
        g.fillRoundRect(x_, y_, w_, h_, MARGIN, MARGIN);

        // draw actual progress rect
        g.setColor(color);
        g.fillRoundRect(x_, y_, w_*value/max_value, h_, MARGIN, MARGIN);
    }
}

class TextItem extends GraphicsItem {
    /*  TextItem was used in this game to display things like:
            - score
            - student ID
            - owned powerups list
            - top 5 scores
            - "game over" and "press any key to continue"   */

    private Color clr;
    private String text;
    private Font font;
    private int font_size;
    private boolean is_centered;

    public TextItem(int x_, int y_, int font_size_, String text_, Color clr_, boolean centered) {
        super(x_, y_, text_.length(), 0, centered);
        clr = clr_;
        text = text_;
        font = null;
        font_size = font_size_;
        is_centered = centered;
    }

    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }

    @Override
    public void draw(Graphics g) {
        drawAt(g, super.x, super.y, is_centered);
    }

    @Override
    public void drawAt(Graphics g, int x_, int y_, boolean centered) {
        Graphics2D g2 = (Graphics2D)g;
        g2.setColor(clr);

        if (font == null)
            font = g2.getFont().deriveFont((float)font_size);

        g2.setFont(font);

        // To center the text, I used this to find the width of the text item:
        // https://stackoverflow.com/questions/258486/calculate-the-display-width-of-a-string-in-java
        if (centered) {
            x_ -= g.getFontMetrics().stringWidth(text) / 2;
            y_ -= font_size / 2;
        }

        g2.drawString(text, x_, y_);
    }
}


class RectItem extends GraphicsItem {

    Color clr;
    public RectItem(int x_, int y_, int w, int h, Color clr_) {
        super(x_, y_, w, h, false);
        clr = clr_;
    }

    @Override
    public void draw(Graphics g) {
        g.setColor(clr);
        g.fillRect(super.x, super.y, super.width, super.height);
    }

    @Override
    public void drawAt(Graphics g, int x, int y, boolean centered) {
        g.setColor(clr);
        g.fillRect(x, y, width, height);
    }
}


class Powerup extends ImageItem {
    /*  Powerups is a base class for the following 3 derived classes:
            - PowerupMask
            - PowerupSanitizer
            - PowerupVaccine

        What are powerups?
        They're items that occasionally drop from virus after getting shot down.
        Running into powerup gives the player temporary special abilities:
            - faster movement (mask)
            - higher shooting rate (sanitizer)
            - immunity to virus (vaccine)

        This class stores a static array with 3 instances of all powerups.
        (the same 3 objects are reused all the time)  */

    public static Powerup[] all_powerups = {
            new PowerupMask(),
            new PowerupVaccine(),
            new PowerupSanitizer()
    };

    // how fast the image goes down after being spawned
    private static final int FALL_SPEED = 1;

    // probability of getting a powerup after destroying a virus (30%)
    private static final float CHANCE_TO_SPAWN = 0.8f;//0.5f

    protected String name;
    protected int duration;

    public Powerup(String file_name) {
        super(0,0, 100, 100, file_name, true);
        isActive(false);
    }

    @Override
    public void draw(Graphics g) {
        // This method (of ImageItem) is overridden for the sake of drawing highlight (rounded rect around it)
        drawAt(g, x, y, false);
    }

    @Override
    public void drawAt(Graphics g, int x_, int y_, boolean centered) {
        super.drawAt(g, x_, y_, centered);

        if (isActive()) {
            // draw highlight (rounded rect)
            g.setColor(Color.ORANGE);
            g.drawRoundRect(x_, y_, width, height, width / 5, width / 5);
        }
    }

    /*  Each call to "advance" will move the image down
        It becomes inactive after going out of screen (so it can be spawned again).  */
    public void advance() {
        if (isActive()) {
            y += FALL_SPEED;
            if (y > Constants.HEIGHT + height)
                isActive(false);
        }
    }

    public boolean collidesWith(Entity e) {
        return new Rectangle(x, y, width, height).intersects(e.getBoundingRect());
    }

    public int getDuration() {
        return duration;
    }

    public String getName() {
        return name;
    }

    // STATIC METHODS

    /*  "resetAll" deactivates all powerups.  */
    public static void resetAll() {
        for (Powerup p : all_powerups)
            p.isActive(false);
    }

    /*  "spawnIfLucky" generates a random number (which later serves as an index)
        to determine if a powerup will be activated or not. Supplied x_ and y_ values
        are central positions of a destroyed virus.  */
    public static Powerup spawnIfLucky(int x_, int y_) {
        int index = new Random().nextInt((int)(all_powerups.length / CHANCE_TO_SPAWN));
        if (index < all_powerups.length) {
            if (!all_powerups[index].isActive()) {
                Powerup p = all_powerups[index];
                p.setCenterX(x_);
                p.setCenterY(y_);
                p.isActive(true);
                return p;
            }
        }
        return null;
    }
}


class PowerupMask extends Powerup {
    public PowerupMask() {
        super("power_up_mask.png");
        name = "Mask (speed)";
        duration = 10000;
    }
}


class PowerupSanitizer extends Powerup {
    public PowerupSanitizer() {
        super("power_up_sanitizer.png");
        name = "Sanitizer (shot rate)";
        duration = 10000;
    }
}

class PowerupVaccine extends Powerup {
    public PowerupVaccine() {
        super("power_up_vaccine.png");
        name = "Vaccine (immunity)";
        duration = 10000;
    }
}

class Virus extends Entity {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 200;

    public static final int MAX_SIZE_TIER = 10;
    private static final int MAX_JUMP_HEIGHT = Constants.GRASS_Y_POS;

    // how large is virus
    int size_tier;

    public Virus(int x_, int y_, int size_tier_) {
        super(1,2,
                new ImageItem(x_,
                        y_,
                        WIDTH / MAX_SIZE_TIER * size_tier_,
                        HEIGHT / MAX_SIZE_TIER * size_tier_,
                        "virus.png", // https://cdn.xl.thumbs.canstockphoto.com/coronavirus-2019-ncov-corona-virus-icon-black-on-red-background-isolated-china-pathogen-drawing_csp80377593.jpg
                        false));
        movesRight(true);
        movesDown(true);
        size_tier = size_tier_;
    }

    public Virus(Virus other) {
        super(1,2,
                new ImageItem(other.getX() + other.getWidth()/2,
                        other.getY() + other.getHeight()/2,
                        WIDTH / MAX_SIZE_TIER * other.getSizeTier()-1,
                        HEIGHT / MAX_SIZE_TIER * other.getSizeTier()-1,
                        "virus.png",
                        false));
        super.x -= width/2;
        super.y -= height/2;
        dx = other.dx;
        dy = other.dy;
        size_tier = other.getSizeTier()-1;
    }

    @Override
    public boolean collidesWith(Entity e) {
        return new Ellipse2D.Float(super.x, super.y, super.width, super.height).intersects(e.getBoundingRect());
    }

    @Override
    public void draw(Graphics g) {
        super.draw(g);
        //virus_item.draw((Graphics2D) g);
    }

    public List<Virus> pop() {
        List<Virus> new_viruses = new ArrayList<>();
        is_active = false;
        if (size_tier > 1) {
            Virus v1 = new Virus(this);
            Virus v2 = new Virus(this);
            v1.movesLeft(true);
            v2.movesRight(true);
            new_viruses.add(v1);
            new_viruses.add(v2);
        }
        return new_viruses;
    }

    @Override
    public void advance() {
        super.advance();

        // this is where the virus is set to bounce off the window (and ground) boundaries

        if (x <= 0)
            movesRight(true);

        if (x >= Constants.WIDTH - width)
            movesLeft(true);

        if (y <= Constants.GRASS_Y_POS - MAX_JUMP_HEIGHT)
            movesDown(true);

        if (y >= Constants.GRASS_Y_POS - height)
            movesUp(true);
    }

    public int getSizeTier() {
        return size_tier;
    }
}

class Player extends Entity {
    private static final int WIDTH = 50;
    private static final int HEIGHT = 100;
    public static final int BASE_SHOOTING_TIME_LIMIT = 1000;
    private static final int SHOOT_ANIM_LENGTH = 240;

    private static final int HIT_COOLDOWN = 1000;

    public static final int MAX_HP = 100;

    //  Different player image is displayed if he shot recently.
    private static ImageItem image_normal = new ImageItem(0,0, WIDTH, HEIGHT,"dude_sanitizer.png",false);
    private static ImageItem image_shooting = new ImageItem(0,0, WIDTH, HEIGHT,"dude_shooting.png",false);

    //  Mask is drawn over players head if he picks up mask powerup.
    private static ImageItem image_mask = new ImageItem(0,0, WIDTH/2, WIDTH/2,"power_up_mask.png",false);
    private static int BASE_SPEED = 3;

    private int health;

    // shooting related
    private boolean is_shooting;
    private int shooting_time_limit;
    private long last_shot_time;
    private ImageItem current_image;
    private long last_hit_time;

    //  powerups
    private boolean is_immune;
    private boolean has_mask;
    private static final int FASTER_MOVEMENT_RATE = 2;
    private boolean faster_shooting;
    private static final int FASTER_SHOOTING_RATE = 4;

    // list of currently owned powerups and their acquisition times
    Map<Powerup, Long> owned_powerups;

    public Player() {
        super(Constants.WIDTH/2, Constants.GRASS_Y_POS-HEIGHT, WIDTH, HEIGHT, BASE_SPEED, 0);
        is_shooting = false;
        last_shot_time = 0;
        shooting_time_limit = BASE_SHOOTING_TIME_LIMIT;
        health = MAX_HP;
        current_image = image_normal;
        last_hit_time = 0;
        owned_powerups = new HashMap<>();
    }

    public int getHealth() {
        return health;
    }

    @Override
    public void draw(Graphics g) {
        super.draw(g);

        // draw immunity indicator (green rounded rect) if needed
        if (is_immune) {
            g.setColor(Color.GREEN);
            final int m = 2;
            g.drawRoundRect(x - m,y - m, width + m*2, height + m*2, width/2,width/2);
        }

        current_image.drawAt(g, x,y,false);

        // draw mask if needed
        if (has_mask)
            image_mask.drawAt(g, x + width/2, y + height / 6, true);
    }

    @Override
    public void advance() {

        // calls "advance" method of Entity (parent class)
        super.advance();

        // prevent player going out of screen
        constrainPosition();

        // if the player is shooting, then set the timer to reset its image after
        // specified time (240ms)
        if (is_shooting) {
            last_shot_time = System.currentTimeMillis();
            Timer timer = new Timer(SHOOT_ANIM_LENGTH, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    current_image = image_normal;
                }
            });
            timer.setRepeats(false);
            timer.start();
            current_image = image_shooting;
        }
        checkPowerupsEnd();
    }

    /*  "addPowerup" keeps reference to the powerup that was collected and hides it (deactivates).
        It also saves the current time so later it can be compared and special abilities removed.  */
    public void addPowerup(Powerup p) {
        if (p instanceof PowerupMask) {
            hasMask(true);
        } else if (p instanceof PowerupVaccine) {
            isImmune(true);
        } else if (p instanceof PowerupSanitizer) {
            fasterShooting(true);
        }
        p.isActive(false);
        owned_powerups.put(p, System.currentTimeMillis());
    }

    public void removePowerup(Powerup p) {
        owned_powerups.remove(p);
    }

    /*  Returns powerup that collides with player.  */
    public Powerup collidingPowerup() {
        return super.collidingPowerup(Arrays.asList(Powerup.all_powerups));
    }

    public boolean hasPowerup() {
        return !owned_powerups.isEmpty();
    }

    public Map<Powerup, Long> getOwnedPowerups() {
        return owned_powerups;
    }

    /*  "checkPowerupsEnd" loops over all currently owned powerups checking their
        acquisition time, duration and compares them against current time. This way
        the special abilities get removed when powerup duration ends.

        Owned powerups are stored in a list, "instanceof" keyword is used to recognize
        which type of powerup duration ended.  */
    private void checkPowerupsEnd() {
        long current_time = System.currentTimeMillis();
        ArrayList<Powerup> powerups_to_remove = new ArrayList<>();
        for (Powerup p : owned_powerups.keySet()) {
            if (current_time - owned_powerups.get(p) > p.getDuration()) {
                powerups_to_remove.add(p);
                if (p instanceof PowerupVaccine) {
                    is_immune = false;
                }
                else if (p instanceof PowerupMask) {
                    has_mask = false;
                    updateSpeed();
                }
                else if (p instanceof PowerupSanitizer) {
                    faster_shooting = false;
                }
            }
        }
        for (Powerup p : powerups_to_remove)
            owned_powerups.remove(p);
    }

    /*  Setter method for shooting state. Attempt to set isShooting to "true"
        before waiting sufficient time (for time limit) will be unsuccessful.

        Method takes into account the state of "faster_shooting" boolean
        (which is a special ability gained by collecting "sanitizer" powerup)  */
    public void isShooting(boolean state) {
        if (state) {
            long time_since_last_shot = System.currentTimeMillis() - last_shot_time;
            is_shooting = time_since_last_shot >= (faster_shooting ? shooting_time_limit / FASTER_SHOOTING_RATE : shooting_time_limit);
        } else {
            is_shooting = false;
        }
    }

    public boolean isShooting() {
        return is_shooting;
    }

    public boolean fasterShooting() {
        return faster_shooting;
    }

    public void fasterShooting(boolean state) {
        faster_shooting = state;
    }

    /*  "onHit" determines what happens when a player touches a virus.
        Typically it decreases health points by a constant value (set to 30).
        "is_immune" boolean variable prevents damage if it's set to true,
        which happens after collecting vaccine powerup. "HIT_COOLDOWN" constant
        is there to prevent getting damaged multiple times by the same
        virus despite getting hit only once.  */
    public void onHit() {
        if (is_immune) return;

        long now = System.currentTimeMillis();
        if (now - last_hit_time >= HIT_COOLDOWN) {
            health -= 30;
            last_hit_time = now;
        }
    }

    public boolean isAlive() {
        return health > 0;
    }

    public boolean isImmune() { return is_immune; }
    public void isImmune(boolean state) {
        is_immune = state;
    }

    public boolean hasMask() { return has_mask; }
    public void hasMask(boolean state) {
        has_mask = state;
        updateSpeed();
    }

    /*  Sets horizontal movement speed value depending on possession of a mask powerup.  */
    private void updateSpeed() {
        speed_x = has_mask ? BASE_SPEED * FASTER_MOVEMENT_RATE :  BASE_SPEED;
    }

    public boolean isDead() { return !isAlive(); }

    public int getSpeed() {
        return speed_x;
    }

    public void setSpeed(int speed) {
        speed_x = speed;
    }

    /*  "timeTillNextShot" is a helper method used for setting progress bar indicator value
        (white bar under the HP bar) to graphically show when the player can shoot again.
        It also helps to recognize the effect of "sanitizer" powerup which increases
        shooting rate.  */
    public long timeTillNextShot() {
        long time_since_last_shot = System.currentTimeMillis() - last_shot_time;
        long temp = (faster_shooting ? shooting_time_limit / FASTER_SHOOTING_RATE : shooting_time_limit) - time_since_last_shot;
        if (temp < 0)
            temp = 0;
        return temp;
    }
}

abstract class Entity {
    /*  Entity abstract class was created to define common behaviour of models like:
            - Player
            - Virus
            - Projectile

        Entities can contain multiple GraphicsItems and tend to represent more complex objects.
        Distinction between GraphicsItems and Entities could be helpful as the game gets developed
        further (and the number of GraphicsItems and Entities increases).  */


    // "dx" and "dy" are added to "x" and "y" positions every frame
    int x, y, dx, dy, width, height, speed_x, speed_y;

    boolean is_active;
    List<GraphicsItem> items;

    public Entity(int speed_x_, int speed_y_, GraphicsItem item_) {
        dx = 0;
        dy = 0;
        speed_x = speed_x_;
        speed_y = speed_y_;
        width = item_.getWidth();
        height = item_.getHeight();
        x = item_.getX();
        y = item_.getY();
        is_active = true;
        items = new ArrayList<>();
        items.add(item_);
    }

    public Entity(int x_, int y_, int w, int h, int speed_x_, int speed_y_) {
        dx = 0;
        dy = 0;
        speed_x = speed_x_;
        speed_y = speed_y_;
        width = w;
        height = h;
        x = x_;
        y = y_;
        is_active = true;
        items = new ArrayList<>();
    }

    public void advance() {
        x += dx;
        y += dy;
    }

    public void draw(Graphics g) {
        // draw entity only if it's active
        if (is_active)
            for (GraphicsItem item : items)
                item.drawAt(g, x, y,false);
    }

    // ensure the entity doesn't go outside of the frame
    protected void constrainPosition() {
        if (x < 0)
            x = 0;
        if (x > Constants.WIDTH - width)
            x = Constants.WIDTH - width;
        if (y < 0)
            y = 0;
        if (y > Constants.HEIGHT)
            y = Constants.HEIGHT;
    }

    public Rectangle getBoundingRect() {
        return new Rectangle(x, y, width, height);
    }

    public boolean collidesWith(Entity e) {
        return getBoundingRect().intersects(e.getBoundingRect());
    }

    public Entity collidingEntity(List<Entity> other_entities) {
        Rectangle r = getBoundingRect();
        for (Entity oe : other_entities)
            if (r.intersects(oe.getBoundingRect()))
                return oe;
        return null;
    }

    public Projectile collidingProjectile(List<Projectile> projectiles) {
        for (Projectile p : projectiles)
            if (p.collidesWith(this))
                return p;
        return null;
    }

    public Powerup collidingPowerup(List<Powerup> powerup) {
        for (Powerup p : powerup)
            if (p.isActive() && p.collidesWith(this))
                return p;
        return null;
    }

    public Virus collidingVirus(List<Virus> viruses) {
        for (Virus v : viruses)
            if (v.collidesWith(this))
                return v;
        return null;
    }

    public boolean movesUp() { return dy < 0; }
    public boolean movesDown() { return dy > 0; }
    public boolean movesLeft() { return dx < 0; }
    public boolean movesRight() { return dx > 0; }

    public void movesUp(boolean is_moving) {
        if (is_moving)
            dy = -speed_y;
        else if (!movesDown())
            dy = 0;
    }

    public void movesDown(boolean is_moving) {
        if (is_moving)
            dy = speed_y;
        else if (!movesUp())
            dy = 0;
    }

    public void movesLeft(boolean is_moving) {
        if (is_moving)
            dx = -speed_x;
        else if (!movesRight())
            dx = 0;
    }

    public void movesRight(boolean is_moving) {
        if (is_moving)
            dx = speed_x;
        else if (!movesLeft())
            dx = 0;
    }

    public int getX() {
        return x;
    }
    public void setX(int x) {
        this.x = x;
    }
    public int getCenterX() {
        return x + width / 2;
    }
    public void setCenterX(int x) { this.x = x - width/2; }
    public int getY() {
        return y;
    }
    public void setY(int y) {
        this.y = y;
    }
    public int getCenterY() {
        return y + height / 2;
    }
    public void setCenterY(int y) { this.y = y - height/2; }
    public int getWidth() {
        return width;
    }
    public int getHeight() {
        return height;
    }
    public void setSpeed_x(int speed_x) {
        this.speed_x = speed_x;
    }
    public int getSpeed_x() {
        return speed_x;
    }
    public void setSpeed_y(int speed_y) {
        this.speed_y = speed_y;
    }
    public int getSpeed_y() {
        return speed_y;
    }

    public boolean isActive() {
        return is_active;
    }
    public void deactivate() {
        is_active = false;
    }
    public void activate() {
        is_active = true;
    }
}

class Projectile extends Entity {
    /*  Projectile is the spray that is created when player shoots.  */

    public static final int WIDTH = 75; // actual image size is:  w=132 h=200
    public static final int HEIGHT = 114;

    private static final int SPEED = 5;

    public Projectile(int x_, int y_) {
        super(0, SPEED, new ImageItem(x_, y_, WIDTH, HEIGHT, "projectile.png", true) );
        movesUp(true);
    }

    @Override
    public void draw(Graphics g) {
        super.draw(g);
    }
}


class Record implements Comparable {
    public String name;
    public int score;
    public Record(String n, int s) {
        name = n;
        score = s;
    }

    @Override
    public int compareTo(Object o) {
        // This method will allow sorting collection of records based on score
        return ((Record)o).score - score;
    }

    @Override
    public String toString() {
        // This function was useful for debugging.

        return "Record{" +
                "name='" + name + '\'' +
                ", score=" + score +
                '}';
    }
}

class ScoreRecords {
    /*  ScoreRecords class manages storing and loading records of past games.
        It allows getting top 5 scores of all time.
        It is built using singleton pattern.  */

    private static ScoreRecords instance = null;
    public static ScoreRecords getInstance() {
        if (instance == null)
            instance = new ScoreRecords();
        return instance;
    }

    private static String FILE_NAME = "RECORDS.txt";
    List<Record> records;
    Record last_added;

    // Constructor attempts to read the "RECORDS.txt" file, populate and sort the records list
    private ScoreRecords() {
        records = new ArrayList<>();
        try {
            File f = new File(FILE_NAME);
            f.createNewFile(); // does nothing if file exists
            Scanner reader = new Scanner(f);
            while (reader.hasNextLine()) {
                String s = reader.nextLine();
                String name = s.split(",")[0];
                Integer score = Integer.parseInt(s.split(",")[1]);
                Record new_record = new Record(name, score);
                records.add(new_record);
            }
            reader.close();
        } catch (FileNotFoundException e) {
            System.out.println("ERROR: Record file could not be found, " + e.getMessage());
        } catch (IOException e) {
            System.out.println("ERROR: Record file could not be created, reason: " + e.getMessage());
        }
        records.sort(Record::compareTo);
    }

    /*  "topFive" method returns the best scores of all time.
         If less than 5 games were played, it fills the empty spaces
         with records having "______" instead of name and "0" as the score.  */
    public List<Record> topFive() {
        List<Record> top5 = new ArrayList<>();
        for (Record record : records) {
            top5.add(record);
            if (top5.size() == 5)
                break;
        }
        while (top5.size() < 5)
            top5.add(new Record("_______", 0));
        return top5;
    }

    // "add" method inserts a new record into a list and overwrites the "RECORDS.txt" file
    public void add(String name, int score) { add(new Record(name, score)); }
    public void add(Record new_record) {
        records.add(new_record);
        last_added = new_record;
        try {
            FileOutputStream fos = new FileOutputStream(FILE_NAME, true);
            fos.write(String.format("%s,%d\r\n", new_record.name, new_record.score).getBytes());
            fos.close();
        } catch (IOException e) {
            System.out.println("ERROR: Couldn't save new score" + e.getMessage());
        }
        records.sort(Record::compareTo);
    }

    public Record lastAdded() {
        return last_added;
    }
}

