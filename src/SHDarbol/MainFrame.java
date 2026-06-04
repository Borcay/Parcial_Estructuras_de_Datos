package avltree;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Main application window.
 *
 * Animation engine:
 *   A single javax.swing.Timer fires at ~60 fps ("render timer").
 *   Each tick it:
 *     1. Calls treePanel.recomputeLayout() to set targetX/targetY.
 *     2. Calls treePanel.tickAnimations() to interpolate drawX/drawY.
 *     3. Calls repaint().
 *
 *   A separate "step timer" fires at the user-selected speed and advances
 *   the step-by-step balancing queue.
 *
 * Step-by-step:
 *   After insert/delete in step mode, AVLTree.balanceSteps holds a list
 *   of BalanceStep objects.  The step controls (Next / Auto-play / Skip all)
 *   call applyNextBalanceStep() which pops one step, calls
 *   tree.applyBalanceStep(), highlights the pivot, logs the action.
 */
public class MainFrame extends JFrame {

    // ── Core objects ─────────────────────────────────────────────────────────
    private final AVLTree   tree      = new AVLTree();
    private TreePanel treePanel;
    private JTextArea logArea;
    private JTextField inputField;
    private JLabel statusLabel;
    private JSlider speedSlider;

    // ── Render timer (60 fps) ─────────────────────────────────────────────────
    private final javax.swing.Timer renderTimer = new javax.swing.Timer(16, e -> renderTick());

    // ── Step-by-step balancing state ──────────────────────────────────────────
    private boolean stepMode = false;
    private final List<AVLTree.BalanceStep> pendingSteps = new ArrayList<>();
    private int stepCursor = 0;
    // Auto-play timer for step mode
    private javax.swing.Timer autoStepTimer = null;

    // ── Search animation state ────────────────────────────────────────────────
    private List<AVLNode> searchPath  = new ArrayList<>();
    private int           searchCursor = 0;
    private javax.swing.Timer searchTimer = null;

    // ── Pause flag (shared between search and auto-step) ─────────────────────
    private boolean paused = false;

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color BG      = new Color(15, 17, 26);
    private static final Color PANEL   = new Color(20, 23, 38);
    private static final Color BLUE    = new Color(41, 98, 255);
    private static final Color BLUE2   = new Color(100, 149, 255);
    private static final Color GREEN   = new Color(0, 200, 100);
    private static final Color AMBER   = new Color(255, 193, 7);
    private static final Color RED     = new Color(255, 82, 82);
    private static final Color TEXT    = new Color(200, 210, 240);
    private static final Color DIM     = new Color(110, 125, 160);

    // Assigned sequence
    private static final int[] EXAMPLE = {70, 45, 100, 30, 60, 85, 110, 58, 59, 57, 56, 61};

    // ── Pause / resume button reference (so we can update its label) ──────────
    private JButton pauseBtn;

