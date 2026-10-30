package avilaos.hardware;

/** Registros de la CPU que se guardan en el cambio de contexto. */
public record ContextoCPU(int idProceso, String etiqueta, int contador, int mar) {
}
