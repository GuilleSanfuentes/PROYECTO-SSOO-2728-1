package avilaos.sync;

import avilaos.enums.RolSemaforo;
import avilaos.enums.TipoSemaforo;
import avilaos.estructuras.Cola;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import avilaos.so.PCB;

/** Semáforo de ÁvilaOS (conteo o binario) con cola FIFO de procesos bloqueados. Lo sincroniza su buffer. */
public class SemaforoSO {

    private static final String ORIGEN = "SemaforoSO";

    private final String nombre;
    private final RolSemaforo rol;
    private final Cola<PCB> bloqueados = new Cola<>();
    private int valor;

    /** Crea el semáforo del buffer con su rol y su valor inicial. */
    public SemaforoSO(String buffer, RolSemaforo rol, int valorInicial) {
        this.nombre = rol.getNombre() + " de " + buffer;
        this.rol = rol;
        int maximo = rol.getTipo() == TipoSemaforo.BINARIO ? 1 : Integer.MAX_VALUE;
        if (valorInicial < 0 || valorInicial > maximo) {
            Debug.error(ORIGEN, nombre + ": valor inicial " + valorInicial + " inválido, se ajusta al rango 0.." + maximo);
            valorInicial = Math.max(0, Math.min(valorInicial, maximo));
        }
        this.valor = valorInicial;
    }

    public boolean esperar(PCB pcb) {
        if (valor > 0) {
            valor--;
            return true;
        }
        bloqueados.encolar(pcb);
        return false;
    }

    public PCB senalar() {
        if (!bloqueados.estaVacia()) {
            return bloqueados.desencolar();
        }
        if (rol.getTipo() == TipoSemaforo.BINARIO && valor >= 1) {
            Debug.error(ORIGEN, nombre + ": semSignal sobre un semáforo binario que ya vale 1");
            return null;
        }
        valor++;
        return null;
    }

    /** Saca al proceso de la cola de bloqueados (ej. por deadline). Devuelve true si estaba. */
    public boolean quitar(PCB pcb) {
        return bloqueados.eliminar(pcb);
    }

    /** Copia de los procesos bloqueados, en orden de llegada. */
    public ListaEnlazada<PCB> copiarBloqueados() {
        ListaEnlazada<PCB> copia = new ListaEnlazada<>();
        Nodo<PCB> nodo = bloqueados.getCabeza();
        while (nodo != null) {
            copia.agregarFinal(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
        return copia;
    }

    public int cantidadBloqueados() {
        return bloqueados.tamano();
    }

    public int getValor() {
        return valor;
    }

    public RolSemaforo getRol() {
        return rol;
    }

    public String getNombre() {
        return nombre;
    }
}
