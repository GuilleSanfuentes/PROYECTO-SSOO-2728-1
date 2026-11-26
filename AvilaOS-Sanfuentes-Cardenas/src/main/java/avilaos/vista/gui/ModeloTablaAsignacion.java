package avilaos.vista.gui;

import avilaos.so.archivos.InfoEntrada;
import java.awt.Color;
import javax.swing.table.AbstractTableModel;

/** Tabla de asignación de archivos: una fila por archivo con su color, bloques, primer bloque, dueño y proceso creador. */
public class ModeloTablaAsignacion extends AbstractTableModel {

    static final int COL_COLOR = 0;

    private static final String[] COLUMNAS = {"Color", "Archivo", "Bloques", "Primer bloque", "Dueño", "Permiso",
        "Creado por", "Última operación"};

    private InfoEntrada[] filas = new InfoEntrada[0];

    /** Reemplaza las filas solo si cambiaron (así no se pierde la selección en cada ciclo). */
    public void setFilas(InfoEntrada[] nuevas) {
        if (iguales(filas, nuevas)) {
            return;
        }
        filas = nuevas;
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
        return columna == COL_COLOR ? Color.class : String.class;
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        InfoEntrada e = filas[fila];
        return switch (columna) {
            case COL_COLOR -> ColoresArchivo.de(e.id());
            case 1 -> e.ruta();
            case 2 -> String.valueOf(e.bloques());
            case 3 -> String.valueOf(e.primerBloque());
            case 4 -> e.dueno();
            case 5 -> e.permiso().getNombre();
            case 6 -> e.creador();
            default -> e.ultimaOperacion();
        };
    }

    /** Archivo de la fila indicada. */
    public InfoEntrada getFila(int fila) {
        return filas[fila];
    }

    /** Compara dos juegos de filas campo por campo. */
    private static boolean iguales(InfoEntrada[] a, InfoEntrada[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }
}
