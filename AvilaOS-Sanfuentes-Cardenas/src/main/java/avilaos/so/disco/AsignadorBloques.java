package avilaos.so.disco;

import avilaos.estructuras.ListaEnlazada;
import avilaos.hardware.Bloque;
import avilaos.hardware.DiscoSD;
import avilaos.log.Debug;
import java.util.concurrent.Semaphore;

/** Asignación encadenada de bloques del SD: archivos en su zona y procesos suspendidos en la de intercambio. */
public class AsignadorBloques {

    private final String nombre;
    private final DiscoSD disco;
    private final Semaphore mutex = new Semaphore(1);

    /** Crea el asignador del disco indicado; todas las escrituras del mapa deben pasar por él. */
    public AsignadorBloques(String nombre, DiscoSD disco) {
        this.nombre = nombre;
        this.disco = disco;
    }

    /** Asigna una cadena de bloques de archivo tomando los primeros libres desde el 0. Devuelve el primer bloque o -1. */
    public int asignar(String dueno, int cantidad) {
        return asignarEnZona(dueno, cantidad, false, Bloque.FIN);
    }

    /** Asigna una cadena que empieza en el bloque indicado (carga desde JSON); el resto, los libres siguientes. */
    public int asignarDesde(String dueno, int cantidad, int primerBloque) {
        if (!disco.esPosicionValida(primerBloque) || disco.esSwap(primerBloque)) {
            Debug.error(nombre, "el bloque inicial " + primerBloque + " de " + dueno + " no está en la zona de archivos (0.."
                    + (disco.getBloquesArchivos() - 1) + ")");
            return Bloque.FIN;
        }
        return asignarEnZona(dueno, cantidad, false, primerBloque);
    }

    /** Reserva en el área de intercambio una cadena para un proceso suspendido. Devuelve el primer bloque o -1. */
    public int asignarSwap(String dueno, int cantidad) {
        return asignarEnZona(dueno, cantidad, true, Bloque.FIN);
    }

    /** Libera la cadena completa que empieza en el bloque indicado. Devuelve cuántos bloques liberó. */
    public int liberarCadena(int primero) {
        mutex.acquireUninterruptibly();
        try {
            Bloque inicio = disco.esPosicionValida(primero) ? disco.leer(primero) : null;
            if (inicio == null || !inicio.isOcupado()) {
                Debug.error(nombre, "liberar: el bloque " + primero + " no es el inicio de una cadena ocupada");
                return 0;
            }
            if (tieneAnterior(primero)) {
                Debug.error(nombre, "liberar: el bloque " + primero + " está en medio de la cadena de " + inicio.dueno());
                return 0;
            }
            int liberados = 0;
            int actual = primero;
            while (actual != Bloque.FIN && liberados < disco.getTotalPosiciones()) {
                int siguiente = disco.leer(actual).siguiente();
                disco.liberar(actual);
                liberados++;
                actual = siguiente;
            }
            Debug.ok(nombre, "liberados " + liberados + " bloques de " + inicio.dueno() + " desde el " + primero);
            return liberados;
        } finally {
            mutex.release();
        }
    }

    /** Números de los bloques de la cadena en orden; vacía con ERROR si el inicio no está ocupado. */
    public ListaEnlazada<Integer> recorrer(int primero) {
        ListaEnlazada<Integer> cadena = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            Bloque actual = disco.esPosicionValida(primero) ? disco.leer(primero) : null;
            if (actual == null || !actual.isOcupado()) {
                Debug.error(nombre, "recorrer: el bloque " + primero + " no está ocupado");
                return cadena;
            }
            while (actual != null && cadena.tamano() < disco.getTotalPosiciones()) {
                cadena.agregarFinal(actual.numero());
                actual = actual.esUltimo() ? null : disco.leer(actual.siguiente());
            }
            return cadena;
        } finally {
            mutex.release();
        }
    }

    /** Busca libres en una zona, los enlaza y los escribe; si no alcanzan no toca nada. */
    private int asignarEnZona(String dueno, int cantidad, boolean swap, int primerBloque) {
        String zona = swap ? "intercambio" : "archivos";
        if (dueno == null || dueno.isBlank()) {
            Debug.error(nombre, "asignación sin dueño en la zona de " + zona);
            return Bloque.FIN;
        }
        if (cantidad <= 0) {
            Debug.error(nombre, "cantidad inválida " + cantidad + " para " + dueno);
            return Bloque.FIN;
        }
        mutex.acquireUninterruptibly();
        try {
            int[] elegidos = elegirLibres(cantidad, swap, primerBloque);
            if (elegidos == null) {
                Debug.info(nombre, "sin espacio en la zona de " + zona + " para " + dueno + ": pide " + cantidad
                        + ", libres " + disco.contarLibres(swap));
                return Bloque.FIN;
            }
            for (int i = 0; i < elegidos.length; i++) {
                int siguiente = i + 1 < elegidos.length ? elegidos[i + 1] : Bloque.FIN;
                disco.escribir(elegidos[i], dueno, siguiente);
            }
            Debug.ok(nombre, dueno + " recibe " + cantidad + " bloques en la zona de " + zona + ", desde el " + elegidos[0]);
            return elegidos[0];
        } finally {
            mutex.release();
        }
    }

    /** Elige los bloques libres en orden; con primer bloque fijo sigue desde él y da la vuelta al inicio de la zona. */
    private int[] elegirLibres(int cantidad, boolean swap, int primerBloque) {
        int desde = swap ? disco.getPrimerBloqueSwap() : 0;
        int hasta = swap ? disco.getTotalPosiciones() : disco.getBloquesArchivos();
        if (cantidad > hasta - desde) {
            return null;
        }
        int[] elegidos = new int[cantidad];
        int n = 0;
        if (primerBloque != Bloque.FIN) {
            if (disco.leer(primerBloque).isOcupado()) {
                Debug.info(nombre, "el bloque inicial " + primerBloque + " ya está ocupado por " + disco.leer(primerBloque).dueno());
                return null;
            }
            elegidos[n++] = primerBloque;
            for (int i = primerBloque + 1; i < hasta && n < cantidad; i++) {
                if (!disco.leer(i).isOcupado()) {
                    elegidos[n++] = i;
                }
            }
            hasta = primerBloque;
        }
        for (int i = desde; i < hasta && n < cantidad; i++) {
            if (!disco.leer(i).isOcupado()) {
                elegidos[n++] = i;
            }
        }
        return n == cantidad ? elegidos : null;
    }

    /** Indica si algún bloque ocupado apunta al indicado (es decir, no es inicio de cadena). */
    private boolean tieneAnterior(int numero) {
        for (Bloque b : disco.copiarMapa()) {
            if (b.isOcupado() && b.siguiente() == numero) {
                return true;
            }
        }
        return false;
    }

    public DiscoSD getDisco() {
        return disco;
    }
}
