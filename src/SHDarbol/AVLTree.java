package avltree;

import java.util.*;

/**
 * AVL Tree with two balancing modes:
 *  - AUTO: insert/delete and immediately rebalance (classic)
 *  - STEP: insert/delete without rebalancing; produce a list of
 *          BalanceStep objects that the GUI replays one at a time.
 *
 * HEIGHT CONVENTION (standard):
 *   A leaf node has height = 1.
 *   The root of a tree with N levels has height = N.
 *   Factor de Equilibrio (FE) = height(left) - height(right).
 *   A balanced AVL node satisfies |FE| <= 1.
 */
public class AVLTree {

    public AVLNode root;
    private final List<String> log = new ArrayList<>();

    // ── Balancing mode ──────────────────────────────────────────────────────
    /** When true, insert/delete do NOT rebalance; call applyNextStep() manually. */
    public boolean stepByStep = false;

    /**
     * Ordered list of balance steps produced by the last insert/delete.
     * Each step carries a snapshot of the tree AFTER that step so the GUI
     * can render it directly.
     */
    public final List<BalanceStep> balanceSteps = new ArrayList<>();

    // ── BalanceStep inner class ─────────────────────────────────────────────
    public static class BalanceStep {
        public enum Kind { INSERT_RAW, ROTATE_LL, ROTATE_RR, ROTATE_LR, ROTATE_RL, BALANCED }
        public final Kind   kind;
        public final String description;
        public final int    pivotValue;   // node being rotated around (for highlight)

        public BalanceStep(Kind kind, int pivotValue, String description) {
            this.kind        = kind;
            this.pivotValue  = pivotValue;
            this.description = description;
        }
    }

    // ── height helpers ──────────────────────────────────────────────────────
    private int height(AVLNode n)          { return n == null ? 0 : n.height; }
    private void recalcHeight(AVLNode n)   { if (n != null) n.height = 1 + Math.max(height(n.left), height(n.right)); }

    // ── Rotations (always update heights internally) ─────────────────────────
    private AVLNode rotateRight(AVLNode y) {
        AVLNode x  = y.left;
        AVLNode T2 = x.right;
        x.right = y;
        y.left  = T2;
        recalcHeight(y);
        recalcHeight(x);
        addLog("  Rotacion Derecha (LL): nodo " + y.value + " baja, sube " + x.value);
        return x;
    }

    private AVLNode rotateLeft(AVLNode x) {
        AVLNode y  = x.right;
        AVLNode T2 = y.left;
        y.left  = x;
        x.right = T2;
        recalcHeight(x);
        recalcHeight(y);
        addLog("  Rotacion Izquierda (RR): nodo " + x.value + " baja, sube " + y.value);
        return y;
    }

    // ── Balance a single node (used in AUTO mode and in step replay) ─────────
    public AVLNode balanceNode(AVLNode node) {
        recalcHeight(node);
        int bf = node.getBalanceFactor();

        if (bf > 1) {
            if (node.left != null && node.left.getBalanceFactor() < 0) {
                addLog("  Caso LR en nodo " + node.value);
                node.left = rotateLeft(node.left);
            } else {
                addLog("  Caso LL en nodo " + node.value);
            }
            return rotateRight(node);
        }
        if (bf < -1) {
            if (node.right != null && node.right.getBalanceFactor() > 0) {
                addLog("  Caso RL en nodo " + node.value);
                node.right = rotateRight(node.right);
            } else {
                addLog("  Caso RR en nodo " + node.value);
            }
            return rotateLeft(node);
        }
        return node;
    }

    // ── INSERT ───────────────────────────────────────────────────────────────
    public void insert(int value) {
        addLog("Insertar: " + value);
        balanceSteps.clear();

        if (stepByStep) {
            // 1) insert WITHOUT balancing
            root = insertNoBalance(root, value);
            recalcAllHeights(root);
            balanceSteps.add(new BalanceStep(BalanceStep.Kind.INSERT_RAW, value,
                    "Nodo " + value + " insertado (sin balancear aun)"));
            // 2) collect all needed rotations as steps
            collectBalanceSteps(root);
            addLog("  " + balanceSteps.size() + " paso(s) de balanceo pendientes");
        } else {
            root = insertAuto(root, value);
            addLog("Arbol balanceado despues de insertar " + value);
        }
    }

