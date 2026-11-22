package avilaos.so.disco.politicas;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.so.disco.SolicitudDisco;

/** LOOK: como SCAN, pero da la vuelta en la última solicitud de su sentido sin llegar al extremo del disco. */
public class PoliticaDiscoLOOK implements PoliticaDisco {

    @Override
    public PoliticaDiscoTipo getTipo() {
        return PoliticaDiscoTipo.LOOK;
    }

    @Override
    public Parada siguiente(ListaEnlazada<SolicitudDisco> cola, int cabezal, DireccionCabezal direccion, int ultimaPosicion) {
        if (cola.estaVacia()) {
            return null;
        }
        SolicitudDisco adelante = Busqueda.siguienteEnSentido(cola, cabezal, direccion);
        if (adelante == null) {
            adelante = Busqueda.siguienteEnSentido(cola, cabezal, direccion.opuesta());
        }
        return Parada.atender(adelante);
    }
}
