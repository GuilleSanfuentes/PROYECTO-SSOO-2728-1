package avilaos.vista.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/** Barra de progreso propia con color y texto centrado; no depende del tema del sistema. */
public class BarraColor extends JComponent {

    private double fraccion;
    private Color color = ColoresEstado.GRIS;
    private Color fondo = Color.WHITE;
    private String texto = "";

    /** Crea una barra vacía. */
    public BarraColor() {
        setOpaque(false);
        setPreferredSize(new Dimension(120, 16));
    }

    /** Fija fracción (0 a 1), color de relleno, texto y color de fondo de la celda. */
    public void setDatos(double fraccion, Color color, String texto, Color fondo) {
        this.fraccion = Math.max(0, Math.min(1, fraccion));
        this.color = color;
        this.texto = texto;
        this.fondo = fondo;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(fondo);
        g2.fillRect(0, 0, getWidth(), getHeight());
        int x = 2;
        int y = 2;
        int ancho = getWidth() - 4;
        int alto = getHeight() - 4;
        g2.setColor(ColoresEstado.PISTA);
        g2.fillRoundRect(x, y, ancho, alto, 8, 8);
        int relleno = (int) Math.round(ancho * fraccion);
        if (relleno > 0) {
            g2.setColor(color);
            g2.fillRoundRect(x, y, Math.max(relleno, 8), alto, 8, 8);
        }
        g2.setFont(getFont().deriveFont(Math.max(9f, alto - 4f)));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(ColoresEstado.TEXTO);
        g2.drawString(texto, x + (ancho - fm.stringWidth(texto)) / 2, y + (alto + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
    }
}
