package avilaos.so.politicas;

import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.Comparador;
import avilaos.so.PCB;

/** First Come First Served: orden de llegada a Listos, sin expulsión. */
public class PoliticaFCFS implements PoliticaPlanificacion {

    @Override
    public PoliticaTipo getTipo() {
        return PoliticaTipo.FCFS;
    }

    /** Todos empatan, así que el orden estable conserva el de llegada. */
    @Override
    public Comparador<PCB> getComparador() {
        return (a, b) -> 0;
    }

    @Override
    public boolean debeExpulsar(PCB enEjecucion, PCB mejorListo, int ciclosEnCPU, int quantum) {
        return false;
    }
}
