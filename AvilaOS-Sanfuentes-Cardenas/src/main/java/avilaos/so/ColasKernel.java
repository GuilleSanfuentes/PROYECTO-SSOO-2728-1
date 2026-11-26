package avilaos.so;

import avilaos.estructuras.ListaEnlazada;

/** Copia de las siete colas del Kernel tomada de una vez, bajo su semáforo; detalleCPU describe al proceso en CPU. */
public record ColasKernel<T>(T enCPU, String detalleCPU, ListaEnlazada<T> nuevos, ListaEnlazada<T> listos,
        ListaEnlazada<T> bloqueados, ListaEnlazada<T> listosSuspendidos, ListaEnlazada<T> bloqueadosSuspendidos,
        ListaEnlazada<T> terminados) {
}
