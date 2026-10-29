package avilaos.metricas;

import avilaos.cluster.Cluster;
import avilaos.cluster.Computador;
import avilaos.enums.EstadoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.CPU;
import avilaos.so.PCB;
import avilaos.so.programas.ProgramaBuffer;
import avilaos.sync.Buffer;
import avilaos.sync.EstadoBuffer;

/** Calcula las métricas acumuladas a partir del estado actual del clúster; no guarda nada. */
public final class CalculadorMetricas {

    private CalculadorMetricas() {
    }

    /** Métricas de un computador: sus procesos y los contadores de su CPU. */
    public static Metricas computador(Cluster cluster, int numero) {
        Computador c = cluster.getComputador(numero);
        CPU cpu = c.getCpu();
        return calcular(c.getNombre(), c.getKernel().getTodos(), cpu.getCiclosUsuario(), cpu.getCiclosSO(),
                cpu.getCiclosOciosos(), cluster.getReloj().getCiclo());
    }

    /** Métricas de todo el clúster: todos los procesos y la suma de los contadores de todas las CPUs. */
    public static Metricas global(Cluster cluster) {
        long usuario = 0;
        long so = 0;
        long ociosos = 0;
        for (int i = 0; i < cluster.cantidadComputadores(); i++) {
            CPU cpu = cluster.getComputador(i).getCpu();
            usuario += cpu.getCiclosUsuario();
            so += cpu.getCiclosSO();
            ociosos += cpu.getCiclosOciosos();
        }
        return calcular("Clúster", cluster.todosLosProcesos(), usuario, so, ociosos, cluster.getReloj().getCiclo());
    }

    /** Una fila por buffer: elementos producidos y consumidos y tiempo promedio que sus procesos pasaron bloqueados en semáforos. */
    public static ListaEnlazada<MetricasBuffer> buffers(Cluster cluster) {
        ListaEnlazada<MetricasBuffer> filas = new ListaEnlazada<>();
        ListaEnlazada<PCB> procesos = cluster.todosLosProcesos();
        long ciclo = cluster.getReloj().getCiclo();
        Nodo<Buffer> nodo = cluster.getBuffers().copiar().getCabeza();
        while (nodo != null) {
            EstadoBuffer e = nodo.getDato().estado();
            int cantidad = 0;
            long bloqueado = 0;
            Nodo<PCB> proceso = procesos.getCabeza();
            while (proceso != null) {
                if (proceso.getDato().getPrograma() instanceof ProgramaBuffer programa && programa.getBuffer().equals(e.nombre())) {
                    cantidad++;
                    bloqueado += proceso.getDato().getCiclosBloqueadoSemaforoHasta(ciclo);
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
    static Metricas calcular(String nombre, ListaEnlazada<PCB> procesos, long usuario, long so, long ociosos, long ciclos) {
        int terminadosOk = 0;
        int vencidos = 0;
        int vivos = 0;
        int conRespuesta = 0;
        long sumaRespuesta = 0;
        Nodo<PCB> nodo = procesos.getCabeza();
        while (nodo != null) {
            PCB p = nodo.getDato();
            if (p.getEstado() != EstadoProceso.TERMINADO) {
                vivos++;
            } else if (p.isDeadlineVencido()) {
                vencidos++;
            } else {
                terminadosOk++;
            }
            if (p.getTiempoRespuesta() >= 0) {
                conRespuesta++;
                sumaRespuesta += p.getTiempoRespuesta();
            }
            nodo = nodo.getSiguiente();
        }
        double[] retornos = new double[terminadosOk];
        int indice = 0;
        nodo = procesos.getCabeza();
        while (nodo != null) {
            PCB p = nodo.getDato();
            if (p.getEstado() == EstadoProceso.TERMINADO && !p.isDeadlineVencido()) {
                retornos[indice++] = (double) p.getTiempoRetorno() / p.getPrograma().totalInstrucciones();
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
