package avilaos.estructuras;

/** Criterio de orden entre dos elementos: negativo si a va antes, 0 si empatan, positivo si b va antes. */
public interface Comparador<T> {

    /** Compara dos elementos según el criterio de orden. */
    int comparar(T a, T b);
}
