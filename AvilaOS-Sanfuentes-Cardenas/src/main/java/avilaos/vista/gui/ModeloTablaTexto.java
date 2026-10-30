package avilaos.vista.gui;

import javax.swing.table.AbstractTableModel;

/** Modelo de tabla de solo texto: columnas fijas y filas que se reemplazan en cada refresco. */
public class ModeloTablaTexto extends AbstractTableModel {

    private final String[] columnas;
    private String[][] filas = new String[0][];

    /** Crea el modelo con los títulos de columna. */
    public ModeloTablaTexto(String[] columnas) {
        this.columnas = columnas;
    }

    /** Reemplaza todas las filas y refresca la tabla. */
    public void setFilas(String[][] nuevas) {
        this.filas = nuevas;
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return filas.length;
    }

    @Override
    public int getColumnCount() {
        return columnas.length;
    }

    @Override
    public String getColumnName(int columna) {
        return columnas[columna];
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        return filas[fila][columna];
    }
}
