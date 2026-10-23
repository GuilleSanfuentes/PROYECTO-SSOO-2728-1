package avilaos.enums;

/** Los tres semáforos de cada buffer en el productor-consumidor. */
public enum RolSemaforo {
    VACIO("vacío", TipoSemaforo.CONTEO),
    LLENO("lleno", TipoSemaforo.CONTEO),
    MUTEX("mutex", TipoSemaforo.BINARIO);

    private final String nombre;
    private final TipoSemaforo tipo;

    RolSemaforo(String nombre, TipoSemaforo tipo) {
        this.nombre = nombre;
        this.tipo = tipo;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Indica si es un semáforo de conteo o binario. */
    public TipoSemaforo getTipo() {
        return tipo;
    }
}
