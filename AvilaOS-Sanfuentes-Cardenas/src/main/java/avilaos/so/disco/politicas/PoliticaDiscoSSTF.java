package avilaos.so.disco.politicas;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.so.disco.SolicitudDisco;

/** SSTF: atiende la solicitud más cercana al cabezal; en empate, la que llegó antes. */
public class PoliticaDiscoSSTF implements PoliticaDisco {

    @Override
    public PoliticaDiscoTipo getTipo() {
        return PoliticaDiscoTipo.SSTF;
    }

    @Override
    public Parada siguiente(ListaEnlazada<SolicitudDisco> cola, int cabezal, DireccionCabezal direccion, int ultimaPosicion) {
        SolicitudDisco cercana = Busqueda.masCercana(cola, cabezal);
        return cercana == null ? null : Parada.atender(cercana);
    }
}
