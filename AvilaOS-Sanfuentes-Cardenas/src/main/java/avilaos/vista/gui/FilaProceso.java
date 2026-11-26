package avilaos.vista.gui;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.TipoProceso;
import avilaos.so.PCB;
import avilaos.so.programas.ProgramaBuffer;

/** Copia inmutable de todos los datos de un PCB que muestran las tablas y la ficha del proceso. */
public record FilaProceso(
        int id,
        String etiqueta,
        String nombre,
        TipoProceso tipo,
        EstadoProceso estado,
        int prioridadBase,
        int prioridadEfectiva,
        int hechas,
        int total,
        int deadlineRestante,
        int deadlineInicial,
        boolean deadlineVencido,
        String motivo,
        int computador,
        int memoria,
        int mar,
        long cicloLlegada,
        long cicloPrimeraEjecucion,
        long cicloFin,
        String buffer,
        int elementosProcesados,
        int elementosObjetivo,
        long ciclosBloqueadoSemaforo,
        String operacionES,
        int bloqueSwap,
        String rechazo) {

    /** Toma la copia del PCB en este instante (llamar con el semáforo del Kernel tomado, vía fotografiarColas). */
    public static FilaProceso de(PCB pcb) {
        String motivo = "";
        boolean bloqueado = pcb.getEstado() == EstadoProceso.BLOQUEADO || pcb.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO;
        if (bloqueado && pcb.getMotivoBloqueo() != MotivoBloqueo.NINGUNO) {
            motivo = pcb.getMotivoBloqueo().getNombre()
                    + (pcb.getDetalleBloqueo().isEmpty() ? "" : ": " + pcb.getDetalleBloqueo());
        }
        String buffer = "";
        int objetivo = 0;
        if (pcb.getPrograma() instanceof ProgramaBuffer programa) {
            buffer = programa.getBuffer();
            objetivo = programa.getElementos();
        }
        return new FilaProceso(pcb.getId(), pcb.getEtiqueta(), pcb.getNombre(), pcb.getTipo(), pcb.getEstado(),
                pcb.getPrioridadBase(), pcb.getPrioridadEfectiva(), pcb.getContador(),
                pcb.getPrograma().totalInstrucciones(), pcb.getDeadlineRestante(), pcb.getDeadlineInicial(),
                pcb.isDeadlineVencido(), motivo, pcb.getComputador(), pcb.getMemoria(), pcb.getMar(),
                pcb.getCicloLlegada(), pcb.getCicloPrimeraEjecucion(), pcb.getCicloFin(), buffer,
                pcb.getElementosProcesados(), objetivo, pcb.getCiclosBloqueadoSemaforo(),
                pcb.getDatosES() == null ? "" : pcb.getDatosES().describir(), pcb.getBloqueSwap(),
                pcb.getMotivoRechazo() == null ? "" : pcb.getMotivoRechazo());
    }

    /** Indica si es un proceso de E/S de archivos. */
    public boolean esDeArchivo() {
        return !operacionES.isEmpty();
    }

    /** Prioridad como "base" o "base>efectiva" si el envejecimiento la mejoró. */
    public String prioridadTexto() {
        return prioridadBase == prioridadEfectiva ? String.valueOf(prioridadBase) : prioridadBase + ">" + prioridadEfectiva;
    }

    /** Tiempo restante: instrucciones que le faltan por ejecutar. */
    public int restantes() {
        return Math.max(0, total - hechas);
    }

    /** Indica si trabaja con un buffer (productor o consumidor). */
    public boolean usaBuffer() {
        return !buffer.isEmpty();
    }
}
