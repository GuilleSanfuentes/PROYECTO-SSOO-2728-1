package avilaos.vista.gui;

import avilaos.cluster.ResultadoPolitica;
import avilaos.metricas.MetricasDisco;
import avilaos.vista.GraficoDesplazamiento;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

/** Gráfico del desplazamiento del cabezal, de la simulación o de la comparación, con botón para exportarlo a PNG. */
public class PanelGraficoDisco extends JPanel {

    private final JButton botonExportar = new BotonSutil("Exportar PNG");
    private final MensajeBreve mensaje = new MensajeBreve();
    private ChartPanel panelGrafico;
    private JFreeChart grafico;
    private String rutaSugerida = GraficoDesplazamiento.RUTA_SIMULACION;

    /** Crea el panel con la barra de exportación; queda sin gráfico hasta que reciba datos. */
    public PanelGraficoDisco() {
        super(new BorderLayout());
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        barra.add(botonExportar);
        barra.add(mensaje);
        add(barra, BorderLayout.NORTH);
        botonExportar.setEnabled(false);
        botonExportar.addActionListener(e -> exportar());
    }

    /** Dibuja la simulación en curso con sus cambios de política. */
    public void mostrar(MetricasDisco metricas) {
        if (metricas != null) {
            poner(GraficoDesplazamiento.crear(metricas), GraficoDesplazamiento.RUTA_SIMULACION);
        }
    }

    /** Dibuja una línea por política con los resultados del comparador. */
    public void mostrarComparacion(ResultadoPolitica[] resultados) {
        if (resultados != null && resultados.length > 0) {
            poner(GraficoDesplazamiento.crearComparacion(resultados), GraficoDesplazamiento.RUTA_COMPARACION);
        }
    }

    /** El gráfico que se está viendo, o null. */
    public JFreeChart getGrafico() {
        return grafico;
    }

    /** Coloca o reemplaza el gráfico y habilita la exportación. */
    private void poner(JFreeChart nuevo, String ruta) {
        grafico = nuevo;
        rutaSugerida = ruta;
        if (panelGrafico == null) {
            panelGrafico = new ChartPanel(grafico);
            add(panelGrafico, BorderLayout.CENTER);
            revalidate();
        } else {
            panelGrafico.setChart(grafico);
        }
        botonExportar.setEnabled(true);
    }

    /** Pide la ruta y guarda como PNG el gráfico que se está viendo. */
    private void exportar() {
        File archivo = SelectorConfiguracion.elegirParaExportarPNG(this, rutaSugerida);
        if (archivo == null) {
            return;
        }
        if (GraficoDesplazamiento.exportarPNG(grafico, archivo)) {
            mensaje.exito("Gráfico exportado: " + archivo.getName());
        } else {
            mensaje.error("No se pudo guardar " + archivo.getName());
        }
    }
}
