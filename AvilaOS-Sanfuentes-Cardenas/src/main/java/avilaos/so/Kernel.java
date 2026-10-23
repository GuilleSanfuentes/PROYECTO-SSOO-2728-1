package avilaos.so;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.TipoInstruccion;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Comparador;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.CPU;
import avilaos.hardware.ContextoCPU;
import avilaos.hardware.DMA;
import avilaos.hardware.Interrupcion;
import avilaos.hardware.RAM;
import avilaos.hardware.SistemaOperativo;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.programas.Instruccion;
import avilaos.so.programas.ProgramaIOBound;
import java.util.concurrent.Semaphore;

/**
 * Núcleo del SO de un computador: colas de procesos, admisión por RAM, planificación, despacho,
 * E/S, deadlines y envejecimiento. Todas sus estructuras se protegen con un único semáforo.
 */
public class Kernel implements SistemaOperativo {

    private static final Comparador<PCB> POR_PRIORIDAD_BASE = (a, b) -> Integer.compare(a.getPrioridadBase(), b.getPrioridadBase());

    private final int idComputador;
    private final String nombreComputador;
    private final String origen;
    private final CPU cpu;
    private final RAM ram;
    private final DMA dma;
    private final Planificador planificador;
    private final LogEventos log;
    private final int envejecimientoCadaCiclos;
    private final Semaphore mutex = new Semaphore(1);

    private final Cola<PCB> nuevos = new Cola<>();
    private final Cola<PCB> listos = new Cola<>();
    private final ListaEnlazada<PCB> bloqueados = new ListaEnlazada<>();
    private final ListaEnlazada<PCB> terminados = new ListaEnlazada<>();
    private PCB enEjecucion;
    private int ciclosEnCPU;

    /** Crea el kernel del computador con su hardware, su planificador y el log compartido. */
    public Kernel(int idComputador, CPU cpu, RAM ram, DMA dma, Planificador planificador,
            int envejecimientoCadaCiclos, LogEventos log) {
        this.idComputador = idComputador;
        this.nombreComputador = "Computador " + idComputador;
        this.origen = "Kernel-PC" + idComputador;
        this.cpu = cpu;
        this.ram = ram;
        this.dma = dma;
        this.planificador = planificador;
        this.envejecimientoCadaCiclos = envejecimientoCadaCiclos;
        this.log = log;
    }

