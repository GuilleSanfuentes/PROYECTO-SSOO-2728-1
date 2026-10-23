package avilaos.hardware;

import avilaos.log.Debug;
import java.util.concurrent.Semaphore;

/** Hilo de Java propio de un componente: espera el disparo del reloj, ejecuta su tick y avisa que terminó. */
final class HiloComponente implements Runnable {

    private static final long ESPERA_APAGADO_MS = 1000;

    private final ComponenteReloj componente;
    private final Semaphore inicio = new Semaphore(0);
    private final Semaphore fin;
    private final Thread hilo;
    private volatile long ciclo;
    private volatile boolean vivo = true;

    /** Crea y arranca el hilo; queda dormido hasta el primer disparo. */
    HiloComponente(ComponenteReloj componente, Semaphore fin) {
        this.componente = componente;
        this.fin = fin;
        this.hilo = new Thread(this, "Hilo-" + componente.getNombre());
        this.hilo.setDaemon(true);
        this.hilo.start();
    }

    /** Despierta al componente para que ejecute el ciclo indicado. */
    void disparar(long cicloActual) {
        ciclo = cicloActual;
        inicio.release();
    }

    /** Esta función define la vida de cada hilo. */
    @Override
    public void run() {
        while (true) {
            inicio.acquireUninterruptibly();
            if (!vivo) {
                return;
            }
            try {
                componente.tick(ciclo);
            } catch (RuntimeException e) {
                StackTraceElement[] pila = e.getStackTrace();
                String donde = pila.length > 0 ? " en " + pila[0] : "";
                Debug.error(componente.getNombre(), "falló en el ciclo " + ciclo + ": " + e + donde);
            } finally {
                fin.release();
            }
        }
    }

    /** Termina el hilo y espera a que salga. */
    void apagar() {
        vivo = false;
        inicio.release();
        try {
            hilo.join(ESPERA_APAGADO_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Nombre del componente que ejecuta este hilo. */
    String getNombreComponente() {
        return componente.getNombre();
    }
}
