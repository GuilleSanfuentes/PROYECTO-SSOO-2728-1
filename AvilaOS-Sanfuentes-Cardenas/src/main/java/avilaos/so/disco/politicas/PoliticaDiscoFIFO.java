package avilaos.so.disco.politicas;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.so.disco.SolicitudDisco;

/** FIFO: atiende las solicitudes en orden de llegada, sin mirar dónde está el cabezal. */
public class PoliticaDiscoFIFO implements PoliticaDisco {

    @Override
    public PoliticaDiscoTipo getTipo() {
        return PoliticaDiscoTipo.FIFO;
    }

    @Override
    public Parada siguiente(ListaEnlazada<SolicitudDisco> cola, int cabezal, DireccionCabezal direccion, int ultimaPosicion) {
        return cola.estaVacia() ? null : Parada.atender(cola.primero());
    }
}
