package avilaos.so.disco.politicas;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.so.disco.SolicitudDisco;

/** C-LOOK: como C-SCAN, pero salta directo a la solicitud más alejada del otro lado en vez de ir al extremo. */
public class PoliticaDiscoCLOOK implements PoliticaDisco {

    @Override
    public PoliticaDiscoTipo getTipo() {
        return PoliticaDiscoTipo.C_LOOK;
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
        return Parada.saltar(Busqueda.inicioDelBarrido(cola, direccion).destino());
    }
}
