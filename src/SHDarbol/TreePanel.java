package avltree;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/**
 * Renders the AVL tree with smooth position interpolation.
 *
 * Layout:  logical (x,y) computed by computePositions().
 *          draw   (drawX, drawY) are interpolated toward (x,y) each tick
 *          so nodes glide into place after rotations.
 *
 * HEIGHT LABEL: h:N  where N = node.height
 *   Leaves show h:1, root shows h = tree depth.
 * FE LABEL: FE:N   coloured green (0), amber (±1), red (|N|>1).
 */
public class TreePanel extends JPanel {

    private AVLTree tree;

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final Color BG        = new Color(15, 17, 26);
    private static final Color DOT_COL   = new Color(28, 32, 52);
    private static final Color NODE_FILL = new Color(31, 66, 180);
    private static final Color NODE_RIM  = new Color(90, 140, 255);
    private static final Color EDGE_COL  = new Color(55, 75, 140);
    private static final Color HI_YELLOW = new Color(255, 193,  7);  // generic
    private static final Color HI_GREEN  = new Color(  0, 210, 100); // search
    private static final Color HI_RED    = new Color(255,  70,  70); // rotation
    private static final Color HI_CYAN   = new Color(  0, 220, 220); // new node pulse
    private static final Color FE_OK     = new Color(100, 230, 150);
    private static final Color FE_WARN   = new Color(255, 193,   7);
    private static final Color FE_BAD    = new Color(255,  70,  70);
    private static final Color TEXT_W    = Color.WHITE;
    private static final Color TEXT_DIM  = new Color(170, 185, 215);

    private static final int R           = 27;   // node radius
    private static final int LEVEL_H     = 82;   // vertical distance per level
    private static final float LERP      = 0.18f; // interpolation speed per frame

    // New-node pulse animation
    private final Map<Integer, Integer> pulseCounters = new HashMap<>(); // value -> frames left

    public TreePanel(AVLTree tree) {
        this.tree = tree;
        setBackground(BG);
        setPreferredSize(new Dimension(900, 520));
    }

    public void setTree(AVLTree t) { this.tree = t; }

    // ── Called by animation timer to advance draw positions ──────────────────
    public boolean tickAnimations() {
        if (tree == null || tree.root == null) return false;
        return tickRec(tree.root);
    }

    private boolean tickRec(AVLNode n) {
        if (n == null) return false;
        boolean moving = false;

        if (!n.positionInitialized) {
            n.drawX = n.targetX;
            n.drawY = n.targetY;
            n.positionInitialized = true;
        }

        float dx = n.targetX - n.drawX;
        float dy = n.targetY - n.drawY;
        if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
            n.drawX += dx * LERP;
            n.drawY += dy * LERP;
            moving = true;
        } else {
            n.drawX = n.targetX;
            n.drawY = n.targetY;
        }

        // pulse countdown
        if (n.newNode) {
            int cnt = pulseCounters.getOrDefault(n.value, 20);
            cnt--;
            if (cnt <= 0) { n.newNode = false; pulseCounters.remove(n.value); }
            else { pulseCounters.put(n.value, cnt); moving = true; }
        }

