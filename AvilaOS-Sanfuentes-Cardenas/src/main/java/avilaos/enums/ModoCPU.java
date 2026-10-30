package avilaos.enums;

/** Modo de ejecución del procesador. */
public enum ModoCPU {
    USUARIO("Usuario"),
    SISTEMA_OPERATIVO("Sistema operativo");

    private final String nombre;

    ModoCPU(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }
}
