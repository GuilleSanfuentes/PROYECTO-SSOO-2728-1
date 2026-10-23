package avilaos.so.politicas;

import avilaos.enums.PoliticaTipo;
import avilaos.log.Debug;

/** Crea la política correspondiente a cada valor de PoliticaTipo. */
public final class FabricaPoliticas {

    private FabricaPoliticas() {
    }

    /** Devuelve una instancia de la política; si el tipo es nulo usa FCFS. */
    public static PoliticaPlanificacion crear(PoliticaTipo tipo) {
        if (tipo == null) {
            Debug.error("FabricaPoliticas", "tipo de política nulo, se usa FCFS");
            return new PoliticaFCFS();
        }
        return switch (tipo) {
            case FCFS -> new PoliticaFCFS();
            case EDF -> new PoliticaEDF();
            case ROUND_ROBIN -> new PoliticaRoundRobin();
            case PRIORIDADES -> new PoliticaPrioridades();
        };
    }
}
