package avilaos.vista.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Pop-up final cuando terminan todos los procesos; al cerrarlo (OK o la X) se ejecuta la acción de salida. */
public final class DialogoDespedida extends JDialog {

    public static final String MENSAJE = "Procesos finalizados, gracias por usar ÁvilaOS";

    private final Runnable alCerrar;
    private boolean cerrado;

    private DialogoDespedida(Window duena, Runnable alCerrar) {
        super(duena, "ÁvilaOS", ModalityType.APPLICATION_MODAL);
        this.alCerrar = alCerrar;
        JLabel texto = new JLabel(MENSAJE, SwingConstants.CENTER);
        texto.setFont(texto.getFont().deriveFont(Font.BOLD, 16f));
        JButton ok = new BotonSutil("OK");
        ok.addActionListener(e -> cerrar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER));
        botones.add(ok);
        JPanel contenido = new JPanel(new BorderLayout(0, 16));
        contenido.setBorder(BorderFactory.createEmptyBorder(24, 32, 16, 32));
        contenido.add(texto, BorderLayout.CENTER);
        contenido.add(botones, BorderLayout.SOUTH);
        setContentPane(contenido);
        getRootPane().setDefaultButton(ok);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });
        setResizable(false);
        pack();
        setLocationRelativeTo(duena);
    }

    /** Muestra el pop-up sobre la ventana; bloquea hasta que el usuario lo cierre. */
    public static void mostrar(Window duena, Runnable alCerrar) {
        new DialogoDespedida(duena, alCerrar).setVisible(true);
    }

    /** Cierra el pop-up y ejecuta la salida una sola vez. */
    private void cerrar() {
        if (cerrado) {
            return;
        }
        cerrado = true;
        dispose();
        alCerrar.run();
    }
}
