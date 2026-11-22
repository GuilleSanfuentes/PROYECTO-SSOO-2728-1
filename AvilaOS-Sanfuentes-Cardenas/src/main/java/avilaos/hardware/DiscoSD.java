package avilaos.hardware;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.EstadoBloque;
import avilaos.log.Debug;
import java.util.concurrent.Semaphore;

/** Disco simulado: bloques de archivos, partición de intercambio al final y un cabezal que avanza con el reloj. */
public class DiscoSD implements ComponenteReloj {

    public static final int BLOQUES_POR_DEFECTO = 200;
    public static final int SWAP_POR_DEFECTO = 100;
    public static final int MAX_BLOQUES = 10000;
    public static final int MAX_VELOCIDAD = 1000;
    public static final int SIN_OBJETIVO = -1;

    private final String nombre;
    private final int bloquesArchivos;
    private final int bloquesSwap;
    private final Bloque[] bloques;
    private final Semaphore mutex = new Semaphore(1);
    private int cabezal;
    private DireccionCabezal direccion = DireccionCabezal.SUBIENDO;
    private int velocidad;
    private int objetivo = SIN_OBJETIVO;
    private long desplazamiento;
    private long ciclosMoviendo;
    private long ciclosTransfiriendo;
    private long ciclosOciosos;
    private long saltos;
    private int accionesEnCiclo;
    private String ultimaAccion = "ninguna";
    private volatile ControladorDisco controlador;

    /** Crea el disco; los valores fuera de rango se reemplazan por los de por defecto con ERROR. */
    public DiscoSD(String nombre, int bloquesArchivos, int bloquesSwap, int cabezalInicial, int velocidad) {
        this.nombre = nombre;
        if (bloquesArchivos < 1 || bloquesArchivos > MAX_BLOQUES) {
            Debug.error(nombre, "bloques de archivos inválidos " + bloquesArchivos + ", se usan " + BLOQUES_POR_DEFECTO);
            bloquesArchivos = BLOQUES_POR_DEFECTO;
        }
        if (bloquesSwap < 0 || bloquesSwap > MAX_BLOQUES) {
            Debug.error(nombre, "tamaño de swap inválido " + bloquesSwap + ", se usan " + SWAP_POR_DEFECTO);
            bloquesSwap = SWAP_POR_DEFECTO;
        }
        this.bloquesArchivos = bloquesArchivos;
        this.bloquesSwap = bloquesSwap;
        this.bloques = new Bloque[bloquesArchivos + bloquesSwap];
        for (int i = 0; i < bloques.length; i++) {
            bloques[i] = Bloque.libre(i, i >= bloquesArchivos);
        }
        if (cabezalInicial < 0 || cabezalInicial >= bloques.length) {
            Debug.error(nombre, "posición inicial del cabezal inválida " + cabezalInicial + ", se usa 0");
            cabezalInicial = 0;
        }
        if (velocidad < 1 || velocidad > MAX_VELOCIDAD) {
            Debug.error(nombre, "velocidad del cabezal inválida " + velocidad + ", se usa 1");
            velocidad = 1;
        }
        this.cabezal = cabezalInicial;
        this.velocidad = velocidad;
        Debug.ok(nombre, "disco de " + bloquesArchivos + " bloques + " + bloquesSwap + " de swap (posiciones "
                + bloquesArchivos + ".." + (bloques.length - 1) + "), cabezal en " + cabezal);
    }

    /** Copia del bloque en la posición indicada, o null con ERROR si no existe. */
    public Bloque leer(int numero) {
        if (!esPosicionValida(numero)) {
            Debug.error(nombre, "leer: el bloque " + numero + " no existe (0.." + (bloques.length - 1) + ")");
            return null;
        }
        mutex.acquireUninterruptibly();
        try {
            return bloques[numero];
        } finally {
            mutex.release();
        }
    }

