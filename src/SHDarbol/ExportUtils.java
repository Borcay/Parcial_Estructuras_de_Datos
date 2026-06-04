package avltree;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ExportUtils {

    // ── PNG ──────────────────────────────────────────────────────────────────
    public static void exportPNG(Component panel, JFrame parent) {
        // Snapshot BEFORE showing dialog (dialog would repaint panel)
        BufferedImage img = snapshot(panel);

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guardar imagen PNG");
        fc.setSelectedFile(new File("arbol_avl.png"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File file = ensureExt(fc.getSelectedFile(), ".png");
        try {
            ImageIO.write(img, "png", file);
            JOptionPane.showMessageDialog(parent,
                    "Imagen guardada:\n" + file.getAbsolutePath(),
                    "PNG exportado", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Error: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── HTML report (printable as PDF from browser) ───────────────────────────
    public static void exportPDF(Component panel, AVLTree tree,
                                 List<String> log, JFrame parent) {
        BufferedImage img = snapshot(panel);

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guardar reporte HTML (abrir en Chrome -> Imprimir -> PDF)");
        fc.setSelectedFile(new File("reporte_avl.html"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File htmlFile = ensureExt(fc.getSelectedFile(), ".html");
        File imgFile  = new File(htmlFile.getParent(), "avl_tree_snapshot.png");

        try {
            ImageIO.write(img, "png", imgFile);
            writeHtml(htmlFile, imgFile.getName(), tree, log);
            JOptionPane.showMessageDialog(parent,
                    "Reporte guardado:\n" + htmlFile.getAbsolutePath()
                    + "\n\nAbrelo en tu navegador y usa Ctrl+P -> Guardar como PDF.",
                    "Reporte exportado", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Error: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Print ─────────────────────────────────────────────────────────────────
    public static void print(Component panel, JFrame parent) {
        BufferedImage img = snapshot(panel);
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Arbol AVL");
        job.setPrintable((g, pf, pageIndex) -> {
            if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) g;
            g2.translate(pf.getImageableX(), pf.getImageableY());
            double scale = Math.min(
                    pf.getImageableWidth()  / img.getWidth(),
                    pf.getImageableHeight() / img.getHeight());
            g2.scale(scale, scale);
            g2.drawImage(img, 0, 0, null);
            return Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) {
            try { job.print(); }
            catch (PrinterException e) {
                JOptionPane.showMessageDialog(parent, "Error de impresion: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static BufferedImage snapshot(Component c) {
        int w = Math.max(c.getWidth(),  10);
        int h = Math.max(c.getHeight(), 10);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        c.paint(g2);
        g2.dispose();
        return img;
    }

    private static File ensureExt(File f, String ext) {
        return f.getName().endsWith(ext) ? f : new File(f.getAbsolutePath() + ext);
    }

    private static void writeHtml(File out, String imgName,
                                   AVLTree tree, List<String> log) throws IOException {
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        List<AVLNode> inorder = tree.inorder();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<title>Reporte Arbol AVL</title><style>");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:Consolas,monospace;background:#0f111a;color:#dde4ff;padding:32px;line-height:1.6}");
        sb.append("h1{color:#6495ff;border-bottom:2px solid #2962ff;padding-bottom:8px;margin-bottom:16px;font-size:1.5em}");
        sb.append("h2{color:#99b8ff;margin:24px 0 10px;font-size:1.1em}");
        sb.append(".stats{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:8px}");
        sb.append(".stat{background:#141828;border:1px solid #2962ff;border-radius:8px;padding:10px 20px;text-align:center}");
        sb.append(".stat .v{font-size:2em;font-weight:bold;color:#6495ff}");
        sb.append(".stat .l{font-size:.75em;color:#7888aa}");
        sb.append("img{max-width:100%;border:2px solid #2962ff;border-radius:8px;margin:8px 0}");
        sb.append(".log{background:#08090f;padding:12px;border-radius:6px;max-height:360px;overflow-y:auto;font-size:.82em}");
        sb.append(".le{padding:2px 6px;border-left:3px solid #1e40af;margin:2px 0}");
        sb.append("@media print{body{background:#fff;color:#000}.stat{border-color:#333}.stat .v{color:#2962ff}.le{border-color:#333}}");
        sb.append("</style></head><body>");

        sb.append("<h1>Reporte — Arbol AVL</h1>");
        sb.append("<p style='color:#7888aa;margin-bottom:16px'>Generado: ").append(date).append("</p>");

        // Stats
        sb.append("<h2>Estadisticas</h2><div class='stats'>");
        stat(sb, tree.size(), "Nodos");
        stat(sb, tree.totalHeight(), "Altura");
        stat(sb, tree.root != null ? tree.root.value : 0, "Raiz");
        stat(sb, inorder.isEmpty() ? 0 : inorder.get(0).value, "Min");
        stat(sb, inorder.isEmpty() ? 0 : inorder.get(inorder.size()-1).value, "Max");
        sb.append("</div>");

        // Inorder
        sb.append("<h2>Recorrido Inorden</h2><p>");
        for (int i = 0; i < inorder.size(); i++) {
            sb.append(inorder.get(i).value);
            if (i < inorder.size()-1) sb.append(" &rarr; ");
        }
        sb.append("</p>");

        // Image
        sb.append("<h2>Visualizacion del Arbol</h2>");
        sb.append("<img src='").append(imgName).append("' alt='Arbol AVL'/>");

        // Log
        sb.append("<h2>Log de Operaciones (").append(log.size()).append(" entradas)</h2>");
        sb.append("<div class='log'>");
        for (String e : log)
            sb.append("<div class='le'>").append(e.replace("<","&lt;").replace(">","&gt;")).append("</div>");
        sb.append("</div></body></html>");

        try (FileWriter fw = new FileWriter(out)) { fw.write(sb.toString()); }
    }

    private static void stat(StringBuilder sb, int val, String label) {
        sb.append("<div class='stat'><div class='v'>").append(val)
          .append("</div><div class='l'>").append(label).append("</div></div>");
    }
}
