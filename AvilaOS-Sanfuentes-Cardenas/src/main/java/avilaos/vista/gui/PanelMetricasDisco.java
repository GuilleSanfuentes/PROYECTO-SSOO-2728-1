package avilaos.vista.gui;

import avilaos.enums.PoliticaDiscoTipo;
import avilaos.metricas.MetricasDisco;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;

/** Métricas del disco y de la suspensión en una tabla de dos columnas: qué se mide y su valor. */
public class PanelMetricasDisco extends JPanel {

    private final Modelo modelo = new Modelo();

    /** Arma la tabla vacía. */
    public PanelMetricasDisco() {
        super(new BorderLayout());
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(22);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(360);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(220);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
    }

    /** Muestra las métricas; con null deja las anteriores. */
    public void mostrar(MetricasDisco m) {
        if (m != null) {
            modelo.filas = filas(m);
            modelo.fireTableDataChanged();
        }
    }

    /** Filas "qué se mide / valor" de las métricas del disco. */
    public static String[][] filas(MetricasDisco m) {
        PoliticaDiscoTipo[] tipos = PoliticaDiscoTipo.values();
        String[][] filas = new String[8 + tipos.length][];
        int k = 0;
        filas[k++] = new String[]{"Ciclo de la medición", String.valueOf(m.ciclo())};
        filas[k++] = new String[]{"Política de disco activa", m.politicaActiva().getNombre()};
        filas[k++] = new String[]{"Desplazamiento total del cabezal (posiciones)", String.valueOf(m.desplazamientoTotal())};
        filas[k++] = new String[]{"Solicitudes atendidas", String.valueOf(m.atendidas())};
        for (PoliticaDiscoTipo t : tipos) {
            filas[k++] = new String[]{"    atendidas con " + t.getNombre(), String.valueOf(m.atendidasPor(t))};
        }
        filas[k++] = new String[]{"Espera promedio en la cola de E/S (ciclos)", decimal(m.esperaPromedioTotal())};
        filas[k++] = new String[]{"    sin contar la espera anticipatoria", decimal(m.esperaPromedioSinAnticipacion())};
        filas[k++] = new String[]{"Suspensiones (procesos llevados al swap)", String.valueOf(m.suspensiones())};
        filas[k] = new String[]{"Reactivaciones (procesos traídos de vuelta a la RAM)", String.valueOf(m.reactivaciones())};
        return filas;
    }

    /** Número con dos decimales y coma decimal ("52,38"). */
    public static String decimal(double valor) {
        long centesimas = Math.round(valor * 100);
        long parteEntera = centesimas / 100;
        long resto = Math.abs(centesimas % 100);
        return parteEntera + "," + (resto < 10 ? "0" : "") + resto;
    }

    /** Modelo de dos columnas de solo lectura. */
    private static final class Modelo extends AbstractTableModel {

        private String[][] filas = new String[0][];

        @Override
        public int getRowCount() {
            return filas.length;
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int columna) {
            return columna == 0 ? "Métrica" : "Valor";
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            return filas[fila][columna];
        }
    }
}
