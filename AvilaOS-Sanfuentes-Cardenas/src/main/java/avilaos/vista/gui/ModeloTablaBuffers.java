package avilaos.vista.gui;

import javax.swing.table.AbstractTableModel;

/** Modelo de tabla de buffers: una fila por buffer, con semáforos, contadores y procesos bloqueados. */
public class ModeloTablaBuffers extends AbstractTableModel {

    static final int COL_ELEMENTOS = 2;
    static final int COL_PRIMER_BLOQUEO = 8;

    private static final String[] COLUMNAS = {"Buffer", "Anfitrión", "Elementos", "Vacío", "Lleno", "Mutex",
        "Producidos", "Consumidos", "Bloq. en vacío", "Bloq. en lleno", "Bloq. en mutex"};

    private FilaBuffer[] filas = new FilaBuffer[0];

    /** Reemplaza todas las filas y refresca la tabla. */
    public void setFilas(FilaBuffer[] nuevas) {
        this.filas = nuevas;
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return filas.length;
    }

    @Override
    public int getColumnCount() {
        return COLUMNAS.length;
    }

    @Override
    public String getColumnName(int columna) {
        return COLUMNAS[columna];
    }

    @Override
    public Class<?> getColumnClass(int columna) {
        return columna == COL_ELEMENTOS ? FilaBuffer.class : String.class;
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        FilaBuffer f = filas[fila];
        return switch (columna) {
            case 0 -> f.nombre();
            case 1 -> "Computador " + f.anfitrion();
            case COL_ELEMENTOS -> f;
            case 3 -> String.valueOf(f.valorVacio());
            case 4 -> String.valueOf(f.valorLleno());
            case 5 -> String.valueOf(f.valorMutex());
            case 6 -> String.valueOf(f.producidos());
            case 7 -> String.valueOf(f.consumidos());
            case 8 -> f.bloqueadosVacio();
            case 9 -> f.bloqueadosLleno();
            default -> f.bloqueadosMutex();
        };
    }
}
