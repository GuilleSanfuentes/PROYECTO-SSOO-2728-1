package avilaos.so.politicas;

import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.Comparador;
import avilaos.so.PCB;

/** Round Robin: orden de llegada; expulsa al agotar el quantum si hay otro proceso listo. */
public class PoliticaRoundRobin implements PoliticaPlanificacion {

    @Override
    public PoliticaTipo getTipo() {
        return PoliticaTipo.ROUND_ROBIN;
    }

    /** Todos empatan, así que el orden estable conserva el de llegada. */
    @Override
    public Comparador<PCB> getComparador() {
        return (a, b) -> 0;
    }

    @Override
    public boolean debeExpulsar(PCB enEjecucion, PCB mejorListo, int ciclosEnCPU, int quantum) {
        return mejorListo != null && ciclosEnCPU >= quantum;
    }
}
