package avilaos.vista.gui;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/** Hace que un campo de texto seleccione todo su contenido al recibir el foco (clic o Tab). */
final class SeleccionAlEnfocar {

    private SeleccionAlEnfocar() {
    }

    /** Instala la selección total al ganar el foco, salvo cuando el foco vuelve de un diálogo. */
    static void instalar(JTextComponent campo) {
        campo.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!e.isTemporary()) {
                    SwingUtilities.invokeLater(campo::selectAll);
                }
            }
        });
    }
}
