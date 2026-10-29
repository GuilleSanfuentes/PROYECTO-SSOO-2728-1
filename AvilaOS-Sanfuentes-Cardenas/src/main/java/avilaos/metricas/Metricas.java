package avilaos.metricas;

/** Métricas acumuladas de un computador o del clúster completo; SIN_DATO (-1) indica que aún no hay datos para calcularla. */
public record Metricas(
        String nombre,
        long ciclos,
        int terminadosOk,
        int vencidos,
        int vivos,
        double throughput,
        double utilizacion,
        double porcentajeUsuario,
        double porcentajeSO,
        double tiempoRespuesta,
        double cumplimiento,
        double equidad) {

    public static final double SIN_DATO = -1;
}
