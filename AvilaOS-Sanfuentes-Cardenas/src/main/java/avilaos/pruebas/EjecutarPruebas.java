package avilaos.pruebas;

import avilaos.config.ArchivoConfiguracion;
import avilaos.log.Debug;
import avilaos.log.LogEventos;

/** Punto de entrada de las pruebas por etapa. En NetBeans: clic derecho sobre este archivo, Run File. */
public final class EjecutarPruebas {

    private EjecutarPruebas() {
    }

    /** Ejecuta las pruebas de todas las etapas en orden. */
    public static void main(String[] args) {
        ArchivoConfiguracion.aplicarDebugger(ArchivoConfiguracion.RUTA_POR_DEFECTO);
        LogEventos log = new LogEventos(LogEventos.CAPACIDAD_POR_DEFECTO);
        Debug.setLog(log);
        PruebasPaso1.ejecutar(log);
        PruebasPaso2.ejecutar();
        PruebasPaso3.ejecutar();
        PruebasPaso4.ejecutar(log);
        PruebasPaso5.ejecutar(log);
        PruebasPaso6.ejecutar(log);
        PruebasPaso7.ejecutar(log);
        PruebasPaso8.ejecutar(log);
        PruebasPaso9.ejecutar(log);
    }
}
