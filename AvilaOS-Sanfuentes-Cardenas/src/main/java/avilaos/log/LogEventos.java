package avilaos.log;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import java.util.concurrent.Semaphore;

/** Registro de eventos del sistema. Se guarda siempre, con o sin debugger. */
public class LogEventos {

    public static final int CAPACIDAD_POR_DEFECTO = 10000;

    private final ListaEnlazada<String> eventos;
    private final Semaphore mutex;
    private final int capacidad;

    /** Crea un log que conserva como máximo la cantidad de eventos indicada. */
    public LogEventos(int capacidad) {
        if (capacidad <= 0) {
            Debug.error("LogEventos", "capacidad inválida " + capacidad + ", se usa " + CAPACIDAD_POR_DEFECTO);
            capacidad = CAPACIDAD_POR_DEFECTO;
        }
        this.capacidad = capacidad;
        this.eventos = new ListaEnlazada<>();
        this.mutex = new Semaphore(1);
    }

    /** Registra un evento; si se supera la capacidad descarta el más antiguo. */
    public void registrar(long ciclo, String texto) {
        String linea = "[Ciclo " + ciclo + "] " + texto;
        mutex.acquireUninterruptibly();
        try {
            eventos.agregarFinal(linea);
            if (eventos.tamano() > capacidad) {
                eventos.eliminarEn(0);
            }
        } finally {
            mutex.release();
        }
        Debug.info("LOG", texto);
    }

    /** Devuelve los últimos n eventos unidos por el separador; vacío si el log está ocupado. */
    public String ultimos(int n, String separador) {
        if (!mutex.tryAcquire()) {
            return "";
        }
        try {
            int saltar = Math.max(0, eventos.tamano() - n);
            StringBuilder sb = new StringBuilder();
            Nodo<String> actual = eventos.getCabeza();
            int indice = 0;
            while (actual != null) {
                if (indice >= saltar) {
                    if (sb.length() > 0) {
                        sb.append(separador);
                    }
                    sb.append(actual.getDato());
                }
                actual = actual.getSiguiente();
                indice++;
            }
            return sb.toString();
        } finally {
            mutex.release();
        }
    }

    /** Devuelve una copia de todos los eventos, del más antiguo al más reciente. */
    public String[] copiar() {
        mutex.acquireUninterruptibly();
        try {
            String[] copia = new String[eventos.tamano()];
            Nodo<String> actual = eventos.getCabeza();
            for (int i = 0; i < copia.length; i++) {
                copia[i] = actual.getDato();
                actual = actual.getSiguiente();
            }
            return copia;
        } finally {
            mutex.release();
        }
    }

    /** Devuelve la cantidad de eventos guardados. */
    public int tamano() {
        mutex.acquireUninterruptibly();
        try {
            return eventos.tamano();
        } finally {
            mutex.release();
        }
    }
}