        return tickRec(n.left) | tickRec(n.right) | moving;
    }

    // ── Layout: assign (x,y) → (targetX, targetY) ───────────────────────────
    public void recomputeLayout() {
        if (tree == null || tree.root == null) return;
        assignLogical(tree.root, getWidth() / 2, 46, getWidth() / 4);
    }

    private void assignLogical(AVLNode n, int x, int y, int xOff) {
        if (n == null) return;
        n.targetX = x;
        n.targetY = y;
        if (!n.positionInitialized) { n.drawX = x; n.drawY = y; n.positionInitialized = true; }
        int next = Math.max(xOff / 2, 28);
        assignLogical(n.left,  x - xOff, y + LEVEL_H, next);
        assignLogical(n.right, x + xOff, y + LEVEL_H, next);
    }

    // ── Highlight helpers ─────────────────────────────────────────────────────
    public void clearHighlights() { clearRec(tree != null ? tree.root : null); repaint(); }
    private void clearRec(AVLNode n) {
        if (n == null) return;
        n.highlighted = n.searchHighlight = n.rotationHighlight = false;
        n.highlightColor = null;
        clearRec(n.left); clearRec(n.right);
    }

    /** Mark a node as newly inserted (triggers cyan pulse). */
    public void markNew(int value) { markNewRec(tree.root, value); }
    private void markNewRec(AVLNode n, int value) {
        if (n == null) return;
        if (n.value == value) { n.newNode = true; pulseCounters.put(value, 25); }
        markNewRec(n.left, value); markNewRec(n.right, value);
    }

    /** Highlight rotation pivot. */
    public void highlightPivot(int value) {
        clearHighlights();
        highlightPivotRec(tree.root, value);
    }
    private void highlightPivotRec(AVLNode n, int value) {
        if (n == null) return;
        if (n.value == value) n.rotationHighlight = true;
        highlightPivotRec(n.left, value); highlightPivotRec(n.right, value);
    }

    // ── Paint ─────────────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawDotGrid(g2);

        if (tree == null || tree.root == null) {
            g2.setColor(new Color(70, 85, 120));
            g2.setFont(new Font("Consolas", Font.ITALIC, 15));
            String m = "Arbol vacio — inserte un valor para comenzar";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(m, (getWidth()-fm.stringWidth(m))/2, getHeight()/2);
            return;
        }

        drawEdgesRec(g2, tree.root);
        drawNodesRec(g2, tree.root);
    }

    private void drawDotGrid(Graphics2D g2) {
        g2.setColor(DOT_COL);
        for (int x = 0; x < getWidth(); x += 28)
            for (int y = 0; y < getHeight(); y += 28)
                g2.fillOval(x-1, y-1, 2, 2);
    }

    private void drawEdgesRec(Graphics2D g2, AVLNode n) {
        if (n == null) return;
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (n.left != null) {
            drawEdge(g2, n, n.left);
            drawEdgesRec(g2, n.left);
        }
        if (n.right != null) {
            drawEdge(g2, n, n.right);
            drawEdgesRec(g2, n.right);
        }
    }

    private void drawEdge(Graphics2D g2, AVLNode from, AVLNode to) {
        g2.setPaint(new GradientPaint(from.drawX, from.drawY, EDGE_COL,
                                      to.drawX,   to.drawY,   new Color(35,45,75)));
        g2.drawLine((int)from.drawX, (int)from.drawY, (int)to.drawX, (int)to.drawY);
    }

    private void drawNodesRec(Graphics2D g2, AVLNode n) {
        if (n == null) return;
        drawNodesRec(g2, n.left);
        drawNodesRec(g2, n.right);
        drawNode(g2, n);
    }

    private void drawNode(Graphics2D g2, AVLNode n) {
        int cx = (int) n.drawX;
        int cy = (int) n.drawY;
        int x  = cx - R, y = cy - R, d = R * 2;

        // ── Choose colour ─────────────────────────────────────────────────
        Color fill, rim;
        if (n.rotationHighlight) { fill = new Color(160, 30, 30); rim = HI_RED;    }
        else if (n.searchHighlight) { fill = new Color(0, 110, 55); rim = HI_GREEN; }
        else if (n.highlighted)     { fill = new Color(130, 95, 0); rim = HI_YELLOW; }
        else if (n.newNode)         { fill = new Color(0, 100, 120); rim = HI_CYAN;  }
        else if (n.highlightColor != null) { fill = n.highlightColor.darker(); rim = n.highlightColor; }
        else { fill = NODE_FILL; rim = NODE_RIM; }

        // Shadow
        g2.setColor(new Color(0,0,0,70));
        g2.fillOval(x+3, y+4, d, d);

        // Radial gradient fill
        try {
            g2.setPaint(new RadialGradientPaint(
                    new Point2D.Float(cx - R*0.3f, cy - R*0.3f), R,
                    new float[]{0f, 1f},
                    new Color[]{fill.brighter(), fill}));
        } catch (Exception e) { g2.setColor(fill); }
        g2.fillOval(x, y, d, d);

        // Rim
        g2.setColor(rim);
        g2.setStroke(new BasicStroke(2.4f));
        g2.drawOval(x, y, d, d);

        // Pulse ring for new nodes
        if (n.newNode) {
            int fc = pulseCounters.getOrDefault(n.value, 0);
            int extra = (25 - fc);   // grows from 0
            g2.setColor(new Color(0, 220, 220, Math.max(0, 140 - extra * 6)));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(x - extra, y - extra, d + extra*2, d + extra*2);
        }

        // ── Value label (centre) ─────────────────────────────────────────
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.setColor(TEXT_W);
        FontMetrics fm = g2.getFontMetrics();
        String vs = String.valueOf(n.value);
        g2.drawString(vs, cx - fm.stringWidth(vs)/2, cy + fm.getAscent()/2 - 2);

        // ── h label (top-left inside node) ──────────────────────────────
        g2.setFont(new Font("Consolas", Font.PLAIN, 8));
        g2.setColor(TEXT_DIM);
        g2.drawString("h:" + n.height, x + 3, y + 10);

        // ── FE label (top-right inside node) ────────────────────────────
        int fe = n.getBalanceFactor();
        g2.setColor(Math.abs(fe) > 1 ? FE_BAD : (fe != 0 ? FE_WARN : FE_OK));
        String fes = "FE:" + fe;
        g2.setFont(new Font("Consolas", Font.PLAIN, 8));
        g2.drawString(fes, x + d - g2.getFontMetrics().stringWidth(fes) - 3, y + 10);
    }
}
