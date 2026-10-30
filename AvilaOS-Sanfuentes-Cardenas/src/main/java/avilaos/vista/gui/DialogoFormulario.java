package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/** Base de los formularios de creación: filas etiqueta/campo, línea de mensaje y botones Crear y Cerrar. */
abstract class DialogoFormulario extends JDialog {

    protected final Cluster cluster;
    private static final int ANCHO_MINIMO = 380;
    private static final int RETARDO_CIERRE_MS = 3500;

    private final JTextArea mensaje = new JTextArea(1, 1);
    private final Timer cierre = new Timer(RETARDO_CIERRE_MS, e -> setVisible(false));
    private boolean contado;
    private int ancho;

    /** Arma el diálogo no modal, para que la simulación siga a la vista mientras se llena. */
    protected DialogoFormulario(Window dueno, Cluster cluster, String titulo) {
        super(dueno, titulo, ModalityType.MODELESS);
        this.cluster = cluster;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setLayout(new BorderLayout());
        cierre.setRepeats(false);
        mensaje.setEditable(false);
        mensaje.setFocusable(false);
        mensaje.setOpaque(false);
        mensaje.setLineWrap(true);
        mensaje.setWrapStyleWord(true);
        mensaje.setFont(UIManager.getFont("Label.font"));
        mensaje.setBorder(new EmptyBorder(0, 12, 0, 12));
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if (!contado) {
                    contado = true;
                    cluster.formularioAbierto(true);
                }
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                cierre.stop();
                mensaje.setText("");
                mensaje.setPreferredSize(null);
                limpiarCampos();
                reajustar();
                if (contado) {
                    contado = false;
                    cluster.formularioAbierto(false);
                }
            }
        });
    }

    /** Termina de armar el diálogo con el panel de campos ya construido. */
    protected void armar(JPanel campos) {
        campos.setBorder(new EmptyBorder(12, 12, 4, 12));
        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(campos, BorderLayout.NORTH);
        JButton crear = new BotonSutil("Crear");
        JButton cerrar = new BotonSutil("Cerrar");
        crear.addActionListener(e -> crear());
        cerrar.addActionListener(e -> setVisible(false));
        getRootPane().setDefaultButton(crear);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        botones.add(crear);
        botones.add(cerrar);
        JPanel sur = new JPanel(new BorderLayout());
        sur.add(mensaje, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.SOUTH);
        add(arriba, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
        pack();
        ancho = Math.max(getWidth(), ANCHO_MINIMO);
        setSize(ancho, getHeight());
        setLocationRelativeTo(getOwner());
    }

    /** Valida los campos y crea el elemento; muestra el motivo si se rechaza. */
    protected abstract void crear();

    /** Deja los campos del formulario como recién abierto. */
    protected abstract void limpiarCampos();

    /** Recalcula el alto con el contenido actual manteniendo el ancho fijo. */
    protected void reajustar() {
        if (ancho == 0) {
            return;
        }
        pack();
        setSize(ancho, getHeight());
    }

    /** Agrega una fila etiqueta + campo al panel de grilla y devuelve la etiqueta. */
    protected static JLabel fila(JPanel panel, int numero, String etiqueta, JComponent campo) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = numero;
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        JLabel nombre = new JLabel(etiqueta);
        panel.add(nombre, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 1, 3, 1);
        panel.add(campo, c);
        return nombre;
    }

    /** Panel de grilla vacío para las filas del formulario. */
    protected static JPanel grilla() {
        return new JPanel(new GridBagLayout());
    }

    /** Muestra un rechazo en rojo sin cerrar el formulario. */
    protected void error(String texto) {
        cierre.stop();
        mostrar(texto, ColoresEstado.ROJO);
    }

    /** Muestra una confirmación en verde y cierra el formulario a los 3,5 segundos. */
    protected void exito(String texto) {
        mostrar(texto, new Color(25, 135, 84));
        cierre.restart();
    }

    /** Parte el texto en varias líneas dentro del ancho fijo y hace crecer el formulario en alto. */
    private void mostrar(String texto, Color color) {
        mensaje.setForeground(color);
        mensaje.setText(texto);
        mensaje.setPreferredSize(null);
        mensaje.setSize(ancho - 20, Short.MAX_VALUE);
        mensaje.setPreferredSize(new java.awt.Dimension(ancho - 20, mensaje.getPreferredSize().height));
        reajustar();
    }

    /** Impide crear si la simulación ya terminó. Devuelve true si se puede seguir. */
    protected boolean simulacionAdmite() {
        if (!cluster.getReloj().isEjecutando() && cluster.getReloj().getCiclo() > 0) {
            error("La simulación ya terminó, no se pueden crear elementos nuevos");
            return false;
        }
        return true;
    }
}
