package avilaos.so.disco.politicas;

import avilaos.so.disco.SolicitudDisco;

/** Próxima parada que decide la política: atender una solicitud, ir a un extremo o saltar sin recorrer. */
public record Parada(SolicitudDisco solicitud, int destino, boolean salto) {

    /** Atender la solicitud indicada. */
    public static Parada atender(SolicitudDisco solicitud) {
        return new Parada(solicitud, solicitud.destino(), false);
    }

    /** Recorrer el disco hasta un extremo sin atender nada (SCAN y C-SCAN). */
    public static Parada extremo(int posicion) {
        return new Parada(null, posicion, false);
    }

    /** Llevar el cabezal a una posición sin recorrer el disco (regreso de C-SCAN y C-LOOK). */
    public static Parada saltar(int posicion) {
        return new Parada(null, posicion, true);
    }

    /** Indica si la parada atiende una solicitud. */
    public boolean esAtencion() {
        return solicitud != null;
    }
}
