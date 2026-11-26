package avilaos.vista.gui;

import avilaos.enums.DireccionCabezal;
import avilaos.hardware.Bloque;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/** Dibujo del disco: un cuadro por bloque con el color de su archivo, el swap aparte, el cabezal y su dirección. */
public class MapaDisco extends JComponent {

    static final int COLUMNAS = 20;
    private static final int CELDA = 24;
    private static final int MARGEN = 8;
    private static final int SEPARACION_SWAP = 22;
    private static final Color BORDE = new Color(208, 213, 220);
    private static final Color CABEZAL = new Color(220, 38, 38);
    private static final Color ACTUAL = new Color(33, 37, 41);

    private FotoDisco foto;

    /** Crea el mapa vacío; se dibuja al recibir la primera foto. */
    public MapaDisco() {
        setToolTipText("");
        setOpaque(true);
        setBackground(Color.WHITE);
    }

    /** Muestra la foto del disco. */
    public void mostrar(FotoDisco nueva) {
        boolean cambioTamano = foto == null || nueva == null || foto.total() != nueva.total();
        foto = nueva;
        if (cambioTamano) {
            revalidate();
        }
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        if (foto == null) {
            return new Dimension(COLUMNAS * CELDA + 2 * MARGEN, 200);
        }
        int filasArchivos = filas(foto.bloquesArchivos());
        int filasSwap = filas(foto.total() - foto.bloquesArchivos());
        return new Dimension(COLUMNAS * CELDA + 2 * MARGEN,
                2 * MARGEN + (filasArchivos + filasSwap) * CELDA + (filasSwap > 0 ? SEPARACION_SWAP : 0) + 12);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (foto == null) {
            g2.dispose();
            return;
        }
        Font numero = getFont().deriveFont(9f);
        for (int i = 0; i < foto.total(); i++) {
            int x = x(i);
            int y = y(i);
            Color fondo = colorDe(i);
            g2.setColor(fondo);
            g2.fillRect(x + 1, y + 1, CELDA - 2, CELDA - 2);
            g2.setColor(BORDE);
            g2.drawRect(x + 1, y + 1, CELDA - 2, CELDA - 2);
            g2.setColor(ColoresArchivo.textoSobre(fondo));
            g2.setFont(numero);
            String texto = String.valueOf(i);
            int ancho = g2.getFontMetrics().stringWidth(texto);
            g2.drawString(texto, x + (CELDA - ancho) / 2, y + CELDA / 2 + 3);
            if (foto.esDeLaActual(i)) {
                g2.setColor(ACTUAL);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(x + 2, y + 2, CELDA - 4, CELDA - 4);
                g2.setStroke(new BasicStroke(1f));
            }
        }
        if (foto.total() > foto.bloquesArchivos()) {
            int ySwap = y(foto.bloquesArchivos()) - SEPARACION_SWAP / 2 - 2;
            g2.setColor(ColoresArchivo.SWAP_OCUPADO);
            g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
            g2.drawString("Área de intercambio (swap): bloques " + foto.bloquesArchivos() + ".." + (foto.total() - 1),
                    MARGEN, ySwap + 4);
        }
        dibujarCabezal(g2);
        g2.dispose();
    }

    /** Marco rojo sobre el bloque del cabezal y una flecha con su dirección. */
    private void dibujarCabezal(Graphics2D g2) {
        int i = foto.cabezal();
        int x = x(i);
        int y = y(i);
        g2.setColor(CABEZAL);
        g2.setStroke(new BasicStroke(3f));
        g2.drawRect(x, y, CELDA, CELDA);
        g2.setStroke(new BasicStroke(1f));
        int centro = y + CELDA / 2;
        int[] xs;
        if (foto.direccion() == DireccionCabezal.SUBIENDO) {
            xs = new int[]{x + CELDA + 1, x + CELDA + 7, x + CELDA + 1};
        } else {
            xs = new int[]{x - 1, x - 7, x - 1};
        }
        g2.fillPolygon(xs, new int[]{centro - 5, centro, centro + 5}, 3);
    }

    /** Color de fondo del bloque según quién lo ocupa. */
    Color colorDe(int i) {
        Bloque b = foto.mapa()[i];
        if (!b.isOcupado()) {
            return b.estado().isSwap() ? ColoresArchivo.SWAP_LIBRE : ColoresArchivo.LIBRE;
        }
        if (b.estado().isSwap()) {
            return ColoresArchivo.SWAP_OCUPADO;
        }
        return foto.idArchivo()[i] == FotoDisco.SIN_ARCHIVO ? ColoresArchivo.RESERVADO : ColoresArchivo.de(foto.idArchivo()[i]);
    }

    @Override
    public String getToolTipText(MouseEvent evento) {
        int i = bloqueEn(evento.getX(), evento.getY());
        if (foto == null || i < 0) {
            return null;
        }
        Bloque b = foto.mapa()[i];
        StringBuilder sb = new StringBuilder("<html><b>Bloque ").append(i).append("</b>");
        sb.append(b.estado().isSwap() ? " (swap)" : "").append("<br>").append(foto.ocupante()[i]);
        if (b.isOcupado()) {
            sb.append("<br>siguiente: ").append(b.esUltimo() ? "fin de la cadena" : String.valueOf(b.siguiente()));
        }
        if (i == foto.cabezal()) {
            sb.append("<br><b>cabezal aquí</b> (").append(foto.direccion().getNombre().toLowerCase()).append(')');
        }
        return sb.append("</html>").toString();
    }

    /** Número del bloque bajo el punto, o -1. */
    int bloqueEn(int px, int py) {
        if (foto == null) {
            return -1;
        }
        for (int i = 0; i < foto.total(); i++) {
            if (px >= x(i) && px < x(i) + CELDA && py >= y(i) && py < y(i) + CELDA) {
                return i;
            }
        }
        return -1;
    }

    private int x(int i) {
        int indice = i < foto.bloquesArchivos() ? i : i - foto.bloquesArchivos();
        return MARGEN + (indice % COLUMNAS) * CELDA;
    }

    private int y(int i) {
        if (i < foto.bloquesArchivos()) {
            return MARGEN + (i / COLUMNAS) * CELDA;
        }
        int base = MARGEN + filas(foto.bloquesArchivos()) * CELDA + SEPARACION_SWAP;
        return base + ((i - foto.bloquesArchivos()) / COLUMNAS) * CELDA;
    }

    private static int filas(int bloques) {
        return (bloques + COLUMNAS - 1) / COLUMNAS;
    }
}
