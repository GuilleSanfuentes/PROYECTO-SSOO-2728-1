package avilaos.hardware;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import java.util.concurrent.Semaphore;

/** Memoria principal: solo controla cuánto está usado y por quién (sin asignación contigua). */
public class RAM {

    private record Asignacion(String dueno, int cantidad) {
    }

    private final String nombre;
    private final int capacidad;
    private final ListaEnlazada<Asignacion> asignaciones = new ListaEnlazada<>();
    private final Semaphore mutex = new Semaphore(1);
    private int usada;

    /** Crea una RAM de la capacidad indicada; si no es válida usa 1024. */
    public RAM(String nombre, int capacidad) {
        if (capacidad <= 0) {
            Debug.error(nombre, "capacidad inválida " + capacidad + ", se usa 1024");
            capacidad = 1024;
        }
        this.nombre = nombre;
        this.capacidad = capacidad;
    }

    /** Reserva memoria para un dueño (ej. "P3-H1" o "Buffer B1"). Devuelve false si no cabe. */
    public boolean reservar(String dueno, int cantidad) {
        if (dueno == null || dueno.isBlank() || cantidad <= 0) {
            Debug.error(nombre, "reserva inválida: dueño '" + dueno + "', cantidad " + cantidad);
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            if (buscar(dueno) != null) {
                Debug.error(nombre, dueno + " ya tiene memoria reservada");
                return false;
            }
            if (usada + cantidad > capacidad) {
                Debug.info(nombre, "sin espacio para " + dueno + ": pide " + cantidad + ", libre " + (capacidad - usada));
                return false;
            }
            asignaciones.agregarFinal(new Asignacion(dueno, cantidad));
            usada += cantidad;
            Debug.ok(nombre, "reservadas " + cantidad + " para " + dueno + " -> " + usada + "/" + capacidad);
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Libera toda la memoria del dueño. Devuelve la cantidad liberada, o 0 si no tenía. */
    public int liberar(String dueno) {
        mutex.acquireUninterruptibly();
        try {
            Asignacion asignacion = buscar(dueno);
            if (asignacion == null) {
                Debug.error(nombre, "liberar: " + dueno + " no tiene memoria reservada");
                return 0;
            }
            asignaciones.eliminar(asignacion);
            usada -= asignacion.cantidad();
            Debug.ok(nombre, "liberadas " + asignacion.cantidad() + " de " + dueno + " -> " + usada + "/" + capacidad);
            return asignacion.cantidad();
        } finally {
            mutex.release();
        }
    }

    /** Indica si hay espacio libre para la cantidad pedida. */
    public boolean cabe(int cantidad) {
        mutex.acquireUninterruptibly();
        try {
            return cantidad > 0 && usada + cantidad <= capacidad;
        } finally {
            mutex.release();
        }
    }

    /** Comprueba que la suma de asignaciones coincide con lo usado y no supera la capacidad. */
    public boolean verificarIntegridad() {
        mutex.acquireUninterruptibly();
        try {
            int suma = 0;
            Nodo<Asignacion> nodo = asignaciones.getCabeza();
            while (nodo != null) {
                suma += nodo.getDato().cantidad();
                nodo = nodo.getSiguiente();
            }
            boolean correcta = suma == usada && usada >= 0 && usada <= capacidad;
            if (!correcta) {
                Debug.error(nombre, "integridad rota: usada=" + usada + ", suma de asignaciones=" + suma + ", capacidad=" + capacidad);
            }
            return correcta;
        } finally {
            mutex.release();
        }
    }

    /** Busca la asignación del dueño (llamar con el mutex tomado). */
    private Asignacion buscar(String dueno) {
        Nodo<Asignacion> nodo = asignaciones.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().dueno().equals(dueno)) {
                return nodo.getDato();
            }
            nodo = nodo.getSiguiente();
        }
        return null;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCapacidad() {
        return capacidad;
    }

    /** Memoria usada en este momento. */
    public int getUsada() {
        mutex.acquireUninterruptibly();
        try {
            return usada;
        } finally {
            mutex.release();
        }
    }

    /** Memoria libre en este momento. */
    public int getLibre() {
        return capacidad - getUsada();
    }
}
