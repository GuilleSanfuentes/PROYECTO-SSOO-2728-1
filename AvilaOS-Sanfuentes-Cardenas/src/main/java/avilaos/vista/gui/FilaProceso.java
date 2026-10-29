package avilaos.vista.gui;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.TipoProceso;
import avilaos.so.PCB;

/** Copia inmutable de los datos de un PCB que muestran las tablas de la interfaz. */
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
        String motivo) {

    /** Toma la copia del PCB en este instante. */
    public static FilaProceso de(PCB pcb) {
        String motivo = "";
        if (pcb.getEstado() == EstadoProceso.BLOQUEADO && pcb.getMotivoBloqueo() != MotivoBloqueo.NINGUNO) {
            motivo = pcb.getMotivoBloqueo().getNombre()
                    + (pcb.getDetalleBloqueo().isEmpty() ? "" : ": " + pcb.getDetalleBloqueo());
        }
        return new FilaProceso(pcb.getId(), pcb.getEtiqueta(), pcb.getNombre(), pcb.getTipo(), pcb.getEstado(),
                pcb.getPrioridadBase(), pcb.getPrioridadEfectiva(), pcb.getContador(),
                pcb.getPrograma().totalInstrucciones(), pcb.getDeadlineRestante(), pcb.getDeadlineInicial(),
                pcb.isDeadlineVencido(), motivo);
    }

    /** Prioridad como "base" o "base>efectiva" si el envejecimiento la mejoró. */
    public String prioridadTexto() {
        return prioridadBase == prioridadEfectiva ? String.valueOf(prioridadBase) : prioridadBase + ">" + prioridadEfectiva;
    }
}
