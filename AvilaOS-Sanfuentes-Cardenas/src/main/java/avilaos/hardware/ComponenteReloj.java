package avilaos.hardware;

/** Componente que avanza al ritmo del reloj global (CPU, DMA, etc.). */
public interface ComponenteReloj {

    /** Ejecuta exactamente un ciclo de reloj. */
    void tick(long ciclo);

    /** Nombre del componente para el log y el debugger. */
    String getNombre();
}
