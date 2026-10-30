package avilaos.vista;

import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import avilaos.metricas.MuestraUtilizacion;
import avilaos.metricas.RegistroUtilizacion;
import java.awt.BasicStroke;
import java.io.File;
import java.io.IOException;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/** Gráfico de utilización de la CPU de cada computador en el tiempo (JFreeChart); se puede exportar a PNG. */
public final class GraficoUtilizacion {

    public static final String RUTA_POR_DEFECTO = "salida/utilizacion_cpu.png";

    private static final String ORIGEN = "Gráfico";
    private static final int ANCHO_PNG = 1000;
    private static final int ALTO_PNG = 500;

    private GraficoUtilizacion() {
    }

    /** Arma el gráfico con una línea por computador: ciclo en el eje X y porcentaje de CPU ocupada en el eje Y. */
    public static JFreeChart crear(RegistroUtilizacion registro) {
        XYSeriesCollection datos = new XYSeriesCollection();
        for (int i = 0; i < registro.cantidadSeries(); i++) {
            XYSeries serie = new XYSeries(registro.nombre(i));
            Nodo<MuestraUtilizacion> nodo = registro.muestras(i).getCabeza();
            while (nodo != null) {
                serie.add(nodo.getDato().ciclo(), nodo.getDato().porcentaje());
                nodo = nodo.getSiguiente();
            }
            datos.addSeries(serie);
        }
        return armar(datos);
    }

    /** Arma el gráfico con una serie vacía por nombre, para ir llenándolo en vivo desde la GUI. */
    public static JFreeChart crearVacio(String[] nombres) {
        XYSeriesCollection datos = new XYSeriesCollection();
        for (String nombre : nombres) {
            datos.addSeries(new XYSeries(nombre));
        }
        return armar(datos);
    }

    private static JFreeChart armar(XYSeriesCollection datos) {
        JFreeChart grafico = ChartFactory.createXYLineChart(
                "Utilización de CPU por computador (ventana de " + RegistroUtilizacion.VENTANA + " ciclos)",
                "Ciclo de reloj", "CPU ocupada (%)", datos, PlotOrientation.VERTICAL, true, true, false);
        XYPlot plot = grafico.getXYPlot();
        plot.getRangeAxis().setRange(-5, 105);
        for (int i = 0; i < datos.getSeriesCount(); i++) {
            plot.getRenderer().setSeriesStroke(i, new BasicStroke(Math.max(1.5f, 3.5f - i)));
        }
        plot.getDomainAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        return grafico;
    }

    /** Guarda el gráfico como imagen PNG en la ruta indicada, creando la carpeta si hace falta. Devuelve false si falla. */
    public static boolean exportarPNG(RegistroUtilizacion registro, String ruta) {
        System.setProperty("java.awt.headless", "true");
        File archivo = new File(ruta);
        File carpeta = archivo.getAbsoluteFile().getParentFile();
        if (carpeta != null && !carpeta.isDirectory() && !carpeta.mkdirs()) {
            Debug.error(ORIGEN, "no se pudo crear la carpeta " + carpeta);
            return false;
        }
        return guardar(crear(registro), archivo);
    }

    /** Guarda como PNG el gráfico indicado (por ejemplo el que muestra la ventana). Devuelve false si falla. */
    public static boolean exportarPNG(JFreeChart grafico, File archivo) {
        File carpeta = archivo.getAbsoluteFile().getParentFile();
        if (carpeta != null && !carpeta.isDirectory() && !carpeta.mkdirs()) {
            Debug.error(ORIGEN, "no se pudo crear la carpeta " + carpeta);
            return false;
        }
        return guardar(grafico, archivo);
    }

    private static boolean guardar(JFreeChart grafico, File archivo) {
        try {
            ChartUtils.saveChartAsPNG(archivo, grafico, ANCHO_PNG, ALTO_PNG);
        } catch (IOException e) {
            Debug.error(ORIGEN, "no se pudo guardar " + archivo + ": " + e.getMessage());
            return false;
        }
        Debug.ok(ORIGEN, "gráfico de utilización guardado en " + archivo);
        return true;
    }
}
