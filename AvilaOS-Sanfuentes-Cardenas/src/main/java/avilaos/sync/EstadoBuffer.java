package avilaos.sync;

import avilaos.estructuras.ListaEnlazada;
import avilaos.so.PCB;

/** Foto de un buffer en un instante: elementos, contadores, valor de cada semáforo y procesos bloqueados en ellos. */
public record EstadoBuffer(
        String nombre,
        int capacidad,
        int anfitrion,
        int elementos,
        int producidos,
        int consumidos,
        int valorVacio,
        int valorLleno,
        int valorMutex,
        ListaEnlazada<PCB> bloqueadosVacio,
        ListaEnlazada<PCB> bloqueadosLleno,
        ListaEnlazada<PCB> bloqueadosMutex) {
}
