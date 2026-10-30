package avilaos.so.politicas;

import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.Comparador;
import avilaos.so.PCB;

/** Política de planificación a corto plazo. Agregar una política nueva es implementar esta interfaz. */
public interface PoliticaPlanificacion {

    /** Tipo de la política. */
    PoliticaTipo getTipo();

    /** Criterio con el que se ordena la cola de listos; el frente es el siguiente en ejecutarse. */
    Comparador<PCB> getComparador();

    /** Indica si el proceso en ejecución debe ceder la CPU al mejor proceso listo. */
    boolean debeExpulsar(PCB enEjecucion, PCB mejorListo, int ciclosEnCPU, int quantum);
}
