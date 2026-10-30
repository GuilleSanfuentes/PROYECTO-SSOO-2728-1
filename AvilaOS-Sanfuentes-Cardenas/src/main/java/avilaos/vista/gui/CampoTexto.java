package avilaos.vista.gui;

import javax.swing.JTextField;

/** Campo de texto de los formularios: al recibir el foco selecciona todo su contenido. */
public class CampoTexto extends JTextField {

    /** Crea el campo con el ancho en columnas indicado. */
    public CampoTexto(int columnas) {
        super(columnas);
        SeleccionAlEnfocar.instalar(this);
    }
}
