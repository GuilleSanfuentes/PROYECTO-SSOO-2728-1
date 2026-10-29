package avilaos.vista.gui;

import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.Timer;

/** Etiqueta de aviso: el error en rojo se queda, la confirmación en verde se borra sola. */
public class MensajeBreve extends JLabel {

    private static final Color VERDE = new Color(25, 135, 84);
    private static final int DURACION_MS = 2000;

    private final Timer reloj = new Timer(DURACION_MS, e -> limpiar());

    /** Crea la etiqueta vacía. */
    public MensajeBreve() {
        super(" ");
        reloj.setRepeats(false);
    }

    /** Muestra un rechazo en rojo hasta que se limpie. */
    public void error(String texto) {
        reloj.stop();
        setForeground(ColoresEstado.ROJO);
        setText(texto);
    }

    /** Muestra una confirmación en verde unos segundos. */
    public void exito(String texto) {
        setForeground(VERDE);
        setText(texto);
        reloj.restart();
    }

    /** Borra el mensaje. */
    public void limpiar() {
        reloj.stop();
        setText(" ");
    }
}
