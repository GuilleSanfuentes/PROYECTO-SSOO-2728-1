package avilaos.vista;

import avilaos.cluster.ResultadoPolitica;
import avilaos.log.Debug;
import avilaos.metricas.CambioPolitica;
import avilaos.metricas.MetricasDisco;
import avilaos.metricas.PuntoDesplazamiento;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.IOException;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/** Gráfico del desplazamiento acumulado del cabezal en el tiempo (JFreeChart); se puede exportar a PNG. */
public final class GraficoDesplazamiento {

    public static final String RUTA_SIMULACION = "salida/desplazamiento_disco.png";
    public static final String RUTA_COMPARACION = "salida/comparacion_politicas.png";

    private static final String ORIGEN = "GráficoDisco";
    private static final int ANCHO_PNG = 1000;
    private static final int ALTO_PNG = 500;
    private static final Color[] COLORES = {new Color(215, 58, 73), new Color(31, 111, 235), new Color(46, 140, 67),
        new Color(225, 120, 0), new Color(130, 60, 190), new Color(25, 25, 25)};

    private GraficoDesplazamiento() {
    }

    /** Una línea con la simulación en curso y una marca vertical en cada cambio de política; vacío si no hay métricas. */
    public static JFreeChart crear(MetricasDisco metricas) {
        XYSeriesCollection datos = new XYSeriesCollection();
        if (metricas != null) {
            datos.addSeries(serie("Desplazamiento", metricas.serie()));
        }
        JFreeChart grafico = armar("Desplazamiento acumulado del cabezal"
                + (metricas == null ? "" : " (política actual: " + metricas.politicaActiva().getNombre() + ")"), datos);
        for (ValueMarker marca : marcas(metricas)) {
            grafico.getXYPlot().addDomainMarker(marca);
        }
        return grafico;
    }

    /** Líneas verticales punteadas, una por cambio de política, con su ciclo y la transición como rótulo. */
    public static ValueMarker[] marcas(MetricasDisco metricas) {
        CambioPolitica[] cambios = metricas == null ? new CambioPolitica[0] : metricas.cambios();
        ValueMarker[] marcas = new ValueMarker[cambios.length];
        for (int i = 0; i < cambios.length; i++) {
            marcas[i] = new ValueMarker(cambios[i].ciclo(), Color.DARK_GRAY,
                    new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
            marcas[i].setLabel(cambios[i].anterior().getNombre() + " → " + cambios[i].nueva().getNombre());
            marcas[i].setLabelAnchor(RectangleAnchor.TOP_LEFT);
            marcas[i].setLabelTextAnchor(TextAnchor.TOP_RIGHT);
        }
        return marcas;
    }

    /** Una línea por política con los resultados del comparador; vacío si no hay resultados. */
    public static JFreeChart crearComparacion(ResultadoPolitica[] resultados) {
        XYSeriesCollection datos = new XYSeriesCollection();
        if (resultados != null) {
            for (ResultadoPolitica r : resultados) {
                datos.addSeries(serie(r.etiqueta(), r.metricas().serie()));
            }
        }
        JFreeChart grafico = armar("Desplazamiento acumulado del cabezal por política de disco", datos);
        senalarFinales(grafico.getXYPlot(), resultados);
        return grafico;
    }

    /** Como las líneas se superponen, cada una lleva un punto donde termina, rotulado con su política y su total. */
    private static void senalarFinales(XYPlot plot, ResultadoPolitica[] resultados) {
        XYLineAndShapeRenderer trazos = new XYLineAndShapeRenderer(true, true) {
            @Override
            public boolean getItemShapeVisible(int serie, int punto) {
                return punto == plot.getDataset().getItemCount(serie) - 1;
            }
        };
        for (int i = 0; i < plot.getDataset().getSeriesCount(); i++) {
            Color color = COLORES[i % COLORES.length];
            trazos.setSeriesPaint(i, color);
            trazos.setSeriesStroke(i, new BasicStroke(Math.max(1.5f, 6f - 0.9f * i)));
            trazos.setSeriesShape(i, new Ellipse2D.Double(-4, -4, 8, 8));
            MetricasDisco metricas = resultados[i].metricas();
            PuntoDesplazamiento[] puntos = metricas.serie();
            XYTextAnnotation rotulo = new XYTextAnnotation("  " + resultados[i].politica().getNombre() + ": "
                    + metricas.desplazamientoTotal(), puntos[puntos.length - 1].ciclo(), puntos[puntos.length - 1].acumulado());
            rotulo.setTextAnchor(TextAnchor.TOP_LEFT);
            rotulo.setPaint(color);
            rotulo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            plot.addAnnotation(rotulo);
        }
        plot.setRenderer(trazos);
        plot.getDomainAxis().setUpperMargin(0.18);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
    }

    /** Guarda el gráfico como PNG, creando la carpeta si hace falta. Devuelve false si falla. */
    public static boolean exportarPNG(JFreeChart grafico, File archivo) {
        if (grafico == null || archivo == null) {
            Debug.error(ORIGEN, "exportar: falta el gráfico o el archivo");
            return false;
        }
        File carpeta = archivo.getAbsoluteFile().getParentFile();
        if (carpeta != null && !carpeta.isDirectory() && !carpeta.mkdirs()) {
            Debug.error(ORIGEN, "no se pudo crear la carpeta " + carpeta);
            return false;
        }
        try {
            ChartUtils.saveChartAsPNG(archivo, grafico, ANCHO_PNG, ALTO_PNG);
        } catch (IOException e) {
            Debug.error(ORIGEN, "no se pudo guardar " + archivo + ": " + e.getMessage());
            return false;
        }
        Debug.ok(ORIGEN, "gráfico del disco guardado en " + archivo);
        return true;
    }

    /** Convierte los puntos del registro en una serie del gráfico. */
    private static XYSeries serie(String nombre, PuntoDesplazamiento[] puntos) {
        XYSeries serie = new XYSeries(nombre);
        for (PuntoDesplazamiento p : puntos) {
            serie.add(p.ciclo(), p.acumulado(), false);
        }
        return serie;
    }

    /** Ejes y trazos comunes; cada línea más gruesa que la siguiente para que las superpuestas se distingan. */
    private static JFreeChart armar(String titulo, XYSeriesCollection datos) {
        JFreeChart grafico = ChartFactory.createXYLineChart(titulo, "Ciclo de reloj", "Posiciones recorridas", datos,
                PlotOrientation.VERTICAL, true, true, false);
        XYPlot plot = grafico.getXYPlot();
        for (int i = 0; i < datos.getSeriesCount(); i++) {
            plot.getRenderer().setSeriesStroke(i, new BasicStroke(Math.max(1.5f, 4f - 0.5f * i)));
        }
        plot.getDomainAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        plot.getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        return grafico;
    }
}
