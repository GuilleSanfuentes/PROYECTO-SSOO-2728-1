package avilaos.estructuras;

/** Nodo de un árbol general propio: un dato, su padre y la lista de hijos. */
public class NodoArbol<T> {

    private final T dato;
    private NodoArbol<T> padre;
    private final ListaEnlazada<NodoArbol<T>> hijos = new ListaEnlazada<>();

    /** Crea un nodo suelto (sin padre). */
    public NodoArbol(T dato) {
        this.dato = dato;
    }

    /** Cuelga un hijo de este nodo; si tenía otro padre, se lo quita. */
    public void agregarHijo(NodoArbol<T> hijo) {
        if (hijo.padre != null) {
            hijo.padre.hijos.eliminar(hijo);
        }
        hijo.padre = this;
        hijos.agregarFinal(hijo);
    }

    /** Descuelga un hijo directo. Devuelve false si no era hijo de este nodo. */
    public boolean quitarHijo(NodoArbol<T> hijo) {
        if (hijo == null || hijo.padre != this) {
            return false;
        }
        hijos.eliminar(hijo);
        hijo.padre = null;
        return true;
    }

    /** Cantidad de nodos de este subárbol, incluido él mismo. */
    public int tamanoSubarbol() {
        int total = 1;
        Nodo<NodoArbol<T>> nodo = hijos.getCabeza();
        while (nodo != null) {
            total += nodo.getDato().tamanoSubarbol();
            nodo = nodo.getSiguiente();
        }
        return total;
    }

    /** Profundidad del nodo: 0 para la raíz. */
    public int profundidad() {
        int nivel = 0;
        NodoArbol<T> actual = padre;
        while (actual != null) {
            nivel++;
            actual = actual.padre;
        }
        return nivel;
    }

    public T getDato() {
        return dato;
    }

    public NodoArbol<T> getPadre() {
        return padre;
    }

    public ListaEnlazada<NodoArbol<T>> getHijos() {
        return hijos;
    }

    /** Indica si no tiene hijos. */
    public boolean esHoja() {
        return hijos.estaVacia();
    }
}
