package avilaos.config;

import avilaos.enums.PoliticaDiscoTipo;

/** Configuración del disco local: bloques de archivos y de swap, cabezal, velocidad, política y espera anticipatoria. */
public record ConfigDisco(int bloquesArchivos, int bloquesSwap, int cabezalInicial, int velocidad,
        PoliticaDiscoTipo politica, int ciclosAnticipacion) {

    /** Disco sin espera anticipatoria. */
    public ConfigDisco(int bloquesArchivos, int bloquesSwap, int cabezalInicial, int velocidad, PoliticaDiscoTipo politica) {
        this(bloquesArchivos, bloquesSwap, cabezalInicial, velocidad, politica, 0);
    }
}
