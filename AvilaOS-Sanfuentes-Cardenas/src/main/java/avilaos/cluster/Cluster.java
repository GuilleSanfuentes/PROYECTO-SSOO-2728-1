package avilaos.cluster;

import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.ObservadorReloj;
import avilaos.hardware.Reloj;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.DatosProceso;
import avilaos.so.FabricaProcesos;
import avilaos.so.PCB;

/** ÁvilaOS completo: reloj global, computadores y puerta de entrada para crear procesos y cambiar parámetros. */
public class Cluster implements ObservadorReloj {

    private static final String ORIGEN = "ÁvilaOS";

    private final ConfiguracionSistema config;
    private final LogEventos log;
    private final Reloj reloj;
    private final Computador[] computadores;
    private final ListaEnlazada<ObservadorReloj> observadores = new ListaEnlazada<>();
    private volatile int maxCiclos;

    /** Arma el clúster según la configuración: un reloj, N computadores y sus hilos. */
    public Cluster(ConfiguracionSistema config, LogEventos log) {
        this.config = config;
        this.log = log;
        this.maxCiclos = config.getMaxCiclos();
        this.reloj = new Reloj(config.getDuracionCicloMs());
        int faseES = reloj.agregarFase("E/S y red");
        int faseCPU = reloj.agregarFase("CPU");
        this.computadores = new Computador[config.getComputadores().tamano()];
        Nodo<ConfigComputador> nodo = config.getComputadores().getCabeza();
        for (int i = 0; i < computadores.length; i++) {
            computadores[i] = new Computador(i, nodo.getDato(), config.getEnvejecimientoCadaCiclos(), log);
            computadores[i].conectar(reloj, faseES, faseCPU);
            nodo = nodo.getSiguiente();
        }
        reloj.agregarObservador(this);
        Debug.ok(ORIGEN, "clúster listo con " + computadores.length + " computadores; fases: " + reloj.describirFases());
    }

    /** Crea los procesos de la carga inicial de la configuración. Devuelve cuántos se crearon. */
    public int cargarProcesosIniciales() {
        int creados = 0;
        Nodo<DatosProceso> nodo = config.getProcesos().getCabeza();
        while (nodo != null) {
            if (crearProceso(nodo.getDato()) != null) {
                creados++;
            }
            nodo = nodo.getSiguiente();
        }
        Debug.ok(ORIGEN, creados + " de " + config.getProcesos().tamano() + " procesos iniciales creados");
        return creados;
    }

    /** Crea un proceso y lo entrega al computador indicado, o al de menor carga si es automático. */
    public PCB crearProceso(DatosProceso datos) {
        long ciclo = reloj.getCiclo();
        if (datos != null && datos.tipo() != null && datos.tipo().usaBuffer()) {
            Debug.info(ORIGEN, "'" + datos.nombre() + "' es " + datos.tipo().getNombre()
                    + ": productores y consumidores estarán disponibles en la etapa 5; se omite");
            return null;
        }
        if (datos != null && datos.computador() >= computadores.length) {
            Debug.error(ORIGEN, "'" + datos.nombre() + "': el computador " + datos.computador() + " no existe (0.."
                    + (computadores.length - 1) + " o -1)");
            return null;
        }
        PCB pcb = FabricaProcesos.crear(datos, ciclo);
        if (pcb == null) {
            return null;
        }
        Computador destino;
        if (datos.computador() == DatosProceso.ASIGNACION_AUTOMATICA) {
            destino = elegirComputador(pcb.getMemoria());
            if (destino == null) {
                Debug.error(ORIGEN, pcb.getEtiqueta() + " pide " + pcb.getMemoria() + " de memoria y no cabe en ningún computador");
                return null;
            }
            pcb.asignarComputador(destino.getId());
            log.registrar(ciclo, "ÁvilaOS asigna " + pcb.getEtiqueta() + " al " + destino.getNombre()
                    + " (menor carga: " + destino.carga() + " procesos, " + destino.getRam().getLibre() + " de RAM libre)");
        } else {
            destino = computadores[datos.computador()];
        }
        return destino.getKernel().agregarProceso(pcb, ciclo) ? pcb : null;
    }

    /** Busca el computador menos cargado para el proceso nuevo. */
    private Computador elegirComputador(int memoria) {
        Computador mejor = null;
        for (Computador c : computadores) {
            if (memoria > c.getRam().getCapacidad()) {
                continue;
            }
            if (mejor == null || c.carga() < mejor.carga()
                    || (c.carga() == mejor.carga() && c.getRam().getLibre() > mejor.getRam().getLibre())) {
                mejor = c;
            }
        }
        return mejor;
    }

