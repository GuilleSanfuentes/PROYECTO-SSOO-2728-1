package avilaos.vista.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Pestaña con una tabla de solo lectura, un texto cuando está vacía y la columna de color pintada (si la hay). */
public class PanelTablaSimple extends JPanel {

    private final AbstractTableModel modelo;
    private final JLabel vacio;

    /** Arma la tabla del modelo con los anchos indicados y el texto para cuando no hay filas. */
    public PanelTablaSimple(AbstractTableModel modelo, int[] anchos, String textoVacio) {
        super(new BorderLayout());
        this.modelo = modelo;
        this.vacio = new JLabel(textoVacio, SwingConstants.CENTER);
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(22);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.setFillsViewportHeight(true);
        tabla.setDefaultRenderer(Color.class, new RenderizadorColor());
        tabla.setDefaultRenderer(String.class, new RenderizadorTexto());
        tabla.setDefaultRenderer(Object.class, new RenderizadorTexto());
        for (int i = 0; i < anchos.length && i < tabla.getColumnCount(); i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        vacio.setForeground(new Color(108, 117, 125));
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(vacio, BorderLayout.SOUTH);
        actualizarVacio();
    }

    /** Muestra u oculta el texto de tabla vacía (llamar después de cambiar las filas). */
    public void actualizarVacio() {
        vacio.setVisible(modelo.getRowCount() == 0);
    }

    /** Celda de color: un cuadro relleno con el color del archivo. */
    private static final class RenderizadorColor extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(javax.swing.JTable tabla, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, "", seleccionada, foco, fila, columna);
            setBackground(valor instanceof Color c ? c : Color.WHITE);
            setToolTipText("Color del archivo en el mapa del disco");
            return this;
        }
    }

    /** Texto con el contenido completo como tooltip. */
    private static final class RenderizadorTexto extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(javax.swing.JTable tabla, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, seleccionada, foco, fila, columna);
            setToolTipText(valor == null ? null : valor.toString());
            return this;
        }
    }
}
