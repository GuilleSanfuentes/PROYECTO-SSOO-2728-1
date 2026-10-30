package avilaos.enums;

/** Tipos de instrucción que puede ejecutar un proceso y el modo de CPU que requieren. */
public enum TipoInstruccion {
    CALCULO("Cálculo", ModoCPU.USUARIO),
    SOLICITAR_ES("Solicitud de E/S", ModoCPU.SISTEMA_OPERATIVO),
    SEM_WAIT("semWait", ModoCPU.SISTEMA_OPERATIVO),
    SEM_SIGNAL("semSignal", ModoCPU.SISTEMA_OPERATIVO),
    INSERTAR("Insertar en buffer", ModoCPU.SISTEMA_OPERATIVO),
    EXTRAER("Extraer del buffer", ModoCPU.SISTEMA_OPERATIVO);

    private final String nombre;
    private final ModoCPU modo;

    TipoInstruccion(String nombre, ModoCPU modo) {
        this.nombre = nombre;
        this.modo = modo;
    }

    /** Nombre legible para el log y la interfaz. */
    public String getNombre() {
        return nombre;
    }

    /** Modo en que la CPU ejecuta esta instrucción. */
    public ModoCPU getModo() {
        return modo;
    }
}