    /** Cambia la política de un computador. */
    public boolean cambiarPolitica(int computador, PoliticaTipo tipo) {
        if (!existe(computador) || tipo == null) {
            Debug.error(ORIGEN, "cambiarPolitica: computador " + computador + " o política inválidos");
            return false;
        }
        computadores[computador].getKernel().cambiarPolitica(tipo, reloj.getCiclo());
        return true;
    }

    /** Cambia el quantum de Round Robin de un computador. */
    public boolean cambiarQuantum(int computador, int quantum) {
        if (!existe(computador)) {
            Debug.error(ORIGEN, "cambiarQuantum: el computador " + computador + " no existe");
            return false;
        }
        return computadores[computador].getKernel().cambiarQuantum(quantum, reloj.getCiclo());
    }

    /** Cambia la duración de cada ciclo de reloj. */
    public boolean cambiarDuracionCiclo(int ms) {
        boolean cambiado = reloj.setDuracionCicloMs(ms);
        if (cambiado) {
            log.registrar(reloj.getCiclo(), "Duración del ciclo cambiada a " + ms + " ms");
        }
        return cambiado;
    }

    /** Cambia el máximo de ciclos (0 = sin límite). */
    public boolean cambiarMaxCiclos(int ciclos) {
        if (ciclos < 0) {
            Debug.error(ORIGEN, "máximo de ciclos " + ciclos + " inválido (>= 0)");
            return false;
        }
        maxCiclos = ciclos;
        Debug.ok(ORIGEN, "máximo de ciclos = " + (ciclos == 0 ? "sin límite" : ciclos));
        return true;
    }

    /** Agrega un observador que se avisa al final de cada ciclo, después de los kernels. */
    public void agregarObservador(ObservadorReloj observador) {
        observadores.agregarFinal(observador);
    }

    /** Cierre del ciclo: cada kernel hace su contabilidad, se avisa a los observadores y se revisa el máximo de ciclos. */
    @Override
    public void alCerrarCiclo(long ciclo) {
        for (Computador c : computadores) {
            c.getKernel().cerrarCiclo(ciclo);
        }
        Nodo<ObservadorReloj> nodo = observadores.getCabeza();
        while (nodo != null) {
            nodo.getDato().alCerrarCiclo(ciclo);
            nodo = nodo.getSiguiente();
        }
        int limite = maxCiclos;
        if (limite > 0 && ciclo >= limite && reloj.isEjecutando()) {
            log.registrar(ciclo, "Simulación detenida: se alcanzó el máximo de " + limite + " ciclos");
            reloj.detener();
        }
    }

    /** Arranca el reloj global. */
    public boolean iniciar() {
        log.registrar(reloj.getCiclo(), "Simulación iniciada (" + reloj.getDuracionCicloMs() + " ms por ciclo, máximo "
                + (maxCiclos == 0 ? "sin límite" : maxCiclos + " ciclos") + ")");
        return reloj.iniciar();
    }

    public void pausar() {
        reloj.pausar();
    }

    public void reanudar() {
        reloj.reanudar();
    }

    /** Detiene la simulación (botón Stop). */
    public void detener() {
        if (reloj.isEjecutando()) {
            log.registrar(reloj.getCiclo(), "Simulación detenida por el usuario");
        }
        reloj.detener();
    }

    /** Espera a que la simulación termine, como máximo maximoMs (0 = sin límite). */
    public boolean esperarFin(long maximoMs) {
        return reloj.esperarFin(maximoMs);
    }

    /** Detiene todo y termina los hilos de los componentes. */
    public void apagar() {
        reloj.apagar();
    }

    /** Indica si todos los procesos de todos los computadores terminaron. */
    public boolean todosTerminados() {
        for (Computador c : computadores) {
            if (c.carga() > 0) {
                return false;
            }
        }
        return true;
    }

    /** Todos los procesos de todos los computadores. */
    public ListaEnlazada<PCB> todosLosProcesos() {
        ListaEnlazada<PCB> todos = new ListaEnlazada<>();
        for (Computador c : computadores) {
            Nodo<PCB> nodo = c.getKernel().getTodos().getCabeza();
            while (nodo != null) {
                todos.agregarFinal(nodo.getDato());
                nodo = nodo.getSiguiente();
            }
        }
        return todos;
    }

    private boolean existe(int computador) {
        return computador >= 0 && computador < computadores.length;
    }

    public Reloj getReloj() {
        return reloj;
    }

    public int cantidadComputadores() {
        return computadores.length;
    }

    /** Computador por número, o null si no existe. */
    public Computador getComputador(int numero) {
        return existe(numero) ? computadores[numero] : null;
    }

    public int getMaxCiclos() {
        return maxCiclos;
    }

    public LogEventos getLog() {
        return log;
    }
}
