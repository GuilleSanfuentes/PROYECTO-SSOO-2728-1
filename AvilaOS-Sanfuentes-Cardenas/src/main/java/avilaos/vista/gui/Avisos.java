package avilaos.vista.gui;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/** Diálogos modales de error y de confirmación Sí/No, con los botones sin recuadro de foco. */
final class Avisos {

    private Avisos() {
    }

    /** Muestra un mensaje de error y espera a que se cierre. */
    static void error(Component padre, String titulo, String mensaje) {
        mostrar(padre, titulo, mensaje, JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION);
    }

    /** Pregunta Sí/No con un aviso de advertencia; true solo si se responde Sí. */
    static boolean confirmar(Component padre, String titulo, String mensaje) {
        return mostrar(padre, titulo, mensaje, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION)
                instanceof Integer respuesta && respuesta == JOptionPane.YES_OPTION;
    }

    private static Object mostrar(Component padre, String titulo, String mensaje, int tipo, int opciones) {
        JOptionPane panel = new JOptionPane(mensaje, tipo, opciones);
        JDialog dialogo = panel.createDialog(padre, titulo);
        sinRecuadroDeFoco(dialogo);
        dialogo.setVisible(true);
        dialogo.dispose();
        return panel.getValue();
    }

    private static void sinRecuadroDeFoco(Container contenedor) {
        for (Component componente : contenedor.getComponents()) {
            if (componente instanceof JButton boton) {
                boton.setFocusPainted(false);
            }
            if (componente instanceof Container hijo) {
                sinRecuadroDeFoco(hijo);
            }
        }
    }
}
