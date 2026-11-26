package avilaos.so;

import avilaos.enums.EstadoProceso;
import avilaos.so.programas.ProgramaBuffer;

/** Foto de lo que las métricas leen de un proceso, tomada de una vez; buffer es null si no usa ninguno. */
public record ResumenProceso(EstadoProceso estado, boolean deadlineVencido, long tiempoRespuesta, long tiempoRetorno,
        int instrucciones, String buffer, long ciclosEnSemaforo) {

    /** Copia los datos del PCB; solo es coherente si se llama con el mutex de su Kernel tomado. */
    static ResumenProceso de(PCB pcb, long ciclo) {
        return new ResumenProceso(pcb.getEstado(), pcb.isDeadlineVencido(), pcb.getTiempoRespuesta(), pcb.getTiempoRetorno(),
                pcb.getPrograma().totalInstrucciones(),
                pcb.getPrograma() instanceof ProgramaBuffer programa ? programa.getBuffer() : null,
                pcb.getCiclosBloqueadoSemaforoHasta(ciclo));
    }
}
