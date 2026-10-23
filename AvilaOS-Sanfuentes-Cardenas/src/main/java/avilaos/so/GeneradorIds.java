package avilaos.so;

import java.util.concurrent.Semaphore;

/** Genera IDs de proceso únicos en todo el sistema distribuido. */
public final class GeneradorIds {

    private static final Semaphore mutex = new Semaphore(1);
    private static int siguiente = 1;

    private GeneradorIds() {
    }

    /** Devuelve un ID nuevo; es seguro llamarlo desde varios hilos. */
    public static int siguienteId() {
        mutex.acquireUninterruptibly();
        try {
            return siguiente++;
        } finally {
            mutex.release();
        }
    }

    /** Reinicia la numeración para una simulación nueva. */
    public static void reiniciar() {
        mutex.acquireUninterruptibly();
        try {
            siguiente = 1;
        } finally {
            mutex.release();
        }
    }
}
