package avilaos.config;

import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.Reloj;
import avilaos.so.DatosProceso;
import avilaos.so.Planificador;
import avilaos.sync.DatosBuffer;

/** Todos los parámetros configurables del sistema, ya validados. Se obtiene con ArchivoConfiguracion.cargar. */
public class ConfiguracionSistema {

    public static final int MIN_COMPUTADORES = 2;
    public static final int MAX_COMPUTADORES = 8;
    public static final int MAX_RAM = 1_000_000;
    public static final int MAX_QUANTUM = Planificador.QUANTUM_MAXIMO;
    public static final int MAX_LATENCIA = 1000;
    public static final int MAX_ENVEJECIMIENTO = 100_000;
    public static final int MAX_CAPACIDAD_BUFFER = 10_000;

    public static final int DEF_DURACION_MS = Reloj.DURACION_POR_DEFECTO_MS;
    public static final int DEF_MAX_CICLOS = 0;
    public static final int DEF_LATENCIA = 2;
    public static final int DEF_ENVEJECIMIENTO = 10;
    public static final int DEF_RAM = 1024;
    public static final PoliticaTipo DEF_POLITICA = PoliticaTipo.FCFS;
    public static final int DEF_QUANTUM = 3;

    private final boolean debugger;
    private final int duracionCicloMs;
    private final int maxCiclos;
    private final int latenciaRed;
    private final int envejecimientoCadaCiclos;
    private final ListaEnlazada<ConfigComputador> computadores;
    private final ListaEnlazada<DatosBuffer> buffers;
    private final ListaEnlazada<DatosProceso> procesos;
    private final int erroresCarga;

    /** Crea la configuración con valores ya validados. */
    public ConfiguracionSistema(boolean debugger, int duracionCicloMs, int maxCiclos, int latenciaRed,
            int envejecimientoCadaCiclos, ListaEnlazada<ConfigComputador> computadores,
            ListaEnlazada<DatosBuffer> buffers, ListaEnlazada<DatosProceso> procesos, int erroresCarga) {
        this.debugger = debugger;
        this.duracionCicloMs = duracionCicloMs;
        this.maxCiclos = maxCiclos;
        this.latenciaRed = latenciaRed;
        this.envejecimientoCadaCiclos = envejecimientoCadaCiclos;
        this.computadores = computadores;
        this.buffers = buffers;
        this.procesos = procesos;
        this.erroresCarga = erroresCarga;
    }

    /** Configuración por defecto: 2 computadores FCFS con 1024 de RAM, sin buffers ni procesos. */
    public static ConfiguracionSistema porDefecto() {
        ListaEnlazada<ConfigComputador> computadores = new ListaEnlazada<>();
        for (int i = 0; i < MIN_COMPUTADORES; i++) {
            computadores.agregarFinal(new ConfigComputador(DEF_RAM, DEF_POLITICA, DEF_QUANTUM));
        }
        return new ConfiguracionSistema(true, DEF_DURACION_MS, DEF_MAX_CICLOS, DEF_LATENCIA, DEF_ENVEJECIMIENTO,
                computadores, new ListaEnlazada<>(), new ListaEnlazada<>(), 0);
    }

    /** Resumen de varias líneas para la terminal. */
    public String describir() {
        StringBuilder sb = new StringBuilder();
        sb.append(" Debugger: ").append(debugger ? "TRUE" : "FALSE")
                .append(" | Ciclo: ").append(duracionCicloMs).append(" ms")
                .append(" | Máx. ciclos: ").append(maxCiclos == 0 ? "sin límite" : String.valueOf(maxCiclos))
                .append(" | Latencia de red: ").append(latenciaRed).append(" ciclos")
                .append(" | Envejecimiento: ").append(envejecimientoCadaCiclos == 0 ? "desactivado" : "cada " + envejecimientoCadaCiclos + " ciclos")
                .append('\n');
        sb.append(" Computadores: ").append(computadores.tamano()).append('\n');
        int numero = 0;
        Nodo<ConfigComputador> c = computadores.getCabeza();
        while (c != null) {
            sb.append("   Computador ").append(numero++).append(": RAM ").append(c.getDato().ram())
                    .append(", política ").append(c.getDato().politica().getNombre())
                    .append(", quantum ").append(c.getDato().quantum()).append('\n');
            c = c.getSiguiente();
        }
        sb.append(" Buffers: ").append(buffers.tamano()).append('\n');
        Nodo<DatosBuffer> b = buffers.getCabeza();
        while (b != null) {
            sb.append("   ").append(b.getDato().nombre()).append(": capacidad ").append(b.getDato().capacidad())
                    .append(", en el computador ").append(b.getDato().computador()).append('\n');
            b = b.getSiguiente();
        }
        sb.append(" Procesos iniciales: ").append(procesos.tamano()).append('\n');
        Nodo<DatosProceso> p = procesos.getCabeza();
        while (p != null) {
            DatosProceso d = p.getDato();
            sb.append("   ").append(d.nombre()).append(" (").append(d.tipo().getNombre()).append(", prioridad ")
                    .append(d.prioridad()).append(", memoria ").append(d.memoria()).append(", deadline ").append(d.deadline())
                    .append(", computador ").append(d.computador() == DatosProceso.ASIGNACION_AUTOMATICA ? "auto" : d.computador())
                    .append(d.tipo().usaBuffer() ? ", buffer " + d.buffer() : "").append(")\n");
            p = p.getSiguiente();
        }
        return sb.toString();
    }

    public boolean isDebugger() {
        return debugger;
    }

    public int getDuracionCicloMs() {
        return duracionCicloMs;
    }

    /** Ciclos tras los que la simulación se detiene sola; 0 = sin límite. */
    public int getMaxCiclos() {
        return maxCiclos;
    }

    public int getLatenciaRed() {
        return latenciaRed;
    }

    /** Ciclos en Listos para mejorar la prioridad en 1; 0 = sin envejecimiento. */
    public int getEnvejecimientoCadaCiclos() {
        return envejecimientoCadaCiclos;
    }

    public ListaEnlazada<ConfigComputador> getComputadores() {
        return computadores;
    }

    public ListaEnlazada<DatosBuffer> getBuffers() {
        return buffers;
    }

    public ListaEnlazada<DatosProceso> getProcesos() {
        return procesos;
    }

    /** Cantidad de valores inválidos que se reemplazaron u omitieron al cargar. */
    public int getErroresCarga() {
        return erroresCarga;
    }
}
