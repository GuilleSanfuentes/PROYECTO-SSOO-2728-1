package avilaos.hardware;

import avilaos.estructuras.Cola;
import avilaos.estructuras.ListaEnlazada;
import avilaos.log.Debug;
import java.util.concurrent.Semaphore;

/** Línea de interrupciones de un computador: los dispositivos las dejan y la CPU las recoge. */
public class LineaInterrupciones {

    private final String nombre;
    private final Cola<Interrupcion> pendientes = new Cola<>();
    private final Semaphore mutex = new Semaphore(1);

    /** Crea la línea con el nombre indicado. */
    public LineaInterrupciones(String nombre) {
        this.nombre = nombre;
    }

    /** Deja una interrupción pendiente para la CPU. */
    public void registrar(Interrupcion interrupcion) {
        if (interrupcion == null) {
            Debug.error(nombre, "interrupción nula");
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            pendientes.encolar(interrupcion);
        } finally {
            mutex.release();
        }
        Debug.info(nombre, "interrupción: " + interrupcion);
    }

    /** Saca todas las interrupciones pendientes en orden de llegada. */
    public ListaEnlazada<Interrupcion> tomarTodas() {
        ListaEnlazada<Interrupcion> tomadas = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            while (!pendientes.estaVacia()) {
                tomadas.agregarFinal(pendientes.desencolar());
            }
        } finally {
            mutex.release();
        }
        return tomadas;
    }

    /** Cantidad de interrupciones sin atender. */
    public int cantidadPendientes() {
        mutex.acquireUninterruptibly();
        try {
            return pendientes.tamano();
        } finally {
            mutex.release();
        }
    }
}
