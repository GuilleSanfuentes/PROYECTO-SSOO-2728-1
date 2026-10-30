package avilaos.vista.gui;

import avilaos.enums.EstadoProceso;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

/** Tabla de procesos con fondo por estado, barras de progreso y de deadline, y ficha del PCB al hacer clic. */
public class TablaProcesos extends JTable {

    private static final int[] ANCHOS = {130, 70, 38, 42, 50, 90, 90, 150};

    /** Acción al hacer clic en un proceso de la tabla. */
    public interface AccionFila {

        /** Recibe la fila del proceso pulsado. */
        void ejecutar(FilaProceso fila);
    }

    private final ModeloTablaPCB modelo;
    private AccionFila alHacerClic;

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
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int fila = rowAtPoint(e.getPoint());
                if (fila >= 0 && fila < modelo.getRowCount() && alHacerClic != null) {
                    alHacerClic.ejecutar(modelo.fila(fila));
                }
            }
        });
    }

    /** Define qué pasa al hacer clic en un proceso (abrir su ficha del PCB). */
    public void alHacerClic(AccionFila accion) {
        this.alHacerClic = accion;
    }

    /** Reemplaza las filas mostradas. */
    public void mostrar(FilaProceso[] filas) {
        modelo.setFilas(filas);
    }

    /** Encabezado que explica cada columna al pasar el ratón. */
    @Override
    protected JTableHeader createDefaultTableHeader() {
        return new JTableHeader(columnModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int columna = columnModel.getColumnIndexAtX(e.getX());
                return columna < 0 ? null : modelo.ayuda(columnModel.getColumn(columna).getModelIndex());
            }
        };
    }

    /** Texto con el fondo suave del estado del proceso de la fila. */
    private class RenderizadorTexto extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, false, false, fila, columna);
            setBackground(ColoresEstado.suave(modelo.fila(fila).estado()));
            setForeground(ColoresEstado.TEXTO);
            int indice = tabla.convertColumnIndexToModel(columna);
            boolean numero = indice == ModeloTablaPCB.COL_PRIORIDAD || indice == ModeloTablaPCB.COL_MEMORIA
                    || indice == ModeloTablaPCB.COL_RESTANTE;
            setHorizontalAlignment(numero ? SwingConstants.CENTER : SwingConstants.LEFT);
            setToolTipText(indice == 0 ? "Clic para ver el PCB completo" : valor == null ? null : valor.toString());
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
