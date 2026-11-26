package avilaos.so;

import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.ReservaArchivo;
import avilaos.so.archivos.ResultadoFS;
import avilaos.so.archivos.ResultadoReserva;
import avilaos.so.archivos.SistemaArchivos;

/** Parte del Kernel que atiende las llamadas sobre archivos: las valida antes del disco y aplica los cambios después. */
final class ServicioArchivos {

    /** Respuesta de la llamada: rechazo, cambio hecho sin disco (cambio) o bloques que se piden al disco. */
    record Preparacion(String motivo, String cambio, int[] bloques, int idEntrada, ReservaArchivo reserva) {

        /** Indica si la operación necesita pasar por el disco. */
        boolean usaDisco() {
            return motivo == null && cambio == null;
        }
    }

    /** Resultado de aplicar el cambio al terminar el disco: motivo si falló, o la descripción de lo hecho. */
    record Aplicacion(String motivo, String cambio) {
    }

    private final SistemaArchivos fs;

    ServicioArchivos(SistemaArchivos fs) {
        this.fs = fs;
    }

    /** Verifica permisos y existencia; resuelve los bloques, reserva si crea, o aplica lo que no usa el disco. */
    Preparacion preparar(PCB pcb) {
        DatosES d = pcb.getDatosES();
        String ruta = SistemaArchivos.normalizar(d.ruta());
        if (ruta == null) {
            return rechazo("la ruta '" + d.ruta() + "' no es válida");
        }
        return switch (d.operacion()) {
            case CREAR -> prepararCreacion(pcb, d, ruta);
            case LEER -> {
                String motivo = fs.motivoLeer(d.usuario(), ruta);
                if (motivo != null) {
                    yield rechazo(motivo);
                }
                InfoEntrada e = fs.buscar(ruta);
                yield e.esDirectorio() ? rechazo(ruta + " es un directorio: no hay bloques que leer")
                        : alDisco(fs.bloquesDe(ruta), e.id(), null);
            }
            case ACTUALIZAR -> d.renombra() ? prepararRenombre(d, ruta) : prepararEscritura(d, ruta);
            case ELIMINAR -> prepararEliminacion(d, ruta);
            case SUSPENDER, REACTIVAR -> rechazo(d.operacion().getNombre() + " no es una operación de archivos");
        };
    }

    /** Aplica, cuando el disco ya terminó, el cambio de la solicitud que el proceso esperaba. */
    Aplicacion aplicar(PCB pcb) {
        DatosES d = pcb.getDatosES();
        SolicitudPendiente p = pcb.getSolicitudPendiente();
        ResultadoFS r = switch (d.operacion()) {
            case CREAR -> fs.confirmar(p.reserva());
            case LEER -> fs.leerPorId(d.usuario(), p.idEntrada());
            case ACTUALIZAR -> d.renombra() ? fs.renombrarPorId(d.usuario(), p.idEntrada(), d.nuevoNombre())
                    : fs.escribirPorId(d.usuario(), p.idEntrada());
            case ELIMINAR -> fs.eliminarPorId(d.usuario(), p.idEntrada());
            case SUSPENDER, REACTIVAR -> null;
        };
        if (r == null || !r.exito()) {
            return new Aplicacion(r == null ? "no es una operación de archivos" : r.motivo(), null);
        }
        String ruta = r.entrada().ruta();
        return new Aplicacion(null, switch (d.operacion()) {
            case CREAR -> "aparece " + ruta + " (" + r.bloques() + " bloques)";
            case LEER -> "lectura de " + ruta + " completada";
            case ACTUALIZAR -> d.renombra() ? "ahora se llama " + ruta : "escritura de " + ruta + " completada";
            default -> "se elimina " + ruta + " (" + r.bloques() + " bloques liberados)";
        });
    }

    /** Deshace lo apartado por una solicitud que no llegará a atenderse. */
    void descartar(ReservaArchivo reserva) {
        if (reserva != null) {
            fs.anular(reserva);
        }
    }

    /** Un directorio se crea en el acto; un archivo reserva sus bloques y espera al disco. */
    private Preparacion prepararCreacion(PCB pcb, DatosES d, String ruta) {
        if (ruta.equals(SistemaArchivos.RAIZ)) {
            return rechazo("la raíz / ya existe");
        }
        String padre = SistemaArchivos.padreDe(ruta);
        String nombre = SistemaArchivos.nombreDe(ruta);
        if (d.creaDirectorio()) {
            ResultadoFS r = fs.crearDirectorio(d.usuario(), padre, nombre, d.usuario().nombre(), d.permiso());
            return r.exito() ? sinDisco("se crea el directorio " + ruta) : rechazo(r.motivo());
        }
        ResultadoReserva r = fs.reservar(d.usuario(), padre, nombre, d.bloquesArchivo(), d.usuario().nombre(), d.permiso(),
                pcb.getEtiqueta());
        return r.exito() ? alDisco(r.reserva().bloques(), r.reserva().id(), r.reserva()) : rechazo(r.motivo());
    }

    /** Renombrar un directorio es inmediato; el de un archivo recorre su cadena. */
    private Preparacion prepararRenombre(DatosES d, String ruta) {
        String motivo = fs.motivoRenombrar(d.usuario(), ruta);
        if (motivo == null) {
            motivo = fs.motivoNuevoNombre(ruta, d.nuevoNombre());
        }
        if (motivo != null) {
            return rechazo(motivo);
        }
        InfoEntrada e = fs.buscar(ruta);
        if (!e.esDirectorio()) {
            return alDisco(fs.bloquesDe(ruta), e.id(), null);
        }
        ResultadoFS r = fs.renombrar(d.usuario(), ruta, d.nuevoNombre());
        return r.exito() ? sinDisco("el directorio " + ruta + " ahora se llama " + r.entrada().ruta()) : rechazo(r.motivo());
    }

    /** Actualizar sin nombre nuevo: escritura sobre los bloques del archivo. */
    private Preparacion prepararEscritura(DatosES d, String ruta) {
        String motivo = fs.motivoEscribir(d.usuario(), ruta);
        return motivo != null ? rechazo(motivo) : alDisco(fs.bloquesDe(ruta), fs.buscar(ruta).id(), null);
    }

    /** Eliminar recorre los bloques que se van a liberar; si no hay ninguno se hace en el acto. */
    private Preparacion prepararEliminacion(DatosES d, String ruta) {
        String motivo = fs.motivoEliminar(d.usuario(), ruta);
        if (motivo != null) {
            return rechazo(motivo);
        }
        int[] bloques = fs.bloquesDeSubarbol(ruta);
        if (bloques.length > 0) {
            return alDisco(bloques, fs.buscar(ruta).id(), null);
        }
        ResultadoFS r = fs.eliminar(d.usuario(), ruta);
        return r.exito() ? sinDisco("se elimina " + ruta + " (sin bloques)") : rechazo(r.motivo());
    }

    private static Preparacion rechazo(String motivo) {
        return new Preparacion(motivo, null, null, -1, null);
    }

    private static Preparacion sinDisco(String cambio) {
        return new Preparacion(null, cambio, null, -1, null);
    }

    private static Preparacion alDisco(int[] bloques, int idEntrada, ReservaArchivo reserva) {
        return new Preparacion(null, null, bloques, idEntrada, reserva);
    }
}
