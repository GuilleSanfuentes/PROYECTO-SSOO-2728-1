package avilaos.so.disco;

import avilaos.enums.TipoOperacionES;

/** Solicitud de E/S en la cola del disco: quién la pide, qué operación y qué bloques recorre (el primero es su destino). */
public record SolicitudDisco(int id, int idProceso, String etiqueta, TipoOperacionES operacion, String ruta,
        int[] bloques, long cicloLlegada) {

    /** Bloque al que debe ir el cabezal para empezar a atenderla. */
    public int destino() {
        return bloques[0];
    }

    /** Cantidad de bloques a leer o escribir (un ciclo cada uno). */
    public int cantidadBloques() {
        return bloques.length;
    }

    /** Texto corto para la interfaz y el log: "P4-H1 Leer bloque 87". */
    public String describir() {
        return etiqueta + " " + operacion.getNombre() + " bloque " + destino()
                + (bloques.length > 1 ? " (+" + (bloques.length - 1) + ")" : "");
    }
}
