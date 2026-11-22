package avilaos.so.disco.politicas;

import avilaos.enums.DireccionCabezal;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.so.disco.SolicitudDisco;

/** Búsquedas sobre la cola del disco que comparten las políticas; en empate gana la que llegó antes. */
final class Busqueda {

    private Busqueda() {
    }

    /** La solicitud más cercana al cabezal en cualquier sentido, o null si la cola está vacía. */
    static SolicitudDisco masCercana(ListaEnlazada<SolicitudDisco> cola, int cabezal) {
        SolicitudDisco mejor = null;
        Nodo<SolicitudDisco> nodo = cola.getCabeza();
        while (nodo != null) {
            SolicitudDisco s = nodo.getDato();
            if (mejor == null || Math.abs(s.destino() - cabezal) < Math.abs(mejor.destino() - cabezal)) {
                mejor = s;
            }
            nodo = nodo.getSiguiente();
        }
        return mejor;
    }

    /** La más cercana por delante del cabezal en el sentido dado (incluida la que está justo debajo), o null. */
    static SolicitudDisco siguienteEnSentido(ListaEnlazada<SolicitudDisco> cola, int cabezal, DireccionCabezal direccion) {
        SolicitudDisco mejor = null;
        Nodo<SolicitudDisco> nodo = cola.getCabeza();
        while (nodo != null) {
            SolicitudDisco s = nodo.getDato();
            int avance = (s.destino() - cabezal) * direccion.getPaso();
            if (avance >= 0 && (mejor == null || avance < (mejor.destino() - cabezal) * direccion.getPaso())) {
                mejor = s;
            }
            nodo = nodo.getSiguiente();
        }
        return mejor;
    }

    /** La más alejada en sentido contrario: donde reanuda un barrido circular (la menor si sube, la mayor si baja). */
    static SolicitudDisco inicioDelBarrido(ListaEnlazada<SolicitudDisco> cola, DireccionCabezal direccion) {
        SolicitudDisco mejor = null;
        Nodo<SolicitudDisco> nodo = cola.getCabeza();
        while (nodo != null) {
            SolicitudDisco s = nodo.getDato();
            if (mejor == null || s.destino() * direccion.getPaso() < mejor.destino() * direccion.getPaso()) {
                mejor = s;
            }
            nodo = nodo.getSiguiente();
        }
        return mejor;
    }

    /** Extremo del disco hacia el que avanza el cabezal. */
    static int extremo(DireccionCabezal direccion, int ultimaPosicion) {
        return direccion == DireccionCabezal.SUBIENDO ? ultimaPosicion : 0;
    }
}
