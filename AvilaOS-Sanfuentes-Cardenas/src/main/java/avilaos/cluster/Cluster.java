package avilaos.cluster;

import avilaos.config.ArchivoConfiguracion;
import avilaos.config.CondicionesParada;
import avilaos.config.ConfigComputador;
import avilaos.config.ConfiguracionSistema;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.LineaInterrupciones;
import avilaos.hardware.ObservadorReloj;
import avilaos.hardware.Reloj;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.metricas.RegistroUtilizacion;
import avilaos.so.DatosProceso;
import avilaos.so.FabricaProcesos;
import avilaos.so.PCB;
import avilaos.sync.Buffer;
import avilaos.sync.DatosBuffer;
import avilaos.sync.RegistroBuffers;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** ÁvilaOS completo: reloj global, computadores y puerta de entrada para crear procesos y cambiar parámetros. */
public class Cluster implements ObservadorReloj {

    private static final String ORIGEN = "ÁvilaOS";

    private final ConfiguracionSistema config;
    private final LogEventos log;
    private final Reloj reloj;
    private final Computador[] computadores;
    private final ListaEnlazada<ObservadorReloj> observadores = new ListaEnlazada<>();
    private final RegistroBuffers buffers = new RegistroBuffers();
    private final CondicionesParada parada;
    private final RegistroUtilizacion utilizacion;
    private final ListaEnlazada<DatosBuffer> buffersCreados = new ListaEnlazada<>();
    private final ListaEnlazada<DatosProceso> procesosCreados = new ListaEnlazada<>();
    private final Semaphore mutexCreados = new Semaphore(1);
    private volatile int maxCiclos;
    private volatile boolean inactivo;
    private volatile boolean inactividadActiva;
    private final AtomicInteger formulariosAbiertos = new AtomicInteger();

    /** Arma el clúster según la configuración: un reloj, N computadores y sus hilos. */
    public Cluster(ConfiguracionSistema config, LogEventos log) {
        this.config = config;
        this.log = log;
        this.parada = config.getParada();
        this.maxCiclos = parada.maxCiclos();
        this.inactividadActiva = parada.inactividadActiva();
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
        LineaInterrupciones[] lineas = new LineaInterrupciones[computadores.length];
        for (int i = 0; i < computadores.length; i++) {
            lineas[i] = computadores[i].getLinea();
        }
        for (Computador c : computadores) {
            c.getKernel().conectarSincronizacion(buffers, lineas);
            c.getKernel().conectarRed(c.getRed(), config.getLatenciaRed());
        }
        Nodo<DatosBuffer> nodoBuffer = config.getBuffers().getCabeza();
        while (nodoBuffer != null) {
            crearBuffer(nodoBuffer.getDato());
            nodoBuffer = nodoBuffer.getSiguiente();
        }
        String[] nombres = new String[computadores.length];
        long[] ocupados = new long[computadores.length];
        for (int i = 0; i < computadores.length; i++) {
            nombres[i] = computadores[i].getNombre();
            ocupados[i] = computadores[i].getCpu().getCiclosUsuario() + computadores[i].getCpu().getCiclosSO();
        }
        this.utilizacion = new RegistroUtilizacion(nombres, ocupados);
        reloj.agregarObservador(this);
        Debug.ok(ORIGEN, "clúster listo con " + computadores.length + " computadores y " + buffers.cantidad()
                + " buffers; fases: " + reloj.describirFases());
    }

