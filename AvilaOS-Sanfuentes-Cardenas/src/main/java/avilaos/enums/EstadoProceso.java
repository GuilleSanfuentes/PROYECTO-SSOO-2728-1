package avilaos.enums;

/** Estados del ciclo de vida de un proceso. */
public enum EstadoProceso {
    NUEVO("Nuevo"),
    LISTO("Listo"),
    EJECUCION("Ejecución"),
    BLOQUEADO("Bloqueado"),
    TERMINADO("Terminado");

    private final String nombre;

    EstadoProceso(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }
}
