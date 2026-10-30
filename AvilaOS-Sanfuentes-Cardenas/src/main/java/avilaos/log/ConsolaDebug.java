package avilaos.log;

import java.util.concurrent.Semaphore;

/** Copia acotada de la salida del Debug para mostrarla en la interfaz; solo guarda mientras está activa. */
public class ConsolaDebug {

    private final String[] lineas;
    private final Semaphore mutex = new Semaphore(1);
    private volatile boolean activa = false;
    private int inicio = 0;
    private int cantidad = 0;

    /** Crea la consola con espacio para la cantidad de mensajes indicada. */
    public ConsolaDebug(int capacidad) {
        this.lineas = new String[Math.max(1, capacidad)];
    }

    /** Activa o desactiva la captura; al desactivarla se vacía. */
    public void setActiva(boolean valor) {
        activa = valor;
        if (!valor) {
            mutex.acquireUninterruptibly();
            try {
                inicio = 0;
                cantidad = 0;
            } finally {
                mutex.release();
            }
        }
    }

    public boolean isActiva() {
        return activa;
    }

    /** Guarda un mensaje; si está llena descarta el más antiguo. */
    public void agregar(String texto) {
        if (!activa) {
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            lineas[(inicio + cantidad) % lineas.length] = texto;
            if (cantidad < lineas.length) {
                cantidad++;
            } else {
                inicio = (inicio + 1) % lineas.length;
            }
        } finally {
            mutex.release();
        }
    }

    /** Copia de los últimos n mensajes, del más antiguo al más reciente. */
    public String[] copiarUltimos(int n) {
        mutex.acquireUninterruptibly();
        try {
            int total = Math.min(Math.max(0, n), cantidad);
            String[] copia = new String[total];
            int primero = cantidad - total;
            for (int i = 0; i < total; i++) {
                copia[i] = lineas[(inicio + primero + i) % lineas.length];
            }
            return copia;
        } finally {
            mutex.release();
        }
    }
}
