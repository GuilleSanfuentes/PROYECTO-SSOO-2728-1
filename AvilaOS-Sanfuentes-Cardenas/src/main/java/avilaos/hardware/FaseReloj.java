package avilaos.hardware;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import java.util.concurrent.Semaphore;

/** Grupo de componentes que se ejecutan en paralelo dentro de un ciclo, con barrera al final. */
final class FaseReloj {

    private final String nombre;
    private final ListaEnlazada<HiloComponente> hilos = new ListaEnlazada<>();
    private final Semaphore fin = new Semaphore(0);

    /** Crea una fase vacía. */
    FaseReloj(String nombre) {
        this.nombre = nombre;
    }

    /** Agrega un componente con su propio hilo. */
    void agregar(ComponenteReloj componente) {
        hilos.agregarFinal(new HiloComponente(componente, fin));
    }

    /** Contador de tiempo por hilo. */
    void ejecutar(long ciclo) {
        Nodo<HiloComponente> nodo = hilos.getCabeza();
        while (nodo != null) {
            nodo.getDato().disparar(ciclo);
            nodo = nodo.getSiguiente();
        }
        fin.acquireUninterruptibly(hilos.tamano());
    }

    /** Termina los hilos de todos los componentes. */
    void apagar() {
        Nodo<HiloComponente> nodo = hilos.getCabeza();
        while (nodo != null) {
            nodo.getDato().apagar();
            nodo = nodo.getSiguiente();
        }
        hilos.vaciar();
    }

    String getNombre() {
        return nombre;
    }

    /** Lista del tipo "DMA-PC1, Red-PC1". */
    String describirComponentes() {
        StringBuilder sb = new StringBuilder();
        Nodo<HiloComponente> nodo = hilos.getCabeza();
        while (nodo != null) {
            sb.append(sb.length() > 0 ? ", " : "").append(nodo.getDato().getNombreComponente());
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }
}
