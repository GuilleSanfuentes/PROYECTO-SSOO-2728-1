package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Base de los formularios de creación: filas etiqueta/campo, línea de mensaje y botones Crear y Cerrar. */
abstract class DialogoFormulario extends JDialog {

    protected final Cluster cluster;
    private final JLabel mensaje = new JLabel(" ");

    /** Arma el diálogo no modal, para que la simulación siga a la vista mientras se llena. */
    protected DialogoFormulario(Window dueno, Cluster cluster, String titulo) {
        super(dueno, titulo, ModalityType.MODELESS);
        this.cluster = cluster;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setLayout(new BorderLayout());
    }

    /** Termina de armar el diálogo con el panel de campos ya construido. */
    protected void armar(JPanel campos) {
        campos.setBorder(new EmptyBorder(12, 12, 4, 12));
        JButton crear = new JButton("Crear");
        JButton cerrar = new JButton("Cerrar");
        crear.addActionListener(e -> crear());
        cerrar.addActionListener(e -> setVisible(false));
        getRootPane().setDefaultButton(crear);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        botones.add(crear);
        botones.add(cerrar);
        JPanel sur = new JPanel(new BorderLayout());
        mensaje.setBorder(new EmptyBorder(0, 12, 0, 12));
        sur.add(mensaje, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.SOUTH);
        add(campos, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(getOwner());
    }

    /** Valida los campos y crea el elemento; muestra el motivo si se rechaza. */
    protected abstract void crear();

    /** Agrega una fila etiqueta + campo al panel de grilla. */
    protected static void fila(JPanel panel, int numero, String etiqueta, JComponent campo) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = numero;
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(etiqueta), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 0);
        panel.add(campo, c);
    }

    /** Panel de grilla vacío para las filas del formulario. */
    protected static JPanel grilla() {
        return new JPanel(new GridBagLayout());
    }

    /** Muestra un rechazo en rojo sin cerrar el formulario. */
    protected void error(String texto) {
        mensaje.setForeground(ColoresEstado.ROJO);
        mensaje.setText("<html><body style='width:300px'>" + escapar(texto) + "</body></html>");
        pack();
    }

    /** Muestra una confirmación en verde. */
    protected void exito(String texto) {
        mensaje.setForeground(new Color(25, 135, 84));
        mensaje.setText("<html><body style='width:300px'>" + escapar(texto) + "</body></html>");
        pack();
    }

    /** Impide crear si la simulación ya terminó. Devuelve true si se puede seguir. */
    protected boolean simulacionAdmite() {
        if (!cluster.getReloj().isEjecutando() && cluster.getReloj().getCiclo() > 0) {
            error("La simulación ya terminó, no se pueden crear elementos nuevos");
            return false;
        }
        return true;
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