    // ══════════════════════════════════════════════════════════════════════════
    public MainFrame() {
        super("Arbol AVL — Visualizador Interactivo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1220, 760);
        setMinimumSize(new Dimension(960, 620));
        setLocationRelativeTo(null);
        buildUI();
        renderTimer.start();
        appendLog("Sistema listo. Secuencia ejemplo: " + Arrays.toString(EXAMPLE));
    }

    // ── UI construction ───────────────────────────────────────────────────────
    private void buildUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    // TOP BAR ─────────────────────────────────────────────────────────────────
    private JPanel buildTopBar() {
        JPanel bar = darkPanel(new BorderLayout());
        bar.setPreferredSize(new Dimension(0, 58));
        bar.setBorder(BorderFactory.createMatteBorder(0,0,1,0,new Color(35,45,75)));

        JLabel title = new JLabel("  \u29C6 AVL TREE VISUALIZER");
        title.setFont(new Font("Consolas", Font.BOLD, 18));
        title.setForeground(BLUE2);
        bar.add(title, BorderLayout.WEST);

        JPanel centre = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 11));
        centre.setOpaque(false);
        inputField = styledField(80);
        centre.add(inputField);
        centre.add(btn("+ Insertar", GREEN,  e -> insertAction()));
        centre.add(btn("- Eliminar", RED,    e -> deleteAction()));
        centre.add(btn("Buscar",     AMBER,  e -> searchAction()));
        inputField.addActionListener(e -> insertAction());
        bar.add(centre, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 14));
        right.setOpaque(false);
        right.add(smallBtn("PNG",      e -> ExportUtils.exportPNG(treePanel, this)));
        right.add(smallBtn("PDF/HTML", e -> ExportUtils.exportPDF(treePanel, tree, tree.getLog(), this)));
        right.add(smallBtn("Imprimir", e -> ExportUtils.print(treePanel, this)));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // CENTER SPLIT ────────────────────────────────────────────────────────────
    private JSplitPane buildCenter() {
        treePanel = new TreePanel(tree);
        JScrollPane treeScroll = new JScrollPane(treePanel);
        treeScroll.getViewport().setBackground(BG);
        treeScroll.setBorder(BorderFactory.createMatteBorder(0,0,0,1,new Color(35,45,75)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, buildRightPanel());
        split.setDividerLocation(830);
        split.setDividerSize(4);
        split.setBorder(null);
        return split;
    }

    // RIGHT PANEL (controls + log) ────────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PANEL);

        JPanel ctrl = new JPanel();
        ctrl.setBackground(PANEL);
        ctrl.setLayout(new BoxLayout(ctrl, BoxLayout.Y_AXIS));
        ctrl.setBorder(new EmptyBorder(12,12,6,12));

        // ── Tree operations ───────────────────────────────────────────────
        ctrl.add(section("OPERACIONES DE ARBOL"));
        ctrl.add(row(
            wideBtn("Cargar Ejemplo",  BLUE,                      e -> loadExampleAction()),
            wideBtn("Aleatorio",       new Color(120, 50, 200),   e -> randomAction()),
            wideBtn("Reiniciar",       new Color(170, 40, 40),    e -> resetAction())
        ));
        ctrl.add(strut(10));

        // ── Balancing mode ────────────────────────────────────────────────
        ctrl.add(section("MODO DE BALANCEO"));
        JToggleButton autoBtn = toggle("Automatico");
        JToggleButton stepBtn = toggle("Paso a Paso");
        ButtonGroup bg = new ButtonGroup(); bg.add(autoBtn); bg.add(stepBtn);
        autoBtn.setSelected(true);
        markToggle(autoBtn, true); markToggle(stepBtn, false);

        autoBtn.addActionListener(e -> {
            stepMode = false; tree.stepByStep = false;
            markToggle(autoBtn,true); markToggle(stepBtn,false);
            stopAutoStep(); pendingSteps.clear();
            status("Modo: Automatico"); appendLog("Modo: Balanceo Automatico");
        });
        stepBtn.addActionListener(e -> {
            stepMode = true; tree.stepByStep = true;
            markToggle(autoBtn,false); markToggle(stepBtn,true);
            status("Modo: Paso a Paso"); appendLog("Modo: Balanceo Paso a Paso");
        });
        ctrl.add(row(autoBtn, stepBtn));
        ctrl.add(strut(6));

        // ── Step controls ─────────────────────────────────────────────────
        ctrl.add(section("CONTROL DE PASOS"));
        pauseBtn = wideBtn("Pausa / Reanudar", new Color(100,90,20), e -> togglePause());
        ctrl.add(row(
            wideBtn("Siguiente",         new Color(0,130,80),    e -> nextStep()),
            pauseBtn,
            wideBtn("Saltar Todo",       new Color(50,50,140),   e -> skipAll())
        ));
        ctrl.add(strut(10));

        // ── Speed slider ──────────────────────────────────────────────────
        ctrl.add(section("VELOCIDAD DE ANIMACION"));
        JPanel slRow = new JPanel(new BorderLayout(6,0));
        slRow.setOpaque(false);
        slRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        speedSlider = new JSlider(1, 20, 7);
        speedSlider.setOpaque(false);
        speedSlider.setForeground(BLUE2);
        speedSlider.setMajorTickSpacing(5);
        speedSlider.setMinorTickSpacing(1);
        speedSlider.setPaintTicks(true);
        // Apply new speed instantly to any running timer
        speedSlider.addChangeListener(e -> {
            if (autoStepTimer != null && autoStepTimer.isRunning()) {
                autoStepTimer.setDelay(stepDelay());
                autoStepTimer.restart();
            }
            if (searchTimer != null && searchTimer.isRunning()) {
                searchTimer.setDelay(searchDelay());
                searchTimer.restart();
            }
        });
        JLabel slow = dimLabel("Lento"); JLabel fast = dimLabel("Rapido");
        slRow.add(slow, BorderLayout.WEST);
        slRow.add(speedSlider, BorderLayout.CENTER);
        slRow.add(fast, BorderLayout.EAST);
        ctrl.add(slRow);
        ctrl.add(strut(10));

        // ── Stats grid ────────────────────────────────────────────────────
        ctrl.add(section("ESTADISTICAS DEL ARBOL"));
        String[] sNames = {"Nodos","Altura","Raiz","Min","Max","FE Raiz"};
        JLabel[] sLabels = new JLabel[6];
        JPanel statsGrid = new JPanel(new GridLayout(2,3,6,6));
        statsGrid.setOpaque(false);
        statsGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        for (int i = 0; i < 6; i++) {
            JPanel box = new JPanel(new BorderLayout());
            box.setBackground(new Color(24, 27, 44));
            box.setBorder(BorderFactory.createLineBorder(new Color(38,48,80)));
            sLabels[i] = new JLabel("—", SwingConstants.CENTER);
            sLabels[i].setForeground(BLUE2);
            sLabels[i].setFont(new Font("Consolas", Font.BOLD, 14));
            JLabel nl = new JLabel(sNames[i], SwingConstants.CENTER);
            nl.setForeground(DIM); nl.setFont(new Font("Consolas",Font.PLAIN,9));
            box.add(sLabels[i], BorderLayout.CENTER);
            box.add(nl, BorderLayout.SOUTH);
            statsGrid.add(box);
        }
        ctrl.add(statsGrid);
        ctrl.add(strut(8));
        ctrl.add(Box.createVerticalGlue());

        // Update stats from render timer
        javax.swing.Timer st = new javax.swing.Timer(400, e -> updateStats(sLabels));
        st.start();

        // ── Log area ──────────────────────────────────────────────────────
        JPanel logSection = new JPanel(new BorderLayout());
        logSection.setBackground(PANEL);
        logSection.setBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(35,45,75)));

        JPanel logHdr = darkPanel(new BorderLayout());
        logHdr.setBorder(new EmptyBorder(5,10,5,8));
        JLabel lt = new JLabel("LOG DE OPERACIONES");
        lt.setFont(new Font("Consolas",Font.BOLD,10)); lt.setForeground(DIM);
        JButton clr = smallBtn("Limpiar", e -> { logArea.setText(""); tree.clearLog(); });
        logHdr.add(lt, BorderLayout.WEST); logHdr.add(clr, BorderLayout.EAST);
        logSection.add(logHdr, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(9,10,18));
        logArea.setForeground(new Color(130,210,130));
        logArea.setFont(new Font("Consolas",Font.PLAIN,11));
        logArea.setMargin(new Insets(6,8,6,8));
        JScrollPane ls = new JScrollPane(logArea);
        ls.setBorder(null);
        ls.setPreferredSize(new Dimension(0,200));
        logSection.add(ls, BorderLayout.CENTER);

        panel.add(ctrl,        BorderLayout.CENTER);
        panel.add(logSection,  BorderLayout.SOUTH);
        return panel;
    }

    // STATUS BAR ──────────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = darkPanel(new BorderLayout());
        bar.setPreferredSize(new Dimension(0,26));
        bar.setBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(35,45,75)));
        statusLabel = new JLabel("  Listo");
        statusLabel.setForeground(DIM);
        statusLabel.setFont(new Font("Consolas",Font.PLAIN,11));
        bar.add(statusLabel, BorderLayout.WEST);
        JLabel hint = new JLabel("ENTER = Insertar   |   Scroll = Zoom en el arbol   ");
        hint.setForeground(new Color(50,60,90));
        hint.setFont(new Font("Consolas",Font.PLAIN,10));
        bar.add(hint, BorderLayout.EAST);
        return bar;
    }

    // ── Render tick (60 fps) ──────────────────────────────────────────────────
    private void renderTick() {
        treePanel.recomputeLayout();
        treePanel.tickAnimations();
        treePanel.repaint();
    }

    // ── ACTION HANDLERS ───────────────────────────────────────────────────────

    private void insertAction() {
        String t = inputField.getText().trim();
        if (t.isEmpty() || t.equals("Valor")) { shake(); return; }
        int val;
        try { val = Integer.parseInt(t); } catch (NumberFormatException e) { shake(); return; }

        stopSearchAnim();
        stopAutoStep();
        pendingSteps.clear();

        tree.insert(val);
        treePanel.markNew(val);
        flushLog();

        if (stepMode && !tree.balanceSteps.isEmpty()) {
            pendingSteps.addAll(tree.balanceSteps);
            stepCursor = 0;
            status("Insertar " + val + " — " + pendingSteps.size() + " paso(s) de balanceo pendientes");
        } else {
            treePanel.clearHighlights();
            status("Insertado: " + val);
        }
        inputField.setText("");
    }

    private void deleteAction() {
        String t = inputField.getText().trim();
        if (t.isEmpty() || t.equals("Valor")) { shake(); return; }
        int val;
        try { val = Integer.parseInt(t); } catch (NumberFormatException e) { shake(); return; }

        stopSearchAnim(); stopAutoStep(); pendingSteps.clear();
        tree.delete(val);
        treePanel.clearHighlights();
        flushLog();

        if (stepMode && !tree.balanceSteps.isEmpty()) {
            pendingSteps.addAll(tree.balanceSteps);
            stepCursor = 0;
            status("Eliminar " + val + " — " + pendingSteps.size() + " paso(s) pendientes");
        } else {
            status("Eliminado: " + val);
        }
        inputField.setText("");
    }

    private void searchAction() {
        String t = inputField.getText().trim();
        if (t.isEmpty() || t.equals("Valor")) { shake(); return; }
        int val;
        try { val = Integer.parseInt(t); } catch (NumberFormatException e) { shake(); return; }

        stopSearchAnim(); stopAutoStep();
        searchPath   = tree.search(val);
        searchCursor = 0;
        flushLog();
        treePanel.clearHighlights();
        startSearchAnim(val);
        inputField.setText("");
    }

    private void loadExampleAction() {
        stopAll();
        tree.reset(); flushLog();
        // Load using auto mode regardless of current mode
        boolean was = tree.stepByStep;
        tree.stepByStep = false;
        appendLog("Cargando secuencia ejemplo: " + Arrays.toString(EXAMPLE));
        for (int v : EXAMPLE) tree.insert(v);
        tree.stepByStep = was;
        treePanel.clearHighlights();
        flushLog();
        status("Secuencia ejemplo cargada (" + EXAMPLE.length + " nodos)");
    }

    private void randomAction() {
        stopAll();
        int n = 5 + new Random().nextInt(16);
        tree.reset(); flushLog();
        boolean was = tree.stepByStep; tree.stepByStep = false;
        appendLog("Generando " + n + " nodos aleatorios...");
        Set<Integer> used = new HashSet<>();
        Random rnd = new Random();
        while (used.size() < n) {
            int v = rnd.nextInt(99) + 1;
            if (!used.contains(v)) { used.add(v); tree.insert(v); }
        }
        tree.stepByStep = was;
        treePanel.clearHighlights();
        flushLog();
        status("Arbol aleatorio: " + n + " nodos");
    }

    private void resetAction() {
        stopAll();
        tree.reset();
        treePanel.clearHighlights();
        flushLog();
        status("Arbol reiniciado");
    }

    // ── Step controls ─────────────────────────────────────────────────────────

    private void nextStep() {
        if (pendingSteps.isEmpty()) { status("No hay pasos de balanceo pendientes"); return; }
        if (stepCursor >= pendingSteps.size()) { status("Todos los pasos aplicados"); return; }
        applyOneStep();
    }

    private void skipAll() {
        if (pendingSteps.isEmpty()) return;
        stopAutoStep();
        while (stepCursor < pendingSteps.size()) applyOneStep();
        treePanel.clearHighlights();
        status("Todos los pasos aplicados");
    }

    private void togglePause() {
        if (autoStepTimer != null && autoStepTimer.isRunning()) {
            // Was auto-playing → pause
            autoStepTimer.stop();
            paused = true;
            status("Pausado");
            pauseBtn.setText("Reanudar");
        } else if (paused && !pendingSteps.isEmpty() && stepCursor < pendingSteps.size()) {
            // Was paused → resume auto-play
            paused = false;
            pauseBtn.setText("Pausa");
            startAutoStep();
        } else {
            // No auto-step running: start auto-play from current position
            if (!pendingSteps.isEmpty() && stepCursor < pendingSteps.size()) {
                paused = false;
                pauseBtn.setText("Pausa");
                startAutoStep();
            } else {
                status("No hay pasos pendientes");
            }
        }
    }

    private void applyOneStep() {
        AVLTree.BalanceStep step = pendingSteps.get(stepCursor++);
        tree.applyBalanceStep(step);
        treePanel.highlightPivot(step.pivotValue);
        flushLog();
        status("[" + stepCursor + "/" + pendingSteps.size() + "] " + step.description);
    }

    private void startAutoStep() {
        stopAutoStep();
        autoStepTimer = new javax.swing.Timer(stepDelay(), null);
        autoStepTimer.addActionListener(e -> {
            if (paused) return;
            if (stepCursor < pendingSteps.size()) {
                applyOneStep();
                ((javax.swing.Timer) e.getSource()).setDelay(stepDelay());
            } else {
                stopAutoStep();
                treePanel.clearHighlights();
                pauseBtn.setText("Pausa");
                status("Balanceo completo");
            }
        });
        autoStepTimer.start();
    }

    private void stopAutoStep() {
        if (autoStepTimer != null) { autoStepTimer.stop(); autoStepTimer = null; }
    }

    private int stepDelay() {
        // slider 1(lento)..20(rapido) → 4000ms..50ms exponential
        double t = (speedSlider.getValue() - 1) / 19.0;
        return (int)(4000 * Math.pow(50.0 / 4000.0, t));
    }

    // ── Search animation ──────────────────────────────────────────────────────

    private void startSearchAnim(int target) {
        if (searchPath.isEmpty()) { status("Arbol vacio o valor no encontrado"); return; }
        status("Buscando " + target + "...");
        searchTimer = new javax.swing.Timer(searchDelay(), null);
        searchTimer.addActionListener(e -> {
            if (paused) return;
            if (searchCursor < searchPath.size()) {
                AVLNode node = searchPath.get(searchCursor++);
                node.searchHighlight = true;
                ((javax.swing.Timer)e.getSource()).setDelay(searchDelay());
                status("Visitando " + node.value + " (h=" + node.height + ", FE=" + node.getBalanceFactor() + ")");
            } else {
                ((javax.swing.Timer)e.getSource()).stop();
                searchTimer = null;
                AVLNode last = searchPath.get(searchPath.size()-1);
                boolean found = last.value == target;
                last.searchHighlight = false;
                last.highlighted     = found;
                if (!found) last.rotationHighlight = true;
                status(found ? "ENCONTRADO: " + target + " (profundidad " + (searchPath.size()-1) + ")"
                             : "NO encontrado: " + target);
            }
        });
        searchTimer.start();
    }

    private void stopSearchAnim() {
        if (searchTimer != null) { searchTimer.stop(); searchTimer = null; }
        treePanel.clearHighlights();
    }

    private int searchDelay() {
        // slider 1(lento)..20(rapido) → 5000ms..60ms exponential
        double t = (speedSlider.getValue() - 1) / 19.0;
        return (int)(5000 * Math.pow(60.0 / 5000.0, t));
    }

    private void stopAll() {
        stopSearchAnim(); stopAutoStep();
        pendingSteps.clear(); stepCursor = 0;
        searchPath.clear();  searchCursor = 0;
        paused = false;
        if (pauseBtn != null) pauseBtn.setText("Pausa");
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    private void updateStats(JLabel[] l) {
        List<AVLNode> io = tree.inorder();
        l[0].setText(String.valueOf(tree.size()));
        l[1].setText(String.valueOf(tree.totalHeight()));
        l[2].setText(tree.root != null ? String.valueOf(tree.root.value) : "—");
        l[3].setText(io.isEmpty() ? "—" : String.valueOf(io.get(0).value));
        l[4].setText(io.isEmpty() ? "—" : String.valueOf(io.get(io.size()-1).value));
        l[5].setText(tree.root != null ? String.valueOf(tree.root.getBalanceFactor()) : "—");
    }

    // ── Log helpers ───────────────────────────────────────────────────────────
    private void appendLog(String msg) { tree.addLog(msg); flushLog(); }
    private void flushLog() {
        List<String> entries = tree.getLog();
        StringBuilder sb = new StringBuilder();
        for (String e : entries) sb.append(e).append("\n");
        logArea.setText(sb.toString());
        logArea.setCaretPosition(Math.max(0, logArea.getDocument().getLength()-1));
    }
    private void status(String msg) { statusLabel.setText("  " + msg); }

    // ── UI factory helpers ────────────────────────────────────────────────────
    private JPanel darkPanel(LayoutManager lm) {
        JPanel p = new JPanel(lm); p.setBackground(new Color(11,13,21)); return p;
    }
    private JLabel dimLabel(String t) {
        JLabel l = new JLabel(t); l.setForeground(DIM);
        l.setFont(new Font("Consolas",Font.PLAIN,10)); return l;
    }
    private JPanel row(Component... cs) {
        JPanel p = new JPanel(new GridLayout(1, cs.length, 6,0));
        p.setOpaque(false); p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        for (Component c : cs) p.add(c);
        return p;
    }
    private Component strut(int h) { return Box.createVerticalStrut(h); }
    private JLabel section(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Consolas",Font.BOLD,9));
        l.setForeground(new Color(70,90,140));
        l.setAlignmentX(0);
        l.setBorder(new EmptyBorder(0,0,3,0));
        return l;
    }

    private JTextField styledField(int w) {
        JTextField f = new JTextField("Valor", 7);
        f.setPreferredSize(new Dimension(w, 30));
        f.setBackground(new Color(24,27,44));
        f.setForeground(TEXT);
        f.setCaretColor(BLUE2);
        f.setFont(new Font("Consolas",Font.PLAIN,14));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45,58,100)),
                new EmptyBorder(3,8,3,8)));
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { if (f.getText().equals("Valor")) f.setText(""); }
            public void focusLost (FocusEvent e) { if (f.getText().isEmpty()) f.setText("Valor"); }
        });
        return f;
    }

    private JButton btn(String txt, Color c, ActionListener a) {
        JButton b = new JButton(txt);
        b.setFont(new Font("Consolas",Font.BOLD,12));
        b.setBackground(c.darker().darker());
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c.darker()),
                new EmptyBorder(5,14,5,14)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(a);
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e){ b.setBackground(c.darker()); }
            public void mouseExited (MouseEvent e){ b.setBackground(c.darker().darker()); }
        });
        return b;
    }

    private JButton wideBtn(String txt, Color c, ActionListener a) {
        JButton b = btn(txt, c, a);
        b.setFont(new Font("Consolas",Font.BOLD,10));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c.darker()),
                new EmptyBorder(5,5,5,5)));
        return b;
    }

    private JButton smallBtn(String txt, ActionListener a) {
        JButton b = new JButton(txt);
        b.setFont(new Font("Consolas",Font.PLAIN,10));
        b.setBackground(new Color(28,32,52));
        b.setForeground(DIM);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(38,48,78)),
                new EmptyBorder(2,8,2,8)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(a);
        return b;
    }

    private JToggleButton toggle(String txt) {
        JToggleButton b = new JToggleButton(txt);
        b.setFont(new Font("Consolas",Font.BOLD,11));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void markToggle(JToggleButton b, boolean on) {
        b.setBackground(on ? BLUE.darker()             : new Color(28,32,52));
        b.setForeground(on ? Color.WHITE               : DIM);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(on ? BLUE : new Color(38,48,78)),
                new EmptyBorder(5,5,5,5)));
    }

    private void shake() {
        Color orig = inputField.getBackground();
        inputField.setBackground(new Color(90,20,20));
        new javax.swing.Timer(300, e -> { inputField.setBackground(orig);
            ((javax.swing.Timer)e.getSource()).stop(); }).start();
    }

    // ── Entry point ───────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