    /** Ocupa un bloque con su dueño y el número del siguiente; valida zona y enlace. Devuelve false si no es válido. */
    public boolean escribir(int numero, String dueno, int siguiente) {
        if (!esPosicionValida(numero)) {
            Debug.error(nombre, "escribir: el bloque " + numero + " no existe");
            return false;
        }
        if (dueno == null || dueno.isBlank()) {
            Debug.error(nombre, "escribir: el bloque " + numero + " necesita un dueño");
            return false;
        }
        if (siguiente != Bloque.FIN && (!esPosicionValida(siguiente) || esSwap(siguiente) != esSwap(numero)
                || siguiente == numero)) {
            Debug.error(nombre, "escribir: enlace inválido " + numero + " -> " + siguiente);
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            EstadoBloque estado = esSwap(numero) ? EstadoBloque.SWAP_OCUPADO : EstadoBloque.ARCHIVO;
            bloques[numero] = new Bloque(numero, estado, dueno, siguiente);
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Deja libre un bloque ocupado. Devuelve false con ERROR si no existe o ya estaba libre. */
    public boolean liberar(int numero) {
        if (!esPosicionValida(numero)) {
            Debug.error(nombre, "liberar: el bloque " + numero + " no existe");
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            if (!bloques[numero].isOcupado()) {
                Debug.error(nombre, "liberar: el bloque " + numero + " ya estaba libre");
                return false;
            }
            bloques[numero] = Bloque.libre(numero, esSwap(numero));
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Bloques libres en la zona de archivos o en la de intercambio. */
    public int contarLibres(boolean swap) {
        mutex.acquireUninterruptibly();
        try {
            int libres = 0;
            int desde = swap ? bloquesArchivos : 0;
            int hasta = swap ? bloques.length : bloquesArchivos;
            for (int i = desde; i < hasta; i++) {
                if (!bloques[i].isOcupado()) {
                    libres++;
                }
            }
            return libres;
        } finally {
            mutex.release();
        }
    }

    /** Copia del mapa completo de bloques (para la interfaz y las pruebas). */
    public Bloque[] copiarMapa() {
        mutex.acquireUninterruptibly();
        try {
            Bloque[] copia = new Bloque[bloques.length];
            System.arraycopy(bloques, 0, copia, 0, bloques.length);
            return copia;
        } finally {
            mutex.release();
        }
    }

    /** Comprueba que cada cadena es lineal, sin cruces ni ciclos, de un solo dueño y dentro de su zona. */
    public boolean verificarIntegridad() {
        Bloque[] mapa = copiarMapa();
        int[] entradas = new int[mapa.length];
        int ocupados = 0;
        for (Bloque b : mapa) {
            if (b.isOcupado()) {
                ocupados++;
                if (b.dueno() == null || b.dueno().isBlank()) {
                    return integridadRota("el bloque " + b.numero() + " está ocupado sin dueño");
                }
                if (b.estado().isSwap() != esSwap(b.numero())) {
                    return integridadRota("el bloque " + b.numero() + " tiene un estado de otra zona");
                }
                if (!b.esUltimo()) {
                    Bloque sig = esPosicionValida(b.siguiente()) ? mapa[b.siguiente()] : null;
                    if (sig == null || !sig.isOcupado() || !b.dueno().equals(sig.dueno())
                            || sig.estado().isSwap() != b.estado().isSwap()) {
                        return integridadRota("el bloque " + b.numero() + " apunta a " + b.siguiente() + ", que no sigue su cadena");
                    }
                    entradas[b.siguiente()]++;
                }
            } else if (!b.esUltimo() || b.dueno() != null) {
                return integridadRota("el bloque libre " + b.numero() + " conserva dueño o enlace");
            }
        }
        int recorridos = 0;
        for (Bloque b : mapa) {
            if (b.isOcupado() && entradas[b.numero()] > 1) {
                return integridadRota("dos bloques apuntan al bloque " + b.numero());
            }
            if (b.isOcupado() && entradas[b.numero()] == 0) {
                int actual = b.numero();
                while (actual != Bloque.FIN && recorridos <= ocupados) {
                    recorridos++;
                    actual = mapa[actual].siguiente();
                }
            }
        }
        if (recorridos != ocupados) {
            return integridadRota("hay bloques ocupados en un ciclo o sin cabeza de cadena (" + recorridos + " de " + ocupados + ")");
        }
        return true;
    }

    /** Fija la posición a la que debe ir el cabezal. Devuelve false con ERROR si no existe. */
    public boolean fijarObjetivo(int destino) {
        if (!esPosicionValida(destino)) {
            Debug.error(nombre, "el cabezal no puede ir a " + destino + " (0.." + (bloques.length - 1) + ")");
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            objetivo = destino;
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Indica si el cabezal ya está sobre su objetivo (o no tiene uno). */
    public boolean enObjetivo() {
        mutex.acquireUninterruptibly();
        try {
            return objetivo == SIN_OBJETIVO || objetivo == cabezal;
        } finally {
            mutex.release();
        }
    }

    /** Lleva el cabezal a una posición sin recorrer el disco (regreso de C-SCAN); no suma desplazamiento. */
    public boolean saltarA(int posicion) {
        if (!esPosicionValida(posicion)) {
            Debug.error(nombre, "salto del cabezal a una posición inexistente " + posicion);
            return false;
        }
        int desde;
        mutex.acquireUninterruptibly();
        try {
            desde = cabezal;
            cabezal = posicion;
            if (objetivo == posicion) {
                objetivo = SIN_OBJETIVO;
            }
            saltos++;
            registrarAccion("salta de " + desde + " a " + posicion);
        } finally {
            mutex.release();
        }
        Debug.info(nombre, "el cabezal salta de " + desde + " a " + posicion + " (no cuenta como desplazamiento)");
        return true;
    }

    /** Un ciclo de trabajo: el controlador ordena una sola acción; sin controlador, el cabezal sigue su objetivo. */
    @Override
    public void tick(long ciclo) {
        ControladorDisco actual = controlador;
        if (actual == null) {
            int destino = getObjetivo();
            if (destino != SIN_OBJETIVO && destino != getCabezal()) {
                moverHacia(destino);
            }
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            accionesEnCiclo = 0;
        } finally {
            mutex.release();
        }
        actual.ejecutarCicloDisco(this, ciclo);
        int acciones;
        mutex.acquireUninterruptibly();
        try {
            acciones = accionesEnCiclo;
        } finally {
            mutex.release();
        }
        if (acciones == 0) {
            Debug.error(nombre, "el controlador no indicó ninguna acción en el ciclo " + ciclo + ", se cuenta como ocioso");
            ocioso();
        } else if (acciones > 1) {
            Debug.error(nombre, acciones + " acciones en el ciclo " + ciclo + "; el disco hace solo una por ciclo");
        }
    }

    /** Un ciclo de movimiento hacia el destino: avanza como máximo la velocidad. Devuelve false si no hay a dónde ir. */
    public boolean moverHacia(int destino) {
        if (!esPosicionValida(destino)) {
            Debug.error(nombre, "el cabezal no puede ir a " + destino + " (0.." + (bloques.length - 1) + ")");
            return false;
        }
        int posicion;
        boolean llego;
        mutex.acquireUninterruptibly();
        try {
            if (destino == cabezal) {
                return false;
            }
            int movido = Math.min(velocidad, Math.abs(destino - cabezal));
            direccion = destino > cabezal ? DireccionCabezal.SUBIENDO : DireccionCabezal.BAJANDO;
            cabezal += movido * direccion.getPaso();
            desplazamiento += movido;
            ciclosMoviendo++;
            posicion = cabezal;
            llego = cabezal == destino;
            if (llego && objetivo == destino) {
                objetivo = SIN_OBJETIVO;
            }
            registrarAccion("mueve el cabezal a " + cabezal + " " + direccion.getSimbolo());
        } finally {
            mutex.release();
        }
        if (llego) {
            Debug.ok(nombre, "el cabezal llega al bloque " + posicion + " (desplazamiento total " + getDesplazamientoTotal() + ")");
        }
        return true;
    }

    /** Lee o escribe el bloque que está bajo el cabezal: cuesta un ciclo. Devuelve false si el cabezal no está ahí. */
    public boolean transferir(int numero, String operacion) {
        mutex.acquireUninterruptibly();
        try {
            if (numero != cabezal) {
                Debug.error(nombre, "no se puede " + operacion + " el bloque " + numero + ": el cabezal está en " + cabezal);
                return false;
            }
            ciclosTransfiriendo++;
            registrarAccion(operacion + " el bloque " + numero);
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Ciclo sin trabajo. */
    public void ocioso() {
        mutex.acquireUninterruptibly();
        try {
            ciclosOciosos++;
            registrarAccion("ocioso");
        } finally {
            mutex.release();
        }
    }

    /** Asocia el controlador (planificador de disco) que decide cada ciclo; null lo desconecta. */
    public void setControlador(ControladorDisco nuevo) {
        controlador = nuevo;
    }

    /** Cambia la velocidad del cabezal (posiciones por ciclo). Devuelve false con ERROR si no está en 1..1000. */
    public boolean setVelocidad(int nueva) {
        if (nueva < 1 || nueva > MAX_VELOCIDAD) {
            Debug.error(nombre, "velocidad del cabezal inválida " + nueva + " (1.." + MAX_VELOCIDAD + ")");
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            velocidad = nueva;
        } finally {
            mutex.release();
        }
        Debug.ok(nombre, "velocidad del cabezal: " + nueva + " posiciones por ciclo");
        return true;
    }

    /** Fija el sentido del cabezal (lo usan SCAN y sus variantes al iniciar). */
    public void setDireccion(DireccionCabezal nueva) {
        if (nueva == null) {
            Debug.error(nombre, "dirección del cabezal nula");
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            direccion = nueva;
        } finally {
            mutex.release();
        }
    }

    /** Indica si la posición existe en el disco. */
    public boolean esPosicionValida(int numero) {
        return numero >= 0 && numero < bloques.length;
    }

    /** Indica si la posición pertenece a la partición de intercambio. */
    public boolean esSwap(int numero) {
        return numero >= bloquesArchivos;
    }

    @Override
    public String getNombre() {
        return nombre;
    }

    public int getBloquesArchivos() {
        return bloquesArchivos;
    }

    public int getBloquesSwap() {
        return bloquesSwap;
    }

    /** Posiciones totales: archivos + intercambio. */
    public int getTotalPosiciones() {
        return bloques.length;
    }

    /** Primera posición de la partición de intercambio. */
    public int getPrimerBloqueSwap() {
        return bloquesArchivos;
    }

    /** Posición actual del cabezal. */
    public int getCabezal() {
        mutex.acquireUninterruptibly();
        try {
            return cabezal;
        } finally {
            mutex.release();
        }
    }

    /** Sentido actual del cabezal. */
    public DireccionCabezal getDireccion() {
        mutex.acquireUninterruptibly();
        try {
            return direccion;
        } finally {
            mutex.release();
        }
    }

    /** Posiciones por ciclo. */
    public int getVelocidad() {
        mutex.acquireUninterruptibly();
        try {
            return velocidad;
        } finally {
            mutex.release();
        }
    }

    /** Posición a la que va el cabezal, o SIN_OBJETIVO. */
    public int getObjetivo() {
        mutex.acquireUninterruptibly();
        try {
            return objetivo;
        } finally {
            mutex.release();
        }
    }

    /** Posiciones recorridas por el cabezal desde el inicio (sin contar saltos). */
    public long getDesplazamientoTotal() {
        mutex.acquireUninterruptibly();
        try {
            return desplazamiento;
        } finally {
            mutex.release();
        }
    }

    /** Ciclos en que el cabezal estuvo en movimiento. */
    public long getCiclosMoviendo() {
        mutex.acquireUninterruptibly();
        try {
            return ciclosMoviendo;
        } finally {
            mutex.release();
        }
    }

    /** Cantidad de saltos de regreso (C-SCAN). */
    public long getSaltos() {
        mutex.acquireUninterruptibly();
        try {
            return saltos;
        } finally {
            mutex.release();
        }
    }

    /** Ciclos en que se leyó o escribió un bloque. */
    public long getCiclosTransfiriendo() {
        mutex.acquireUninterruptibly();
        try {
            return ciclosTransfiriendo;
        } finally {
            mutex.release();
        }
    }

    /** Ciclos sin trabajo. */
    public long getCiclosOciosos() {
        mutex.acquireUninterruptibly();
        try {
            return ciclosOciosos;
        } finally {
            mutex.release();
        }
    }

    /** Lo que hizo el disco en su último ciclo (para la interfaz). */
    public String getUltimaAccion() {
        mutex.acquireUninterruptibly();
        try {
            return ultimaAccion;
        } finally {
            mutex.release();
        }
    }

    /** Cuenta la acción del ciclo (llamar con el mutex tomado). */
    private void registrarAccion(String descripcion) {
        accionesEnCiclo++;
        ultimaAccion = descripcion;
    }

    /** Reporta la integridad rota y devuelve false. */
    private boolean integridadRota(String motivo) {
        Debug.error(nombre, "integridad del disco rota: " + motivo);
        return false;
    }
}
