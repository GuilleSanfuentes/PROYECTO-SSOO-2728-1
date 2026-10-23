package avilaos.hardware;

/** Operación en curso en un dispositivo: a quién pertenece y cuántos ciclos le faltan. */
final class OperacionEspera {

    final int idProceso;
    final String etiqueta;
    final String detalle;
    int restantes;

    /** Crea la operación con los ciclos que dura. */
    OperacionEspera(int idProceso, String etiqueta, String detalle, int ciclos) {
        this.idProceso = idProceso;
        this.etiqueta = etiqueta;
        this.detalle = detalle;
        this.restantes = ciclos;
    }
}
