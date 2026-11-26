package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.cluster.Computador;
import avilaos.metricas.MetricasDisco;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import avilaos.metricas.CalculadorMetricas;
import avilaos.metricas.MuestraUtilizacion;
import avilaos.metricas.Metricas;
import avilaos.metricas.MetricasBuffer;
import avilaos.so.PCB;
import avilaos.sync.Buffer;

/**
 * Foto inmutable del clúster en un ciclo: computadores, buffers, métricas, puntos del gráfico, las últimas líneas del log y
 * de la consola y, en el Proyecto 2, el disco, el sistema de archivos y las métricas del disco (estas solo cada cierto
 * número de ciclos o con el reloj quieto, porque copian toda la serie).
 */
public record FotoCluster(long ciclo, FotoComputador[] computadores, FilaBuffer[] buffers, String[] log,
        String[] consola, Metricas[] metricas, MetricasBuffer[] metricasBuffers, SerieGrafico[] series,
        FotoDisco disco, FotoArchivos archivos, MetricasDisco metricasDisco) {

    public static final int LINEAS_LOG = 500;
    public static final int LINEAS_CONSOLA = 1000;
    public static final int PUNTOS_GRAFICO = 1000;
    public static final int CICLOS_ENTRE_METRICAS_DISCO = 10;

    /** Toma la foto del clúster en este instante. */
    public static FotoCluster de(Cluster cluster) {
        FotoComputador[] fotos = new FotoComputador[cluster.cantidadComputadores()];
        for (int i = 0; i < fotos.length; i++) {
            fotos[i] = FotoComputador.de(cluster.getComputador(i));
        }
        ListaEnlazada<Buffer> lista = cluster.getBuffers().copiar();
        FilaBuffer[] buffers = new FilaBuffer[lista.tamano()];
        Nodo<Buffer> nodo = lista.getCabeza();
        ListaEnlazada<PCB> procesos = lista.estaVacia() ? new ListaEnlazada<>() : cluster.todosLosProcesos();
        for (int i = 0; i < buffers.length && nodo != null; i++) {
            Buffer b = nodo.getDato();
            buffers[i] = FilaBuffer.de(b.estado(), FilaBuffer.usuarios(b.getNombre(), b.getAnfitrion(), procesos));
            nodo = nodo.getSiguiente();
        }
        long ciclo = cluster.getReloj().getCiclo();
        Computador primero = cluster.getComputador(0);
        boolean quieto = !cluster.getReloj().isEjecutando() || cluster.getReloj().isPausado();
        MetricasDisco metricasDisco = primero.getDisco() != null && (quieto || ciclo % CICLOS_ENTRE_METRICAS_DISCO == 0)
                ? cluster.getMetricasDisco() : null;
        return new FotoCluster(ciclo, fotos, buffers,
                cluster.getLog().copiarUltimos(LINEAS_LOG), Debug.getConsola().copiarUltimos(LINEAS_CONSOLA),
                metricas(cluster), metricasBuffers(cluster), series(cluster), FotoDisco.de(primero, ciclo),
                FotoArchivos.de(primero.getSistemaArchivos()), metricasDisco);
    }

    /** Indica si es una foto del Proyecto 2 (computador con disco y sistema de archivos). */
    public boolean conDisco() {
        return disco != null;
    }

    /** Métricas de cada computador y, al final, las del clúster completo. */
    private static Metricas[] metricas(Cluster cluster) {
        int n = cluster.cantidadComputadores();
        Metricas[] filas = new Metricas[n + 1];
        for (int i = 0; i < n; i++) {
            filas[i] = CalculadorMetricas.computador(cluster, i);
        }
        filas[n] = CalculadorMetricas.global(cluster);
        return filas;
    }

    private static MetricasBuffer[] metricasBuffers(Cluster cluster) {
        ListaEnlazada<MetricasBuffer> lista = CalculadorMetricas.buffers(cluster);
        MetricasBuffer[] filas = new MetricasBuffer[lista.tamano()];
        Nodo<MetricasBuffer> nodo = lista.getCabeza();
        for (int i = 0; i < filas.length && nodo != null; i++) {
            filas[i] = nodo.getDato();
            nodo = nodo.getSiguiente();
        }
        return filas;
    }

    /** Últimos puntos de utilización de cada computador. */
    private static SerieGrafico[] series(Cluster cluster) {
        SerieGrafico[] series = new SerieGrafico[cluster.getUtilizacion().cantidadSeries()];
        for (int i = 0; i < series.length; i++) {
            ListaEnlazada<MuestraUtilizacion> muestras = cluster.getUtilizacion().muestras(i);
            int saltar = Math.max(0, muestras.tamano() - PUNTOS_GRAFICO);
            double[] ciclos = new double[muestras.tamano() - saltar];
            double[] porcentajes = new double[ciclos.length];
            Nodo<MuestraUtilizacion> nodo = muestras.getCabeza();
            for (int k = 0; nodo != null; k++) {
                if (k >= saltar) {
                    ciclos[k - saltar] = nodo.getDato().ciclo();
                    porcentajes[k - saltar] = nodo.getDato().porcentaje();
                }
                nodo = nodo.getSiguiente();
            }
            series[i] = new SerieGrafico(cluster.getUtilizacion().nombre(i), ciclos, porcentajes);
        }
        return series;
    }
}