    /** Motivo por el que el buffer no se puede crear ahora, o null si los datos son válidos. */
    public String validarBuffer(DatosBuffer datos) {
        if (datos == null) {
            return "datos de buffer nulos";
        }
        if (datos.nombre() == null || datos.nombre().isBlank()) {
            return "el nombre del buffer está vacío";
        }
        String nombre = datos.nombre().trim();
        if (datos.capacidad() <= 0 || datos.capacidad() > ConfiguracionSistema.MAX_CAPACIDAD_BUFFER) {
            return "capacidad " + datos.capacidad() + " fuera de 1.." + ConfiguracionSistema.MAX_CAPACIDAD_BUFFER;
        }
        if (!existe(datos.computador())) {
            return "el computador anfitrión " + datos.computador() + " no existe (0.." + (computadores.length - 1) + ")";
        }
        if (buffers.buscar(nombre) != null) {
            return "ya existe un buffer llamado '" + nombre + "'";
        }
        Computador anfitrion = computadores[datos.computador()];
        if (!anfitrion.getRam().cabe(datos.capacidad())) {
            return "no caben " + datos.capacidad() + " de memoria en la RAM del " + anfitrion.getNombre()
                    + " (libre " + anfitrion.getRam().getLibre() + ")";
        }
        return null;
    }

    /** Crea un buffer en su computador anfitrión y le reserva en la RAM tanta memoria como su capacidad. */
    public Buffer crearBuffer(DatosBuffer datos) {
        String error = validarBuffer(datos);
        if (error != null) {
            Debug.error(ORIGEN, "buffer rechazado: " + error);
            return null;
        }
        String nombre = datos.nombre().trim();
        Computador anfitrion = computadores[datos.computador()];
        if (!anfitrion.getRam().reservar(Buffer.duenoEnRam(nombre), datos.capacidad())) {
            Debug.error(ORIGEN, "buffer '" + nombre + "': no se pudo reservar la RAM del " + anfitrion.getNombre());
            return null;
        }
        Buffer buffer = new Buffer(nombre, datos.capacidad(), datos.computador());
        if (!buffers.agregar(buffer)) {
            anfitrion.getRam().liberar(Buffer.duenoEnRam(nombre));
            return null;
        }
        registrarBuffer(new DatosBuffer(nombre, datos.capacidad(), datos.computador()));
        log.registrar(reloj.getCiclo(), "Buffer " + nombre + " creado en el " + anfitrion.getNombre() + " (capacidad "
                + datos.capacidad() + ", RAM " + anfitrion.getRam().getUsada() + "/" + anfitrion.getRam().getCapacidad() + ")");
        Debug.ok(ORIGEN, "buffer '" + nombre + "' creado en el " + anfitrion.getNombre());
        return buffer;
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

    /** Motivo por el que el proceso no se puede crear ahora, o null si los datos son válidos. */
    public String validarProceso(DatosProceso datos) {
        if (datos == null || datos.tipo() == null) {
            return "datos de proceso nulos o sin tipo";
        }
        String errores = FabricaProcesos.validar(datos).trim();
        if (!errores.isEmpty()) {
            return errores.endsWith(";") ? errores.substring(0, errores.length() - 1) : errores;
        }
        if (datos.computador() >= computadores.length) {
            return "el computador " + datos.computador() + " no existe (0.." + (computadores.length - 1) + " o -1)";
        }
        int mayorRam = 0;
        for (Computador c : computadores) {
            mayorRam = Math.max(mayorRam, c.getRam().getCapacidad());
        }
        if (datos.computador() == DatosProceso.ASIGNACION_AUTOMATICA && datos.memoria() > mayorRam) {
            return "pide " + datos.memoria() + " de memoria y la mayor RAM del clúster es " + mayorRam;
        }
        if (datos.computador() != DatosProceso.ASIGNACION_AUTOMATICA
                && datos.memoria() > computadores[datos.computador()].getRam().getCapacidad()) {
            return "pide " + datos.memoria() + " de memoria y la RAM total del " + computadores[datos.computador()].getNombre()
                    + " es " + computadores[datos.computador()].getRam().getCapacidad();
        }
        if (datos.tipo().usaBuffer() && buffers.buscar(datos.buffer().trim()) == null) {
            return "el buffer '" + datos.buffer().trim() + "' no existe";
        }
        return null;
    }

    /** Crea un proceso y lo entrega al computador indicado, o al de menor carga si es automático. */
    public PCB crearProceso(DatosProceso datos) {
        long ciclo = reloj.getCiclo();
        String error = validarProceso(datos);
        if (error != null) {
            Debug.error(ORIGEN, "proceso '" + (datos == null ? null : datos.nombre()) + "' rechazado: " + error);
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
                Debug.error(ORIGEN, pcb.getEtiqueta() + " no tiene computador donde ejecutarse (pide " + pcb.getMemoria()
                        + " de memoria)");
                return null;
            }
            pcb.asignarComputador(destino.getId());
            log.registrar(ciclo, "ÁvilaOS asigna " + pcb.getEtiqueta() + " al " + destino.getNombre()
                    + " (menor carga: " + destino.carga() + " procesos, " + destino.getRam().getLibre() + " de RAM libre)");
        } else {
            destino = computadores[datos.computador()];
        }
        if (!destino.getKernel().agregarProceso(pcb, ciclo)) {
            return null;
        }
        registrarProceso(datos);
        inactivo = false;
        return pcb;
    }

