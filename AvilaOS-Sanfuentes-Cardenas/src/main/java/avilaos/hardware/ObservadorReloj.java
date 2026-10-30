package avilaos.hardware;

/** Recibe el aviso de cierre de cada ciclo, cuando todas las fases ya terminaron. */
public interface ObservadorReloj {

    /** Se llama una vez por ciclo, después de todas las fases. */
    void alCerrarCiclo(long ciclo);
}
