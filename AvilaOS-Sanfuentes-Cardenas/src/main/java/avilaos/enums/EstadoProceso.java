package avilaos.enums;

/** Estados del ciclo de vida de un proceso. */
public enum EstadoProceso {
    NUEVO("Nuevo"),
    LISTO("Listo"),
    EJECUCION("Ejecución"),
    BLOQUEADO("Bloqueado"),
    TERMINADO("Terminado"),
    LISTO_SUSPENDIDO("Listo/Suspendido"),
    BLOQUEADO_SUSPENDIDO("Bloqueado/Suspendido");

    private final String nombre;

    EstadoProceso(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Indica si el proceso está fuera de la RAM, en el área de intercambio. */
    public boolean esSuspendido() {
        return this == LISTO_SUSPENDIDO || this == BLOQUEADO_SUSPENDIDO;
    }
}
