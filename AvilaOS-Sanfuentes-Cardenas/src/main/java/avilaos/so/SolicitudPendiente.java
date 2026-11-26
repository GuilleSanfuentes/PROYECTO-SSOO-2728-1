package avilaos.so;

import avilaos.so.archivos.ReservaArchivo;

/** Lo que un proceso bloqueado espera del disco: solicitud, entrada afectada y reserva (null si no crea). */
public record SolicitudPendiente(int idSolicitud, int idEntrada, ReservaArchivo reserva) {
}
