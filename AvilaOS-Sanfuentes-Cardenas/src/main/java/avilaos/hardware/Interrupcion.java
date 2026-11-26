package avilaos.hardware;

import avilaos.enums.TipoInterrupcion;

/** Aviso de un dispositivo a la CPU: qué terminó, para qué proceso, en qué ciclo y, si es del disco, qué solicitud. */
public record Interrupcion(TipoInterrupcion tipo, int idProceso, String etiqueta, String origen, long ciclo, int idSolicitud) {

    public static final int SIN_SOLICITUD = -1;

    /** Aviso que no viene de una solicitud del disco. */
    public Interrupcion(TipoInterrupcion tipo, int idProceso, String etiqueta, String origen, long ciclo) {
        this(tipo, idProceso, etiqueta, origen, ciclo, SIN_SOLICITUD);
    }

    /** Texto del tipo "Fin de E/S de P2-H1 (DMA-PC1, ciclo 15)". */
    @Override
    public String toString() {
        return tipo.getNombre() + " de " + etiqueta + " (" + origen + ", ciclo " + ciclo + ")";
    }
}
