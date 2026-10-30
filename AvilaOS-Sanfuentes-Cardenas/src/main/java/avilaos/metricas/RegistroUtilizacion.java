package avilaos.metricas;

import avilaos.estructuras.ListaEnlazada;
import avilaos.log.Debug;
import java.util.concurrent.Semaphore;

/** Utilización de la CPU de cada computador en el tiempo, medida sobre una ventana de ciclos; alimenta el gráfico. */
public class RegistroUtilizacion {

    public static final int VENTANA = 20;

    private static final String ORIGEN = "RegistroUtilizacion";

    private final SerieUtilizacion[] series;
    private final Semaphore mutex = new Semaphore(1);

    /** Crea una serie por computador; ocupadosIniciales[i] son los ciclos ocupados de su CPU al empezar. */
    public RegistroUtilizacion(String[] nombres, long[] ocupadosIniciales) {
        series = new SerieUtilizacion[nombres.length];
        for (int i = 0; i < nombres.length; i++) {
            series[i] = new SerieUtilizacion(nombres[i], VENTANA, ocupadosIniciales[i]);
        }
    }

    /** Anota la muestra de un computador al cerrar el ciclo; ocupados es el total acumulado de ciclos ocupados de su CPU. */
    public void registrar(long ciclo, int computador, long ocupados) {
        if (computador < 0 || computador >= series.length) {
            Debug.error(ORIGEN, "el computador " + computador + " no tiene serie de utilización");
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            series[computador].agregar(ciclo, ocupados);
        } finally {
            mutex.release();
        }
    }

    /** Copia de las muestras de un computador en orden de ciclo; vacía si el computador no existe. */
    public ListaEnlazada<MuestraUtilizacion> muestras(int computador) {
        if (computador < 0 || computador >= series.length) {
            Debug.error(ORIGEN, "el computador " + computador + " no tiene serie de utilización");
            return new ListaEnlazada<>();
        }
        mutex.acquireUninterruptibly();
        try {
            return series[computador].copiar();
        } finally {
            mutex.release();
        }
    }

    /** Nombre del computador al que pertenece la serie. */
    public String nombre(int computador) {
        return series[computador].getNombre();
    }

    public int cantidadSeries() {
        return series.length;
    }
}
