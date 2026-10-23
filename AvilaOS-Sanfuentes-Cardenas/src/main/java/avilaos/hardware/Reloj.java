package avilaos.hardware;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import java.util.concurrent.Semaphore;

/**
 * Reloj global. En cada ciclo ejecuta las fases en orden; dentro de una fase cada componente corre en su hilo
 * y la fase no termina hasta que todos terminan (barrera). Así cada computador avanza exactamente un ciclo.
 */
public class Reloj {

    public static final int DURACION_MAXIMA_MS = 60000;
    public static final int DURACION_POR_DEFECTO_MS = 500;

    private static final String ORIGEN = "Reloj";

    private final ListaEnlazada<FaseReloj> fases = new ListaEnlazada<>();
    private final ListaEnlazada<ObservadorReloj> observadores = new ListaEnlazada<>();
    private final Semaphore mutexCiclo = new Semaphore(1);
    private final Semaphore esperaPausa = new Semaphore(0);

    private volatile long ciclo;
    private volatile int duracionCicloMs;
    private volatile boolean ejecutando;
    private volatile boolean pausado;
    private Thread hilo;

    /** Crea el reloj detenido en el ciclo 0. */
    public Reloj(int duracionCicloMs) {
        this.duracionCicloMs = DURACION_POR_DEFECTO_MS;
        setDuracionCicloMs(duracionCicloMs);
    }

    /** Agrega una fase al final del ciclo y devuelve su número. */
    public int agregarFase(String nombre) {
        mutexCiclo.acquireUninterruptibly();
        try {
            fases.agregarFinal(new FaseReloj(nombre));
            Debug.ok(ORIGEN, "fase " + (fases.tamano() - 1) + " '" + nombre + "' agregada");
            return fases.tamano() - 1;
        } finally {
            mutexCiclo.release();
        }
    }

    /** Agrega un componente a la fase indicada; arranca su hilo. */
    public boolean agregarComponente(int numeroFase, ComponenteReloj componente) {
        if (componente == null) {
            Debug.error(ORIGEN, "componente nulo");
            return false;
        }
        mutexCiclo.acquireUninterruptibly();
        try {
            if (numeroFase < 0 || numeroFase >= fases.tamano()) {
                Debug.error(ORIGEN, "fase " + numeroFase + " no existe (hay " + fases.tamano() + ")");
                return false;
            }
            FaseReloj fase = fases.obtener(numeroFase);
            fase.agregar(componente);
            Debug.ok(ORIGEN, componente.getNombre() + " conectado a la fase '" + fase.getNombre() + "' con hilo propio");
            return true;
        } finally {
            mutexCiclo.release();
        }
    }

    /** Agrega un observador que se avisa al cerrar cada ciclo. */
    public void agregarObservador(ObservadorReloj observador) {
        mutexCiclo.acquireUninterruptibly();
        try {
            observadores.agregarFinal(observador);
        } finally {
            mutexCiclo.release();
        }
    }

    /** Cambia la duración del ciclo (0 = sin espera). Se aplica desde el ciclo siguiente. */
    public boolean setDuracionCicloMs(int ms) {
        if (ms < 0 || ms > DURACION_MAXIMA_MS) {
            Debug.error(ORIGEN, "duración " + ms + " ms fuera de 0.." + DURACION_MAXIMA_MS + ", se mantiene " + duracionCicloMs);
            return false;
        }
        duracionCicloMs = ms;
        Debug.ok(ORIGEN, "duración del ciclo: " + ms + " ms");
        return true;
    }

    /** Arranca el hilo del reloj. */
    public boolean iniciar() {
        if (ejecutando) {
            Debug.error(ORIGEN, "el reloj ya está en marcha");
            return false;
        }
        ejecutando = true;
        pausado = false;
        hilo = new Thread(this::bucle, "Hilo-Reloj");
        hilo.setDaemon(true);
        hilo.start();
        Debug.ok(ORIGEN, "en marcha (" + duracionCicloMs + " ms por ciclo)");
        return true;
    }

    /** Detiene el avance al terminar el ciclo en curso. */
    public void pausar() {
        if (ejecutando && !pausado) {
            pausado = true;
            Debug.ok(ORIGEN, "pausado en el ciclo " + ciclo);
        }
    }

