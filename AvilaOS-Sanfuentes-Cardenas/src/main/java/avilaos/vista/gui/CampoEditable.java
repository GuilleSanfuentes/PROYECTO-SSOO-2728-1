package avilaos.vista.gui;

import java.awt.Color;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Campo de texto que el refresco de pantalla no sobrescribe mientras tenga una edición del usuario sin aplicar. */
public class CampoEditable extends JTextField {

    private static final Color COLOR_PENDIENTE = new Color(255, 249, 196);

    private final Color colorNormal;
    private String valorBase = "";
    private boolean pendiente;
    private boolean programatico;

    /** Crea el campo con el ancho en columnas indicado. */
    public CampoEditable(int columnas) {
        super(columnas);
        colorNormal = getBackground();
        SeleccionAlEnfocar.instalar(this);
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                cambio();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                cambio();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                cambio();
            }
        });
    }

    /** Muestra el valor actual del sistema, salvo que haya una edición pendiente de aplicar. */
    public void mostrarValor(String valor) {
        valorBase = valor;
        if (!pendiente) {
            asignar(valor);
        }
    }

    /** Marca la edición como aplicada con éxito y toma el texto actual como valor vigente. */
    public void confirmar() {
        valorBase = getText().trim();
        pendiente = false;
        asignar(valorBase);
        setBackground(colorNormal);
    }

    private void asignar(String valor) {
        if (getText().equals(valor)) {
            return;
        }
        programatico = true;
        setText(valor);
        programatico = false;
    }

    private void cambio() {
        if (!programatico) {
            pendiente = !getText().equals(valorBase);
            setBackground(pendiente ? COLOR_PENDIENTE : colorNormal);
        }
    }
}