    /** Insert without any rebalancing. */
    private AVLNode insertNoBalance(AVLNode node, int value) {
        if (node == null) return new AVLNode(value);
        if (value < node.value) {
            addLog("  " + value + " < " + node.value + " -> ir izquierda");
            node.left  = insertNoBalance(node.left,  value);
        } else if (value > node.value) {
            addLog("  " + value + " > " + node.value + " -> ir derecha");
            node.right = insertNoBalance(node.right, value);
        } else {
            addLog("  Valor " + value + " ya existe, ignorando");
        }
        return node;
    }

    /** Recalculate heights bottom-up (used after unbalanced insert). */
    private void recalcAllHeights(AVLNode n) {
        if (n == null) return;
        recalcAllHeights(n.left);
        recalcAllHeights(n.right);
        recalcHeight(n);
    }

    /** Walk the tree and record which nodes need rotation and in what order. */
    private void collectBalanceSteps(AVLNode node) {
        if (node == null) return;
        collectBalanceSteps(node.left);
        collectBalanceSteps(node.right);
        recalcHeight(node);
        int bf = node.getBalanceFactor();
        if (bf > 1) {
            if (node.left != null && node.left.getBalanceFactor() < 0) {
                balanceSteps.add(new BalanceStep(BalanceStep.Kind.ROTATE_LR, node.value,
                        "Rotacion LR (doble) en nodo " + node.value + " (FE=" + bf + ")"));
            } else {
                balanceSteps.add(new BalanceStep(BalanceStep.Kind.ROTATE_LL, node.value,
                        "Rotacion LL (simple derecha) en nodo " + node.value + " (FE=" + bf + ")"));
            }
        } else if (bf < -1) {
            if (node.right != null && node.right.getBalanceFactor() > 0) {
                balanceSteps.add(new BalanceStep(BalanceStep.Kind.ROTATE_RL, node.value,
                        "Rotacion RL (doble) en nodo " + node.value + " (FE=" + bf + ")"));
            } else {
                balanceSteps.add(new BalanceStep(BalanceStep.Kind.ROTATE_RR, node.value,
                        "Rotacion RR (simple izquierda) en nodo " + node.value + " (FE=" + bf + ")"));
            }
        }
    }

    /** Classic AVL insert with rebalancing. */
    private AVLNode insertAuto(AVLNode node, int value) {
        if (node == null) return new AVLNode(value);
        if (value < node.value) {
            addLog("  " + value + " < " + node.value + " -> ir izquierda");
            node.left  = insertAuto(node.left,  value);
        } else if (value > node.value) {
            addLog("  " + value + " > " + node.value + " -> ir derecha");
            node.right = insertAuto(node.right, value);
        } else {
            addLog("  Valor " + value + " ya existe, ignorando");
            return node;
        }
        return balanceNode(node);
    }

    // ── Apply one balance step (called by GUI in step mode) ──────────────────
    /**
     * Applies the rotation described by {@code step} to the live tree.
     * Returns the pivot node value so the GUI can highlight it.
     */
    public void applyBalanceStep(BalanceStep step) {
        switch (step.kind) {
            case ROTATE_LL: root = applyLL(root, step.pivotValue); break;
            case ROTATE_RR: root = applyRR(root, step.pivotValue); break;
            case ROTATE_LR: root = applyLR(root, step.pivotValue); break;
            case ROTATE_RL: root = applyRL(root, step.pivotValue); break;
            default: break;
        }
        recalcAllHeights(root);
        addLog("  [Paso aplicado] " + step.description);
    }

    private AVLNode applyLL(AVLNode node, int pivot) {
        if (node == null) return null;
        if (node.value == pivot) return rotateRight(node);
        node.left  = applyLL(node.left,  pivot);
        node.right = applyLL(node.right, pivot);
        recalcHeight(node);
        return node;
    }
    private AVLNode applyRR(AVLNode node, int pivot) {
        if (node == null) return null;
        if (node.value == pivot) return rotateLeft(node);
        node.left  = applyRR(node.left,  pivot);
        node.right = applyRR(node.right, pivot);
        recalcHeight(node);
        return node;
    }
    private AVLNode applyLR(AVLNode node, int pivot) {
        if (node == null) return null;
        if (node.value == pivot) {
            node.left = rotateLeft(node.left);
            return rotateRight(node);
        }
        node.left  = applyLR(node.left,  pivot);
        node.right = applyLR(node.right, pivot);
        recalcHeight(node);
        return node;
    }
    private AVLNode applyRL(AVLNode node, int pivot) {
        if (node == null) return null;
        if (node.value == pivot) {
            node.right = rotateRight(node.right);
            return rotateLeft(node);
        }
        node.left  = applyRL(node.left,  pivot);
        node.right = applyRL(node.right, pivot);
        recalcHeight(node);
        return node;
    }

