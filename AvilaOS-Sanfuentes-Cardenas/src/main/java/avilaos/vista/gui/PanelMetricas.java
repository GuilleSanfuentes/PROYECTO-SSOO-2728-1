package avilaos.vista.gui;

import avilaos.metricas.Metricas;
import avilaos.metricas.MetricasBuffer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/** Pestaña Métricas: una fila por computador más la del clúster, y debajo las de productor-consumidor de cada buffer. */
public class PanelMetricas extends JPanel {

    private static final String SIN_DATO = "n/d";
    private static final String[] COLUMNAS = {"Ámbito", "Throughput (proc/ciclo)", "CPU ocupada %", "Usuario %", "SO %",
        "Respuesta prom. (ciclos)", "Deadlines cumplidos %", "Equidad (Jain)", "Terminados OK", "Vencidos", "Vivos"};
    private static final String[] COLUMNAS_BUFFER = {"Buffer", "Producidos", "Consumidos", "Procesos",
        "Bloqueado en semáforos (ciclos prom. por proceso)"};

    private final ModeloTablaTexto modelo = new ModeloTablaTexto(COLUMNAS);
    private final ModeloTablaTexto modeloBuffers = new ModeloTablaTexto(COLUMNAS_BUFFER);

    /** Arma las dos tablas. */
    public PanelMetricas() {
        super(new BorderLayout(0, 6));
        JScrollPane general = new JScrollPane(tabla(modelo, true));
        JScrollPane buffers = new JScrollPane(tabla(modeloBuffers, false));
        buffers.setBorder(BorderFactory.createTitledBorder("Productor-consumidor por buffer"));
        buffers.setPreferredSize(new Dimension(0, 130));
        add(general, BorderLayout.CENTER);
        add(buffers, BorderLayout.SOUTH);
    }

    /** Muestra las métricas de la foto. */
    public void mostrar(Metricas[] metricas, MetricasBuffer[] porBuffer) {
        String[][] filas = new String[metricas.length][];
        for (int i = 0; i < metricas.length; i++) {
            Metricas m = metricas[i];
            filas[i] = new String[]{m.nombre(), String.format(Locale.ROOT, "%.4f", m.throughput()),
                String.format(Locale.ROOT, "%.1f", m.utilizacion()), String.format(Locale.ROOT, "%.1f", m.porcentajeUsuario()),
                String.format(Locale.ROOT, "%.1f", m.porcentajeSO()), dato(m.tiempoRespuesta(), "%.2f"),
                dato(m.cumplimiento(), "%.1f"), dato(m.equidad(), "%.3f"), String.valueOf(m.terminadosOk()),
                String.valueOf(m.vencidos()), String.valueOf(m.vivos())};
        }
        modelo.setFilas(filas);
        String[][] filasBuffer = new String[porBuffer.length][];
        for (int i = 0; i < porBuffer.length; i++) {
            MetricasBuffer m = porBuffer[i];
            filasBuffer[i] = new String[]{m.nombre(), String.valueOf(m.producidos()), String.valueOf(m.consumidos()),
                String.valueOf(m.procesos()), dato(m.bloqueadoPromedio(), "%.2f")};
        }
        modeloBuffers.setFilas(filasBuffer);
    }

    private static String dato(double valor, String formato) {
        return valor < 0 ? SIN_DATO : String.format(Locale.ROOT, formato, valor);
    }

    private static JTable tabla(ModeloTablaTexto modelo, boolean resaltarUltima) {
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(24);
        tabla.setShowGrid(false);
        tabla.setIntercellSpacing(new Dimension(0, 0));
        tabla.setRowSelectionAllowed(false);
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.setDefaultRenderer(Object.class, new RenderizadorMetricas(resaltarUltima));
        return tabla;
    }

    /** Texto centrado; la última fila (clúster) va en negrita sobre fondo gris claro. */
    private static class RenderizadorMetricas extends DefaultTableCellRenderer {

        private final boolean resaltarUltima;

        RenderizadorMetricas(boolean resaltarUltima) {
            this.resaltarUltima = resaltarUltima;
        }

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, false, false, fila, columna);
            boolean ultima = resaltarUltima && fila == tabla.getRowCount() - 1;
            setFont(tabla.getFont().deriveFont(ultima ? Font.BOLD : Font.PLAIN));
            setForeground(ColoresEstado.TEXTO);
            setBackground(ultima ? ColoresEstado.PISTA : Color.WHITE);
            setHorizontalAlignment(columna == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
            return this;
        }
    }
}
