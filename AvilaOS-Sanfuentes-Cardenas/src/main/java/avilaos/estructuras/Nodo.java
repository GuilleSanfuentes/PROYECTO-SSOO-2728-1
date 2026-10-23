package avilaos.estructuras;

/** Nodo de una lista simplemente enlazada. */
public class Nodo<T> {

    private T dato;
    private Nodo<T> siguiente;

    /** Crea un nodo con el dato indicado y sin siguiente. */
    public Nodo(T dato) {
        this.dato = dato;
        this.siguiente = null;
    }

    /** Devuelve el dato almacenado. */
    public T getDato() {
        return dato;
    }

    /** Reemplaza el dato almacenado. */
    public void setDato(T dato) {
        this.dato = dato;
    }

    /** Devuelve el nodo siguiente o null si es el último. */
    public Nodo<T> getSiguiente() {
        return siguiente;
    }

    /** Enlaza este nodo con el siguiente. */
    public void setSiguiente(Nodo<T> siguiente) {
        this.siguiente = siguiente;
    }
}
