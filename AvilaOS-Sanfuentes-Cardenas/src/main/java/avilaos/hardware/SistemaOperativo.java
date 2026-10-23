package avilaos.hardware;

import avilaos.estructuras.ListaEnlazada;

/**
 * Código del sistema operativo que la CPU ejecuta en cada ciclo. Lo implementa el kernel.
 * En cada llamada debe indicar a la CPU exactamente una acción: ejecutarInstruccion, ejecutarCodigoSO o quedarOciosa.
 */
public interface SistemaOperativo {

    /** Atiende las interrupciones recibidas y decide qué hace la CPU en este ciclo. */
    void ejecutarCiclo(CPU cpu, ListaEnlazada<Interrupcion> interrupciones, long ciclo);
}
