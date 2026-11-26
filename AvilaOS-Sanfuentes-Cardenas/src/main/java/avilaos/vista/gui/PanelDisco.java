package avilaos.vista.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Columna del disco: cabezal (posición, dirección, velocidad), solicitud en curso, mapa de bloques y leyenda. */
public class PanelDisco extends JPanel {

    private final JLabel cabezal = new JLabel("-");
    private final JLabel actual = new JLabel("-");
    private final JLabel accion = new JLabel("-");
    private final JLabel ocupacion = new JLabel("-");
    private final MapaDisco mapa = new MapaDisco();

    /** Arma la columna vacía; los datos llegan con mostrar. */
    public PanelDisco() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(208, 213, 220)), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        JLabel titulo = new JLabel("Disco (SD)");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        cabezal.setFont(cabezal.getFont().deriveFont(Font.BOLD));
        JPanel datos = new JPanel(new GridLayout(4, 1, 0, 2));
        datos.add(cabezal);
        datos.add(actual);
        datos.add(accion);
        datos.add(ocupacion);
        JPanel arriba = new JPanel(new BorderLayout(0, 4));
        arriba.add(titulo, BorderLayout.NORTH);
        arriba.add(datos, BorderLayout.CENTER);
        arriba.add(leyenda(), BorderLayout.SOUTH);
        add(arriba, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(mapa);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(222, 226, 230)));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
    }

    /** Dibuja la foto del disco. */
    public void mostrar(FotoDisco f) {
        if (f == null) {
            return;
        }
        cabezal.setText("Cabezal en el bloque " + f.cabezal() + " " + f.direccion().getSimbolo() + " ("
                + f.direccion().getNombre().toLowerCase() + ")  ·  " + f.velocidad() + " pos./ciclo  ·  política "
                + f.politica().getNombre());
        actual.setText(f.actual() == null ? "Sin solicitud en atención" + (f.cola().length > 0 ? " (" + f.cola().length + " en cola)" : "")
                : "Atendiendo #" + f.actual().id() + ": " + f.actual().proceso() + " " + f.actual().operacion().getNombre() + " "
                + f.actual().ruta() + " (bloque " + f.actual().destino() + ", " + f.actual().bloques() + " bl.)");
        accion.setText("Última acción: " + f.ultimaAccion() + "  ·  desplazamiento total " + f.desplazamiento());
        ocupacion.setText("Libres: " + f.libresArchivos() + " de " + f.bloquesArchivos() + " bloques de archivos, "
                + f.libresSwap() + " de " + (f.total() - f.bloquesArchivos()) + " de swap");
        mapa.mostrar(f);
    }

    /** Leyenda de colores del mapa. */
    private static JPanel leyenda() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.add(muestra(ColoresArchivo.LIBRE, "libre"));
        panel.add(muestra(ColoresArchivo.de(1), "archivo (un color por archivo)"));
        panel.add(muestra(ColoresArchivo.RESERVADO, "reservado"));
        panel.add(muestra(ColoresArchivo.SWAP_LIBRE, "swap libre"));
        panel.add(muestra(ColoresArchivo.SWAP_OCUPADO, "proceso suspendido"));
        JLabel cabezal = new JLabel("cabezal", new Cuadro(Color.WHITE, new Color(220, 38, 38)), JLabel.LEFT);
        panel.add(cabezal);
        return panel;
    }

    private static JLabel muestra(Color color, String texto) {
        return new JLabel(texto, new Cuadro(color, new Color(160, 166, 175)), JLabel.LEFT);
    }

    /** Cuadrito de color con borde para la leyenda. */
    private static final class Cuadro implements javax.swing.Icon {

        private final Color relleno;
        private final Color borde;

        Cuadro(Color relleno, Color borde) {
            this.relleno = relleno;
            this.borde = borde;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            g.setColor(relleno);
            g.fillRect(x, y, 11, 11);
            g.setColor(borde);
            g.drawRect(x, y, 11, 11);
            if (relleno.equals(Color.WHITE)) {
                g.drawRect(x + 1, y + 1, 9, 9);
            }
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }
}
