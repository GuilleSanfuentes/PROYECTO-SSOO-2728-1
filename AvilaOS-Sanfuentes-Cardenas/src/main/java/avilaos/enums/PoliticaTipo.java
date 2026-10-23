package avilaos.enums;

/** Políticas de planificación disponibles. */
public enum PoliticaTipo {
    FCFS("FCFS", false),
    EDF("EDF", true),
    ROUND_ROBIN("Round Robin", true),
    PRIORIDADES("Prioridades apropiativas", true);

    private final String nombre;
    private final boolean apropiativa;

    PoliticaTipo(String nombre, boolean apropiativa) {
        this.nombre = nombre;
        this.apropiativa = apropiativa;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Indica si la política puede expulsar al proceso en ejecución. */
    public boolean esApropiativa() {
        return apropiativa;
    }
}
