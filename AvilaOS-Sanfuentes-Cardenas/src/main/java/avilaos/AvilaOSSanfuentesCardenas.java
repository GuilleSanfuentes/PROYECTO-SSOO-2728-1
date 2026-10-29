/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package avilaos;

import avilaos.cluster.Cluster;
import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfiguracionSistema;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.vista.ConsolaTerminal;
import avilaos.vista.ReporteTerminal;
import avilaos.vista.gui.VentanaPrincipal;

/**
 * Punto de entrada de la simulación: arma el clúster desde el JSON y abre la ventana en pausa, esperando Iniciar.
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

        Cluster cluster = new Cluster(config, log);
        cluster.cargarProcesosIniciales();
        new ConsolaTerminal(cluster).iniciar();
        new VentanaPrincipal(cluster).mostrar();
    }
}
