package avilaos.estructuras;

import avilaos.log.Debug;

/** Lista simplemente enlazada genérica. No es segura entre hilos: la sincroniza su dueño. */
public class ListaEnlazada<T> {

    private static final String ORIGEN = "ListaEnlazada";

    private Nodo<T> cabeza;
    private Nodo<T> ultimo;
    private int tamano;

    /** Crea una lista vacía. */
    public ListaEnlazada() {
        cabeza = null;
        ultimo = null;
        tamano = 0;
    }

    /** Agrega un elemento al final. */
    public void agregarFinal(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        if (cabeza == null) {
            cabeza = nuevo;
        } else {
            ultimo.setSiguiente(nuevo);
        }
        ultimo = nuevo;
        tamano++;
    }

    /** Agrega un elemento al inicio. */
    public void agregarInicio(T dato) {
        Nodo<T> nuevo = new Nodo<>(dato);
        nuevo.setSiguiente(cabeza);
        cabeza = nuevo;
        if (ultimo == null) {
            ultimo = nuevo;
        }
        tamano++;
    }

    /** Inserta un elemento en la posición indicada (0..tamano). */
    public boolean insertarEn(int indice, T dato) {
        if (indice < 0 || indice > tamano) {
            Debug.error(ORIGEN, "insertarEn: índice " + indice + " fuera de rango [0.." + tamano + "]");
            return false;
        }
        if (indice == 0) {
            agregarInicio(dato);
            return true;
        }
        if (indice == tamano) {
            agregarFinal(dato);
            return true;
        }
        Nodo<T> previo = nodoEn(indice - 1);
        Nodo<T> nuevo = new Nodo<>(dato);
        nuevo.setSiguiente(previo.getSiguiente());
        previo.setSiguiente(nuevo);
        tamano++;
        return true;
    }

    /** Devuelve el elemento en la posición indicada o null si no existe. */
    public T obtener(int indice) {
        if (indice < 0 || indice >= tamano) {
            Debug.error(ORIGEN, "obtener: índice " + indice + " fuera de rango (" + rangoValido() + ")");
            return null;
        }
        return nodoEn(indice).getDato();
    }

    /** Elimina y devuelve el elemento en la posición indicada, o null si no existe. */
    public T eliminarEn(int indice) {
        if (indice < 0 || indice >= tamano) {
            Debug.error(ORIGEN, "eliminarEn: índice " + indice + " fuera de rango (" + rangoValido() + ")");
            return null;
        }
        Nodo<T> eliminado;
        if (indice == 0) {
            eliminado = cabeza;
            cabeza = cabeza.getSiguiente();
            if (cabeza == null) {
                ultimo = null;
            }
        } else {
            Nodo<T> previo = nodoEn(indice - 1);
            eliminado = previo.getSiguiente();
            previo.setSiguiente(eliminado.getSiguiente());
            if (eliminado == ultimo) {
                ultimo = previo;
            }
        }
        tamano--;
        return eliminado.getDato();
    }

    /** Elimina la primera aparición del objeto (por referencia). Devuelve true si lo encontró. */
    public boolean eliminar(T dato) {
        int indice = indiceDe(dato);
        if (indice < 0) {
            return false;
        }
        eliminarEn(indice);
        return true;
    }

    /** Devuelve la posición del objeto (por referencia) o -1 si no está. */
    public int indiceDe(T dato) {
        Nodo<T> actual = cabeza;
        int indice = 0;
        while (actual != null) {
            if (actual.getDato() == dato) {
                return indice;
            }
            actual = actual.getSiguiente();
            indice++;
        }
        return -1;
    }

    /** Indica si el objeto (por referencia) está en la lista. */
    public boolean contiene(T dato) {
        return indiceDe(dato) >= 0;
    }

    /** Devuelve el primer elemento o null si la lista está vacía. */
    public T primero() {
        return cabeza == null ? null : cabeza.getDato();
    }

    /** Devuelve el último elemento o null si la lista está vacía. */
    public T ultimo() {
        return ultimo == null ? null : ultimo.getDato();
    }

    /** Devuelve la cantidad de elementos. */
    public int tamano() {
        return tamano;
    }

    /** Indica si la lista no tiene elementos. */
    public boolean estaVacia() {
        return tamano == 0;
    }

    /** Elimina todos los elementos. */
    public void vaciar() {
        cabeza = null;
        ultimo = null;
        tamano = 0;
    }

    /** Devuelve el primer nodo para recorrer la lista sin coste por índice. */
    public Nodo<T> getCabeza() {
        return cabeza;
    }

    /** Ordena los nodos en base a un criterio. */
    public void insertarOrdenado(T dato, Comparador<T> comparador) {
        if (comparador == null) {
            Debug.error(ORIGEN, "insertarOrdenado: comparador nulo");
            return;
        }
        insertarNodoOrdenado(new Nodo<>(dato), comparador);
    }

    /** Reorganiza los nodos insertados. */
    public void ordenar(Comparador<T> comparador) {
        if (comparador == null) {
            Debug.error(ORIGEN, "ordenar: comparador nulo");
            return;
        }
        Nodo<T> actual = cabeza;
        cabeza = null;
        ultimo = null;
        tamano = 0;
        while (actual != null) {
            Nodo<T> siguiente = actual.getSiguiente();
            actual.setSiguiente(null);
            insertarNodoOrdenado(actual, comparador);
            actual = siguiente;
        }
    }

    /** Inserta nodos nuevos. */
    private void insertarNodoOrdenado(Nodo<T> nuevo, Comparador<T> comparador) {
        if (cabeza == null) {
            cabeza = nuevo;
            ultimo = nuevo;
        } else if (comparador.comparar(nuevo.getDato(), ultimo.getDato()) >= 0) {
            ultimo.setSiguiente(nuevo);
            ultimo = nuevo;
        } else if (comparador.comparar(nuevo.getDato(), cabeza.getDato()) < 0) {
            nuevo.setSiguiente(cabeza);
            cabeza = nuevo;
        } else {
            Nodo<T> previo = cabeza;
            while (comparador.comparar(nuevo.getDato(), previo.getSiguiente().getDato()) >= 0) {
                previo = previo.getSiguiente();
            }
            nuevo.setSiguiente(previo.getSiguiente());
            previo.setSiguiente(nuevo);
        }
        tamano++;
    }

    /** Texto con el rango de índices válidos para los mensajes de error. */
    private String rangoValido() {
        return tamano == 0 ? "la lista está vacía" : "válido: 0.." + (tamano - 1);
    }

    /** Devuelve el nodo en la posición indicada (sin validar). */
    private Nodo<T> nodoEn(int indice) {
        Nodo<T> actual = cabeza;
        for (int i = 0; i < indice; i++) {
            actual = actual.getSiguiente();
        }
        return actual;
    }

    /** Representación del tipo [a, b, c]. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        Nodo<T> actual = cabeza;
        while (actual != null) {
            sb.append(actual.getDato());
            if (actual.getSiguiente() != null) {
                sb.append(", ");
            }
            actual = actual.getSiguiente();
        }
        return sb.append("]").toString();
    }
}
