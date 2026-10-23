package avilaos.vista;

import avilaos.config.ConfiguracionSistema;
import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.CPU;
import avilaos.hardware.ControladorEspera;
import avilaos.hardware.RAM;
import avilaos.log.Debug;
import avilaos.so.DatosProceso;
import avilaos.so.PCB;

/** Reportes gráficos en caracteres para la terminal. Solo se imprimen con el debugger activo. */
public final class ReporteTerminal {

    private static final int ANCHO = 118;
    private static final int ANCHO_BARRA = 12;

    private ReporteTerminal() {
    }

    /** Línea separadora de guiones. */
    public static String separador() {
        return "-".repeat(ANCHO);
    }

    /** Imprime el resumen de una etapa: lo que se hizo y el estado de cada proceso. */
    public static void resumenEtapa(int etapa, String titulo, ListaEnlazada<String> hechos, ListaEnlazada<String> secciones,
            ListaEnlazada<PCB> procesos, int correctas, int fallidas) {
        if (!Debug.isActivo()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(separador()).append('\n');
        sb.append(" ").append(titulo.toUpperCase())
                .append(fallidas == 0 ? "  -  COMPLETADA" : "  -  CON FALLOS").append('\n');
        sb.append(separador()).append('\n');
        sb.append(" Lo que se hizo (").append(correctas).append(" correctas, ").append(fallidas).append(" fallidas):\n");
        Nodo<String> hecho = hechos.getCabeza();
        while (hecho != null) {
            sb.append("   ").append(hecho.getDato()).append('\n');
            hecho = hecho.getSiguiente();
        }
        sb.append('\n');
        Nodo<String> seccion = secciones.getCabeza();
        while (seccion != null) {
            sb.append(seccion.getDato()).append('\n');
            seccion = seccion.getSiguiente();
        }
        if (!secciones.estaVacia()) {
            sb.append('\n');
        }
        sb.append(tablaProcesos(procesos));
        sb.append('\n');
        sb.append(" Listo: ").append(AvanceProyecto.listos(etapa)).append('\n');
        sb.append(" Falta: ").append(AvanceProyecto.faltan(etapa)).append('\n');
        sb.append(separador());
        Debug.bloque(sb.toString());
    }

    /** Imprime la configuración cargada, entre separadores. */
    public static void resumenConfiguracion(String ruta, ConfiguracionSistema config) {
        if (!Debug.isActivo()) {
            return;
        }
        Debug.bloque(separador() + "\n CONFIGURACIÓN CARGADA DE " + ruta
                + (config.getErroresCarga() == 0 ? "  -  SIN ERRORES" : "  -  " + config.getErroresCarga() + " VALORES INVÁLIDOS")
                + "\n" + separador() + "\n" + config.describir() + separador());
    }

    /** Conteo por estado y una fila por proceso con barras de progreso y de deadline. */
    public static String tablaProcesos(ListaEnlazada<PCB> procesos) {
        StringBuilder sb = new StringBuilder();
        if (procesos == null || procesos.estaVacia()) {
            return sb.append(" Procesos en el sistema: ninguno\n").toString();
        }
        sb.append(" Procesos en el sistema: ").append(procesos.tamano()).append('\n');
        sb.append(" Por estado:");
        for (EstadoProceso estado : EstadoProceso.values()) {
            sb.append("  ").append(estado.getNombre()).append(' ').append(contar(procesos, estado));
        }
        sb.append("\n\n");
        sb.append(String.format(" %-8s %-10s %-10s %-10s %-4s %-5s %-4s %-24s %-24s %s%n",
                "ID", "Nombre", "Tipo", "Estado", "Comp", "Prio", "Mem", "Progreso (PC/total)", "Deadline (restante)", "Bloqueo"));
        Nodo<PCB> nodo = procesos.getCabeza();
        while (nodo != null) {
            sb.append(fila(nodo.getDato())).append('\n');
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    /** Fila de un proceso en la tabla. */
    private static String fila(PCB p) {
        int total = p.getPrograma().totalInstrucciones();
        String computador = p.getComputador() == DatosProceso.ASIGNACION_AUTOMATICA ? "auto" : String.valueOf(p.getComputador());
        String prioridad = p.getPrioridadEfectiva() == p.getPrioridadBase()
                ? String.valueOf(p.getPrioridadBase())
                : p.getPrioridadBase() + ">" + p.getPrioridadEfectiva();
        String bloqueo = p.getMotivoBloqueo() == MotivoBloqueo.NINGUNO ? ""
                : p.getMotivoBloqueo().getNombre() + (p.getDetalleBloqueo().isEmpty() ? "" : ": " + p.getDetalleBloqueo());
        if (p.getEstado() == EstadoProceso.TERMINADO && p.isDeadlineVencido()) {
            bloqueo = "terminado por deadline";
        }
        return String.format(" %-8s %-10s %-10s %-10s %-4s %-5s %-4d %-24s %-24s %s",
                p.getEtiqueta(), recortar(p.getNombre(), 10), recortar(p.getTipo().getNombre(), 10),
                p.getEstado().getNombre(), computador, prioridad, p.getMemoria(),
                barra(p.getPc(), total) + " " + p.getPc() + "/" + total,
                barra(p.getDeadlineRestante(), p.getDeadlineInicial()) + " " + p.getDeadlineRestante() + "/" + p.getDeadlineInicial(),
                bloqueo);
    }

    /** Estado de la CPU: modo, proceso cargado, registros y uso. */
    public static String lineaCPU(CPU cpu) {
        long total = cpu.getCiclosUsuario() + cpu.getCiclosSO() + cpu.getCiclosOciosos();
        String proceso = cpu.getIdProceso() == CPU.SIN_PROCESO ? "ninguno" : cpu.getEtiquetaProceso() + " PC=" + cpu.getPc() + " MAR=" + cpu.getMar();
        return String.format(" %-10s modo=%-17s proceso=%-22s uso %s %5.1f%%  (usuario %d, SO %d, ociosa %d de %d ciclos)",
                cpu.getNombre(), cpu.isOciosa() ? "ociosa" : cpu.getModo().getNombre(), proceso,
                barra((int) (cpu.getCiclosUsuario() + cpu.getCiclosSO()), (int) total), cpu.getUtilizacion(),
                cpu.getCiclosUsuario(), cpu.getCiclosSO(), cpu.getCiclosOciosos(), total);
    }

    /** Ocupación de la RAM con barra. */
    public static String lineaRAM(RAM ram) {
        int usada = ram.getUsada();
        return String.format(" %-10s %s %d/%d usada, %d libre", ram.getNombre(), barra(usada, ram.getCapacidad()),
                usada, ram.getCapacidad(), ram.getCapacidad() - usada);
    }

    /** Operaciones en curso y completadas de un DMA o tarjeta de red. */
    public static String lineaDispositivo(ControladorEspera dispositivo) {
        return String.format(" %-10s en curso %s, completadas %d", dispositivo.getNombre(),
                dispositivo.describirPendientes(), dispositivo.getCompletadas());
    }

    /** Barra del tipo [#####.......] proporcional a actual/total. */
    public static String barra(int actual, int total) {
        int llenos = total <= 0 ? 0 : (int) Math.round((double) Math.max(0, Math.min(actual, total)) * ANCHO_BARRA / total);
        return "[" + "#".repeat(llenos) + ".".repeat(ANCHO_BARRA - llenos) + "]";
    }

    /** Cantidad de procesos en el estado indicado. */
    private static int contar(ListaEnlazada<PCB> procesos, EstadoProceso estado) {
        int cantidad = 0;
        Nodo<PCB> nodo = procesos.getCabeza();
        while (nodo != null) {
            if (nodo.getDato().getEstado() == estado) {
                cantidad++;
            }
            nodo = nodo.getSiguiente();
        }
        return cantidad;
    }

    /** Corta el texto al ancho indicado. */
    private static String recortar(String texto, int ancho) {
        return texto.length() <= ancho ? texto : texto.substring(0, ancho - 1) + "~";
    }
}
