package avilaos.config;

import avilaos.enums.PoliticaDiscoTipo;

/** Configuración del disco local: bloques de archivos y de swap, cabezal inicial, velocidad y política. */
public record ConfigDisco(int bloquesArchivos, int bloquesSwap, int cabezalInicial, int velocidad,
        PoliticaDiscoTipo politica) {
}