    /** Guarda los datos de un buffer creado con éxito, para poder guardarlos en la configuración. */
    private void registrarBuffer(DatosBuffer datos) {
        mutexCreados.acquireUninterruptibly();
        try {
            buffersCreados.agregarFinal(datos);
        } finally {
            mutexCreados.release();
        }
    }

    /** Guarda los datos de un proceso creado con éxito, para poder guardarlos en la configuración. */
    private void registrarProceso(DatosProceso datos) {
        mutexCreados.acquireUninterruptibly();
        try {
            procesosCreados.agregarFinal(datos);
        } finally {
            mutexCreados.release();
        }
    }

    /** Configuración con la que se armó este clúster (sin los cambios hechos durante la simulación). */
    public ConfiguracionSistema configuracionInicial() {
        return config;
    }

    /** Configuración con el estado actual: parámetros vigentes y todos los buffers y procesos creados con éxito. */
    public ConfiguracionSistema configuracionActual() {
        ListaEnlazada<ConfigComputador> configComputadores = new ListaEnlazada<>();
        for (Computador c : computadores) {
            configComputadores.agregarFinal(new ConfigComputador(c.getRam().getCapacidad(),
                    c.getKernel().getPolitica(), c.getKernel().getQuantum()));
        }
        ListaEnlazada<DatosBuffer> datosBuffers = new ListaEnlazada<>();
        ListaEnlazada<DatosProceso> datosProcesos = new ListaEnlazada<>();
        mutexCreados.acquireUninterruptibly();
        try {
            Nodo<DatosBuffer> b = buffersCreados.getCabeza();
            while (b != null) {
                datosBuffers.agregarFinal(b.getDato());
                b = b.getSiguiente();
            }
            Nodo<DatosProceso> p = procesosCreados.getCabeza();
            while (p != null) {
                datosProcesos.agregarFinal(p.getDato());
                p = p.getSiguiente();
            }
        } finally {
            mutexCreados.release();
        }
        CondicionesParada vigente = new CondicionesParada(maxCiclos, parada.limiteTiempoActivo(),
                parada.limiteTiempoSegundos(), inactividadActiva, parada.inactividadSegundos());
        return new ConfiguracionSistema(Debug.isActivo(), reloj.getDuracionCicloMs(), vigente, config.getLatenciaRed(),
                config.getEnvejecimientoCadaCiclos(), configComputadores, datosBuffers, datosProcesos, 0);
    }

    /** Guarda en el archivo la configuración actual (la carga inicial más lo creado en ejecución). */
    public boolean guardarConfiguracion(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            Debug.error(ORIGEN, "guardar: la ruta del archivo está vacía");
            return false;
        }
        boolean guardado = ArchivoConfiguracion.guardarConfiguracion(configuracionActual(), ruta.trim());
        if (guardado) {
            log.registrar(reloj.getCiclo(), "Configuración guardada en " + ruta.trim());
        }
        return guardado;
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

