package avilaos.estructuras;

import avilaos.log.Debug;

/** Cola FIFO sobre ListaEnlazada, con reordenamiento según un criterio. No es segura entre hilos. */
public class Cola<T> {

    private static final String ORIGEN = "Cola";

    private final ListaEnlazada<T> elementos;

    /** Crea una cola vacía. */
    public Cola() {
        elementos = new ListaEnlazada<>();
    }

    /** Agrega un elemento al final de la cola. */
    public void encolar(T dato) {
        elementos.agregarFinal(dato);
    }

    /** Saca el elemento del frente. Vacía: devuelve null y marca ERROR, el Kernel debe revisar antes con estaVacia. */
    public T desencolar() {
        if (elementos.estaVacia()) {
            Debug.error(ORIGEN, "desencolar: la cola está vacía");
            return null;
        }
        return elementos.eliminarEn(0);
    }

    /** Devuelve el elemento del frente sin sacarlo, o null si está vacía. */
    public T verFrente() {
        return elementos.primero();
    }

    /** Saca un elemento concreto de cualquier posición. Devuelve true si estaba. */
    public boolean eliminar(T dato) {
        return elementos.eliminar(dato);
    }

    /** Devuelve el elemento en la posición indicada sin sacarlo. */
    public T obtener(int indice) {
        return elementos.obtener(indice);
    }

    /** Indica si el objeto (por referencia) está en la cola. */
    public boolean contiene(T dato) {
        return elementos.contiene(dato);
    }

    /** Inserta respetando el orden del comparador; en empate queda detrás de los iguales. */
    public void insertarOrdenado(T dato, Comparador<T> comparador) {
        elementos.insertarOrdenado(dato, comparador);
    }

    /** Reordena la cola según el comparador; en empate conserva el orden de llegada. */
    public void ordenar(Comparador<T> comparador) {
        elementos.ordenar(comparador);
    }

    /** Devuelve la cantidad de elementos. */
    public int tamano() {
        return elementos.tamano();
    }

    /** Indica si la cola no tiene elementos. */
    public boolean estaVacia() {
        return elementos.estaVacia();
    }

    /** Elimina todos los elementos. */
    public void vaciar() {
        elementos.vaciar();
    }

    /** Devuelve el primer nodo para recorrer la cola desde el frente. */
    public Nodo<T> getCabeza() {
        return elementos.getCabeza();
    }

    /** Representación del tipo [frente, ..., final]. */
    @Override
    public String toString() {
        return elementos.toString();
    }
}
