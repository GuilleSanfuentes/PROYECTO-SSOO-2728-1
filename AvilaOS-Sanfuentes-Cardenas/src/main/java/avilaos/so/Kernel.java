package avilaos.so;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.PoliticaTipo;
import avilaos.enums.RolSemaforo;
import avilaos.enums.TipoInstruccion;
import avilaos.enums.TipoInterrupcion;
import avilaos.enums.TipoOperacionES;
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
import avilaos.hardware.TarjetaRed;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.metricas.MetricasDisco;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.disco.AsignadorBloques;
import avilaos.so.disco.PlanificadorDisco;
import avilaos.so.disco.SolicitudDisco;
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
    private final ListaEnlazada<PCB> listosSuspendidos = new ListaEnlazada<>();
    private final ListaEnlazada<PCB> bloqueadosSuspendidos = new ListaEnlazada<>();
    private final ListaEnlazada<Interrupcion> diferidas = new ListaEnlazada<>();
    private volatile RegistroBuffers buffers;
    private volatile LineaInterrupciones[] lineas;
    private volatile TarjetaRed red;
    private volatile int latenciaRed;
    private volatile PlanificadorDisco planificadorDisco;
    private volatile ServicioArchivos archivos;
    private volatile AsignadorBloques asignador;
    private PCB beneficiario;
    private int excedentePlan;
    private long suspensiones;
    private long reactivaciones;
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

    /** Conecta la tarjeta de red de este computador y la latencia (en ciclos) de cada acceso remoto a un buffer. */
    public void conectarRed(TarjetaRed tarjeta, int latencia) {
        this.red = tarjeta;
        this.latenciaRed = latencia;
    }

    /** Conecta el planificador del disco local, su sistema de archivos y el asignador (con él hay intercambio). */
    public void conectarDisco(PlanificadorDisco disco, SistemaArchivos sistema, AsignadorBloques bloques) {
        this.planificadorDisco = disco;
        this.asignador = bloques;
        this.archivos = new ServicioArchivos(sistema);
    }

    /** Recibe un proceso nuevo en la cola de Nuevos. Rechaza los que nunca cabrían en la RAM. */
    public boolean agregarProceso(PCB pcb, long ciclo) {
        if (pcb.getMemoria() > ram.getCapacidad()) {
            Debug.error(origen, pcb.getEtiqueta() + " pide " + pcb.getMemoria() + " de memoria y la RAM total es "
                    + ram.getCapacidad() + "; nunca podría admitirse, se rechaza");
            return false;
        }
        if (pcb.getTipo().usaBuffer() && !bufferExiste(pcb)) {
            return false;
        }
        if (pcb.getDatosES() != null && planificadorDisco == null) {
            Debug.error(origen, pcb.getEtiqueta() + " es de E/S de archivos y este computador no tiene disco; se rechaza");
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
            repartirMemoria(ciclo);
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
            descontarDeadlines(listosSuspendidos.getCabeza(), vencidos);
            descontarDeadlines(bloqueadosSuspendidos.getCabeza(), vencidos);
            if (enEjecucion != null && enEjecucion.descontarDeadline()) {
                vencidos.agregarFinal(enEjecucion);
            }
            Nodo<PCB> vencido = vencidos.getCabeza();
            while (vencido != null) {
                terminarPorDeadline(vencido.getDato(), ciclo);
                vencido = vencido.getSiguiente();
            }
            if (conIntercambio()) {
                repartirMemoria(ciclo);
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
        if (conIntercambio()) {
            ListaEnlazada<Interrupcion> lote = new ListaEnlazada<>();
            agregarInterrupciones(lote, diferidas.getCabeza());
            agregarInterrupciones(lote, interrupciones.getCabeza());
            lote.ordenar(this::compararInterrupciones);
            recorrerInterrupciones(lote.getCabeza(), aplazadas, ciclo);
        } else {
            recorrerInterrupciones(diferidas.getCabeza(), aplazadas, ciclo);
            recorrerInterrupciones(interrupciones.getCabeza(), aplazadas, ciclo);
        }
        diferidas.vaciar();
        Nodo<Interrupcion> aplazada = aplazadas.getCabeza();
        while (aplazada != null) {
            diferidas.agregarFinal(aplazada.getDato());
            aplazada = aplazada.getSiguiente();
        }
    }

    /** Copia al lote las interrupciones desde el nodo dado. */
    private void agregarInterrupciones(ListaEnlazada<Interrupcion> lote, Nodo<Interrupcion> nodo) {
        while (nodo != null) {
            lote.agregarFinal(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
    }

    /** Orden fijo del lote, sin depender de qué hilo avisó antes: primero los eventos y al final los intercambios. */
    private int compararInterrupciones(Interrupcion a, Interrupcion b) {
        int porClase = Boolean.compare(esFinDeIntercambio(a), esFinDeIntercambio(b));
        if (porClase != 0) {
            return porClase;
        }
        int porTipo = a.tipo().compareTo(b.tipo());
        return porTipo != 0 ? porTipo : Integer.compare(a.idProceso(), b.idProceso());
    }

    /** Indica si la interrupción avisa que terminó la escritura o lectura del swap de un proceso. */
    private boolean esFinDeIntercambio(Interrupcion irq) {
        if (irq.tipo() != TipoInterrupcion.FIN_DISCO) {
            return false;
        }
        PCB pcb = buscarEnEspera(irq.idProceso());
        return pcb != null && pcb.getIntercambio() != null && pcb.getIdIntercambio() == irq.idSolicitud();
    }

    /** Proceso que espera una interrupción: bloqueado o suspendido; null si no está. */
    private PCB buscarEnEspera(int id) {
        PCB pcb = buscar(bloqueados.getCabeza(), id);
        if (pcb == null) {
            pcb = buscar(bloqueadosSuspendidos.getCabeza(), id);
        }
        return pcb != null ? pcb : buscar(listosSuspendidos.getCabeza(), id);
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

    /** Atiende una interrupción: fin de un intercambio, o el evento que esperaba un bloqueado (suspendido o no). */
    private void atenderInterrupcion(Interrupcion irq, long ciclo) {
        if (esFinDeIntercambio(irq)) {
            terminarIntercambio(buscarEnEspera(irq.idProceso()), ciclo);
            return;
        }
        PCB pcb = buscar(bloqueados.getCabeza(), irq.idProceso());
        if (pcb == null) {
            pcb = buscar(bloqueadosSuspendidos.getCabeza(), irq.idProceso());
        }
        if (pcb == null) {
            Debug.info(origen, "interrupción de " + irq.etiqueta() + " ignorada: ya no está bloqueado");
            return;
        }
        if (irq.tipo() == TipoInterrupcion.FIN_DISCO) {
            if (pcb.getSolicitudPendiente() != null && pcb.getSolicitudPendiente().idSolicitud() != irq.idSolicitud()) {
                Debug.info(origen, "fin de disco ignorado: " + pcb.getEtiqueta() + " no espera la solicitud #" + irq.idSolicitud());
                return;
            }
            atenderFinDisco(pcb, ciclo);
            return;
        }
        if (irq.tipo() == TipoInterrupcion.FIN_RED) {
            completarAccesoRemoto(pcb, ciclo);
        }
        ocurreEvento(pcb, irq.tipo(), ciclo);
    }

    /** Ya ocurrió lo que esperaba: pasa a Listo o a Listo/Suspendido, o termina si no le queda nada que hacer. */
    private void ocurreEvento(PCB pcb, TipoInterrupcion tipo, long ciclo) {
        if (pcb.getIntercambio() == TipoOperacionES.SUSPENDER && !cancelarSuspension(pcb, ciclo)) {
            pcb.marcarEventoEnTransito(tipo);
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + ": ocurre su evento (" + tipo.getNombre()
                    + ") mientras se escribe en el swap; la suspensión se completa");
            return;
        }
        boolean suspendido = bloqueadosSuspendidos.eliminar(pcb);
        if (!suspendido) {
            bloqueados.eliminar(pcb);
        }
        if (pcb.getMotivoRechazo() != null || (pcb.getDatosES() == null && pcb.programaTerminado())) {
            if (pcb.getMotivoRechazo() == null) {
                evento(ciclo, "Proceso " + pcb.getEtiqueta() + " termina su última espera");
            }
            terminarProceso(pcb, ciclo, false);
        } else if (suspendido) {
            pcb.ocurrirEventoSuspendido(ciclo);
            listosSuspendidos.agregarFinal(pcb);
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + ": Bloqueado/Suspendido -> Listo/Suspendido ("
                    + tipo.getNombre() + ")");
        } else {
            pcb.desbloquear(ciclo);
            listos.encolar(pcb);
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + " se desbloquea (" + tipo.getNombre() + ") y pasa a Listos");
        }
    }

    /** El disco terminó: aplica el cambio y devuelve el proceso a Listos, o lo termina si falló o venció su deadline. */
    private void atenderFinDisco(PCB pcb, long ciclo) {
        if (pcb.getSolicitudPendiente() == null) {
            Debug.info(origen, "fin de disco ignorado: " + pcb.getEtiqueta() + " no espera ninguna solicitud");
            return;
        }
        ServicioArchivos.Aplicacion resultado = archivos.aplicar(pcb);
        pcb.setSolicitudPendiente(null);
        if (resultado.motivo() == null) {
            evento(ciclo, "Cambio aplicado por " + pcb.getEtiqueta() + ": " + resultado.cambio());
        } else {
            pcb.setMotivoRechazo(resultado.motivo());
            evento(ciclo, "No se aplica la operación de " + pcb.getEtiqueta() + " (" + pcb.getDatosES().describir() + "): "
                    + resultado.motivo());
        }
        if (pcb.isVencidoEnDisco()) {
            terminarVencido(pcb, ciclo);
        } else {
            ocurreEvento(pcb, TipoInterrupcion.FIN_DISCO, ciclo);
        }
    }

    /** Llamada al sistema sobre archivos: si es válida va al disco y bloquea; devuelve si el proceso sigue en la CPU. */
    private boolean solicitarDisco(PCB pcb, long ciclo) {
        DatosES datos = pcb.getDatosES();
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " pide al sistema: " + datos.describir() + " (usuario "
                + datos.usuario().nombre() + ")");
        ServicioArchivos.Preparacion preparacion = archivos.preparar(pcb);
        if (preparacion.motivo() != null) {
            rechazarOperacion(pcb, preparacion.motivo(), ciclo);
            return false;
        }
        if (!preparacion.usaDisco()) {
            evento(ciclo, "Cambio aplicado por " + pcb.getEtiqueta() + " sin usar el disco: " + preparacion.cambio());
            return true;
        }
        SolicitudDisco solicitud = planificadorDisco.encolar(pcb.getId(), pcb.getEtiqueta(), datos.operacion(), datos.ruta(),
                preparacion.bloques(), ciclo);
        if (solicitud == null) {
            archivos.descartar(preparacion.reserva());
            rechazarOperacion(pcb, "el disco no admitió la solicitud", ciclo);
            return false;
        }
        liberarCPU();
        pcb.restaurarPrioridad();
        pcb.bloquear(MotivoBloqueo.ENTRADA_SALIDA, "esperando el disco: solicitud #" + solicitud.id() + ", bloque "
                + solicitud.destino(), ciclo);
        pcb.setSolicitudPendiente(new SolicitudPendiente(solicitud.id(), preparacion.idEntrada(), preparacion.reserva()));
        bloqueados.agregarFinal(pcb);
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " se bloquea por E/S (solicitud #" + solicitud.id() + " al disco)");
        return false;
    }

    /** Termina al proceso cuya operación no se puede hacer, sin enviar nada al disco. */
    private void rechazarOperacion(PCB pcb, String motivo, long ciclo) {
        pcb.setMotivoRechazo(motivo);
        evento(ciclo, "Se rechaza la operación de " + pcb.getEtiqueta() + " (" + pcb.getDatosES().describir() + "): " + motivo);
        liberarCPU();
        terminarProceso(pcb, ciclo, false);
    }

    /** Al vencer, cancela lo que esté en cola; si el disco ya atiende algo suyo, lo deja acabar y devuelve false. */
    private boolean cancelarSolicitudes(PCB pcb, long ciclo) {
        boolean enAtencion = false;
        if (pcb.getIntercambio() == TipoOperacionES.SUSPENDER) {
            enAtencion = !cancelarSuspension(pcb, ciclo);
        } else if (pcb.getIntercambio() == TipoOperacionES.REACTIVAR) {
            if (planificadorDisco.cancelarSolicitud(pcb.getIdIntercambio(), ciclo) == null) {
                enAtencion = true;
            } else {
                pcb.setIntercambio(null, 0);
            }
        }
        SolicitudPendiente pendiente = pcb.getSolicitudPendiente();
        if (pendiente != null) {
            if (planificadorDisco.cancelarSolicitud(pendiente.idSolicitud(), ciclo) == null) {
                enAtencion = true;
            } else {
                archivos.descartar(pendiente.reserva());
                pcb.setSolicitudPendiente(null);
            }
        }
        if (enAtencion) {
            pcb.marcarVencidoEnDisco();
            evento(ciclo, "Proceso " + pcb.getEtiqueta()
                    + " alcanza su deadline mientras el disco atiende su solicitud; terminará cuando el disco acabe");
        }
        return !enAtencion;
    }

    /** El disco acabó la solicitud de un proceso cuyo deadline ya había vencido: cancela lo demás y lo termina. */
    private void terminarVencido(PCB pcb, long ciclo) {
        if (pcb.getIntercambio() == TipoOperacionES.SUSPENDER) {
            cancelarSuspension(pcb, ciclo);
        }
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " alcanza su deadline y es terminado (el disco ya acabó su solicitud)");
        retirarYTerminar(pcb, ciclo);
    }

    /** Portero de la RAM: entra el más importante que quepa. */
    private void admitir(long ciclo) {
        nuevos.ordenar(POR_PRIORIDAD_BASE);
        int cantidad = nuevos.tamano();
        for (int i = 0; i < cantidad; i++) {
            PCB pcb = nuevos.desencolar();
            if (ram.cabe(pcb.getMemoria()) && ram.reservar(pcb.getEtiqueta(), pcb.getMemoria())) {
                pasarAListos(pcb, ciclo);
            } else {
                nuevos.encolar(pcb);
            }
        }
    }

    /** Un Nuevo con su RAM ya reservada entra en Listos. */
    private void pasarAListos(PCB pcb, long ciclo) {
        pcb.setOcupaRam(true);
        pcb.cambiarEstado(EstadoProceso.LISTO);
        listos.encolar(pcb);
        evento(ciclo, nombreComputador + " admite " + pcb.getEtiqueta() + " en Listos (RAM " + ram.getUsada()
                + "/" + ram.getCapacidad() + ")");
    }

    // ---------------------------------------------------------------- mediano plazo

    /** Sin disco no hay suspensión: el computador se comporta como en el Proyecto 1. */
    private boolean conIntercambio() {
        return asignador != null;
    }

    /** Reparte la RAM en orden fijo: beneficiario, reactivaciones, Nuevos y nuevas suspensiones. */
    private void repartirMemoria(long ciclo) {
        if (!conIntercambio()) {
            admitir(ciclo);
            return;
        }
        if (beneficiario != null && !atenderBeneficiario(ciclo)) {
            return;
        }
        reactivar(ciclo);
        admitir(ciclo);
        buscarBeneficiario(ciclo);
    }

    /** La RAM queda apartada para el beneficiario; devuelve true cuando deja de estarlo (entró o ya no se puede). */
    private boolean atenderBeneficiario(long ciclo) {
        PCB elegido = beneficiario;
        if (ram.cabe(elegido.getMemoria())) {
            nuevos.eliminar(elegido);
            ram.reservar(elegido.getEtiqueta(), elegido.getMemoria());
            abandonarBeneficiario(ciclo);
            pasarAListos(elegido, ciclo);
            return true;
        }
        if (planear(elegido, ciclo)) {
            return false;
        }
        evento(ciclo, "Ya no se puede hacer sitio a " + elegido.getEtiqueta() + " suspendiendo procesos; sigue en Nuevos");
        abandonarBeneficiario(ciclo);
        return true;
    }

    /** Pide volver a la RAM, de uno en uno, para los Listos/Suspendidos que quepan. */
    private void reactivar(long ciclo) {
        PCB elegido = PlanificadorMedianoPlazo.elegirReactivacion(sinIntercambio(listosSuspendidos), ram.getLibre());
        while (elegido != null) {
            ram.reservar(elegido.getEtiqueta(), elegido.getMemoria());
            elegido.setOcupaRam(true);
            SolicitudDisco solicitud = planificadorDisco.encolar(elegido.getId(), elegido.getEtiqueta(), TipoOperacionES.REACTIVAR,
                    "swap", cadenaSwap(elegido), ciclo);
            elegido.setIntercambio(TipoOperacionES.REACTIVAR, solicitud.id());
            evento(ciclo, "Se reactiva al proceso " + elegido.getEtiqueta() + " (solicitud #" + solicitud.id()
                    + ", RAM " + ram.getUsada() + "/" + ram.getCapacidad() + ")");
            elegido = PlanificadorMedianoPlazo.elegirReactivacion(sinIntercambio(listosSuspendidos), ram.getLibre());
        }
    }

    /** Elige el primer Nuevo, por prioridad, al que se le pueda hacer sitio suspendiendo procesos. */
    private void buscarBeneficiario(long ciclo) {
        Nodo<PCB> nodo = nuevos.getCabeza();
        while (nodo != null && beneficiario == null) {
            PCB candidato = nodo.getDato();
            excedentePlan = Integer.MAX_VALUE;
            if (!ram.cabe(candidato.getMemoria()) && planear(candidato, ciclo)) {
                beneficiario = candidato;
                excedentePlan = Math.max(0, ram.getLibre() + ramEnCamino() - candidato.getMemoria());
            }
            nodo = nodo.getSiguiente();
        }
    }

    /** Completa o recorta las suspensiones que hacen falta para el proceso; false si ni así cabría. */
    private boolean planear(PCB destino, long ciclo) {
        int faltante = destino.getMemoria() - ram.getLibre() - ramEnCamino();
        if (faltante > 0) {
            ListaEnlazada<PCB> candidatas = new ListaEnlazada<>();
            Nodo<PCB> nodo = bloqueados.getCabeza();
            while (nodo != null) {
                PCB p = nodo.getDato();
                if (p.ocupaRam() && p.getIntercambio() == null && !p.isVencidoEnDisco()) {
                    candidatas.agregarFinal(p);
                }
                nodo = nodo.getSiguiente();
            }
            ListaEnlazada<PCB> victimas = PlanificadorMedianoPlazo.elegirVictimas(candidatas, faltante,
                    asignador.getDisco().contarLibres(true));
            if (victimas == null) {
                return false;
            }
            Nodo<PCB> victima = victimas.getCabeza();
            while (victima != null) {
                suspender(victima.getDato(), destino, ciclo);
                victima = victima.getSiguiente();
            }
            excedentePlan = Math.max(0, ram.getLibre() + ramEnCamino() - destino.getMemoria());
        } else if (-faltante > excedentePlan) {
            cancelarSobrantes(-faltante - excedentePlan, ciclo);
        }
        return true;
    }

    /** RAM que liberarán las suspensiones ya pedidas. */
    private int ramEnCamino() {
        int enCamino = 0;
        Nodo<PCB> nodo = bloqueados.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().getIntercambio() == TipoOperacionES.SUSPENDER) {
                enCamino += nodo.getDato().getMemoria();
            }
            nodo = nodo.getSiguiente();
        }
        return enCamino;
    }

    /** Aparta el swap de la víctima y pide al disco escribirla; sigue en RAM hasta que termine. */
    private void suspender(PCB victima, PCB destino, long ciclo) {
        victima.setBloqueSwap(asignador.asignarSwap(victima.getEtiqueta(), victima.getMemoria()));
        SolicitudDisco solicitud = planificadorDisco.encolar(victima.getId(), victima.getEtiqueta(), TipoOperacionES.SUSPENDER,
                "swap", cadenaSwap(victima), ciclo);
        victima.setIntercambio(TipoOperacionES.SUSPENDER, solicitud.id());
        evento(ciclo, "Se suspende al proceso " + victima.getEtiqueta() + " para admitir a " + destino.getEtiqueta()
                + " (solicitud #" + solicitud.id() + ")");
    }

    /** Cancela, empezando por la última elegida, las suspensiones en cola que sobran. */
    private void cancelarSobrantes(int sobrante, long ciclo) {
        ListaEnlazada<PCB> pedidas = PlanificadorMedianoPlazo.ordenar(suspensionesPedidas(),
                (a, b) -> PlanificadorMedianoPlazo.ORDEN_VICTIMAS.comparar(b, a));
        Nodo<PCB> nodo = pedidas.getCabeza();
        while (nodo != null) {
            PCB p = nodo.getDato();
            if (p.getMemoria() <= sobrante && cancelarSuspension(p, ciclo)) {
                sobrante -= p.getMemoria();
            }
            nodo = nodo.getSiguiente();
        }
    }

    /** Bloqueados cuya suspensión aún no termina. */
    private ListaEnlazada<PCB> suspensionesPedidas() {
        ListaEnlazada<PCB> pedidas = new ListaEnlazada<>();
        Nodo<PCB> nodo = bloqueados.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().getIntercambio() == TipoOperacionES.SUSPENDER) {
                pedidas.agregarFinal(nodo.getDato());
            }
            nodo = nodo.getSiguiente();
        }
        return pedidas;
    }

    /** Si la suspensión sigue en cola la retira y libera su swap; false si el disco ya la atiende o la acabó. */
    private boolean cancelarSuspension(PCB p, long ciclo) {
        if (planificadorDisco.cancelarSolicitud(p.getIdIntercambio(), ciclo) == null) {
            return false;
        }
        p.setIntercambio(null, 0);
        liberarSwap(p);
        evento(ciclo, "Se cancela la suspensión de " + p.getEtiqueta() + "; sigue en RAM");
        return true;
    }

    /** Deja de apartar RAM y cancela las suspensiones que aún estaban en cola. */
    private void abandonarBeneficiario(long ciclo) {
        beneficiario = null;
        Nodo<PCB> nodo = suspensionesPedidas().getCabeza();
        while (nodo != null) {
            cancelarSuspension(nodo.getDato(), ciclo);
            nodo = nodo.getSiguiente();
        }
    }

    /** El disco acabó de escribir o leer el swap: se completa la suspensión o la reactivación. */
    private void terminarIntercambio(PCB pcb, long ciclo) {
        TipoOperacionES operacion = pcb.getIntercambio();
        TipoInterrupcion eventoOcurrido = pcb.getEventoEnTransito();
        pcb.setIntercambio(null, 0);
        if (operacion == TipoOperacionES.SUSPENDER) {
            terminarSuspension(pcb, ciclo);
            if (pcb.isVencidoEnDisco()) {
                terminarVencido(pcb, ciclo);
            } else if (eventoOcurrido != null) {
                ocurreEvento(pcb, eventoOcurrido, ciclo);
            }
        } else if (pcb.isVencidoEnDisco()) {
            terminarVencido(pcb, ciclo);
        } else {
            terminarReactivacion(pcb, ciclo);
        }
    }

    /** Ya está escrito en el swap: libera su RAM y pasa a Bloqueado/Suspendido. */
    private void terminarSuspension(PCB pcb, long ciclo) {
        ram.liberar(pcb.getEtiqueta());
        pcb.setOcupaRam(false);
        suspensiones++;
        bloqueados.eliminar(pcb);
        pcb.suspender();
        bloqueadosSuspendidos.agregarFinal(pcb);
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " pasa a Bloqueado/Suspendido y libera su memoria (RAM "
                + ram.getUsada() + "/" + ram.getCapacidad() + ")");
    }

    /** Ya está leído del swap: libera esos bloques y pasa a Listo. */
    private void terminarReactivacion(PCB pcb, long ciclo) {
        liberarSwap(pcb);
        reactivaciones++;
        listosSuspendidos.eliminar(pcb);
        pcb.reactivar();
        listos.encolar(pcb);
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " vuelve a la RAM: Listo/Suspendido -> Listo");
    }

    /** Libera los bloques de swap del proceso, si tiene. */
    private void liberarSwap(PCB pcb) {
        if (pcb.getBloqueSwap() != PCB.SIN_SWAP) {
            asignador.liberarCadena(pcb.getBloqueSwap());
            pcb.setBloqueSwap(PCB.SIN_SWAP);
        }
    }

    /** Bloques de su imagen en el swap, en el orden de la cadena. */
    private int[] cadenaSwap(PCB pcb) {
        ListaEnlazada<Integer> cadena = asignador.recorrer(pcb.getBloqueSwap());
        int[] bloques = new int[cadena.tamano()];
        Nodo<Integer> nodo = cadena.getCabeza();
        for (int i = 0; i < bloques.length; i++) {
            bloques[i] = nodo.getDato();
            nodo = nodo.getSiguiente();
        }
        return bloques;
    }

    /** Los procesos de la lista que no tienen un intercambio en curso. */
    private ListaEnlazada<PCB> sinIntercambio(ListaEnlazada<PCB> procesos) {
        ListaEnlazada<PCB> libres = new ListaEnlazada<>();
        Nodo<PCB> nodo = procesos.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().getIntercambio() == null) {
                libres.agregarFinal(nodo.getDato());
            }
            nodo = nodo.getSiguiente();
        }
        return libres;
    }

    /** Suspensiones completadas desde el inicio. */
    public long getSuspensiones() {
        mutex.acquireUninterruptibly();
        try {
            return suspensiones;
        } finally {
            mutex.release();
        }
    }

    /** Reactivaciones completadas desde el inicio. */
    public long getReactivaciones() {
        mutex.acquireUninterruptibly();
        try {
            return reactivaciones;
        } finally {
            mutex.release();
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
            if (pcb.getDatosES() == null) {
                Debug.error(origen, pcb.getEtiqueta() + " en CPU sin instrucciones pendientes; se termina");
            }
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
            case SOLICITAR_DISCO -> sigueEnCPU = solicitarDisco(pcb, ciclo);
            case SEM_WAIT -> sigueEnCPU = semWait(pcb, instruccion.semaforo(), ciclo);
            case SEM_SIGNAL -> semSignal(pcb, instruccion.semaforo(), ciclo);
            case INSERTAR -> sigueEnCPU = insertar(pcb, ciclo);
            case EXTRAER -> sigueEnCPU = extraer(pcb, ciclo);
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

    /** Al morir un proceso por deadline, deduce por su Contador qué permisos de semáforo tenía tomados y los devuelve, para que no queden espacios ocupados ni procesos bloqueados para siempre; si murió esperando (en la cola de un semáforo o en la red), esa última instrucción no cuenta. */
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

    /** Agrega un elemento al buffer del proceso (productor); si el buffer es remoto lo bloquea por red y devuelve false. */
    private boolean insertar(PCB pcb, long ciclo) {
        Buffer buffer = bufferDe(pcb);
        if (buffer != null && esRemoto(buffer)) {
            iniciarAccesoRemoto(pcb, buffer, ciclo);
            return false;
        }
        completarInsertar(pcb, buffer, ciclo);
        return true;
    }

    /** Inserta el elemento en el buffer y lo anota en el proceso. */
    private void completarInsertar(PCB pcb, Buffer buffer, long ciclo) {
        int elemento = buffer == null ? -1 : buffer.insertar();
        if (elemento < 0) {
            Debug.error(origen, pcb.getEtiqueta() + " no pudo insertar en su buffer");
            return;
        }
        pcb.registrarElemento();
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " inserta el elemento " + elemento + " en " + buffer.getNombre());
    }

    /** Saca un elemento del buffer del proceso (consumidor); si el buffer es remoto lo bloquea por red y devuelve false. */
    private boolean extraer(PCB pcb, long ciclo) {
        Buffer buffer = bufferDe(pcb);
        if (buffer != null && esRemoto(buffer)) {
            iniciarAccesoRemoto(pcb, buffer, ciclo);
            return false;
        }
        completarExtraer(pcb, buffer, ciclo);
        return true;
    }

    /** Extrae el elemento del buffer y lo anota en el proceso. */
    private void completarExtraer(PCB pcb, Buffer buffer, long ciclo) {
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

    /** Comprueba que el buffer del proceso exista, esté en este computador o en otro. */
    private boolean bufferExiste(PCB pcb) {
        if (bufferDe(pcb) == null) {
            Debug.error(origen, pcb.getEtiqueta() + " usa un buffer que no existe; se rechaza");
            return false;
        }
        return true;
    }

    /** Un acceso es remoto si el buffer vive en otro computador y la latencia de red es mayor que 0. */
    private boolean esRemoto(Buffer buffer) {
        return red != null && latenciaRed > 0 && buffer.getAnfitrion() != idComputador;
    }

    /** Acceso remoto: el proceso pide el buffer de otro computador por la red; libera la CPU y queda Bloqueado por RED mientras la tarjeta de red cuenta la latencia; el buffer no cambia hasta que termina la espera. */
    private void iniciarAccesoRemoto(PCB pcb, Buffer buffer, long ciclo) {
        String detalle = "acceso remoto a " + buffer.getNombre() + " (" + latenciaRed + " ciclos)";
        liberarCPU();
        pcb.restaurarPrioridad();
        pcb.bloquear(MotivoBloqueo.RED, detalle, ciclo);
        bloqueados.agregarFinal(pcb);
        red.solicitar(pcb.getId(), pcb.getEtiqueta(), latenciaRed, "acceso remoto a " + buffer.getNombre());
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " inicia " + detalle);
    }

    /** Fin del acceso remoto: al llegar la interrupción FIN_RED, el Kernel revisa la última instrucción del proceso y aplica en el buffer el INSERTAR o EXTRAER pendiente; quien lo devuelve a Listos es atenderInterrupcion. */
    private void completarAccesoRemoto(PCB pcb, long ciclo) {
        Buffer buffer = bufferDe(pcb);
        Instruccion pendiente = pcb.getPrograma().instruccionEn(pcb.getContador() - 1);
        if (buffer == null || pendiente == null) {
            Debug.error(origen, pcb.getEtiqueta() + " termina un acceso remoto sin buffer o sin instrucción pendiente");
            return;
        }
        if (pendiente.tipo() == TipoInstruccion.INSERTAR) {
            completarInsertar(pcb, buffer, ciclo);
        } else if (pendiente.tipo() == TipoInstruccion.EXTRAER) {
            completarExtraer(pcb, buffer, ciclo);
        } else {
            Debug.error(origen, pcb.getEtiqueta() + " termina un acceso remoto pero su instrucción pendiente es " + pendiente);
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

    /** Termina por deadline vencido; si el disco ya atiende una solicitud suya, espera a que la acabe. */
    private void terminarPorDeadline(PCB pcb, long ciclo) {
        if (pcb.isVencidoEnDisco() || !cancelarSolicitudes(pcb, ciclo)) {
            return;
        }
        evento(ciclo, "Proceso " + pcb.getEtiqueta() + " alcanza su deadline y es terminado (estaba en "
                + pcb.getEstado().getNombre() + ")");
        retirarYTerminar(pcb, ciclo);
    }

    /** Saca al proceso de donde esté (CPU, colas, dispositivo o semáforo) y lo termina por deadline. */
    private void retirarYTerminar(PCB pcb, long ciclo) {
        boolean esperabaEnCola = false;
        if (pcb == enEjecucion) {
            liberarCPU();
        } else if (!nuevos.eliminar(pcb) && !listos.eliminar(pcb) && !listosSuspendidos.eliminar(pcb)
                && (bloqueados.eliminar(pcb) || bloqueadosSuspendidos.eliminar(pcb))) {
            if (pcb.getMotivoBloqueo() == MotivoBloqueo.ENTRADA_SALIDA && pcb.getDatosES() == null) {
                dma.cancelar(pcb.getId());
            } else if (pcb.getMotivoBloqueo() == MotivoBloqueo.SEMAFORO) {
                Buffer buffer = bufferDe(pcb);
                esperabaEnCola = buffer != null && buffer.quitarBloqueado(pcb);
            } else if (pcb.getMotivoBloqueo() == MotivoBloqueo.RED) {
                esperabaEnCola = red != null && red.cancelar(pcb.getId()) > 0;
            }
        }
        liberarFichas(pcb, esperabaEnCola, ciclo);
        terminarProceso(pcb, ciclo, true);
    }

    /** Pasa el proceso a Terminados, libera su RAM y revisa si algún proceso de Nuevos puede entrar. */
    private void terminarProceso(PCB pcb, long ciclo, boolean porDeadline) {
        boolean teniaMemoria = pcb.ocupaRam();
        pcb.terminar(ciclo, porDeadline);
        terminados.agregarFinal(pcb);
        if (!porDeadline) {
            evento(ciclo, "Proceso " + pcb.getEtiqueta() + " termina en el " + nombreComputador);
        }
        if (conIntercambio()) {
            liberarSwap(pcb);
            if (pcb == beneficiario) {
                abandonarBeneficiario(ciclo);
            }
        }
        if (teniaMemoria) {
            ram.liberar(pcb.getEtiqueta());
            pcb.setOcupaRam(false);
            if (!conIntercambio()) {
                admitir(ciclo);
            }
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

    /** Copia de la cola de Listos/Suspendidos. */
    public ListaEnlazada<PCB> getListosSuspendidos() {
        return copiar(EstadoProceso.LISTO_SUSPENDIDO);
    }

    /** Copia de la cola de Bloqueados/Suspendidos; cada uno conserva su motivo de bloqueo. */
    public ListaEnlazada<PCB> getBloqueadosSuspendidos() {
        return copiar(EstadoProceso.BLOQUEADO_SUSPENDIDO);
    }

    /** Copia de la lista de Terminados. */
    public ListaEnlazada<PCB> getTerminados() {
        return copiar(EstadoProceso.TERMINADO);
    }

    /** Copia de todos los procesos del computador, en cualquier estado. */
    public ListaEnlazada<PCB> getTodos() {
        mutex.acquireUninterruptibly();
        try {
            return juntarTodos();
        } finally {
            mutex.release();
        }
    }

    /** Foto de los procesos y de los ciclos de CPU tomada de una vez: nada cambia mientras se copia. */
    public ResumenComputador getResumen(long ciclo) {
        mutex.acquireUninterruptibly();
        try {
            ListaEnlazada<ResumenProceso> resumenes = new ListaEnlazada<>();
            Nodo<PCB> nodo = juntarTodos().getCabeza();
            while (nodo != null) {
                resumenes.agregarFinal(ResumenProceso.de(nodo.getDato(), ciclo));
                nodo = nodo.getSiguiente();
            }
            return new ResumenComputador(resumenes, cpu.getCiclosUsuario(), cpu.getCiclosSO(), cpu.getCiclosOciosos());
        } finally {
            mutex.release();
        }
    }

    /** Métricas del disco y de la suspensión del mismo instante, o null si el computador no tiene disco. */
    public MetricasDisco getMetricasDisco() {
        mutex.acquireUninterruptibly();
        try {
            return planificadorDisco == null ? null : planificadorDisco.fotografiar(suspensiones, reactivaciones);
        } finally {
            mutex.release();
        }
    }

    /** Todos los procesos de todas las colas (llamar con el mutex tomado). */
    private ListaEnlazada<PCB> juntarTodos() {
        ListaEnlazada<PCB> todos = new ListaEnlazada<>();
        agregarTodos(todos, nuevos.getCabeza());
        agregarTodos(todos, listos.getCabeza());
        if (enEjecucion != null) {
            todos.agregarFinal(enEjecucion);
        }
        agregarTodos(todos, bloqueados.getCabeza());
        agregarTodos(todos, listosSuspendidos.getCabeza());
        agregarTodos(todos, bloqueadosSuspendidos.getCabeza());
        agregarTodos(todos, terminados.getCabeza());
        return todos;
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
            return nuevos.tamano() + listos.tamano() + bloqueados.tamano() + listosSuspendidos.tamano()
                    + bloqueadosSuspendidos.tamano() + (enEjecucion == null ? 0 : 1);
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
                case LISTO_SUSPENDIDO -> listosSuspendidos.getCabeza();
                case BLOQUEADO_SUSPENDIDO -> bloqueadosSuspendidos.getCabeza();
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
