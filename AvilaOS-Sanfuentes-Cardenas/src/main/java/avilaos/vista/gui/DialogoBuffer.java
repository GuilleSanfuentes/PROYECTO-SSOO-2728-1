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

    private final JTextField nombre = new CampoTexto(12);
    private final JTextField capacidad = new CampoTexto(12);
    private final JComboBox<String> anfitrion = new JComboBox<>();

    /** Arma el formulario sobre el clúster indicado. */
    public DialogoBuffer(Window dueno, Cluster cluster) {
        super(dueno, cluster, "Crear buffer");
        FiltroEntrada.identificador(nombre);
        FiltroEntrada.soloDigitos(capacidad, String.valueOf(ConfiguracionSistema.MAX_CAPACIDAD_BUFFER).length());
        nombre.setToolTipText("Letras, números, - y _ (sin espacios), hasta " + FiltroEntrada.LARGO_IDENTIFICADOR + " caracteres");
        for (int i = 0; i < cluster.cantidadComputadores(); i++) {
            anfitrion.addItem(cluster.getComputador(i).getNombre());
        }
        JPanel campos = grilla();
        fila(campos, 0, "Nombre:", nombre);
        fila(campos, 1, "Capacidad:", capacidad);
        fila(campos, 2, "Anfitrión:", anfitrion);
        armar(campos);
    }

    @Override
    protected void limpiarCampos() {
        nombre.setText("");
        capacidad.setText("");
        if (anfitrion.getItemCount() > 0) {
            anfitrion.setSelectedIndex(0);
        }
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
