package avilaos.vista.gui;

import avilaos.enums.TipoOperacionES;
import avilaos.so.disco.SolicitudDisco;

/** Copia de una solicitud de la cola del disco para la tabla: quién, qué operación, dónde y cuánto lleva esperando. */
public record FilaSolicitud(int id, String proceso, TipoOperacionES operacion, String ruta, int destino, int bloques,
        long espera, boolean enAtencion, int[] cadena) {

    /** Copia la solicitud; la espera se mide hasta el ciclo indicado. */
    public static FilaSolicitud de(SolicitudDisco s, long ciclo, boolean enAtencion) {
        return new FilaSolicitud(s.id(), s.etiqueta(), s.operacion(), s.ruta() == null ? "" : s.ruta(), s.destino(),
                s.cantidadBloques(), Math.max(0, ciclo - s.cicloLlegada()), enAtencion, s.bloques().clone());
    }
}