    /** Recibe un proceso nuevo en la cola de Nuevos. Rechaza los que nunca cabrían en la RAM. */
    public boolean agregarProceso(PCB pcb, long ciclo) {
        if (pcb.getMemoria() > ram.getCapacidad()) {
            Debug.error(origen, pcb.getEtiqueta() + " pide " + pcb.getMemoria() + " de memoria y la RAM total es "
                    + ram.getCapacidad() + "; nunca podría admitirse, se rechaza");
            return false;
        }
        if (pcb.getTipo().usaBuffer()) {
            Debug.error(origen, pcb.getEtiqueta() + ": productores y consumidores estarán disponibles en la etapa 5; se rechaza");
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            nuevos.encolar(pcb);
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + " '" + pcb.getNombre() + "' llega al " + nombreComputador
                    + " (cola de Nuevos, prioridad " + pcb.getPrioridadBase() + ")");
            return true;
        } finally {
            mutex.release();
        }
    }

    /** El cerebro del SO en cada ciclo: atiende, admite, ordena, expulsa y ejecuta. */
    @Override
    public void ejecutarCiclo(CPU cpuCiclo, ListaEnlazada<Interrupcion> interrupciones, long ciclo) {
        mutex.acquireUninterruptibly();
        try {
            atenderInterrupciones(interrupciones, ciclo);
            admitir(ciclo);
            planificador.ordenar(listos);
            if (planificador.debeExpulsar(enEjecucion, listos.verFrente(), ciclosEnCPU)) {
                expulsar(ciclo);
            }
            if (enEjecucion == null) {
                if (listos.estaVacia()) {
                    cpu.quedarOciosa();
                } else {
                    despachar(ciclo);
                }
                return;
            }
            ejecutarInstruccion(ciclo);
        } finally {
            mutex.release();
        }
    }

    /** Fin de cada ciclo: resta deadline, saca a los vencidos y envejece a los que esperan. */
    public void cerrarCiclo(long ciclo) {
        mutex.acquireUninterruptibly();
        try {
            ListaEnlazada<PCB> vencidos = new ListaEnlazada<>();
            descontarDeadlines(nuevos.getCabeza(), vencidos);
            descontarDeadlines(listos.getCabeza(), vencidos);
            descontarDeadlines(bloqueados.getCabeza(), vencidos);
            if (enEjecucion != null && enEjecucion.descontarDeadline()) {
                vencidos.agregarFinal(enEjecucion);
            }
            Nodo<PCB> vencido = vencidos.getCabeza();
            while (vencido != null) {
                terminarPorDeadline(vencido.getDato(), ciclo);
                vencido = vencido.getSiguiente();
            }
            if (envejecimientoCadaCiclos > 0) {
                Nodo<PCB> nodo = listos.getCabeza();
                while (nodo != null) {
                    PCB pcb = nodo.getDato();
                    int antes = pcb.getPrioridadEfectiva();
                    if (pcb.envejecer(envejecimientoCadaCiclos)) {
                        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " envejece en Listos: prioridad " + antes
                                + " -> " + pcb.getPrioridadEfectiva());
                    }
                    nodo = nodo.getSiguiente();
                }
            }
            planificador.ordenar(listos);
        } finally {
            mutex.release();
        }
    }

    /** Cambia la política de este computador; se aplica desde el ciclo siguiente. */
    public void cambiarPolitica(PoliticaTipo tipo, long ciclo) {
        mutex.acquireUninterruptibly();
        try {
            PoliticaTipo anterior = planificador.getTipo();
            planificador.cambiarPolitica(tipo);
            planificador.ordenar(listos);
            evento(ciclo, nombreComputador + " cambia su política de " + anterior.getNombre() + " a " + tipo.getNombre());
        } finally {
            mutex.release();
        }
    }

    /** Cambia el quantum de Round Robin de este computador. */
    public boolean cambiarQuantum(int quantum, long ciclo) {
        mutex.acquireUninterruptibly();
        try {
            boolean cambiado = planificador.setQuantum(quantum);
            if (cambiado) {
                evento(ciclo, nombreComputador + " cambia su quantum a " + quantum);
            }
            return cambiado;
        } finally {
            mutex.release();
        }
    }

    /** Devuelve a Listos los procesos cuya E/S terminó; si era su última instrucción, los termina. */
    private void atenderInterrupciones(ListaEnlazada<Interrupcion> interrupciones, long ciclo) {
        Nodo<Interrupcion> nodo = interrupciones.getCabeza();
        while (nodo != null) {
            Interrupcion irq = nodo.getDato();
            PCB pcb = buscar(bloqueados.getCabeza(), irq.idProceso());
            if (pcb == null) {
                Debug.info(origen, "interrupción de " + irq.etiqueta() + " ignorada: ya no está bloqueado");
            } else {
                bloqueados.eliminar(pcb);
                if (pcb.programaTerminado()) {
                    evento(ciclo, "Proceso " + pcb.getEtiqueta() + " termina su última E/S");
                    terminarProceso(pcb, ciclo, false);
                } else {
                    pcb.desbloquear(ciclo);
                    listos.encolar(pcb);
                    evento(ciclo, "Proceso " + pcb.getEtiqueta() + " se desbloquea (" + irq.tipo().getNombre() + ") y pasa a Listos");
                }
            }
            nodo = nodo.getSiguiente();
        }
    }

    /** Portero de la RAM: entra el más importante que quepa. */
    private void admitir(long ciclo) {
        nuevos.ordenar(POR_PRIORIDAD_BASE);
        int cantidad = nuevos.tamano();
        for (int i = 0; i < cantidad; i++) {
            PCB pcb = nuevos.desencolar();
            if (ram.cabe(pcb.getMemoria()) && ram.reservar(pcb.getEtiqueta(), pcb.getMemoria())) {
                pcb.cambiarEstado(EstadoProceso.LISTO);
                listos.encolar(pcb);
                evento(ciclo, nombreComputador + " admite " + pcb.getEtiqueta() + " en Listos (RAM " + ram.getUsada()
                        + "/" + ram.getCapacidad() + ")");
            } else {
                nuevos.encolar(pcb);
            }
        }
    }

    /** Saca al proceso de la CPU y lo manda de vuelta a la fila de Listos. */
    private void expulsar(long ciclo) {
        PCB expulsado = enEjecucion;
        String motivo = planificador.getTipo() == PoliticaTipo.ROUND_ROBIN
                ? "agotó su quantum de " + planificador.getQuantum()
                : "llega " + listos.verFrente().getEtiqueta() + " con mayor preferencia (" + planificador.describir() + ")";
        liberarCPU();
        expulsado.restaurarPrioridad();
        expulsado.cambiarEstado(EstadoProceso.LISTO);
        listos.encolar(expulsado);
        planificador.ordenar(listos);
        evento(ciclo, nombreComputador + " expulsa a " + expulsado.getEtiqueta() + ": " + motivo);
    }

    /** Cambio de contexto: carga en la CPU el primero de Listos; ocupa el ciclo en modo SO. */
    private void despachar(long ciclo) {
        PCB elegido = listos.desencolar();
        elegido.despachar(ciclo);
        cpu.cargarContexto(elegido.getId(), elegido.getEtiqueta(), elegido.getContador(), elegido.getMar());
        cpu.ejecutarCodigoSO("cambio de contexto: despacha " + elegido.getEtiqueta());
        enEjecucion = elegido;
        ciclosEnCPU = 0;
        evento(ciclo, nombreComputador + " selecciona el proceso " + elegido.getEtiqueta() + " (" + planificador.describir() + ")");
    }

    /** Ejecuta la instrucción del proceso en CPU; si pide E/S lo bloquea, si era la última lo termina. */
    private void ejecutarInstruccion(long ciclo) {
        PCB pcb = enEjecucion;
        Instruccion instruccion = pcb.instruccionActual();
        if (instruccion == null) {
            Debug.error(origen, pcb.getEtiqueta() + " en CPU sin instrucciones pendientes; se termina");
            cpu.ejecutarCodigoSO("termina " + pcb.getEtiqueta());
            liberarCPU();
            terminarProceso(pcb, ciclo, false);
            return;
        }
        cpu.ejecutarInstruccion(instruccion.modo(), instruccion.toString());
        pcb.avanzarContador();
        ciclosEnCPU++;
        if (instruccion.tipo() == TipoInstruccion.SOLICITAR_ES) {
            int duracion = ((ProgramaIOBound) pcb.getPrograma()).getDuracionES();
            liberarCPU();
            pcb.restaurarPrioridad();
            pcb.bloquear(MotivoBloqueo.ENTRADA_SALIDA, duracion + " ciclos en DMA", ciclo);
            bloqueados.agregarFinal(pcb);
            dma.solicitar(pcb.getId(), pcb.getEtiqueta(), duracion, "E/S");
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + " se bloquea por E/S (" + duracion + " ciclos)");
        } else if (pcb.programaTerminado()) {
            liberarCPU();
            terminarProceso(pcb, ciclo, false);
        }
    }

    /** Guarda el contexto del proceso en CPU y comprueba que los registros coinciden con su PCB. */
    private void liberarCPU() {
        ContextoCPU contexto = cpu.guardarContexto();
        if (contexto != null && (contexto.contador() != enEjecucion.getContador() || contexto.mar() != enEjecucion.getMar())) {
            Debug.error(origen, "registros de la CPU (Contador " + contexto.contador() + ") no coinciden con el PCB de "
                    + enEjecucion.getEtiqueta() + " (Contador " + enEjecucion.getContador() + ")");
        }
        enEjecucion = null;
        ciclosEnCPU = 0;
    }

    /** Saca al proceso de donde esté (CPU, cola o DMA) y lo termina por deadline vencido. */
    private void terminarPorDeadline(PCB pcb, long ciclo) {
        if (pcb == enEjecucion) {
            liberarCPU();
        } else if (!nuevos.eliminar(pcb) && !listos.eliminar(pcb) && bloqueados.eliminar(pcb)
                && pcb.getMotivoBloqueo() == MotivoBloqueo.ENTRADA_SALIDA) {
            dma.cancelar(pcb.getId());
        }
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " alcanza su deadline y es terminado (estaba en "
                + pcb.getEstado().getNombre() + ")");
        terminarProceso(pcb, ciclo, true);
    }

    /** Pasa el proceso a Terminados, libera su RAM y revisa si algún proceso de Nuevos puede entrar. */
    private void terminarProceso(PCB pcb, long ciclo, boolean porDeadline) {
        boolean teniaMemoria = pcb.getEstado() != EstadoProceso.NUEVO;
        pcb.terminar(ciclo, porDeadline);
        terminados.agregarFinal(pcb);
        if (!porDeadline) {
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + " termina en el " + nombreComputador);
        }
        if (teniaMemoria) {
            ram.liberar(pcb.getEtiqueta());
            admitir(ciclo);
        }
    }

    /** Resta un ciclo al deadline de cada proceso y anota los que llegan a 0. */
    private void descontarDeadlines(Nodo<PCB> nodo, ListaEnlazada<PCB> vencidos) {
        while (nodo != null) {
            if (nodo.getDato().descontarDeadline()) {
                vencidos.agregarFinal(nodo.getDato());
            }
            nodo = nodo.getSiguiente();
        }
    }

    /** Busca un proceso por ID recorriendo desde el nodo dado. */
    private PCB buscar(Nodo<PCB> nodo, int id) {
        while (nodo != null) {
            if (nodo.getDato().getId() == id) {
                return nodo.getDato();
            }
            nodo = nodo.getSiguiente();
        }
        return null;
    }

    /** Registra un evento en el log del sistema. */
    private void evento(long ciclo, String texto) {
        log.registrar(ciclo, texto);
    }

    /** Copia de la cola de Nuevos en su orden actual. */
    public ListaEnlazada<PCB> getNuevos() {
        return copiar(EstadoProceso.NUEVO);
    }

    /** Copia de la cola de Listos en su orden actual (el frente es el próximo a ejecutar). */
    public ListaEnlazada<PCB> getListos() {
        return copiar(EstadoProceso.LISTO);
    }

    /** Copia de la lista de Bloqueados. */
    public ListaEnlazada<PCB> getBloqueados() {
        return copiar(EstadoProceso.BLOQUEADO);
    }

    /** Copia de la lista de Terminados. */
    public ListaEnlazada<PCB> getTerminados() {
        return copiar(EstadoProceso.TERMINADO);
    }

    /** Copia de todos los procesos del computador, en cualquier estado. */
    public ListaEnlazada<PCB> getTodos() {
        mutex.acquireUninterruptibly();
        try {
            ListaEnlazada<PCB> todos = new ListaEnlazada<>();
            agregarTodos(todos, nuevos.getCabeza());
            agregarTodos(todos, listos.getCabeza());
            if (enEjecucion != null) {
                todos.agregarFinal(enEjecucion);
            }
            agregarTodos(todos, bloqueados.getCabeza());
            agregarTodos(todos, terminados.getCabeza());
            return todos;
        } finally {
            mutex.release();
        }
    }

    /** Proceso en ejecución o null. */
    public PCB getEnEjecucion() {
        mutex.acquireUninterruptibly();
        try {
            return enEjecucion;
        } finally {
            mutex.release();
        }
    }

    /** Ciclos seguidos que lleva en CPU el proceso actual. */
    public int getCiclosEnCPU() {
        return ciclosEnCPU;
    }

    /** Procesos que aún no terminaron (carga del computador). */
    public int procesosVivos() {
        mutex.acquireUninterruptibly();
        try {
            return nuevos.tamano() + listos.tamano() + bloqueados.tamano() + (enEjecucion == null ? 0 : 1);
        } finally {
            mutex.release();
        }
    }

    public String describirPlanificador() {
        return planificador.describir();
    }

    public PoliticaTipo getPolitica() {
        return planificador.getTipo();
    }

    public int getQuantum() {
        return planificador.getQuantum();
    }

    public int getIdComputador() {
        return idComputador;
    }

    /** Copia, en exclusión mutua, la cola o lista que corresponde al estado indicado. */
    private ListaEnlazada<PCB> copiar(EstadoProceso estado) {
        mutex.acquireUninterruptibly();
        try {
            ListaEnlazada<PCB> copia = new ListaEnlazada<>();
            Nodo<PCB> cabeza = switch (estado) {
                case NUEVO -> nuevos.getCabeza();
                case LISTO -> listos.getCabeza();
                case BLOQUEADO -> bloqueados.getCabeza();
                case TERMINADO -> terminados.getCabeza();
                case EJECUCION -> null;
            };
            agregarTodos(copia, cabeza);
            return copia;
        } finally {
            mutex.release();
        }
    }

    /** Agrega a destino todos los procesos desde el nodo dado. */
    private void agregarTodos(ListaEnlazada<PCB> destino, Nodo<PCB> nodo) {
        while (nodo != null) {
            destino.agregarFinal(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
    }
}
