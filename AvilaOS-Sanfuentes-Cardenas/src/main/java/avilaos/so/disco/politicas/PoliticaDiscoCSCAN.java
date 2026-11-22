package avilaos.so.disco.politicas;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.so.disco.SolicitudDisco;

/** C-SCAN: barre en un solo sentido hasta el extremo, salta al otro extremo sin atender y vuelve a barrer. */
public class PoliticaDiscoCSCAN implements PoliticaDisco {

    @Override
    public PoliticaDiscoTipo getTipo() {
        return PoliticaDiscoTipo.C_SCAN;
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
        return Parada.saltar(Busqueda.extremo(direccion.opuesta(), ultimaPosicion));
    }
}
