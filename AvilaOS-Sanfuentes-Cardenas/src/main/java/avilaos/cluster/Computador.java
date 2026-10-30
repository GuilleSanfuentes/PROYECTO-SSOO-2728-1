package avilaos.cluster;

import avilaos.config.ConfigComputador;
import avilaos.hardware.CPU;
import avilaos.hardware.DMA;
import avilaos.hardware.LineaInterrupciones;
import avilaos.hardware.RAM;
import avilaos.hardware.Reloj;
import avilaos.hardware.TarjetaRed;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.Kernel;
import avilaos.so.Planificador;

/** Unidad reutilizable del clúster: ensambla su hardware (CPU, RAM, DMA, red) y su núcleo del SO. */
public class Computador {

    private final int id;
    private final String nombre;
    private final LineaInterrupciones linea;
    private final CPU cpu;
    private final RAM ram;
    private final DMA dma;
    private final TarjetaRed red;
    private final Kernel kernel;

    /** Crea el computador con su configuración; el kernel queda conectado a la CPU. */
    public Computador(int id, ConfigComputador config, int envejecimientoCadaCiclos, LogEventos log) {
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
        Debug.ok(nombre, "ensamblado: RAM " + ram.getCapacidad() + ", " + planificador.describir());
    }

    /** Conecta DMA y red a la fase de E/S y la CPU a la fase de CPU del reloj global. */
    public void conectar(Reloj reloj, int faseES, int faseCPU) {
        reloj.agregarComponente(faseES, dma);
        reloj.agregarComponente(faseES, red);
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
}
