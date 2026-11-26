package avilaos.so;

import avilaos.enums.Permiso;
import avilaos.enums.TipoProceso;
import avilaos.log.Debug;
import avilaos.so.archivos.Usuario;
import avilaos.so.programas.Programa;
import avilaos.so.programas.ProgramaArchivo;
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

    /** Crea el PCB de un proceso de E/S de archivos que actúa como el usuario indicado; null con ERROR si algo no sirve. */
    public static PCB crearArchivo(DatosProcesoArchivo datos, Usuario usuario, Permiso permiso, int computador,
            long cicloActual) {
        String errores = validarArchivo(datos, usuario, permiso);
        if (!errores.isEmpty()) {
            Debug.error(ORIGEN, "proceso de E/S rechazado:" + errores);
            return null;
        }
        DatosES pedido = new DatosES(usuario, datos.operacion(), datos.ruta(), datos.bloquesArchivo(), datos.nuevoNombre(),
                permiso);
        Programa programa = new ProgramaArchivo(datos.instrucciones(), pedido.describir());
        PCB pcb = new PCB(GeneradorIds.siguienteId(), datos.nombre().trim(), TipoProceso.ES_ARCHIVO, programa,
                datos.memoria(), datos.prioridad(), datos.deadline(), computador, cicloActual, pedido);
        Debug.ok(ORIGEN, "creado " + pcb.getEtiqueta() + " '" + pcb.getNombre() + "': " + programa.descripcion()
                + ", usuario=" + usuario.nombre() + ", memoria=" + pcb.getMemoria() + ", deadline=" + pcb.getDeadlineInicial());
        return pcb;
    }

    /** Errores de los datos de un proceso de E/S de archivos, o vacío si son válidos. */
    public static String validarArchivo(DatosProcesoArchivo datos, Usuario usuario, Permiso permiso) {
        if (datos == null || usuario == null || permiso == null) {
            return " faltan los datos, el usuario o el permiso;";
        }
        StringBuilder errores = new StringBuilder();
        if (datos.nombre() == null || datos.nombre().isBlank()) {
            errores.append(" nombre vacío;");
        }
        if (datos.prioridad() < PRIORIDAD_MAXIMA || datos.prioridad() > PRIORIDAD_MINIMA) {
            errores.append(" prioridad fuera de ").append(PRIORIDAD_MAXIMA).append("..").append(PRIORIDAD_MINIMA).append(";");
        }
        if (datos.memoria() <= 0) {
            errores.append(" memoria debe ser > 0;");
        }
        if (datos.deadline() <= 0) {
            errores.append(" deadline debe ser > 0;");
        }
        if (datos.instrucciones() <= 0) {
            errores.append(" instrucciones debe ser > 0;");
        }
        if (datos.operacion() == null || datos.operacion().isIntercambio()) {
            errores.append(" la operación debe ser crear, leer, actualizar o eliminar;");
        }
        if (datos.ruta() == null || datos.ruta().isBlank()) {
            errores.append(" ruta vacía;");
        }
        if (datos.bloquesArchivo() < 0) {
            errores.append(" los bloques del archivo no pueden ser negativos;");
        }
        return errores.toString();
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
        if (tipo == TipoProceso.ES_ARCHIVO) {
            errores.append(" los procesos de E/S de archivos se crean con sus propios datos;");
        }
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
            case ES_ARCHIVO -> null;
        };
    }
}