    /** Activa o desactiva en caliente el apagado por inactividad. */
    public void setApagadoInactividad(boolean activo) {
        inactividadActiva = activo;
        Debug.ok(ORIGEN, "apagado por inactividad " + (activo ? "activado" : "desactivado"));
    }

    public boolean isApagadoInactividad() {
        return inactividadActiva;
    }

    /** Avisa que un formulario se abrió o se cerró; con alguno abierto no corre el tiempo de inactividad. */
    public void formularioAbierto(boolean abierto) {
        if (abierto) {
            formulariosAbiertos.incrementAndGet();
        } else if (formulariosAbiertos.decrementAndGet() < 0) {
            formulariosAbiertos.set(0);
        }
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
        for (int i = 0; i < computadores.length; i++) {
            utilizacion.registrar(ciclo, i, computadores[i].getCpu().getCiclosUsuario() + computadores[i].getCpu().getCiclosSO());
        }
        inactivo = calcularInactividad();
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

    /** Todos los computadores sin procesos vivos y con la CPU ociosa. */
    private boolean calcularInactividad() {
        for (Computador c : computadores) {
            if (c.carga() > 0 || !c.getCpu().isOciosa()) {
                return false;
            }
        }
        return true;
    }

    /** Arranca el reloj global y, si hace falta, el vigilante del límite de tiempo y de la inactividad. */
    public boolean iniciar() {
        log.registrar(reloj.getCiclo(), "Simulación iniciada (" + reloj.getDuracionCicloMs() + " ms por ciclo; "
                + parada.describir() + ")");
        boolean iniciado = reloj.iniciar();
        if (iniciado) {
            Thread vigilante = new Thread(this::vigilar, "Hilo-Vigilante");
            vigilante.setDaemon(true);
            vigilante.start();
        }
        return iniciado;
    }

    /** Vigilante: cuenta el tiempo en marcha y el tiempo seguido de inactividad; las pausas no cuentan. */
    private void vigilar() {
        final long paso = 50;
        long limiteTiempo = parada.limiteTiempoSegundos() * 1000L;
        long limiteInactividad = parada.inactividadSegundos() * 1000L;
        long enMarcha = 0;
        long sinActividad = 0;
        while (reloj.isEjecutando()) {
            try {
                Thread.sleep(paso);
            } catch (InterruptedException e) {
                return;
            }
            if (reloj.isPausado()) {
                continue;
            }
            enMarcha += paso;
            sinActividad = inactivo && formulariosAbiertos.get() == 0 ? sinActividad + paso : 0;
            if (parada.limiteTiempoActivo() && enMarcha >= limiteTiempo) {
                detenerPor("se cumplió el límite de " + parada.limiteTiempoSegundos() + " segundos");
                return;
            }
            if (inactividadActiva && sinActividad >= limiteInactividad) {
                detenerPor("todos los computadores llevan " + parada.inactividadSegundos()
                        + " segundos sin procesos y con la CPU ociosa");
                return;
            }
        }
    }

    /** Detiene la simulación registrando el motivo. */
    private void detenerPor(String motivo) {
        if (reloj.isEjecutando()) {
            log.registrar(reloj.getCiclo(), "Simulación detenida: " + motivo);
            reloj.detener();
        }
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

    /** Condiciones de parada automática de esta simulación. */
    public CondicionesParada getParada() {
        return parada;
    }

    /** Indica si en el último ciclo todos los computadores estaban sin procesos y ociosos. */
    public boolean isInactivo() {
        return inactivo;
    }

    /** Registro con los buffers creados en el clúster. */
    public RegistroBuffers getBuffers() {
        return buffers;
    }

    /** Utilización de la CPU de cada computador en el tiempo (datos del gráfico). */
    public RegistroUtilizacion getUtilizacion() {
        return utilizacion;
    }

    public LogEventos getLog() {
        return log;
    }
}
