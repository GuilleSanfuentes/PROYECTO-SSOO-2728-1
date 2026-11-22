package avilaos.so.archivos;

/** Resultado de una operación sobre el sistema de archivos: si se hizo, el motivo si no, y los bloques afectados. */
public record ResultadoFS(boolean exito, String motivo, InfoEntrada entrada, int bloques) {

    /** Operación hecha sobre la entrada indicada. */
    static ResultadoFS hecho(InfoEntrada entrada, int bloques) {
        return new ResultadoFS(true, null, entrada, bloques);
    }

    /** Operación rechazada con su motivo, para mostrarlo al usuario. */
    static ResultadoFS rechazo(String motivo) {
        return new ResultadoFS(false, motivo, null, 0);
    }
}
