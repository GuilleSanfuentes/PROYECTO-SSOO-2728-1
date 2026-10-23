package avilaos.hardware;

import avilaos.enums.TipoInterrupcion;

/** Aviso de un dispositivo a la CPU: qué terminó, para qué proceso y en qué ciclo. */
public record Interrupcion(TipoInterrupcion tipo, int idProceso, String etiqueta, String origen, long ciclo) {

    /** Texto del tipo "Fin de E/S de P2-H1 (DMA-PC1, ciclo 15)". */
    @Override
    public String toString() {
        return tipo.getNombre() + " de " + etiqueta + " (" + origen + ", ciclo " + ciclo + ")";
    }
}
