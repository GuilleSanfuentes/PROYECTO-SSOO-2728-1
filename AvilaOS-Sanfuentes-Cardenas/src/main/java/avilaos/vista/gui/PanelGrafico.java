package avilaos.vista.gui;

import avilaos.vista.GraficoUtilizacion;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/** Pestaña Gráfico: utilización de CPU de cada computador en el tiempo, actualizada con cada foto. */
public class PanelGrafico extends JPanel {

    private XYSeriesCollection datos;

    /** Crea el panel vacío; el gráfico se arma con la primera foto. */
    public PanelGrafico() {
        super(new BorderLayout());
    }

    /** Redibuja las líneas con los puntos de la foto; si cambió la cantidad de computadores, rearma el gráfico. */
    public void mostrar(SerieGrafico[] series) {
        if (datos == null || datos.getSeriesCount() != series.length) {
            String[] nombres = new String[series.length];
            for (int i = 0; i < series.length; i++) {
                nombres[i] = series[i].nombre();
            }
            JFreeChart grafico = GraficoUtilizacion.crearVacio(nombres);
            datos = (XYSeriesCollection) grafico.getXYPlot().getDataset();
            removeAll();
            add(new ChartPanel(grafico), BorderLayout.CENTER);
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
}
