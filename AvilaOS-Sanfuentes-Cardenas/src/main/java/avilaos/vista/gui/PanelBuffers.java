package avilaos.vista.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/** Pestaña Buffers: una fila por buffer con barra de ocupación, semáforos, contadores y bloqueados. */
public class PanelBuffers extends JPanel {

    private static final int[] ANCHOS = {60, 100, 120, 50, 50, 50, 80, 85, 110, 110, 110, 310};

    private final ModeloTablaBuffers modelo = new ModeloTablaBuffers();
    private final JLabel vacio = new JLabel("No hay buffers creados", SwingConstants.CENTER);

    /** Arma la tabla de buffers. */
    public PanelBuffers() {
        super(new BorderLayout());
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(24);
        tabla.setShowGrid(false);
        tabla.setIntercellSpacing(new java.awt.Dimension(0, 0));
        tabla.setRowSelectionAllowed(false);
        tabla.setFillsViewportHeight(true);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.setDefaultRenderer(String.class, new RenderizadorTexto());
        for (int i = 0; i < ANCHOS.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(ANCHOS[i]);
        }
        tabla.getColumnModel().getColumn(ModeloTablaBuffers.COL_ELEMENTOS).setCellRenderer(new RenderizadorBarra());
        vacio.setForeground(new Color(108, 117, 125));
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(vacio, BorderLayout.SOUTH);
    }

    /** Muestra la foto de los buffers. */
    public void mostrar(FilaBuffer[] filas) {
        modelo.setFilas(filas);
        vacio.setVisible(filas.length == 0);
    }

    /** Texto con tooltip; los procesos bloqueados se resaltan en rojo. */
    private static class RenderizadorTexto extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, false, false, fila, columna);
            boolean hayBloqueados = columna >= ModeloTablaBuffers.COL_PRIMER_BLOQUEO && columna < ModeloTablaBuffers.COL_USADO_POR
                    && !FilaBuffer.NINGUNO.equals(valor);
            setForeground(hayBloqueados ? ColoresEstado.ROJO : ColoresEstado.TEXTO);
            setHorizontalAlignment(columna >= 3 && columna < ModeloTablaBuffers.COL_PRIMER_BLOQUEO
                    ? SwingConstants.CENTER : SwingConstants.LEFT);
            setToolTipText(valor == null ? null : valor.toString());
            return this;
        }
    }

    /** Barra de elementos / capacidad: verde con espacio, amarillo casi lleno, rojo lleno. */
    private static class RenderizadorBarra implements TableCellRenderer {

        private final BarraColor barra = new BarraColor();

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            FilaBuffer f = (FilaBuffer) valor;
            double uso = f.capacidad() > 0 ? (double) f.elementos() / f.capacidad() : 0;
            barra.setDatos(uso, ColoresEstado.ram(uso), f.elementos() + "/" + f.capacidad(), Color.WHITE);
            barra.setToolTipText("Elementos / capacidad");
            return barra;
        }
    }
}
