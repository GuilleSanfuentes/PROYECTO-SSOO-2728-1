package avilaos.config;

import avilaos.enums.PoliticaTipo;

/** Configuración de un computador: tamaño de RAM, política inicial y quantum de Round Robin. */
public record ConfigComputador(int ram, PoliticaTipo politica, int quantum) {
}