    /** Continúa después de una pausa. */
    public void reanudar() {
        if (ejecutando && pausado) {
            pausado = false;
            esperaPausa.release();
            Debug.ok(ORIGEN, "reanudado en el ciclo " + ciclo);
        }
    }

    /** Detiene el hilo del reloj; los componentes siguen vivos. */
    public void detener() {
        if (!ejecutando) {
            return;
        }
        ejecutando = false;
        pausado = false;
        esperaPausa.release();
        hilo.interrupt();
        try {
            hilo.join(DURACION_MAXIMA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Debug.ok(ORIGEN, "detenido en el ciclo " + ciclo);
    }

    /** Detiene el reloj y termina los hilos de todos los componentes. */
    public void apagar() {
        detener();
        mutexCiclo.acquireUninterruptibly();
        try {
            Nodo<FaseReloj> nodo = fases.getCabeza();
            while (nodo != null) {
                nodo.getDato().apagar();
                nodo = nodo.getSiguiente();
            }
        } finally {
            mutexCiclo.release();
        }
        Debug.ok(ORIGEN, "componentes apagados");
    }

    /** Ejecuta n ciclos seguidos en el hilo que llama, sin esperas (para pruebas y avance paso a paso). */
    public boolean ejecutarCiclos(int n) {
        if (ejecutando) {
            Debug.error(ORIGEN, "ejecutarCiclos con el reloj en marcha; usar pausar o detener antes");
            return false;
        }
        for (int i = 0; i < n; i++) {
            ejecutarUnCiclo();
        }
        return true;
    }

    /** Bucle del hilo del reloj: espera la duración del ciclo y lo ejecuta, salvo pausa o detención. */
    private void bucle() {
        while (ejecutando) {
            if (pausado) {
                esperaPausa.acquireUninterruptibly();
                continue;
            }
            int espera = duracionCicloMs;
            if (espera > 0) {
                try {
                    Thread.sleep(espera);
                } catch (InterruptedException e) {
                    if (!ejecutando) {
                        return;
                    }
                }
            }
            if (ejecutando && !pausado) {
                ejecutarUnCiclo();
            }
        }
    }

    /** Tic, tac, cada tic y cada tac del reloj. */
    private void ejecutarUnCiclo() {
        mutexCiclo.acquireUninterruptibly();
        try {
            long actual = ciclo + 1;
            ciclo = actual;
            Debug.setCiclo(actual);
            Debug.info(ORIGEN, "===== ciclo " + actual + " =====");
            Nodo<FaseReloj> fase = fases.getCabeza();
            while (fase != null) {
                fase.getDato().ejecutar(actual);
                fase = fase.getSiguiente();
            }
            Nodo<ObservadorReloj> observador = observadores.getCabeza();
            while (observador != null) {
                try {
                    observador.getDato().alCerrarCiclo(actual);
                } catch (RuntimeException e) {
                    Debug.error(ORIGEN, "observador falló al cerrar el ciclo " + actual + ": " + e);
                }
                observador = observador.getSiguiente();
            }
        } finally {
            mutexCiclo.release();
        }
    }

    /** Ciclo actual desde el inicio de la simulación. */
    public long getCiclo() {
        return ciclo;
    }

    public int getDuracionCicloMs() {
        return duracionCicloMs;
    }

    public boolean isEjecutando() {
        return ejecutando;
    }

    public boolean isPausado() {
        return pausado;
    }

    /** Texto del tipo "0 'E/S' [DMA-PC1, Red-PC1] -> 1 'CPU' [CPU-PC1]". */
    public String describirFases() {
        mutexCiclo.acquireUninterruptibly();
        try {
            StringBuilder sb = new StringBuilder();
            Nodo<FaseReloj> nodo = fases.getCabeza();
            int numero = 0;
            while (nodo != null) {
                sb.append(numero > 0 ? " -> " : "").append(numero).append(" '").append(nodo.getDato().getNombre())
                        .append("' [").append(nodo.getDato().describirComponentes()).append(']');
                nodo = nodo.getSiguiente();
                numero++;
            }
            return sb.toString();
        } finally {
            mutexCiclo.release();
        }
    }
}
