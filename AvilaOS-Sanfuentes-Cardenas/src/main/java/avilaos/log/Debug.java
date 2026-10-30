package avilaos.log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/** Salida de depuración por terminal. Solo imprime si el debugger está activo. */
public final class Debug {

    private static final int EVENTOS_EN_ERROR = 5;
    private static final Semaphore salida = new Semaphore(1);

    private static volatile boolean activo = false;
    private static volatile long ciclo = 0;
    private static volatile LogEventos log = null;
    private static final ConsolaDebug consola = new ConsolaDebug(1000);
    private static final AtomicLong errores = new AtomicLong();

    private Debug() {
    }

    /** Activa o desactiva el debugger. */
    public static void setActivo(boolean valor) {
        activo = valor;
    }

    /** Indica si el debugger está activo. */
    public static boolean isActivo() {
        return activo;
    }

    /** Actualiza el ciclo que aparece en el prefijo de cada mensaje. */
    public static void setCiclo(long valor) {
        ciclo = valor;
    }

    /** Asocia el log de eventos que se muestra junto a cada error. */
    public static void setLog(LogEventos valor) {
        log = valor;
    }

    /** Cantidad de ERROR emitidos desde que arrancó el programa (se cuentan aunque el debugger esté apagado). */
    public static long getErrores() {
        return errores.get();
    }

    /** Copia de la salida del Debug que lee la interfaz. */
    public static ConsolaDebug getConsola() {
        return consola;
    }

    /** Mensaje de operación correcta. */
    public static void ok(String origen, String mensaje) {
        if (activo) {
            escribir(formatear("OK", origen, mensaje));
        }
    }

    /** Mensaje informativo de lo que ocurre en el sistema. */
    public static void info(String origen, String mensaje) {
        if (activo) {
            escribir(formatear("INFO", origen, mensaje));
        }
    }

    /** Mensaje de error con la ubicación en el código y los últimos eventos del log. */
    public static void error(String origen, String mensaje) {
        errores.incrementAndGet();
        if (!activo) {
            return;
        }
        StringBuilder sb = new StringBuilder(formatear("ERROR", origen, mensaje));
        sb.append("\n        en ").append(ubicacionLlamada());
        LogEventos actual = log;
        if (actual != null) {
            String ultimos = actual.ultimos(EVENTOS_EN_ERROR, "\n          ");
            if (!ultimos.isEmpty()) {
                sb.append("\n        últimos eventos:\n          ").append(ultimos);
            }
        }
        escribir(sb.toString());
    }

    /** Imprime un bloque de texto tal cual, sin prefijo (reportes y gráficos). */
    public static void bloque(String texto) {
        if (activo) {
            escribir(texto);
        }
    }

    /** Arma el prefijo [Cnnnn][origen] NIVEL: mensaje. */
    private static String formatear(String nivel, String origen, String mensaje) {
        return String.format("[C%04d][%s] %s: %s", ciclo, origen, nivel, mensaje);
    }

    /** Imprime en exclusión mutua para que no se mezclen líneas de distintos hilos. */
    private static void escribir(String texto) {
        salida.acquireUninterruptibly();
        try {
            System.out.println(texto);
            consola.agregar(texto);
        } finally {
            salida.release();
        }
    }

    /** Devuelve Clase.metodo(Archivo.java:linea) de quien llamó a Debug. */
    private static String ubicacionLlamada() {
        StackTraceElement[] pila = Thread.currentThread().getStackTrace();
        for (StackTraceElement marco : pila) {
            String clase = marco.getClassName();
            if (!clase.equals(Debug.class.getName()) && !clase.equals(Thread.class.getName())) {
                return clase + "." + marco.getMethodName()
                        + "(" + marco.getFileName() + ":" + marco.getLineNumber() + ")";
            }
        }
        return "ubicación desconocida";
    }
}
