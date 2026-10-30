package avilaos.vista;

/** Componentes del sistema y la etapa en que quedan listos, para el reporte de avance. */
public final class AvanceProyecto {

    private static final String[] COMPONENTES = {
        "Estructuras propias", "Enums", "Debugger y log", "Config JSON",
        "PCB y estados", "Programas (Contador)", "Políticas de planificación",
        "Reloj global", "CPU", "RAM", "DMA (E/S)",
        "Kernel y planificador", "Admisión (largo plazo)",
        "Semáforos ÁvilaOS", "Buffers productor-consumidor",
        "Clúster y red (acceso remoto)",
        "Métricas y gráfico de CPU",
        "Carga y guardado de simulaciones",
        "GUI"
    };

    private static final int[] ETAPA = {
        1, 1, 1, 1,
        2, 2, 2,
        3, 3, 3, 3,
        4, 4,
        5, 5,
        6,
        7,
        8,
        9
    };

    private AvanceProyecto() {
    }

    /** Componentes terminados hasta la etapa indicada, separados por coma. */
    public static String listos(int etapa) {
        return unir(etapa, true);
    }

    /** Componentes que faltan después de la etapa indicada, separados por coma. */
    public static String faltan(int etapa) {
        return unir(etapa, false);
    }

    /** Une los componentes que cumplen la condición de etapa. */
    private static String unir(int etapa, boolean listos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COMPONENTES.length; i++) {
            if ((ETAPA[i] <= etapa) == listos) {
                sb.append(sb.length() > 0 ? ", " : "").append(COMPONENTES[i]);
            }
        }
        return sb.length() == 0 ? "nada" : sb.toString();
    }
}
