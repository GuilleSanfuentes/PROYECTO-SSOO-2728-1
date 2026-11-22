package avilaos.so.disco.politicas;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.so.disco.SolicitudDisco;

/** SCAN (ascensor): barre en su sentido hasta el extremo del disco y luego regresa atendiendo las del otro lado. */
public class PoliticaDiscoSCAN implements PoliticaDisco {

    @Override
    public PoliticaDiscoTipo getTipo() {
        return PoliticaDiscoTipo.SCAN;
    }

    @Override
    public Parada siguiente(ListaEnlazada<SolicitudDisco> cola, int cabezal, DireccionCabezal direccion, int ultimaPosicion) {
        if (cola.estaVacia()) {
            return null;
        }
        SolicitudDisco adelante = Busqueda.siguienteEnSentido(cola, cabezal, direccion);
        if (adelante != null) {
            return Parada.atender(adelante);
        }
        int extremo = Busqueda.extremo(direccion, ultimaPosicion);
        if (cabezal != extremo) {
            return Parada.extremo(extremo);
        }
        return Parada.atender(Busqueda.siguienteEnSentido(cola, cabezal, direccion.opuesta()));
    }
}
