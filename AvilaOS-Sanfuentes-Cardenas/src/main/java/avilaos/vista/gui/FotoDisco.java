package avilaos.vista.gui;

import avilaos.cluster.Computador;
import avilaos.enums.DireccionCabezal;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.Bloque;
import avilaos.hardware.DiscoSD;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.disco.PlanificadorDisco;
import avilaos.so.disco.SolicitudDisco;

/** Foto inmutable del disco: mapa de bloques con su ocupante, cabezal, política y la cola de E/S. */
public record FotoDisco(int bloquesArchivos, Bloque[] mapa, String[] ocupante, int[] idArchivo, int cabezal,
        DireccionCabezal direccion, int velocidad, String ultimaAccion, PoliticaDiscoTipo politica, long desplazamiento,
        int libresArchivos, int libresSwap, FilaSolicitud actual, FilaSolicitud[] cola) {

    /** ID de archivo para los bloques libres, del swap o reservados. */
    public static final int SIN_ARCHIVO = -1;

    /** Toma la foto del disco del computador (null si no tiene disco). */
    public static FotoDisco de(Computador computador, long ciclo) {
        DiscoSD disco = computador.getDisco();
        if (disco == null) {
            return null;
        }
        PlanificadorDisco planificador = computador.getPlanificadorDisco();
        Bloque[] mapa = disco.copiarMapa();
        ListaEnlazada<InfoEntrada> archivos = computador.getSistemaArchivos().listarArchivos();
        String[] ocupante = new String[mapa.length];
        int[] ids = new int[mapa.length];
        for (int i = 0; i < mapa.length; i++) {
            ids[i] = SIN_ARCHIVO;
            ocupante[i] = describir(mapa[i], archivos, ids, i);
        }
        SolicitudDisco enCurso = planificador.getActual();
        ListaEnlazada<SolicitudDisco> cola = planificador.copiarCola();
        FilaSolicitud[] filas = new FilaSolicitud[cola.tamano()];
        Nodo<SolicitudDisco> nodo = cola.getCabeza();
        for (int i = 0; i < filas.length && nodo != null; i++) {
            filas[i] = FilaSolicitud.de(nodo.getDato(), ciclo, false);
            nodo = nodo.getSiguiente();
        }
        return new FotoDisco(disco.getBloquesArchivos(), mapa, ocupante, ids, disco.getCabezal(), disco.getDireccion(),
                disco.getVelocidad(), disco.getUltimaAccion(), planificador.getPolitica(), disco.getDesplazamientoTotal(),
                disco.contarLibres(false), disco.contarLibres(true),
                enCurso == null ? null : FilaSolicitud.de(enCurso, ciclo, true), filas);
    }

    /** Quién ocupa el bloque, en palabras: la ruta del archivo, el proceso suspendido o "libre". */
    private static String describir(Bloque b, ListaEnlazada<InfoEntrada> archivos, int[] ids, int i) {
        if (!b.isOcupado()) {
            return b.estado().isSwap() ? "libre (swap)" : "libre";
        }
        if (b.estado().isSwap()) {
            return b.dueno() + " (suspendido)";
        }
        Nodo<InfoEntrada> nodo = archivos.getCabeza();
        while (nodo != null) {
            if (SistemaArchivos.marca(nodo.getDato().id()).equals(b.dueno())) {
                ids[i] = nodo.getDato().id();
                return nodo.getDato().ruta();
            }
            nodo = nodo.getSiguiente();
        }
        return "reservado (creación en curso)";
    }

    /** Total de posiciones del disco (archivos + swap). */
    public int total() {
        return mapa.length;
    }

    /** Indica si el bloque pertenece a la solicitud que el disco atiende ahora. */
    public boolean esDeLaActual(int numero) {
        if (actual == null) {
            return false;
        }
        for (int b : actual.cadena()) {
            if (b == numero) {
                return true;
            }
        }
        return false;
    }
}
