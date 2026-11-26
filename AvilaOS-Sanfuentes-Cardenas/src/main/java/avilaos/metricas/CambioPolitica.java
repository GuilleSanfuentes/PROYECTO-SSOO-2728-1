package avilaos.metricas;

import avilaos.enums.PoliticaDiscoTipo;

/** Cambio de política de disco; ciclo es el primero en que el disco trabaja con la nueva. */
public record CambioPolitica(long ciclo, PoliticaDiscoTipo anterior, PoliticaDiscoTipo nueva) {
}
