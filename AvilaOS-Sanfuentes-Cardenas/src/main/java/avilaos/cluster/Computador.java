package avilaos.cluster;

import avilaos.config.ConfigComputador;
import avilaos.config.ConfigDisco;
import avilaos.enums.TipoInterrupcion;
import avilaos.hardware.CPU;
import avilaos.hardware.DMA;
import avilaos.hardware.DiscoSD;
import avilaos.hardware.Interrupcion;
import avilaos.hardware.LineaInterrupciones;
import avilaos.hardware.RAM;
import avilaos.hardware.Reloj;
import avilaos.hardware.TarjetaRed;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.Kernel;
import avilaos.so.Planificador;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.disco.AsignadorBloques;
import avilaos.so.disco.PlanificadorDisco;

/** Unidad reutilizable del clúster: ensambla su hardware (CPU, RAM, DMA, red y disco opcional) y su núcleo del SO. */
public class Computador {

    private final int id;
    private final String nombre;
    private final LineaInterrupciones linea;
    private final CPU cpu;
    private final RAM ram;
    private final DMA dma;
    private final TarjetaRed red;
    private final Kernel kernel;
    private final DiscoSD disco;
    private final AsignadorBloques asignador;
    private final PlanificadorDisco planificadorDisco;
    private final SistemaArchivos sistemaArchivos;

    /** Crea el computador sin disco (modo del Proyecto 1). */
    public Computador(int id, ConfigComputador config, int envejecimientoCadaCiclos, LogEventos log) {
        this(id, config, envejecimientoCadaCiclos, log, null);
    }

    /** Crea el computador con su configuración y, si se indica, su disco; el kernel queda conectado a la CPU. */
    public Computador(int id, ConfigComputador config, int envejecimientoCadaCiclos, LogEventos log,
            ConfigDisco configDisco) {
        this.id = id;
        this.nombre = "Computador " + id;
        String sufijo = "-PC" + id;
        this.linea = new LineaInterrupciones("IRQ" + sufijo);
        this.cpu = new CPU("CPU" + sufijo, linea);
        this.ram = new RAM("RAM" + sufijo, config.ram());
        this.dma = new DMA("DMA" + sufijo, linea);
        this.red = new TarjetaRed("Red" + sufijo, linea);
        Planificador planificador = new Planificador("Planif" + sufijo, config.politica(), config.quantum());
        this.kernel = new Kernel(id, cpu, ram, dma, planificador, envejecimientoCadaCiclos, log);
        cpu.conectarSistemaOperativo(kernel);
        if (configDisco == null) {
            this.disco = null;
            this.asignador = null;
            this.planificadorDisco = null;
            this.sistemaArchivos = null;
        } else {
            this.disco = new DiscoSD("SD" + sufijo, configDisco.bloquesArchivos(), configDisco.bloquesSwap(),
                    configDisco.cabezalInicial(), configDisco.velocidad());
            this.asignador = new AsignadorBloques("Asignador" + sufijo, disco);
            this.planificadorDisco = new PlanificadorDisco("PlanifDisco" + sufijo, disco, configDisco.politica(), log,
                    configDisco.ciclosAnticipacion());
            this.sistemaArchivos = new SistemaArchivos("FS" + sufijo, asignador);
            kernel.conectarDisco(planificadorDisco, sistemaArchivos);
            planificadorDisco.setAlTerminar((solicitud, ciclo) -> linea.registrar(new Interrupcion(TipoInterrupcion.FIN_DISCO,
                    solicitud.idProceso(), solicitud.etiqueta(), disco.getNombre(), ciclo)));
        }
        Debug.ok(nombre, "ensamblado: RAM " + ram.getCapacidad() + ", " + planificador.describir());
    }

    /** Conecta DMA, red y disco a la fase de E/S y la CPU a la fase de CPU del reloj global. */
    public void conectar(Reloj reloj, int faseES, int faseCPU) {
        reloj.agregarComponente(faseES, dma);
        reloj.agregarComponente(faseES, red);
        if (disco != null) {
            reloj.agregarComponente(faseES, disco);
        }
        reloj.agregarComponente(faseCPU, cpu);
    }

    /** Procesos que aún no terminaron en este computador. */
    public int carga() {
        return kernel.procesosVivos();
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    /** Línea por la que llegan a este computador las interrupciones de sus dispositivos y de los semáforos. */
    public LineaInterrupciones getLinea() {
        return linea;
    }

    public CPU getCpu() {
        return cpu;
    }

    public RAM getRam() {
        return ram;
    }

    public DMA getDma() {
        return dma;
    }

    public TarjetaRed getRed() {
        return red;
    }

    public Kernel getKernel() {
        return kernel;
    }

    /** Disco local, o null en el modo del Proyecto 1. */
    public DiscoSD getDisco() {
        return disco;
    }

    /** Asignador de bloques del disco, o null si no hay disco. */
    public AsignadorBloques getAsignador() {
        return asignador;
    }

    /** Planificador del disco, o null si no hay disco. */
    public PlanificadorDisco getPlanificadorDisco() {
        return planificadorDisco;
    }

    /** Sistema de archivos del disco, o null si no hay disco. */
    public SistemaArchivos getSistemaArchivos() {
        return sistemaArchivos;
    }
}
