package avilaos.metricas;

/** Métricas de productor-consumidor de un buffer: elementos movidos y espera promedio de sus procesos en semáforos. */
public record MetricasBuffer(
        String nombre,
        int producidos,
        int consumidos,
        int procesos,
        double bloqueadoPromedio) {
}
