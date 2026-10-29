package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.config.ConfiguracionSistema;
import avilaos.sync.DatosBuffer;
import java.awt.Window;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Formulario para crear un buffer acotado: nombre, capacidad y computador anfitrión. */
public class DialogoBuffer extends DialogoFormulario {

    private final JTextField nombre = new JTextField(12);
    private final JTextField capacidad = new JTextField(12);
    private final JComboBox<String> anfitrion = new JComboBox<>();

    /** Arma el formulario sobre el clúster indicado. */
    public DialogoBuffer(Window dueno, Cluster cluster) {
        super(dueno, cluster, "Crear buffer");
        for (int i = 0; i < cluster.cantidadComputadores(); i++) {
            anfitrion.addItem(cluster.getComputador(i).getNombre());
        }
        capacidad.setToolTipText("Entre 1 y " + ConfiguracionSistema.MAX_CAPACIDAD_BUFFER + "; ocupa esa cantidad de RAM");
        JPanel campos = grilla();
        fila(campos, 0, "Nombre:", nombre);
        fila(campos, 1, "Capacidad:", capacidad);
        fila(campos, 2, "Anfitrión:", anfitrion);
        armar(campos);
    }

    @Override
    protected void crear() {
        if (!simulacionAdmite()) {
            return;
        }
        Validador.ResultadoEntero cap = Validador.entero(capacidad.getText(), "Capacidad", 1, ConfiguracionSistema.MAX_CAPACIDAD_BUFFER);
        if (!cap.valido()) {
            error(cap.motivo());
            return;
        }
        DatosBuffer datos = new DatosBuffer(nombre.getText().trim(), cap.valor(), anfitrion.getSelectedIndex());
        String motivo = cluster.validarBuffer(datos);
        if (motivo != null) {
            error("Rechazado: " + motivo);
            return;
        }
        if (cluster.crearBuffer(datos) == null) {
            error("No se pudo crear el buffer '" + datos.nombre() + "'");
            return;
        }
        exito("Buffer '" + datos.nombre() + "' creado en el " + anfitrion.getSelectedItem());
        nombre.setText("");
        capacidad.setText("");
    }
}
