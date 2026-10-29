package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.hardware.Reloj;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Barra superior: ciclo del reloj, estado de la simulación y botones Iniciar, Pausar/Seguir y Stop. */
public class PanelControl extends JPanel {

    private final Cluster cluster;
    private final JLabel etiquetaCiclo = new JLabel("Ciclo: 0");
    private final JLabel etiquetaEstado = new JLabel();
    private final JButton botonIniciar = new JButton("Iniciar");
    private final JButton botonPausa = new JButton("Pausar");
    private final JButton botonStop = new JButton("Stop");

    /** Arma la barra sobre el clúster indicado. */
    public PanelControl(Cluster cluster) {
        super(new FlowLayout(FlowLayout.LEFT, 10, 6));
        this.cluster = cluster;
        add(botonIniciar);
        add(botonPausa);
        add(botonStop);
        add(etiquetaCiclo);
        add(etiquetaEstado);
        botonIniciar.addActionListener(e -> cluster.iniciar());
        botonPausa.addActionListener(e -> alternarPausa());
        botonStop.addActionListener(e -> detener());
        actualizarEstado();
    }

    /** Muestra el ciclo recibido. */
    public void mostrarCiclo(long ciclo) {
        etiquetaCiclo.setText("Ciclo: " + ciclo);
    }

    /** Ajusta etiqueta de estado y botones según el reloj. */
    public void actualizarEstado() {
        Reloj reloj = cluster.getReloj();
        boolean marcha = reloj.isEjecutando();
        boolean pausado = marcha && reloj.isPausado();
        boolean nuevo = !marcha && reloj.getCiclo() == 0;
        etiquetaEstado.setText("Estado: " + (nuevo ? "listo para iniciar" : !marcha ? "detenida" : pausado ? "en pausa" : "en marcha"));
        botonIniciar.setEnabled(nuevo);
        botonPausa.setEnabled(marcha);
        botonPausa.setText(pausado ? "Seguir" : "Pausar");
        botonStop.setEnabled(marcha);
    }

    private void alternarPausa() {
        if (cluster.getReloj().isPausado()) {
            cluster.reanudar();
        } else {
            cluster.pausar();
        }
        actualizarEstado();
    }

    private void detener() {
        botonStop.setEnabled(false);
        Thread hilo = new Thread(() -> {
            cluster.detener();
            javax.swing.SwingUtilities.invokeLater(this::actualizarEstado);
        }, "Hilo-Stop-GUI");
        hilo.setDaemon(true);
        hilo.start();
    }
}
