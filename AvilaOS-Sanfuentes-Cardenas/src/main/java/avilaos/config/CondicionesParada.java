package avilaos.config;

/**
 * Condiciones con las que la simulación se detiene sola; se aplica la primera que se cumpla.
 * maxCiclos = 0 significa sin límite de ciclos. Los segundos cuentan solo el tiempo en marcha (no las pausas).
 */
public record CondicionesParada(int maxCiclos, boolean limiteTiempoActivo, int limiteTiempoSegundos,
        boolean inactividadActiva, int inactividadSegundos) {

    public static final int DEF_MAX_CICLOS = 0;
    public static final boolean DEF_LIMITE_TIEMPO_ACTIVO = false;
    public static final int DEF_LIMITE_SEGUNDOS = 20;
    public static final boolean DEF_INACTIVIDAD_ACTIVA = true;
    public static final int DEF_INACTIVIDAD_SEGUNDOS = 40;
    public static final int MAX_SEGUNDOS = 86_400;

    /** Valores por defecto: sin límite de ciclos ni de tiempo; apagado tras 40 s de inactividad. */
    public static CondicionesParada porDefecto() {
        return new CondicionesParada(DEF_MAX_CICLOS, DEF_LIMITE_TIEMPO_ACTIVO, DEF_LIMITE_SEGUNDOS,
                DEF_INACTIVIDAD_ACTIVA, DEF_INACTIVIDAD_SEGUNDOS);
    }

    /** Texto del tipo "Máx. ciclos: sin límite | Límite de tiempo: 20 s | Apagado por inactividad: 40 s". */
    public String describir() {
        return "Máx. ciclos: " + (maxCiclos == 0 ? "sin límite" : String.valueOf(maxCiclos))
                + " | Límite de tiempo: " + (limiteTiempoActivo ? limiteTiempoSegundos + " s" : "desactivado")
                + " | Apagado por inactividad: " + (inactividadActiva ? inactividadSegundos + " s" : "desactivado");
    }
}
