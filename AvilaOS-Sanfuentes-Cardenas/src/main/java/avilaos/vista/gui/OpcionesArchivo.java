package avilaos.vista.gui;

import avilaos.enums.TipoEntrada;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.archivos.Usuario;

/** Qué opciones del clic derecho se ofrecen para un elemento y, si están deshabilitadas, por qué (según el modo de usuario). */
public final class OpcionesArchivo {

    /** Una opción del menú: habilitada o con el motivo del rechazo para el tooltip. */
    public record Opcion(AccionArchivo accion, boolean habilitada, String motivo) {
    }

    private OpcionesArchivo() {
    }

    /** Opciones para el elemento: directorios (crear, renombrar, eliminar) y archivos (leer, actualizar, renombrar, eliminar). */
    public static Opcion[] para(InfoEntrada entrada, Usuario usuario, SistemaArchivos fs) {
        String ruta = entrada.ruta();
        if (entrada.esDirectorio()) {
            return new Opcion[]{
                opcion(AccionArchivo.CREAR_ARCHIVO, fs.motivoCrear(usuario, ruta, TipoEntrada.ARCHIVO)),
                opcion(AccionArchivo.CREAR_DIRECTORIO, fs.motivoCrear(usuario, ruta, TipoEntrada.DIRECTORIO)),
                opcion(AccionArchivo.RENOMBRAR, fs.motivoRenombrar(usuario, ruta)),
                opcion(AccionArchivo.ELIMINAR, fs.motivoEliminar(usuario, ruta))
            };
        }
        return new Opcion[]{
            opcion(AccionArchivo.LEER, fs.motivoLeer(usuario, ruta)),
            opcion(AccionArchivo.ACTUALIZAR, fs.motivoEscribir(usuario, ruta)),
            opcion(AccionArchivo.RENOMBRAR, fs.motivoRenombrar(usuario, ruta)),
            opcion(AccionArchivo.ELIMINAR, fs.motivoEliminar(usuario, ruta))
        };
    }

    /** Busca la opción de una acción en el arreglo; null si no se ofrece para ese elemento. */
    public static Opcion buscar(Opcion[] opciones, AccionArchivo accion) {
        for (Opcion o : opciones) {
            if (o.accion() == accion) {
                return o;
            }
        }
        return null;
    }

    private static Opcion opcion(AccionArchivo accion, String motivo) {
        return new Opcion(accion, motivo == null, motivo == null ? "" : motivo);
    }
}
