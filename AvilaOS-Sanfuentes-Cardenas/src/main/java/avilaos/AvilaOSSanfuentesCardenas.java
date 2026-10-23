/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package avilaos;

import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfiguracionSistema;
import avilaos.vista.ReporteTerminal;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.pruebas.PruebasPaso1;
import avilaos.pruebas.PruebasPaso2;
import avilaos.pruebas.PruebasPaso3;

/**
 *
 * @author guill
 */
public class AvilaOSSanfuentesCardenas {

    public static void main(String[] args) {
        ArchivoConfiguracion.aplicarDebugger(ArchivoConfiguracion.RUTA_POR_DEFECTO);
        LogEventos log = new LogEventos(LogEventos.CAPACIDAD_POR_DEFECTO);
        Debug.setLog(log);
        ConfiguracionSistema config = ArchivoConfiguracion.cargar(ArchivoConfiguracion.RUTA_POR_DEFECTO);
        ReporteTerminal.resumenConfiguracion(ArchivoConfiguracion.RUTA_POR_DEFECTO, config);
        PruebasPaso1.ejecutar(log);
        PruebasPaso2.ejecutar();
        PruebasPaso3.ejecutar();
    }
}
