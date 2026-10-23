package avilaos.enums;

/** Tipos de interrupción que el hardware envía a la CPU. */
public enum TipoInterrupcion {
    FIN_ES("Fin de E/S"),
    FIN_RED("Fin de acceso remoto");

    private final String nombre;

    TipoInterrupcion(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }
}
