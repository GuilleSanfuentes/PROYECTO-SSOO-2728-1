package avilaos.vista.gui;

import avilaos.vista.GraficoUtilizacion;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/** Pestaña Gráfico: utilización de CPU de cada computador en el tiempo, con botón para exportarlo a PNG. */
public class PanelGrafico extends JPanel {

    private final JButton botonExportar = new BotonSutil("Exportar PNG");
    private final MensajeBreve mensaje = new MensajeBreve();
    private ChartPanel panelGrafico;
    private JFreeChart grafico;
    private XYSeriesCollection datos;

    /** Crea el panel con la barra de exportación; el gráfico se arma con la primera foto. */
    public PanelGrafico() {
        super(new BorderLayout());
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        barra.add(botonExportar);
        barra.add(mensaje);
        add(barra, BorderLayout.NORTH);
        botonExportar.setEnabled(false);
        botonExportar.addActionListener(e -> exportar());
    }

    /** Redibuja las líneas con los puntos de la foto; si cambió la cantidad de computadores, rearma el gráfico. */
    public void mostrar(SerieGrafico[] series) {
        if (datos == null || datos.getSeriesCount() != series.length) {
            String[] nombres = new String[series.length];
            for (int i = 0; i < series.length; i++) {
                nombres[i] = series[i].nombre();
            }
            grafico = GraficoUtilizacion.crearVacio(nombres);
            datos = (XYSeriesCollection) grafico.getXYPlot().getDataset();
            if (panelGrafico != null) {
                remove(panelGrafico);
            }
            panelGrafico = new ChartPanel(grafico);
            add(panelGrafico, BorderLayout.CENTER);
            botonExportar.setEnabled(true);
            revalidate();
        }
        for (int i = 0; i < series.length; i++) {
            XYSeries serie = datos.getSeries(i);
            serie.setNotify(false);
            serie.clear();
            for (int k = 0; k < series[i].ciclos().length; k++) {
                serie.add(series[i].ciclos()[k], series[i].porcentajes()[k]);
            }
            serie.setNotify(true);
        }
    }

    /** Pide la ruta y guarda el gráfico que se está viendo como PNG. */
    private void exportar() {
        File archivo = SelectorConfiguracion.elegirParaExportarPNG(this);
        if (archivo == null) {
            return;
        }
        if (GraficoUtilizacion.exportarPNG(grafico, archivo)) {
            mensaje.exito("Gráfico exportado: " + archivo.getName());
        } else {
            mensaje.error("No se pudo guardar " + archivo.getName());
        }
    }
}
