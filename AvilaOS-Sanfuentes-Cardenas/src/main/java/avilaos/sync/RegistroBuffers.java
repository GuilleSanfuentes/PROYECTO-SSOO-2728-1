package avilaos.sync;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import java.util.concurrent.Semaphore;

/** Buffers existentes en el clúster, consultables por nombre desde cualquier kernel. */
public class RegistroBuffers {

    private static final String ORIGEN = "RegistroBuffers";

    private final ListaEnlazada<Buffer> buffers = new ListaEnlazada<>();
    private final Semaphore mutex = new Semaphore(1);

    /** Agrega el buffer si su nombre no está repetido. */
    public boolean agregar(Buffer buffer) {
        mutex.acquireUninterruptibly();
        try {
            if (buscarSinBloqueo(buffer.getNombre()) != null) {
                Debug.error(ORIGEN, "ya existe un buffer llamado '" + buffer.getNombre() + "'");
                return false;
            }
            buffers.agregarFinal(buffer);
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Busca un buffer por nombre; null si no existe. */
    public Buffer buscar(String nombre) {
        mutex.acquireUninterruptibly();
        try {
            return buscarSinBloqueo(nombre);
        } finally {
            mutex.release();
        }
    }

    /** Copia de la lista de buffers en orden de creación. */
    public ListaEnlazada<Buffer> copiar() {
        mutex.acquireUninterruptibly();
        try {
            ListaEnlazada<Buffer> copia = new ListaEnlazada<>();
            Nodo<Buffer> nodo = buffers.getCabeza();
            while (nodo != null) {
                copia.agregarFinal(nodo.getDato());
                nodo = nodo.getSiguiente();
            }
            return copia;
        } finally {
            mutex.release();
        }
    }

    /** Cantidad de buffers creados. */
    public int cantidad() {
        mutex.acquireUninterruptibly();
        try {
            return buffers.tamano();
        } finally {
            mutex.release();
        }
    }

    /** Busca por nombre (llamar con el mutex tomado). */
    private Buffer buscarSinBloqueo(String nombre) {
        Nodo<Buffer> nodo = buffers.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().getNombre().equals(nombre)) {
                return nodo.getDato();
            }
            nodo = nodo.getSiguiente();
        }
        return null;
    }
}
