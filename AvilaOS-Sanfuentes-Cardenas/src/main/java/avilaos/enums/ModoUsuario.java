package avilaos.enums;

/** Modo de uso de ÁvilaOS: el administrador puede todo; el usuario, solo lo suyo y lo público. */
public enum ModoUsuario {
    ADMINISTRADOR("Administrador"),
    USUARIO("Usuario");

    private final String nombre;

    ModoUsuario(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }
}
