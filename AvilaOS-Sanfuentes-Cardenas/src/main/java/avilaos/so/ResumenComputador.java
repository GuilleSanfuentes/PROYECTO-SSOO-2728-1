package avilaos.so;

import avilaos.estructuras.ListaEnlazada;

/** Foto de un computador para las métricas: sus procesos y los ciclos de su CPU en el mismo instante. */
public record ResumenComputador(ListaEnlazada<ResumenProceso> procesos, long ciclosUsuario, long ciclosSO,
        long ciclosOciosos) {
}
