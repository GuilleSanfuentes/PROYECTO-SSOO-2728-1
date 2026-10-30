package avilaos.sync;

import avilaos.enums.RolSemaforo;
import avilaos.estructuras.Cola;
import avilaos.log.Debug;
import avilaos.so.PCB;
import java.util.concurrent.Semaphore;

/**
 * Buffer acotado del productor-consumidor (Stallings): elementos, contadores y sus tres semáforos.
 * Lo tocan las CPUs de varios computadores, por eso todo acceso pasa por su semáforo de Java.
 */
public class Buffer {

    private static final String ORIGEN = "Buffer";

    private final String nombre;
    private final int capacidad;
    private final int anfitrion;
    private final SemaforoSO vacio;
    private final SemaforoSO lleno;
    private final SemaforoSO exclusion;
    private final Cola<Integer> elementos = new Cola<>();
    private final Semaphore mutex = new Semaphore(1);
    private int producidos;
    private int consumidos;

    /** Crea el buffer: vacío = capacidad, lleno = 0 y exclusión mutua = 1. */
    public Buffer(String nombre, int capacidad, int anfitrion) {
        this.nombre = nombre;
        this.capacidad = capacidad;
        this.anfitrion = anfitrion;
        this.vacio = new SemaforoSO(nombre, RolSemaforo.VACIO, capacidad);
        this.lleno = new SemaforoSO(nombre, RolSemaforo.LLENO, 0);
        this.exclusion = new SemaforoSO(nombre, RolSemaforo.MUTEX, 1);
    }

    /** Nombre con el que el buffer figura como dueño de su reserva en la RAM. */
    public static String duenoEnRam(String nombreBuffer) {
        return "Buffer " + nombreBuffer;
    }

    /** semWait sobre el semáforo del rol: true si el proceso sigue, false si quedó en la cola del semáforo. */
    public boolean esperar(RolSemaforo rol, PCB pcb) {
        mutex.acquireUninterruptibly();
        try {
            return semaforo(rol).esperar(pcb);
        } finally {
            mutex.release();
        }
    }

    /** semSignal sobre el semáforo del rol: devuelve el proceso que debe despertar, o null si no había ninguno. */
    public PCB senalar(RolSemaforo rol) {
        mutex.acquireUninterruptibly();
        try {
            return semaforo(rol).senalar();
        } finally {
            mutex.release();
        }
    }

    /** Añade un elemento nuevo. Devuelve su número, o -1 (ERROR) si el buffer estaba lleno. */
    public int insertar() {
        mutex.acquireUninterruptibly();
        try {
            if (elementos.tamano() >= capacidad) {
                Debug.error(ORIGEN, nombre + ": insertar con el buffer lleno (" + capacidad + "); los semáforos no lo impidieron");
                return -1;
            }
            producidos++;
            elementos.encolar(producidos);
            return producidos;
        } finally {
            mutex.release();
        }
    }

    /** Saca el elemento más antiguo. Devuelve su número, o -1 (ERROR) si el buffer estaba vacío. */
    public int extraer() {
        mutex.acquireUninterruptibly();
        try {
            if (elementos.estaVacia()) {
                Debug.error(ORIGEN, nombre + ": extraer con el buffer vacío; los semáforos no lo impidieron");
                return -1;
            }
            consumidos++;
            return elementos.desencolar();
        } finally {
            mutex.release();
        }
    }

    /** Saca al proceso de la cola de cualquiera de los tres semáforos. Devuelve true si estaba en alguna. */
    public boolean quitarBloqueado(PCB pcb) {
        mutex.acquireUninterruptibly();
        try {
            return vacio.quitar(pcb) || lleno.quitar(pcb) || exclusion.quitar(pcb);
        } finally {
            mutex.release();
        }
    }

    /** Comprueba 0 <= elementos <= capacidad, lleno <= elementos, elementos + vacío <= capacidad y producidos - consumidos = elementos. */
    public boolean verificarInvariante() {
        mutex.acquireUninterruptibly();
        try {
            int n = elementos.tamano();
            boolean correcto = n >= 0 && n <= capacidad
                    && lleno.getValor() <= n
                    && n + vacio.getValor() <= capacidad
                    && exclusion.getValor() <= 1
                    && producidos - consumidos == n;
            if (!correcto) {
                Debug.error(ORIGEN, nombre + ": invariante rota: elementos=" + n + ", capacidad=" + capacidad + ", vacío="
                        + vacio.getValor() + ", lleno=" + lleno.getValor() + ", mutex=" + exclusion.getValor()
                        + ", producidos=" + producidos + ", consumidos=" + consumidos);
            }
            return correcto;
        } finally {
            mutex.release();
        }
    }

    /** Foto del buffer tomada en exclusión mutua, para reportes y para la interfaz. */
    public EstadoBuffer estado() {
        mutex.acquireUninterruptibly();
        try {
            return new EstadoBuffer(nombre, capacidad, anfitrion, elementos.tamano(), producidos, consumidos,
                    vacio.getValor(), lleno.getValor(), exclusion.getValor(),
                    vacio.copiarBloqueados(), lleno.copiarBloqueados(), exclusion.copiarBloqueados());
        } finally {
            mutex.release();
        }
    }

    /** Semáforo que corresponde al rol (llamar con el mutex tomado). */
    private SemaforoSO semaforo(RolSemaforo rol) {
        return switch (rol) {
            case VACIO -> vacio;
            case LLENO -> lleno;
            case MUTEX -> exclusion;
        };
    }

    public String getNombre() {
        return nombre;
    }

    public int getCapacidad() {
        return capacidad;
    }

    /** Número del computador donde vive el buffer (el dueño de su RAM). */
    public int getAnfitrion() {
        return anfitrion;
    }
}
