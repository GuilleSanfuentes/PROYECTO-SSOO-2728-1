package avilaos.vista;

import avilaos.cluster.Cluster;
import avilaos.cluster.Computador;
import avilaos.config.ConfiguracionSistema;
import avilaos.so.Kernel;
import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.CPU;
import avilaos.hardware.ControladorEspera;
import avilaos.hardware.RAM;
import avilaos.log.Debug;
import avilaos.metricas.CalculadorMetricas;
import avilaos.metricas.Metricas;
import avilaos.metricas.MetricasBuffer;
import avilaos.so.DatosProceso;
import avilaos.so.PCB;
import avilaos.sync.Buffer;
import avilaos.sync.EstadoBuffer;

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

    /** Estado de todo el clúster al cerrar un ciclo: por computador, CPU, RAM y las cuatro colas. */
    public static String estadoCiclo(Cluster cluster, long ciclo) {
        StringBuilder sb = new StringBuilder();
        int max = cluster.getMaxCiclos();
        sb.append(separador()).append('\n');
        sb.append(" CICLO ").append(ciclo).append(max > 0 ? " de " + max : "")
                .append("  |  ").append(cluster.getReloj().getDuracionCicloMs()).append(" ms por ciclo")
                .append(cluster.getParada().limiteTiempoActivo() ? "  |  límite " + cluster.getParada().limiteTiempoSegundos() + " s" : "")
                .append(cluster.isInactivo() ? "  |  SIN ACTIVIDAD" : "")
                .append(cluster.getReloj().isPausado() ? "  |  PAUSADO" : "").append('\n');
        for (int i = 0; i < cluster.cantidadComputadores(); i++) {
            Computador c = cluster.getComputador(i);
            Kernel k = c.getKernel();
            CPU cpu = c.getCpu();
            RAM ram = c.getRam();
            sb.append(String.format(" %s | %-26s | CPU: %-17s | RAM %s %d/%d%n", c.getNombre(), k.describirPlanificador(),
                    cpu.isOciosa() ? "ociosa" : cpu.getModo().getNombre(), barra(ram.getUsada(), ram.getCapacidad()),
                    ram.getUsada(), ram.getCapacidad()));
            PCB actual = k.getEnEjecucion();
            sb.append("   En CPU     ").append(actual == null ? "ninguno"
                    : actual.getEtiqueta() + " '" + actual.getNombre() + "' Contador=" + cpu.getContador() + "/"
                    + actual.getPrograma().totalInstrucciones() + " prio=" + actual.getPrioridadEfectiva()
                    + " dl=" + actual.getDeadlineRestante() + " (" + k.getCiclosEnCPU() + " ciclos seguidos)")
                    .append("  | última acción: ").append(cpu.getUltimaAccion()).append('\n');
            sb.append("   Nuevos     ").append(listaCorta(k.getNuevos())).append('\n');
            sb.append("   Listos     ").append(listaCorta(k.getListos())).append('\n');
            sb.append("   Bloqueados ").append(listaCorta(k.getBloqueados())).append('\n');
            sb.append("   Terminados ").append(listaCorta(k.getTerminados())).append('\n');
        }
        sb.append(lineasBuffers(cluster));
        sb.append(separador());
        return sb.toString();
    }

    /** Un bloque por buffer: elementos, semáforos, contadores y procesos bloqueados en cada semáforo. */
    public static String lineasBuffers(Cluster cluster) {
        StringBuilder sb = new StringBuilder();
        Nodo<Buffer> nodo = cluster.getBuffers().copiar().getCabeza();
        while (nodo != null) {
            EstadoBuffer e = nodo.getDato().estado();
            sb.append(String.format(" Buffer %-4s (Computador %d) %s %d/%d | vacío=%d lleno=%d mutex=%d | producidos %d, consumidos %d%n",
                    e.nombre(), e.anfitrion(), barra(e.elementos(), e.capacidad()), e.elementos(), e.capacidad(),
                    e.valorVacio(), e.valorLleno(), e.valorMutex(), e.producidos(), e.consumidos()));
            agregarBloqueados(sb, "vacío", e.bloqueadosVacio());
            agregarBloqueados(sb, "lleno", e.bloqueadosLleno());
            agregarBloqueados(sb, "mutex", e.bloqueadosMutex());
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    /** Agrega la línea de procesos bloqueados en un semáforo, si hay alguno. */
    private static void agregarBloqueados(StringBuilder sb, String semaforo, ListaEnlazada<PCB> procesos) {
        if (procesos.estaVacia()) {
            return;
        }
        sb.append("   Bloqueados en '").append(semaforo).append("': [");
        Nodo<PCB> nodo = procesos.getCabeza();
        while (nodo != null) {
            sb.append(nodo.getDato() != procesos.primero() ? ", " : "").append(nodo.getDato().getEtiqueta());
            nodo = nodo.getSiguiente();
        }
        sb.append("]\n");
    }

    /** Resumen final de la simulación: hardware de cada computador y tabla de todos los procesos. */
    public static void resumenFinal(Cluster cluster) {
        if (!Debug.isActivo()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(separador()).append('\n');
        sb.append(" SIMULACIÓN FINALIZADA EN EL CICLO ").append(cluster.getReloj().getCiclo()).append('\n');
        sb.append(separador()).append('\n');
        for (int i = 0; i < cluster.cantidadComputadores(); i++) {
            Computador c = cluster.getComputador(i);
            sb.append(" ").append(c.getNombre()).append(" (").append(c.getKernel().describirPlanificador()).append(")\n");
            sb.append(lineaCPU(c.getCpu())).append('\n');
            sb.append(lineaRAM(c.getRam())).append('\n');
            sb.append(lineaDispositivo(c.getDma())).append('\n');
        }
        sb.append(lineasBuffers(cluster));
        sb.append('\n').append(tablaMetricas(cluster));
        sb.append('\n').append(tablaProcesos(cluster.todosLosProcesos()));
        sb.append(separador());
        Debug.bloque(sb.toString());
    }

    /** Métricas acumuladas por computador y globales, y las de productor-consumidor de cada buffer. */
    public static String tablaMetricas(Cluster cluster) {
        StringBuilder sb = new StringBuilder();
        sb.append(" MÉTRICAS acumuladas hasta el ciclo ").append(cluster.getReloj().getCiclo()).append('\n');
        sb.append(String.format(" %-14s %-12s %-19s %-12s %-12s %-10s %-9s %s%n", "Ámbito", "Throughput", "CPU (usu+SO)",
                "T.respuesta", "Deadlines", "Equidad", "Terminó", "Vencidos / Vivos"));
        for (int i = 0; i < cluster.cantidadComputadores(); i++) {
            sb.append(filaMetricas(CalculadorMetricas.computador(cluster, i)));
        }
        sb.append(filaMetricas(CalculadorMetricas.global(cluster)));
        sb.append(" Throughput = procesos terminados bien por ciclo | T.respuesta = ciclos de llegada a primera ejecución\n");
        sb.append(" Deadlines = % de terminados que cumplieron | Equidad = índice de Jain sobre (tiempo de retorno / instrucciones)\n");
        ListaEnlazada<MetricasBuffer> buffers = CalculadorMetricas.buffers(cluster);
        if (!buffers.estaVacia()) {
            sb.append('\n').append(String.format(" %-8s %-11s %-11s %-9s %s%n", "Buffer", "Producidos", "Consumidos", "Procesos",
                    "Bloqueado en semáforos (promedio por proceso)"));
            Nodo<MetricasBuffer> nodo = buffers.getCabeza();
            while (nodo != null) {
                MetricasBuffer m = nodo.getDato();
                sb.append(String.format(" %-8s %-11d %-11d %-9d %s%n", m.nombre(), m.producidos(), m.consumidos(), m.procesos(),
                        m.bloqueadoPromedio() < 0 ? "n/d" : String.format("%.2f ciclos", m.bloqueadoPromedio())));
                nodo = nodo.getSiguiente();
            }
        }
        return sb.toString();
    }

    /** Fila de la tabla de métricas; n/d cuando aún no hay datos. */
    private static String filaMetricas(Metricas m) {
        return String.format(" %-14s %-12s %-19s %-12s %-12s %-10s %-9d %d / %d%n", m.nombre(),
                String.format("%.4f", m.throughput()),
                String.format("%.1f%% (%.1f+%.1f)", m.utilizacion(), m.porcentajeUsuario(), m.porcentajeSO()),
                dato(m.tiempoRespuesta(), "%.2f"), m.cumplimiento() < 0 ? "n/d" : String.format("%.1f%%", m.cumplimiento()),
                dato(m.equidad(), "%.3f"), m.terminadosOk(), m.vencidos(), m.vivos());
    }

    /** Número con el formato dado, o n/d si no hay dato. */
    private static String dato(double valor, String formato) {
        return valor < 0 ? "n/d" : String.format(formato, valor);
    }

    /** Lista del tipo [P1-H1 p2 dl40, P5-H1 (E/S: 4 ciclos en DMA)]. */
    private static String listaCorta(ListaEnlazada<PCB> procesos) {
        StringBuilder sb = new StringBuilder("[");
        Nodo<PCB> nodo = procesos.getCabeza();
        while (nodo != null) {
            PCB p = nodo.getDato();
            sb.append(sb.length() > 1 ? ", " : "").append(p.getEtiqueta());
            switch (p.getEstado()) {
                case BLOQUEADO -> sb.append(" (").append(p.getMotivoBloqueo().getNombre())
                        .append(p.getDetalleBloqueo().isEmpty() ? "" : ": " + p.getDetalleBloqueo()).append(')');
                case TERMINADO -> sb.append(p.isDeadlineVencido() ? " (deadline)" : "");
                default -> sb.append(" p").append(p.getPrioridadEfectiva()).append(" dl").append(p.getDeadlineRestante());
            }
            nodo = nodo.getSiguiente();
        }
        return sb.append(']').toString();
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
                "ID", "Nombre", "Tipo", "Estado", "Comp", "Prio", "Mem", "Progreso (Contador)", "Deadline (restante)", "Bloqueo"));
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
                barra(p.getContador(), total) + " " + p.getContador() + "/" + total,
                barra(p.getDeadlineRestante(), p.getDeadlineInicial()) + " " + p.getDeadlineRestante() + "/" + p.getDeadlineInicial(),
                bloqueo);
    }

    /** Estado de la CPU: modo, proceso cargado, registros y uso. */
    public static String lineaCPU(CPU cpu) {
        long total = cpu.getCiclosUsuario() + cpu.getCiclosSO() + cpu.getCiclosOciosos();
        String proceso = cpu.getIdProceso() == CPU.SIN_PROCESO ? "ninguno" : cpu.getEtiquetaProceso() + " Contador=" + cpu.getContador() + " MAR=" + cpu.getMar();
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
