package avilaos.metricas;

/** Punto del gráfico del disco: posiciones recorridas por el cabezal desde el inicio hasta ese ciclo. */
public record PuntoDesplazamiento(long ciclo, long acumulado) {
}
