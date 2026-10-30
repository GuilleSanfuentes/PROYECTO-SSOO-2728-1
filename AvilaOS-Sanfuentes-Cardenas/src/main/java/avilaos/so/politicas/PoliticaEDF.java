package avilaos.so.politicas;

import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.Comparador;
import avilaos.so.PCB;

/** Earliest Deadline First: primero el deadline más cercano; expulsa si llega uno más urgente. */
public class PoliticaEDF implements PoliticaPlanificacion {

    @Override
    public PoliticaTipo getTipo() {
        return PoliticaTipo.EDF;
    }

    /** Menor deadline restante primero; en empate, orden de llegada. */
    @Override
    public Comparador<PCB> getComparador() {
        return (a, b) -> Integer.compare(a.getDeadlineRestante(), b.getDeadlineRestante());
    }

    @Override
    public boolean debeExpulsar(PCB enEjecucion, PCB mejorListo, int ciclosEnCPU, int quantum) {
        return mejorListo != null && mejorListo.getDeadlineRestante() < enEjecucion.getDeadlineRestante();
    }
}
