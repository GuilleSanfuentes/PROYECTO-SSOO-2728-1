package avilaos.so.politicas;

import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.Comparador;
import avilaos.so.PCB;

/** Prioridades apropiativas: 1 es la más importante; expulsa si llega uno más prioritario. */
public class PoliticaPrioridades implements PoliticaPlanificacion {

    @Override
    public PoliticaTipo getTipo() {
        return PoliticaTipo.PRIORIDADES;
    }

    /** Menor prioridad efectiva primero; en empate, orden de llegada. */
    @Override
    public Comparador<PCB> getComparador() {
        return (a, b) -> Integer.compare(a.getPrioridadEfectiva(), b.getPrioridadEfectiva());
    }

    @Override
    public boolean debeExpulsar(PCB enEjecucion, PCB mejorListo, int ciclosEnCPU, int quantum) {
        return mejorListo != null && mejorListo.getPrioridadEfectiva() < enEjecucion.getPrioridadEfectiva();
    }
}
