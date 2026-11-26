package avilaos.metricas;

import avilaos.cluster.Cluster;
import avilaos.cluster.Computador;
import avilaos.enums.EstadoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.so.ResumenComputador;
import avilaos.so.ResumenProceso;
import avilaos.sync.Buffer;
import avilaos.sync.EstadoBuffer;

/** Calcula las métricas acumuladas a partir del estado actual del clúster; no guarda nada. */
public final class CalculadorMetricas {

    private CalculadorMetricas() {
    }

    /** Métricas de un computador: sus procesos y los contadores de su CPU. */
    public static Metricas computador(Cluster cluster, int numero) {
        Computador c = cluster.getComputador(numero);
        long ciclo = cluster.getReloj().getCiclo();
        ResumenComputador foto = c.getKernel().getResumen(ciclo);
        return calcular(c.getNombre(), foto.procesos(), foto.ciclosUsuario(), foto.ciclosSO(), foto.ciclosOciosos(), ciclo);
    }

    /** Métricas de todo el clúster: todos los procesos y la suma de los contadores de todas las CPUs. */
    public static Metricas global(Cluster cluster) {
        long ciclo = cluster.getReloj().getCiclo();
        ResumenComputador foto = resumenCluster(cluster, ciclo);
        return calcular("Clúster", foto.procesos(), foto.ciclosUsuario(), foto.ciclosSO(), foto.ciclosOciosos(), ciclo);
    }

    /** Suma las fotos de todos los computadores, cada uno copiado bajo su propio mutex. */
    private static ResumenComputador resumenCluster(Cluster cluster, long ciclo) {
        ListaEnlazada<ResumenProceso> todos = new ListaEnlazada<>();
        long usuario = 0;
        long so = 0;
        long ociosos = 0;
        for (int i = 0; i < cluster.cantidadComputadores(); i++) {
            ResumenComputador foto = cluster.getComputador(i).getKernel().getResumen(ciclo);
            Nodo<ResumenProceso> nodo = foto.procesos().getCabeza();
            while (nodo != null) {
                todos.agregarFinal(nodo.getDato());
                nodo = nodo.getSiguiente();
            }
            usuario += foto.ciclosUsuario();
            so += foto.ciclosSO();
            ociosos += foto.ciclosOciosos();
        }
        return new ResumenComputador(todos, usuario, so, ociosos);
    }

    /** Una fila por buffer: elementos producidos y consumidos y tiempo promedio que sus procesos pasaron bloqueados en semáforos. */
    public static ListaEnlazada<MetricasBuffer> buffers(Cluster cluster) {
        ListaEnlazada<MetricasBuffer> filas = new ListaEnlazada<>();
        ListaEnlazada<ResumenProceso> procesos = resumenCluster(cluster, cluster.getReloj().getCiclo()).procesos();
        Nodo<Buffer> nodo = cluster.getBuffers().copiar().getCabeza();
        while (nodo != null) {
            EstadoBuffer e = nodo.getDato().estado();
            int cantidad = 0;
            long bloqueado = 0;
            Nodo<ResumenProceso> proceso = procesos.getCabeza();
            while (proceso != null) {
                if (e.nombre().equals(proceso.getDato().buffer())) {
                    cantidad++;
                    bloqueado += proceso.getDato().ciclosEnSemaforo();
                }
                proceso = proceso.getSiguiente();
            }
            filas.agregarFinal(new MetricasBuffer(e.nombre(), e.producidos(), e.consumidos(), cantidad,
                    cantidad == 0 ? Metricas.SIN_DATO : (double) bloqueado / cantidad));
            nodo = nodo.getSiguiente();
        }
        return filas;
    }

    /** Calcula las métricas de un grupo de procesos con los ciclos de CPU indicados. */
    static Metricas calcular(String nombre, ListaEnlazada<ResumenProceso> procesos, long usuario, long so, long ociosos,
            long ciclos) {
        int terminadosOk = 0;
        int vencidos = 0;
        int vivos = 0;
        int conRespuesta = 0;
        long sumaRespuesta = 0;
        Nodo<ResumenProceso> nodo = procesos.getCabeza();
        while (nodo != null) {
            ResumenProceso p = nodo.getDato();
            if (p.estado() != EstadoProceso.TERMINADO) {
                vivos++;
            } else if (p.deadlineVencido()) {
                vencidos++;
            } else {
                terminadosOk++;
            }
            if (p.tiempoRespuesta() >= 0) {
                conRespuesta++;
                sumaRespuesta += p.tiempoRespuesta();
            }
            nodo = nodo.getSiguiente();
        }
        double[] retornos = new double[terminadosOk];
        int indice = 0;
        nodo = procesos.getCabeza();
        while (nodo != null) {
            ResumenProceso p = nodo.getDato();
            if (p.estado() == EstadoProceso.TERMINADO && !p.deadlineVencido()) {
                retornos[indice++] = (double) p.tiempoRetorno() / p.instrucciones();
            }
            nodo = nodo.getSiguiente();
        }
        long total = usuario + so + ociosos;
        int terminados = terminadosOk + vencidos;
        return new Metricas(nombre, ciclos, terminadosOk, vencidos, vivos,
                ciclos == 0 ? 0 : (double) terminadosOk / ciclos,
                total == 0 ? 0 : 100.0 * (usuario + so) / total,
                total == 0 ? 0 : 100.0 * usuario / total,
                total == 0 ? 0 : 100.0 * so / total,
                conRespuesta == 0 ? Metricas.SIN_DATO : (double) sumaRespuesta / conRespuesta,
                terminados == 0 ? Metricas.SIN_DATO : 100.0 * terminadosOk / terminados,
                indiceJain(retornos));
    }

    /** Mide qué tan parejo trató el planificador a los procesos: con el tiempo de retorno ÷ instrucciones de cada uno, eleva al cuadrado la suma y la divide entre la cantidad de procesos por la suma de los cuadrados; 1 = reparto justo, más bajo = más desigual, -1 si no hay procesos. */
    public static double indiceJain(double[] valores) {
        if (valores.length == 0) {
            return Metricas.SIN_DATO;
        }
        double suma = 0;
        double sumaCuadrados = 0;
        for (double valor : valores) {
            suma += valor;
            sumaCuadrados += valor * valor;
        }
        return sumaCuadrados == 0 ? 1.0 : suma * suma / (valores.length * sumaCuadrados);
    }
}
