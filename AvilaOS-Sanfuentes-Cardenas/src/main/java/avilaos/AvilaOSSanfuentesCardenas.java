/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package avilaos;

import avilaos.config.ArchivoConfiguracion;
import avilaos.vista.gui.VentanaPrincipal;
import javax.swing.SwingUtilities;

/**
 * Punto de entrada de la simulación: pide los dos JSON obligatorios (sistema de archivos y procesos), arma el
 * computador con su disco y abre la ventana en pausa, esperando Iniciar.
 *
 * @author guill
 */
public class AvilaOSSanfuentesCardenas {

    public static void main(String[] args) {
        ArchivoConfiguracion.aplicarDebugger(ArchivoConfiguracion.RUTA_POR_DEFECTO);
        SwingUtilities.invokeLater(VentanaPrincipal::abrirProyecto2);
    }
}
