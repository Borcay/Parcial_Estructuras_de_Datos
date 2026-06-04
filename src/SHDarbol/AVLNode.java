package avltree;

import java.awt.Color;

public class AVLNode {
    public int value;
    public int height;
    public AVLNode left, right;

    // Logical position computed by layout
    public int x, y;

    // Animated display position (interpolated)
    public float drawX, drawY;
    public float targetX, targetY;
    public boolean positionInitialized = false;

    // Highlight states
    public boolean highlighted       = false;   // generic yellow
    public boolean searchHighlight   = false;   // green  – search path
    public boolean rotationHighlight = false;   // red    – rotation pivot
    public boolean newNode           = false;   // pulse  – just inserted
    public Color   highlightColor    = null;

    public AVLNode(int value) {
        this.value  = value;
        this.height = 1;
    }

    public int getBalanceFactor() {
        int l = (left  != null) ? left.height  : 0;
        int r = (right != null) ? right.height : 0;
        return l - r;
    }

    @Override
    public String toString() {
        return "Node(" + value + ", h=" + height + ", FE=" + getBalanceFactor() + ")";
    }
}
