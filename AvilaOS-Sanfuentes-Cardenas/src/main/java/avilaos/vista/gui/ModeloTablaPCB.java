package avilaos.vista.gui;

import javax.swing.table.AbstractTableModel;

/** Modelo de tabla de procesos: una fila por PCB, con columna de motivo solo en la cola de Bloqueados. */
public class ModeloTablaPCB extends AbstractTableModel {

    static final int COL_PRIORIDAD = 2;
    static final int COL_MEMORIA = 3;
    static final int COL_RESTANTE = 4;
    static final int COL_PROGRESO = 5;
    static final int COL_DEADLINE = 6;
    static final int COL_MOTIVO = 7;

    private static final String[] COLUMNAS = {"Proceso", "Tipo", "Prio", "Mem", "Restan", "Progreso", "Deadline", "Motivo"};
    private static final String[] AYUDAS = {
        "ID y nombre del proceso", "Tipo de proceso", "Prioridad (base>efectiva si envejeció)",
        "Memoria que ocupa en la RAM", "Tiempo restante: instrucciones que le faltan",
        "Instrucciones hechas / total", "Deadline restante / inicial", "Motivo del bloqueo"};

    private final int columnas;
    private FilaProceso[] filas = new FilaProceso[0];

    /** Crea el modelo; conMotivo agrega la columna del motivo de bloqueo. */
    public ModeloTablaPCB(boolean conMotivo) {
        this.columnas = conMotivo ? COLUMNAS.length : COLUMNAS.length - 1;
    }

    /** Reemplaza todas las filas y refresca la tabla. */
    public void setFilas(FilaProceso[] nuevas) {
        this.filas = nuevas;
        fireTableDataChanged();
    }

    /** Fila del PCB mostrado en esa posición. */
    public FilaProceso fila(int indice) {
        return filas[indice];
    }

    /** Texto de ayuda del encabezado de la columna. */
    public String ayuda(int columna) {
        return AYUDAS[columna];
    }

    @Override
    public int getRowCount() {
        return filas.length;
    }

    @Override
    public int getColumnCount() {
        return columnas;
    }

    @Override
    public String getColumnName(int columna) {
        return COLUMNAS[columna];
    }

    @Override
    public Class<?> getColumnClass(int columna) {
        return columna == COL_PROGRESO || columna == COL_DEADLINE ? FilaProceso.class : String.class;
    }

    @Override
    public Object getValueAt(int fila, int columna) {
        FilaProceso f = filas[fila];
        return switch (columna) {
            case 0 -> f.etiqueta() + " " + f.nombre();
            case 1 -> f.tipo().getNombre();
            case COL_PRIORIDAD -> f.prioridadTexto();
            case COL_MEMORIA -> String.valueOf(f.memoria());
            case COL_RESTANTE -> String.valueOf(f.restantes());
            case COL_PROGRESO, COL_DEADLINE -> f;
            default -> f.motivo();
        };
    }
}
