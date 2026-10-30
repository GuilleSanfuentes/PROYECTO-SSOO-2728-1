package avilaos.so;

import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.Cola;
import avilaos.log.Debug;
import avilaos.so.politicas.FabricaPoliticas;
import avilaos.so.politicas.PoliticaPlanificacion;

/** Planificador a corto plazo: ordena la cola de listos y decide expulsiones según la política activa. */
public class Planificador {

    public static final int QUANTUM_MAXIMO = 1000;

    private final String origen;
    private PoliticaPlanificacion politica;
    private int quantum;

    /** Crea el planificador con la política y el quantum iniciales. */
    public Planificador(String origen, PoliticaTipo tipo, int quantum) {
        this.origen = origen;
        this.politica = FabricaPoliticas.crear(tipo);
        this.quantum = 3;
        setQuantum(quantum);
    }

    /** Reordena la cola de listos con el criterio de la política (en empate, orden de llegada). */
    public void ordenar(Cola<PCB> listos) {
        listos.ordenar(politica.getComparador());
    }

    /** Indica si el proceso en CPU debe ceder la CPU al mejor proceso listo. */
    public boolean debeExpulsar(PCB enEjecucion, PCB mejorListo, int ciclosEnCPU) {
        return enEjecucion != null && mejorListo != null && politica.debeExpulsar(enEjecucion, mejorListo, ciclosEnCPU, quantum);
    }

    /** Cambia la política; se aplica desde el siguiente ordenamiento. */
    public void cambiarPolitica(PoliticaTipo tipo) {
        politica = FabricaPoliticas.crear(tipo);
        Debug.ok(origen, "política cambiada a " + politica.getTipo().getNombre());
    }

    /** Cambia el quantum de Round Robin (1..QUANTUM_MAXIMO). */
    public boolean setQuantum(int nuevo) {
        if (nuevo < 1 || nuevo > QUANTUM_MAXIMO) {
            Debug.error(origen, "quantum " + nuevo + " fuera de 1.." + QUANTUM_MAXIMO + ", se mantiene " + quantum);
            return false;
        }
        quantum = nuevo;
        Debug.ok(origen, "quantum = " + nuevo);
        return true;
    }

    public PoliticaTipo getTipo() {
        return politica.getTipo();
    }

    public int getQuantum() {
        return quantum;
    }

    /** Texto del tipo "Round Robin (q=3)" o "FCFS". */
    public String describir() {
        return politica.getTipo() == PoliticaTipo.ROUND_ROBIN
                ? politica.getTipo().getNombre() + " (q=" + quantum + ")"
                : politica.getTipo().getNombre();
    }
}