    // ── DELETE ───────────────────────────────────────────────────────────────
    public void delete(int value) {
        addLog("Eliminar: " + value);
        balanceSteps.clear();
        if (stepByStep) {
            root = deleteNoBalance(root, value);
            recalcAllHeights(root);
            collectBalanceSteps(root);
            addLog("  " + balanceSteps.size() + " paso(s) de balanceo pendientes");
        } else {
            root = deleteAuto(root, value);
            addLog("Arbol rebalanceado despues de eliminar " + value);
        }
    }

    private AVLNode deleteNoBalance(AVLNode node, int value) {
        if (node == null) { addLog("  Nodo " + value + " no encontrado"); return null; }
        if (value < node.value)       node.left  = deleteNoBalance(node.left,  value);
        else if (value > node.value)  node.right = deleteNoBalance(node.right, value);
        else {
            addLog("  Nodo " + value + " encontrado, eliminando");
            if (node.left == null || node.right == null)
                return node.left != null ? node.left : node.right;
            AVLNode min = findMin(node.right);
            addLog("  Reemplazar con sucesor: " + min.value);
            node.value = min.value;
            node.right = deleteNoBalance(node.right, min.value);
        }
        return node;
    }

    private AVLNode deleteAuto(AVLNode node, int value) {
        if (node == null) { addLog("  Nodo " + value + " no encontrado"); return null; }
        if (value < node.value)       node.left  = deleteAuto(node.left,  value);
        else if (value > node.value)  node.right = deleteAuto(node.right, value);
        else {
            addLog("  Nodo " + value + " encontrado, eliminando");
            if (node.left == null || node.right == null)
                return node.left != null ? node.left : node.right;
            AVLNode min = findMin(node.right);
            addLog("  Reemplazar con sucesor: " + min.value);
            node.value = min.value;
            node.right = deleteAuto(node.right, min.value);
        }
        return balanceNode(node);
    }

    private AVLNode findMin(AVLNode n) { while (n.left != null) n = n.left; return n; }

    // ── SEARCH ───────────────────────────────────────────────────────────────
    public List<AVLNode> search(int value) {
        List<AVLNode> path = new ArrayList<>();
        addLog("Buscar: " + value);
        searchRec(root, value, path);
        if (!path.isEmpty() && path.get(path.size()-1).value == value)
            addLog("  ENCONTRADO en profundidad " + (path.size()-1));
        else
            addLog("  NO encontrado");
        return path;
    }

    private boolean searchRec(AVLNode n, int value, List<AVLNode> path) {
        if (n == null) return false;
        path.add(n);
        addLog("  Visitar " + n.value + " (h=" + n.height + ", FE=" + n.getBalanceFactor() + ")");
        if (value == n.value) return true;
        return value < n.value ? searchRec(n.left, value, path) : searchRec(n.right, value, path);
    }

    // ── UTILITIES ────────────────────────────────────────────────────────────
    public void reset() { root = null; log.clear(); balanceSteps.clear(); addLog("Arbol reiniciado"); }
    public int  size()  { return sizeRec(root); }
    private int sizeRec(AVLNode n) { return n==null?0:1+sizeRec(n.left)+sizeRec(n.right); }
    public int  totalHeight() { return height(root); }

    public List<AVLNode> inorder() {
        List<AVLNode> l = new ArrayList<>(); inorderRec(root, l); return l;
    }
    private void inorderRec(AVLNode n, List<AVLNode> l) {
        if (n==null) return; inorderRec(n.left,l); l.add(n); inorderRec(n.right,l);
    }

    public void          addLog(String msg) { log.add(msg); }
    public List<String>  getLog()           { return log; }
    public void          clearLog()         { log.clear(); }
}
