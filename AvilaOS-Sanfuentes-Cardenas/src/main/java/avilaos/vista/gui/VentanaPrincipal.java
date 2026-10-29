package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.hardware.ObservadorReloj;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/** Ventana principal de ÁvilaOS: observa al clúster y delega todas las acciones en él. */
public class VentanaPrincipal extends JFrame implements ObservadorReloj {

    private static final int REFRESCO_ESTADO_MS = 200;

    private final Cluster cluster;
    private final PanelControl control;
    private final PuenteEDT<Long> puente;
    private boolean enMarcha;

    /** Arma la ventana sobre el clúster indicado, sin iniciar la simulación. */
    public VentanaPrincipal(Cluster cluster) {
        super("ÁvilaOS");
        this.cluster = cluster;
        this.control = new PanelControl(cluster);
        this.puente = new PuenteEDT<>(this::refrescar);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        add(control, BorderLayout.NORTH);
        add(new JLabel("Paneles de los computadores (parte 2)", SwingConstants.CENTER), BorderLayout.CENTER);
        setMinimumSize(new Dimension(900, 600));
        setSize(1280, 760);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });
        cluster.agregarObservador(this);
        new Timer(REFRESCO_ESTADO_MS, e -> vigilarEstado()).start();
    }

    /** Muestra la ventana en el hilo de Swing. */
    public void mostrar() {
        javax.swing.SwingUtilities.invokeLater(() -> setVisible(true));
    }

    @Override
    public void alCerrarCiclo(long ciclo) {
        puente.publicar(ciclo);
    }

    private void refrescar(long ciclo) {
        control.mostrarCiclo(ciclo);
        control.actualizarEstado();
    }

    private void vigilarEstado() {
        boolean ahora = cluster.getReloj().isEjecutando();
        if (enMarcha && !ahora) {
            refrescar(cluster.getReloj().getCiclo());
        }
        enMarcha = ahora;
        control.actualizarEstado();
    }

    private void cerrar() {
        cluster.apagar();
        dispose();
        System.exit(0);
    }
}
