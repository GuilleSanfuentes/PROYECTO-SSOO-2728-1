package avilaos.so.disco.politicas;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.so.disco.SolicitudDisco;

/** Política de planificación del disco: decide la próxima parada del cabezal. Agregar una es crear otra clase. */
public interface PoliticaDisco {

    /** Tipo de la política (para el log, la interfaz y las métricas). */
    PoliticaDiscoTipo getTipo();

    /** Próxima parada según la cola (en orden de llegada), el cabezal y su sentido; null si la cola está vacía. */
    Parada siguiente(ListaEnlazada<SolicitudDisco> cola, int cabezal, DireccionCabezal direccion, int ultimaPosicion);
}
