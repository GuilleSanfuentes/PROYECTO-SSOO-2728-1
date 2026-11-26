package avilaos.so;

import avilaos.estructuras.Comparador;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;

/** Decide a quién suspender y a quién reactivar; no cambia nada, el Kernel ejecuta lo elegido. */
public final class PlanificadorMedianoPlazo {

    // Límite conocido: solo suspende para admitir Nuevos; si un Listo/Suspendido no cabe y la RAM la ocupan quienes esperan su semáforo, solo los saca el deadline.

    /** Primero la víctima: peor prioridad; luego el que lleva más tiempo bloqueado; luego el menor ID. */
    public static final Comparador<PCB> ORDEN_VICTIMAS = (a, b) -> {
        if (a.getPrioridadBase() != b.getPrioridadBase()) {
            return Integer.compare(b.getPrioridadBase(), a.getPrioridadBase());
        }
        int porBloqueo = Long.compare(a.getCicloInicioBloqueo(), b.getCicloInicioBloqueo());
        return porBloqueo != 0 ? porBloqueo : Integer.compare(a.getId(), b.getId());
    };

    /** Primero en volver: mejor prioridad; luego el que lleva más tiempo en Listo/Suspendido; luego el menor ID. */
    public static final Comparador<PCB> ORDEN_REACTIVACION = (a, b) -> {
        if (a.getPrioridadBase() != b.getPrioridadBase()) {
            return Integer.compare(a.getPrioridadBase(), b.getPrioridadBase());
        }
        int porEspera = Long.compare(a.getCicloListoSuspendido(), b.getCicloListoSuspendido());
        return porEspera != 0 ? porEspera : Integer.compare(a.getId(), b.getId());
    };

    private PlanificadorMedianoPlazo() {
    }

    /** Víctimas, una a una, hasta reunir la RAM que falta sin pasarse del swap; null si no se alcanza. */
    public static ListaEnlazada<PCB> elegirVictimas(ListaEnlazada<PCB> candidatas, int ramFaltante, int swapLibre) {
        ListaEnlazada<PCB> elegidas = new ListaEnlazada<>();
        int reunida = 0;
        int swap = swapLibre;
        Nodo<PCB> nodo = ordenar(candidatas, ORDEN_VICTIMAS).getCabeza();
        while (nodo != null && reunida < ramFaltante) {
            PCB p = nodo.getDato();
            if (p.getMemoria() <= swap) {
                elegidas.agregarFinal(p);
                swap -= p.getMemoria();
                reunida += p.getMemoria();
            }
            nodo = nodo.getSiguiente();
        }
        return reunida >= ramFaltante ? elegidas : null;
    }

    /** El de mejor prioridad que quepa en la RAM libre, o null si ninguno cabe. */
    public static PCB elegirReactivacion(ListaEnlazada<PCB> suspendidos, int ramLibre) {
        Nodo<PCB> nodo = ordenar(suspendidos, ORDEN_REACTIVACION).getCabeza();
        while (nodo != null) {
            if (nodo.getDato().getMemoria() <= ramLibre) {
                return nodo.getDato();
            }
            nodo = nodo.getSiguiente();
        }
        return null;
    }

    /** Copia ordenada de la lista, sin tocar la original. */
    static ListaEnlazada<PCB> ordenar(ListaEnlazada<PCB> procesos, Comparador<PCB> orden) {
        ListaEnlazada<PCB> copia = new ListaEnlazada<>();
        Nodo<PCB> nodo = procesos.getCabeza();
        while (nodo != null) {
            copia.agregarFinal(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
        copia.ordenar(orden);
        return copia;
    }
}
