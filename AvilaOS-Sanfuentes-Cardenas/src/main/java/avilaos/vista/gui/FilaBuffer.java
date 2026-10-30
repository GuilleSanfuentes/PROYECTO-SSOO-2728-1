package avilaos.vista.gui;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.so.PCB;
import avilaos.sync.EstadoBuffer;

/** Copia inmutable de un buffer para la tabla: contadores, valores de semáforos y bloqueados como texto. */
public record FilaBuffer(
        String nombre,
        int anfitrion,
        int elementos,
        int capacidad,
        int valorVacio,
        int valorLleno,
        int valorMutex,
        int producidos,
        int consumidos,
        String bloqueadosVacio,
        String bloqueadosLleno,
        String bloqueadosMutex) {

    public static final String NINGUNO = "-";

    /** Convierte la foto del buffer en una fila lista para dibujar. */
    public static FilaBuffer de(EstadoBuffer e) {
        return new FilaBuffer(e.nombre(), e.anfitrion(), e.elementos(), e.capacidad(), e.valorVacio(),
                e.valorLleno(), e.valorMutex(), e.producidos(), e.consumidos(),
                etiquetas(e.bloqueadosVacio()), etiquetas(e.bloqueadosLleno()), etiquetas(e.bloqueadosMutex()));
    }

    private static String etiquetas(ListaEnlazada<PCB> lista) {
        StringBuilder sb = new StringBuilder();
        Nodo<PCB> nodo = lista.getCabeza();
        while (nodo != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(nodo.getDato().getEtiqueta());
            nodo = nodo.getSiguiente();
        }
        return sb.length() == 0 ? NINGUNO : sb.toString();
    }
}
