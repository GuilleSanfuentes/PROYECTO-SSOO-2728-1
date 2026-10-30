package avilaos.so;

import avilaos.enums.TipoProceso;
import avilaos.log.Debug;
import avilaos.so.programas.Programa;
import avilaos.so.programas.ProgramaCPUBound;
import avilaos.so.programas.ProgramaConsumidor;
import avilaos.so.programas.ProgramaIOBound;
import avilaos.so.programas.ProgramaProductor;

/** Valida los datos de un proceso y crea su PCB con el programa que corresponde a su tipo. */
public final class FabricaProcesos {

    public static final int PRIORIDAD_MAXIMA = 1;
    public static final int PRIORIDAD_MINIMA = 10;

    private static final String ORIGEN = "FabricaProcesos";

    private FabricaProcesos() {
    }

    /** Crea el PCB en estado Nuevo, o devuelve null e informa todos los datos inválidos. */
    public static PCB crear(DatosProceso datos, long cicloActual) {
        if (datos == null || datos.tipo() == null) {
            Debug.error(ORIGEN, "datos de proceso nulos o sin tipo");
            return null;
        }
        String errores = validar(datos);
        if (!errores.isEmpty()) {
            Debug.error(ORIGEN, "proceso '" + datos.nombre() + "' rechazado:" + errores);
            return null;
        }
        Programa programa = crearPrograma(datos);
        PCB pcb = new PCB(GeneradorIds.siguienteId(), datos.nombre().trim(), datos.tipo(), programa,
                datos.memoria(), datos.prioridad(), datos.deadline(), datos.computador(), cicloActual);
        Debug.ok(ORIGEN, "creado " + pcb.getEtiqueta() + " '" + pcb.getNombre() + "': " + programa.descripcion()
                + ", memoria=" + pcb.getMemoria() + ", prioridad=" + pcb.getPrioridadBase()
                + ", deadline=" + pcb.getDeadlineInicial());
        return pcb;
    }

    /** Devuelve la lista de errores de los datos, o vacío si son válidos. */
    public static String validar(DatosProceso datos) {
        StringBuilder errores = new StringBuilder();
        if (datos.nombre() == null || datos.nombre().isBlank()) {
            errores.append(" nombre vacío;");
        }
        if (datos.prioridad() < PRIORIDAD_MAXIMA || datos.prioridad() > PRIORIDAD_MINIMA) {
            errores.append(" prioridad ").append(datos.prioridad()).append(" fuera de ")
                    .append(PRIORIDAD_MAXIMA).append("..").append(PRIORIDAD_MINIMA).append(";");
        }
        if (datos.memoria() <= 0) {
            errores.append(" memoria debe ser > 0;");
        }
        if (datos.deadline() <= 0) {
            errores.append(" deadline debe ser > 0;");
        }
        if (datos.computador() < DatosProceso.ASIGNACION_AUTOMATICA) {
            errores.append(" computador ").append(datos.computador()).append(" inválido;");
        }
        TipoProceso tipo = datos.tipo();
        if (tipo == TipoProceso.CPU_BOUND || tipo == TipoProceso.IO_BOUND) {
            if (datos.instrucciones() <= 0) {
                errores.append(" instrucciones debe ser > 0;");
            }
        }
        if (tipo == TipoProceso.IO_BOUND) {
            if (datos.cadaCiclosES() <= 0) {
                errores.append(" cadaCiclosES debe ser > 0;");
            }
            if (datos.duracionES() <= 0) {
                errores.append(" duracionES debe ser > 0;");
            }
        }
        if (tipo.usaBuffer()) {
            if (datos.buffer() == null || datos.buffer().isBlank()) {
                errores.append(" buffer vacío;");
            }
            if (datos.ciclosPorElemento() <= 0) {
                errores.append(" ciclosPorElemento debe ser > 0;");
            }
            if (datos.elementos() <= 0) {
                errores.append(" elementos debe ser > 0;");
            }
        }
        return errores.toString();
    }

    /** Construye el programa según el tipo de proceso. */
    private static Programa crearPrograma(DatosProceso datos) {
        return switch (datos.tipo()) {
            case CPU_BOUND -> new ProgramaCPUBound(datos.instrucciones());
            case IO_BOUND -> new ProgramaIOBound(datos.instrucciones(), datos.cadaCiclosES(), datos.duracionES());
            case PRODUCTOR -> new ProgramaProductor(datos.buffer().trim(), datos.ciclosPorElemento(), datos.elementos());
            case CONSUMIDOR -> new ProgramaConsumidor(datos.buffer().trim(), datos.ciclosPorElemento(), datos.elementos());
        };
    }
}
