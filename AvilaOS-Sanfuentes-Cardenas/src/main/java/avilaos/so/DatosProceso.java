package avilaos.so;

import avilaos.enums.TipoProceso;

/**
 * Atributos con los que el usuario pide crear un proceso. Los campos que no aplican al tipo se ignoran.
 * computador = -1 significa asignación automática.
 */
public record DatosProceso(
        String nombre,
        TipoProceso tipo,
        int prioridad,
        int memoria,
        int deadline,
        int instrucciones,
        int cadaCiclosES,
        int duracionES,
        String buffer,
        int ciclosPorElemento,
        int elementos,
        int computador) {

    public static final int ASIGNACION_AUTOMATICA = -1;

    /** Datos de un proceso CPU bound. */
    public static DatosProceso cpuBound(String nombre, int prioridad, int memoria, int deadline,
            int instrucciones, int computador) {
        return new DatosProceso(nombre, TipoProceso.CPU_BOUND, prioridad, memoria, deadline,
                instrucciones, 0, 0, null, 0, 0, computador);
    }

    /** Datos de un proceso I/O bound. */
    public static DatosProceso ioBound(String nombre, int prioridad, int memoria, int deadline,
            int instrucciones, int cadaCiclosES, int duracionES, int computador) {
        return new DatosProceso(nombre, TipoProceso.IO_BOUND, prioridad, memoria, deadline,
                instrucciones, cadaCiclosES, duracionES, null, 0, 0, computador);
    }

    /** Datos de un productor. */
    public static DatosProceso productor(String nombre, int prioridad, int memoria, int deadline,
            String buffer, int ciclosPorElemento, int elementos, int computador) {
        return new DatosProceso(nombre, TipoProceso.PRODUCTOR, prioridad, memoria, deadline,
                0, 0, 0, buffer, ciclosPorElemento, elementos, computador);
    }

    /** Datos de un consumidor. */
    public static DatosProceso consumidor(String nombre, int prioridad, int memoria, int deadline,
            String buffer, int ciclosPorElemento, int elementos, int computador) {
        return new DatosProceso(nombre, TipoProceso.CONSUMIDOR, prioridad, memoria, deadline,
                0, 0, 0, buffer, ciclosPorElemento, elementos, computador);
    }
}
