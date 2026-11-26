package avilaos.vista.gui;

import javax.swing.table.AbstractTableModel;

/** Cola de E/S del disco: primero la solicitud en atención (si hay) y luego las que esperan, en orden de llegada. */
public class ModeloColaDisco extends AbstractTableModel {

    private static final String[] COLUMNAS = {"Estado", "#", "Proceso", "Operación", "Ruta", "Bloque destino", "Bloques",
        "Espera (ciclos)"};

    private FilaSolicitud[] filas = new FilaSolicitud[0];

    /** Reemplaza las filas con la solicitud en atención (o null) y la cola. */
    public void setFilas(FilaSolicitud actual, FilaSolicitud[] cola) {
        int n = cola.length + (actual == null ? 0 : 1);
        FilaSolicitud[] nuevas = new FilaSolicitud[n];
        int k = 0;
        if (actual != null) {
            nuevas[k++] = actual;
        }
        for (FilaSolicitud s : cola) {
            nuevas[k++] = s;
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
    public Object getValueAt(int fila, int columna) {
        FilaSolicitud s = filas[fila];
        return switch (columna) {
            case 0 -> s.enAtencion() ? "en atención" : "en cola";
            case 1 -> String.valueOf(s.id());
            case 2 -> s.proceso();
            case 3 -> s.operacion().getNombre();
            case 4 -> s.ruta();
            case 5 -> String.valueOf(s.destino());
            case 6 -> String.valueOf(s.bloques());
            default -> String.valueOf(s.espera());
        };
    }

    /** Solicitud de la fila indicada. */
    public FilaSolicitud getFila(int fila) {
        return filas[fila];
    }
}
