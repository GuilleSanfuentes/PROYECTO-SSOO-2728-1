package avilaos.so;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.RolSemaforo;
import avilaos.enums.TipoInstruccion;
import avilaos.enums.TipoInterrupcion;
import avilaos.estructuras.Cola;
import avilaos.estructuras.Comparador;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.CPU;
import avilaos.hardware.ContextoCPU;
import avilaos.hardware.DMA;
import avilaos.hardware.Interrupcion;
import avilaos.hardware.LineaInterrupciones;
import avilaos.hardware.RAM;
import avilaos.hardware.SistemaOperativo;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.programas.Instruccion;
import avilaos.so.programas.ProgramaBuffer;
import avilaos.so.programas.ProgramaIOBound;
import avilaos.sync.Buffer;
import avilaos.sync.RegistroBuffers;
import java.util.concurrent.Semaphore;

/**
 * Núcleo del SO de un computador: colas de procesos, admisión por RAM, planificación, despacho,
 * E/S, semáforos y buffers, deadlines y envejecimiento. Todas sus estructuras se protegen con un único semáforo.
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
    private final ListaEnlazada<Interrupcion> diferidas = new ListaEnlazada<>();
    private volatile RegistroBuffers buffers;
    private volatile LineaInterrupciones[] lineas;
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

    /** Conecta el registro de buffers del clúster y las líneas de interrupción de todos los computadores. */
    public void conectarSincronizacion(RegistroBuffers registro, LineaInterrupciones[] lineasClaster) {
        this.buffers = registro;
        this.lineas = lineasClaster;
    }

    /** Recibe un proceso nuevo en la cola de Nuevos. Rechaza los que nunca cabrían en la RAM. */
    public boolean agregarProceso(PCB pcb, long ciclo) {
        if (pcb.getMemoria() > ram.getCapacidad()) {
            Debug.error(origen, pcb.getEtiqueta() + " pide " + pcb.getMemoria() + " de memoria y la RAM total es "
                    + ram.getCapacidad() + "; nunca podría admitirse, se rechaza");
            return false;
        }
        if (pcb.getTipo().usaBuffer() && !bufferAceptable(pcb)) {
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

    /** Atiende las interrupciones pendientes; los despertares de semáforo emitidos en este mismo ciclo esperan al siguiente. */
    private void atenderInterrupciones(ListaEnlazada<Interrupcion> interrupciones, long ciclo) {
        ListaEnlazada<Interrupcion> aplazadas = new ListaEnlazada<>();
        recorrerInterrupciones(diferidas.getCabeza(), aplazadas, ciclo);
        recorrerInterrupciones(interrupciones.getCabeza(), aplazadas, ciclo);
        diferidas.vaciar();
        Nodo<Interrupcion> aplazada = aplazadas.getCabeza();
        while (aplazada != null) {
            diferidas.agregarFinal(aplazada.getDato());
            aplazada = aplazada.getSiguiente();
        }
    }

    /** Atiende cada interrupción desde el nodo dado; las de semáforo del ciclo actual se anotan en aplazadas. */
    private void recorrerInterrupciones(Nodo<Interrupcion> nodo, ListaEnlazada<Interrupcion> aplazadas, long ciclo) {
        while (nodo != null) {
            Interrupcion irq = nodo.getDato();
            if (irq.tipo() == TipoInterrupcion.SEMAFORO_SIGNAL && irq.ciclo() >= ciclo) {
                aplazadas.agregarFinal(irq);
            } else {
                atenderInterrupcion(irq, ciclo);
            }
            nodo = nodo.getSiguiente();
        }
    }

    /** Devuelve a Listos al proceso que esperaba (E/S o semáforo); si era su última instrucción, lo termina. */
    private void atenderInterrupcion(Interrupcion irq, long ciclo) {
        PCB pcb = buscar(bloqueados.getCabeza(), irq.idProceso());
        if (pcb == null) {
            Debug.info(origen, "interrupción de " + irq.etiqueta() + " ignorada: ya no está bloqueado");
            return;
        }
        bloqueados.eliminar(pcb);
        if (pcb.programaTerminado()) {
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + " termina su última espera");
            terminarProceso(pcb, ciclo, false);
        } else {
            pcb.desbloquear(ciclo);
            listos.encolar(pcb);
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + " se desbloquea (" + irq.tipo().getNombre() + ") y pasa a Listos");
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

    /** Ejecuta la instrucción del proceso en CPU; si lo bloquea (E/S o semáforo) libera la CPU, si era la última lo termina. */
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
        boolean sigueEnCPU = true;
        switch (instruccion.tipo()) {
            case SOLICITAR_ES -> {
                solicitarES(pcb, ciclo);
                sigueEnCPU = false;
            }
            case SEM_WAIT -> sigueEnCPU = semWait(pcb, instruccion.semaforo(), ciclo);
            case SEM_SIGNAL -> semSignal(pcb, instruccion.semaforo(), ciclo);
            case INSERTAR -> insertar(pcb, ciclo);
            case EXTRAER -> extraer(pcb, ciclo);
            default -> {
            }
        }
        if (sigueEnCPU && pcb.programaTerminado()) {
            liberarCPU();
            terminarProceso(pcb, ciclo, false);
        }
    }

    /** Bloquea al proceso por E/S y encarga la operación al DMA. */
    private void solicitarES(PCB pcb, long ciclo) {
        int duracion = ((ProgramaIOBound) pcb.getPrograma()).getDuracionES();
        liberarCPU();
        pcb.restaurarPrioridad();
        pcb.bloquear(MotivoBloqueo.ENTRADA_SALIDA, duracion + " ciclos en DMA", ciclo);
        bloqueados.agregarFinal(pcb);
        dma.solicitar(pcb.getId(), pcb.getEtiqueta(), duracion, "E/S");
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " se bloquea por E/S (" + duracion + " ciclos)");
    }

    /** Kernel ejecuta el semWait: le pide el permiso al semáforo; si lo obtiene, el proceso sigue en la CPU; si no, libera la CPU y deja al proceso Bloqueado por semáforo hasta que lo despierten (devuelve false). */
    private boolean semWait(PCB pcb, RolSemaforo rol, long ciclo) {
        Buffer buffer = bufferDe(pcb);
        if (buffer == null) {
            Debug.error(origen, pcb.getEtiqueta() + " ejecuta semWait sin buffer");
            return true;
        }
        if (buffer.esperar(rol, pcb)) {
            return true;
        }
        String semaforo = "semáforo '" + rol.getNombre() + "' de " + buffer.getNombre();
        liberarCPU();
        pcb.restaurarPrioridad();
        pcb.bloquear(MotivoBloqueo.SEMAFORO, semaforo, ciclo);
        bloqueados.agregarFinal(pcb);
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " se bloquea en el " + semaforo);
        return false;
    }

    /** Kernel ejecuta el semSignal: libera el permiso en el semáforo; si había alguien esperando, le avisa por interrupción para que despierte en el siguiente ciclo y pase a Listos; si no, el permiso queda libre. Quien señala sigue en la CPU. */
    private void semSignal(PCB pcb, RolSemaforo rol, long ciclo) {
        Buffer buffer = bufferDe(pcb);
        if (buffer == null) {
            Debug.error(origen, pcb.getEtiqueta() + " ejecuta semSignal sin buffer");
            return;
        }
        senalarYDespertar(buffer, rol, pcb, ciclo);
    }

    /** Señala el semáforo en nombre del proceso y, si había uno esperando, le avisa por interrupción. */
    private void senalarYDespertar(Buffer buffer, RolSemaforo rol, PCB pcb, long ciclo) {
        PCB despertado = buffer.senalar(rol);
        if (despertado != null) {
            String semaforo = "semáforo '" + rol.getNombre() + "' de " + buffer.getNombre();
            enviarDespertar(despertado, semaforo, ciclo);
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + " despierta a " + despertado.getEtiqueta() + " (" + semaforo + ")");
        }
    }

    /** Al morir un proceso por deadline, deduce por su Contador qué permisos de semáforo tenía tomados y los devuelve, para que no queden espacios ocupados ni procesos bloqueados para siempre; si murió esperando en la cola, ese permiso no cuenta. */
    private void liberarFichas(PCB pcb, boolean esperabaEnCola, long ciclo) {
        Buffer buffer = bufferDe(pcb);
        if (buffer == null) {
            return;
        }
        int ejecutadas = pcb.getContador() - (esperabaEnCola ? 1 : 0);
        RolSemaforo[] retenidas = ((ProgramaBuffer) pcb.getPrograma()).fichasRetenidas(ejecutadas);
        if (retenidas.length == 0) {
            return;
        }
        StringBuilder nombres = new StringBuilder();
        for (RolSemaforo rol : retenidas) {
            nombres.append(nombres.length() > 0 ? ", " : "").append(rol.getNombre());
        }
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " libera al morir sus fichas en " + buffer.getNombre() + ": " + nombres);
        for (RolSemaforo rol : retenidas) {
            senalarYDespertar(buffer, rol, pcb, ciclo);
        }
    }

    /** Avisa por interrupción al computador del proceso dormido; llegue de donde llegue, entra por la misma vía. */
    private void enviarDespertar(PCB despertado, String semaforo, long ciclo) {
        LineaInterrupciones[] todas = lineas;
        int destino = despertado.getComputador();
        if (todas == null || destino < 0 || destino >= todas.length) {
            Debug.error(origen, "no hay línea de interrupciones para despertar a " + despertado.getEtiqueta()
                    + " en el computador " + destino);
            return;
        }
        todas[destino].registrar(new Interrupcion(TipoInterrupcion.SEMAFORO_SIGNAL, despertado.getId(),
                despertado.getEtiqueta(), semaforo, ciclo));
    }

    /** Agrega un elemento al buffer del proceso (productor). */
    private void insertar(PCB pcb, long ciclo) {
        Buffer buffer = bufferDe(pcb);
        int elemento = buffer == null ? -1 : buffer.insertar();
        if (elemento < 0) {
            Debug.error(origen, pcb.getEtiqueta() + " no pudo insertar en su buffer");
            return;
        }
        pcb.registrarElemento();
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " inserta el elemento " + elemento + " en " + buffer.getNombre());
    }

    /** Saca un elemento del buffer del proceso (consumidor). */
    private void extraer(PCB pcb, long ciclo) {
        Buffer buffer = bufferDe(pcb);
        int elemento = buffer == null ? -1 : buffer.extraer();
        if (elemento < 0) {
            Debug.error(origen, pcb.getEtiqueta() + " no pudo extraer de su buffer");
            return;
        }
        pcb.registrarElemento();
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " extrae el elemento " + elemento + " de " + buffer.getNombre());
    }

    /** Buffer con el que trabaja el proceso, o null si no usa buffer o no existe. */
    private Buffer bufferDe(PCB pcb) {
        RegistroBuffers registro = buffers;
        if (registro == null || !(pcb.getPrograma() instanceof ProgramaBuffer programa)) {
            return null;
        }
        return registro.buscar(programa.getBuffer());
    }

    /** Comprueba que el buffer del proceso exista y esté en este computador (el acceso remoto llega en la etapa 6). */
    private boolean bufferAceptable(PCB pcb) {
        Buffer buffer = bufferDe(pcb);
        if (buffer == null) {
            Debug.error(origen, pcb.getEtiqueta() + " usa un buffer que no existe; se rechaza");
            return false;
        }
        if (buffer.getAnfitrion() != idComputador) {
            Debug.error(origen, pcb.getEtiqueta() + " usa el buffer " + buffer.getNombre() + " del computador "
                    + buffer.getAnfitrion() + ": el acceso remoto estará disponible en la etapa 6; se rechaza");
            return false;
        }
        return true;
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

    /** Saca al proceso de donde esté (CPU, cola, DMA o cola de un semáforo) y lo termina por deadline vencido. */
    private void terminarPorDeadline(PCB pcb, long ciclo) {
        boolean esperabaEnCola = false;
        if (pcb == enEjecucion) {
            liberarCPU();
        } else if (!nuevos.eliminar(pcb) && !listos.eliminar(pcb) && bloqueados.eliminar(pcb)) {
            if (pcb.getMotivoBloqueo() == MotivoBloqueo.ENTRADA_SALIDA) {
                dma.cancelar(pcb.getId());
            } else if (pcb.getMotivoBloqueo() == MotivoBloqueo.SEMAFORO) {
                Buffer buffer = bufferDe(pcb);
                esperabaEnCola = buffer != null && buffer.quitarBloqueado(pcb);
            }
        }
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " alcanza su deadline y es terminado (estaba en "
                + pcb.getEstado().getNombre() + ")");
        liberarFichas(pcb, esperabaEnCola, ciclo);
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
