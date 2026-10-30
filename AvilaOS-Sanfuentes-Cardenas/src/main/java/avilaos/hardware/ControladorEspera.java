package avilaos.hardware;

import avilaos.enums.TipoInterrupcion;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import java.util.concurrent.Semaphore;

/** Dispositivo que cuenta ciclos por operación y, al terminar cada una, interrumpe a la CPU. */
public abstract class ControladorEspera implements ComponenteReloj {

    private final String nombre;
    private final TipoInterrupcion tipo;
    private final LineaInterrupciones linea;
    private final Semaphore mutex = new Semaphore(1);
    private ListaEnlazada<OperacionEspera> operaciones = new ListaEnlazada<>();
    private long completadas;

    /** Crea el dispositivo que avisará con el tipo de interrupción indicado. */
    protected ControladorEspera(String nombre, TipoInterrupcion tipo, LineaInterrupciones linea) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.linea = linea;
    }

    /** Inicia una operación de la duración indicada para el proceso. Devuelve false si los datos no son válidos. */
    public boolean solicitar(int idProceso, String etiqueta, int ciclos, String detalle) {
        if (ciclos <= 0) {
            Debug.error(nombre, "solicitud de " + etiqueta + " con duración inválida " + ciclos);
            return false;
        }
        mutex.acquireUninterruptibly();
        try {
            operaciones.agregarFinal(new OperacionEspera(idProceso, etiqueta, detalle, ciclos));
        } finally {
            mutex.release();
        }
        Debug.ok(nombre, "inicia " + detalle + " de " + etiqueta + " (" + ciclos + " ciclos)");
        return true;
    }

    /** Resta un ciclo a cada operación; las que llegan a 0 generan su interrupción. */
    @Override
    public void tick(long ciclo) {
        ListaEnlazada<OperacionEspera> siguen = new ListaEnlazada<>();
        ListaEnlazada<OperacionEspera> terminadas = new ListaEnlazada<>();
        mutex.acquireUninterruptibly();
        try {
            Nodo<OperacionEspera> nodo = operaciones.getCabeza();
            while (nodo != null) {
                OperacionEspera op = nodo.getDato();
                op.restantes--;
                if (op.restantes <= 0) {
                    terminadas.agregarFinal(op);
                } else {
                    siguen.agregarFinal(op);
                }
                nodo = nodo.getSiguiente();
            }
            operaciones = siguen;
            completadas += terminadas.tamano();
        } finally {
            mutex.release();
        }
        Nodo<OperacionEspera> nodo = terminadas.getCabeza();
        while (nodo != null) {
            OperacionEspera op = nodo.getDato();
            Debug.ok(nombre, "termina " + op.detalle + " de " + op.etiqueta);
            linea.registrar(new Interrupcion(tipo, op.idProceso, op.etiqueta, nombre, ciclo));
            nodo = nodo.getSiguiente();
        }
    }

    /** Cancela las operaciones en curso del proceso (ej. terminado por deadline). Devuelve cuántas canceló. */
    public int cancelar(int idProceso) {
        int canceladas = 0;
        mutex.acquireUninterruptibly();
        try {
            ListaEnlazada<OperacionEspera> siguen = new ListaEnlazada<>();
            Nodo<OperacionEspera> nodo = operaciones.getCabeza();
            while (nodo != null) {
                if (nodo.getDato().idProceso == idProceso) {
                    canceladas++;
                } else {
                    siguen.agregarFinal(nodo.getDato());
                }
                nodo = nodo.getSiguiente();
            }
            operaciones = siguen;
        } finally {
            mutex.release();
        }
        if (canceladas > 0) {
            Debug.ok(nombre, "canceladas " + canceladas + " operaciones del proceso " + idProceso);
        }
        return canceladas;
    }

    @Override
    public String getNombre() {
        return nombre;
    }

    /** Operaciones aún en curso. */
    public int cantidadPendientes() {
        mutex.acquireUninterruptibly();
        try {
            return operaciones.tamano();
        } finally {
            mutex.release();
        }
    }

    /** Operaciones terminadas desde el inicio. */
    public long getCompletadas() {
        mutex.acquireUninterruptibly();
        try {
            return completadas;
        } finally {
            mutex.release();
        }
    }

    /** Lista del tipo [P2-H1 3c, P5-H1 1c]. */
    public String describirPendientes() {
        StringBuilder sb = new StringBuilder("[");
        mutex.acquireUninterruptibly();
        try {
            Nodo<OperacionEspera> nodo = operaciones.getCabeza();
            while (nodo != null) {
                sb.append(sb.length() > 1 ? ", " : "").append(nodo.getDato().etiqueta).append(' ')
                        .append(nodo.getDato().restantes).append('c');
                nodo = nodo.getSiguiente();
            }
        } finally {
            mutex.release();
        }
        return sb.append(']').toString();
    }
}
