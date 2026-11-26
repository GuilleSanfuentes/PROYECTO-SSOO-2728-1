package avilaos.metricas;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;

/** Desplazamiento acumulado ciclo a ciclo, sin límite; de cada tramo quieto guarda solo los extremos. No es segura entre hilos. */
public class SerieDesplazamiento {

    private final ListaEnlazada<PuntoDesplazamiento> fijos = new ListaEnlazada<>();
    private PuntoDesplazamiento ultimo = new PuntoDesplazamiento(0, 0);

    /** Anota el acumulado al terminar un ciclo del disco. */
    public void registrar(long ciclo, long acumulado) {
        PuntoDesplazamiento anterior = fijos.ultimo();
        boolean sigueQuieto = anterior != null && anterior.acumulado() == acumulado && ultimo.acumulado() == acumulado;
        if (!sigueQuieto) {
            fijos.agregarFinal(ultimo);
        }
        ultimo = new PuntoDesplazamiento(ciclo, acumulado);
    }

    /** Copia de los puntos en orden de ciclo; empieza en el ciclo 0. */
    public PuntoDesplazamiento[] copiar() {
        PuntoDesplazamiento[] puntos = new PuntoDesplazamiento[fijos.tamano() + 1];
        Nodo<PuntoDesplazamiento> nodo = fijos.getCabeza();
        for (int i = 0; nodo != null; i++) {
            puntos[i] = nodo.getDato();
            nodo = nodo.getSiguiente();
        }
        puntos[puntos.length - 1] = ultimo;
        return puntos;
    }
}
