package avilaos.enums;

/** Operación que una solicitud pide al disco: las CRUD sobre archivos y el intercambio de procesos. */
public enum TipoOperacionES {
    CREAR("Crear", true),
    LEER("Leer", false),
    ACTUALIZAR("Actualizar", true),
    ELIMINAR("Eliminar", true),
    SUSPENDER("Suspender", true),
    REACTIVAR("Reactivar", false);

    private final String nombre;
    private final boolean escritura;

    TipoOperacionES(String nombre, boolean escritura) {
        this.nombre = nombre;
        this.escritura = escritura;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Indica si la operación escribe en el disco (si no, lee). */
    public boolean isEscritura() {
        return escritura;
    }

    /** Indica si la operación es del intercambio (suspender o reactivar un proceso). */
    public boolean isIntercambio() {
        return this == SUSPENDER || this == REACTIVAR;
    }
}
