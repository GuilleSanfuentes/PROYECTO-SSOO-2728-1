package avilaos.vista.gui;

import avilaos.cluster.CargaSimulacion;
import avilaos.cluster.Cluster;
import avilaos.cluster.ComparadorPoliticas;
import avilaos.metricas.MetricasDisco;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Pestaña del gráfico del desplazamiento: la simulación en curso o la comparación de las 6 políticas con la misma carga. */
public class PanelDesplazamiento extends JPanel {

    private final PanelGraficoDisco grafico = new PanelGraficoDisco();
    private final JButton verSimulacion = new BotonSutil("Ver la simulación");
    private final JButton comparar = new BotonSutil("Comparar las 6 políticas");
    private final JLabel estado = new JLabel(" ");
    private final CargaSimulacion carga;
    private final Cluster cluster;
    private MetricasDisco ultimas;
    private boolean comparando;
    private boolean viendoComparacion;

    /** Arma la pestaña sobre la carga (para el comparador) y el sistema en curso. */
    public PanelDesplazamiento(CargaSimulacion carga, Cluster cluster) {
        super(new BorderLayout());
        this.carga = carga;
        this.cluster = cluster;
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        barra.add(verSimulacion);
        barra.add(comparar);
        barra.add(estado);
        comparar.setToolTipText("Corre los mismos dos JSON con FIFO, SSTF, SCAN, C-SCAN, LOOK y C-LOOK sin interfaz y los dibuja juntos");
        verSimulacion.setEnabled(false);
        verSimulacion.addActionListener(e -> {
            viendoComparacion = false;
            verSimulacion.setEnabled(false);
            grafico.mostrar(ultimas);
        });
        comparar.addActionListener(e -> comparar());
        add(barra, BorderLayout.NORTH);
        add(grafico, BorderLayout.CENTER);
    }

    /** Actualiza el gráfico de la simulación (si no se está viendo la comparación). */
    public void mostrar(MetricasDisco metricas) {
        if (metricas == null) {
            return;
        }
        ultimas = metricas;
        if (!viendoComparacion) {
            grafico.mostrar(metricas);
        }
    }

    /** Corre el comparador en un hilo aparte (tarda) y dibuja el resultado en el hilo de Swing. */
    private void comparar() {
        if (comparando) {
            return;
        }
        comparando = true;
        comparar.setEnabled(false);
        estado.setText("Comparando las 6 políticas, esto puede tardar unos segundos...");
        Thread hilo = new Thread(() -> {
            ComparadorPoliticas.Comparacion resultado = ComparadorPoliticas.comparar(carga, cluster);
            SwingUtilities.invokeLater(() -> {
                comparando = false;
                comparar.setEnabled(true);
                if (resultado.hecha()) {
                    viendoComparacion = true;
                    verSimulacion.setEnabled(true);
                    grafico.mostrarComparacion(resultado.resultados());
                    estado.setText("Comparación lista: mismos dos JSON con cada política de disco");
                } else {
                    estado.setText("No se pudo comparar: " + resultado.motivo());
                }
            });
        }, "Hilo-Comparador-Disco");
        hilo.setDaemon(true);
        hilo.start();
    }
}
