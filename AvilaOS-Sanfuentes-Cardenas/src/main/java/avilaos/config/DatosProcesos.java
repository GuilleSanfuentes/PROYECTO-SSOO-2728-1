package avilaos.config;

import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.so.DatosProcesoArchivo;
import avilaos.sync.DatosBuffer;

/** Lo leído del JSON de procesos: ajustes globales, computador, procesos válidos y problemas; debugger es null si no viene. */
public record DatosProcesos(boolean cargable, int duracionCicloMs, int bloquesSwap, PoliticaDiscoTipo politicaDisco,
        int velocidadCabezal, int ciclosAnticipacion, Boolean debugger, int ram,PoliticaTipo politica, int quantum,
        ListaEnlazada<DatosBuffer> buffers, ListaEnlazada<DatosProcesoArchivo> procesos, ListaEnlazada<String> problemas) {

    /** Quantum que usa el planificador: el 0 admitido sin Round Robin se cambia por el de por defecto. */
    public int quantumInterno() {
        return quantum == 0 ? ConfiguracionSistema.DEF_QUANTUM : quantum;
    }
}
