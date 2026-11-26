package avilaos.so.archivos;

/** Resultado de reservar un archivo: la reserva, o el motivo por el que no se pudo. */
public record ResultadoReserva(ReservaArchivo reserva, String motivo) {

    /** Indica si los bloques quedaron reservados. */
    public boolean exito() {
        return reserva != null;
    }
}
