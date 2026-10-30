package avilaos.vista.gui;

import avilaos.enums.EstadoProceso;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

/** Tabla de procesos con fondo por estado y barras de progreso y de deadline restante. */
public class TablaProcesos extends JTable {

    private static final int[] ANCHOS = {140, 72, 40, 100, 100, 160};

    private final ModeloTablaPCB modelo;

    /** Crea la tabla; conMotivo agrega la columna del motivo de bloqueo. */
    public TablaProcesos(boolean conMotivo) {
        super(new ModeloTablaPCB(conMotivo));
        this.modelo = (ModeloTablaPCB) getModel();
        setRowHeight(22);
        setShowGrid(false);
        setIntercellSpacing(new java.awt.Dimension(0, 0));
        setRowSelectionAllowed(false);
        setFillsViewportHeight(true);
        getTableHeader().setReorderingAllowed(false);
        setDefaultRenderer(String.class, new RenderizadorTexto());
        TableColumnModel columnas = getColumnModel();
        for (int i = 0; i < columnas.getColumnCount(); i++) {
            columnas.getColumn(i).setPreferredWidth(ANCHOS[i]);
        }
        columnas.getColumn(ModeloTablaPCB.COL_PROGRESO).setCellRenderer(new RenderizadorBarra(false));
        columnas.getColumn(ModeloTablaPCB.COL_DEADLINE).setCellRenderer(new RenderizadorBarra(true));
    }

    /** Reemplaza las filas mostradas. */
    public void mostrar(FilaProceso[] filas) {
        modelo.setFilas(filas);
    }

    /** Texto con el fondo suave del estado del proceso de la fila. */
    private class RenderizadorTexto extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, false, false, fila, columna);
            setBackground(ColoresEstado.suave(modelo.fila(fila).estado()));
            setForeground(ColoresEstado.TEXTO);
            setHorizontalAlignment(columna == 2 ? SwingConstants.CENTER : SwingConstants.LEFT);
            setToolTipText(valor == null ? null : valor.toString());
            return this;
        }
    }

    /** Barra de progreso (instrucciones hechas / total) o de deadline restante, según el modo. */
    private static class RenderizadorBarra implements TableCellRenderer {

        private final BarraColor barra = new BarraColor();
        private final boolean deadline;

        RenderizadorBarra(boolean deadline) {
            this.deadline = deadline;
        }

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            FilaProceso f = (FilaProceso) valor;
            Color fondo = ColoresEstado.suave(f.estado());
            if (!deadline) {
                double fraccion = f.total() > 0 ? (double) f.hechas() / f.total() : 0;
                barra.setDatos(fraccion, ColoresEstado.fuerte(f.estado()), f.hechas() + "/" + f.total(), fondo);
            } else if (f.estado() == EstadoProceso.TERMINADO) {
                barra.setDatos(1, ColoresEstado.GRIS, f.deadlineVencido() ? "venció" : "cumplido", fondo);
            } else {
                double fraccion = f.deadlineInicial() > 0 ? (double) f.deadlineRestante() / f.deadlineInicial() : 0;
                Color color = fraccion > 0.5 ? new Color(96, 125, 139) : fraccion > 0.25 ? ColoresEstado.NARANJA : ColoresEstado.ROJO;
                barra.setDatos(fraccion, color, f.deadlineRestante() + "/" + f.deadlineInicial(), fondo);
            }
            ((JComponent) barra).setToolTipText(deadline ? "Deadline restante / inicial" : "Instrucciones hechas / total");
            return barra;
        }
    }
}
