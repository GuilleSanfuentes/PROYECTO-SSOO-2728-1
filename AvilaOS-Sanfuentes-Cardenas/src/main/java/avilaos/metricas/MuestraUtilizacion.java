package avilaos.metricas;

/** Utilización de una CPU (0 a 100) medida en un ciclo, sobre la ventana de ciclos recientes. */
public record MuestraUtilizacion(long ciclo, double porcentaje) {
}
