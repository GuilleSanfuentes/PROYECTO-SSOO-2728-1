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
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** Base de los formularios de creación: filas etiqueta/campo, línea de mensaje y botones Crear y Cerrar. */
abstract class DialogoFormulario extends JDialog {

    protected final Cluster cluster;
    private static final int ANCHO_MINIMO = 380;
    private static final int RETARDO_CIERRE_MS = 1500;

    private final JLabel mensaje = new JLabel(" ");
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
                mensaje.setText(" ");
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
        JButton crear = new BotonSutil("Crear");
        JButton cerrar = new BotonSutil("Cerrar");
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
        ancho = Math.max(getWidth(), ANCHO_MINIMO);
        setSize(ancho, getHeight());
        setMinimumSize(getSize());
        setLocationRelativeTo(getOwner());
    }

    /** Pone el texto en varias líneas dentro del ancho fijo y ajusta solo el alto. */
    private void mostrar(String texto, Color color) {
        mensaje.setForeground(color);
        mensaje.setText("<html><body style='width:" + (ancho - 40) + "px'>" + escapar(texto) + "</body></html>");
        pack();
        setSize(ancho, getHeight());
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
        cierre.stop();
        mostrar(texto, ColoresEstado.ROJO);
    }

    /** Muestra una confirmación en verde y cierra el formulario a los 1,5 segundos. */
    protected void exito(String texto) {
        mostrar(texto, new Color(25, 135, 84));
        cierre.restart();
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
