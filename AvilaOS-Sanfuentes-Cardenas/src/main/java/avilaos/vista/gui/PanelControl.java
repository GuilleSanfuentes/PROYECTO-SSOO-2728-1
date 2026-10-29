package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.hardware.Reloj;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Barra superior: ciclo del reloj, estado de la simulación y botones Iniciar, Pausar/Seguir y Stop. */
public class PanelControl extends JPanel {

    private final Cluster cluster;
    private final JLabel etiquetaCiclo = new JLabel("Ciclo: 0");
    private final JLabel etiquetaEstado = new JLabel();
    private final JButton botonIniciar = new JButton("Iniciar");
    private final JButton botonPausa = new JButton("Pausar");
    private final JButton botonStop = new JButton("Stop");
    private final CampoEditable campoDuracion = new CampoEditable(5);
    private final CampoEditable campoMaxCiclos = new CampoEditable(5);
    private final JLabel errorCampos = new JLabel();
    private DialogoProceso dialogoProceso;
    private DialogoBuffer dialogoBuffer;

    /** Arma la barra sobre el clúster indicado. */
    public PanelControl(Cluster cluster) {
        super(new FlowLayout(FlowLayout.LEFT, 10, 6));
        this.cluster = cluster;
        add(botonIniciar);
        add(botonPausa);
        add(botonStop);
        add(etiquetaCiclo);
        add(etiquetaEstado);
        campoDuracion.setToolTipText("Duración de cada ciclo en milisegundos (0.." + Reloj.DURACION_MAXIMA_MS + ")");
        campoMaxCiclos.setToolTipText("Ciclo en el que se detiene la simulación (0 = sin límite)");
        add(new JLabel("Ciclo (ms):"));
        add(campoDuracion);
        add(boton("Aplicar", this::aplicarDuracion));
        add(new JLabel("Parar en el ciclo:"));
        add(campoMaxCiclos);
        add(boton("Aplicar", this::aplicarMaxCiclos));
        add(boton("Crear proceso", () -> abrir(dialogoProceso())));
        add(boton("Crear buffer", () -> abrir(dialogoBuffer())));
        errorCampos.setForeground(ColoresEstado.ROJO);
        add(errorCampos);
        campoDuracion.addActionListener(e -> aplicarDuracion());
        campoMaxCiclos.addActionListener(e -> aplicarMaxCiclos());
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
        campoDuracion.mostrarValor(String.valueOf(reloj.getDuracionCicloMs()));
        campoMaxCiclos.mostrarValor(String.valueOf(cluster.getMaxCiclos()));
        boolean marcha = reloj.isEjecutando();
        boolean pausado = marcha && reloj.isPausado();
        boolean nuevo = !marcha && reloj.getCiclo() == 0;
        etiquetaEstado.setText("Estado: " + (nuevo ? "listo para iniciar" : !marcha ? "detenida" : pausado ? "en pausa" : "en marcha"));
        botonIniciar.setEnabled(nuevo);
        botonPausa.setEnabled(marcha);
        botonPausa.setText(pausado ? "Seguir" : "Pausar");
        botonStop.setEnabled(marcha);
    }

    private DialogoProceso dialogoProceso() {
        if (dialogoProceso == null) {
            dialogoProceso = new DialogoProceso(SwingUtilities.getWindowAncestor(this), cluster);
        }
        return dialogoProceso;
    }

    private DialogoBuffer dialogoBuffer() {
        if (dialogoBuffer == null) {
            dialogoBuffer = new DialogoBuffer(SwingUtilities.getWindowAncestor(this), cluster);
        }
        return dialogoBuffer;
    }

    private static void abrir(java.awt.Window dialogo) {
        dialogo.setVisible(true);
        dialogo.toFront();
    }

    private static JButton boton(String texto, Runnable accion) {
        JButton boton = new JButton(texto);
        boton.addActionListener(e -> accion.run());
        return boton;
    }

    /** Valida la duración escrita y, si es correcta, la aplica al reloj. */
    private void aplicarDuracion() {
        Validador.ResultadoEntero r = Validador.entero(campoDuracion.getText(), "Duración del ciclo", 0, Reloj.DURACION_MAXIMA_MS);
        if (!r.valido()) {
            errorCampos.setText(r.motivo());
            return;
        }
        boolean cambiado = cluster.cambiarDuracionCiclo(r.valor());
        if (cambiado) {
            campoDuracion.confirmar();
        }
        errorCampos.setText(cambiado ? "" : "No se pudo cambiar la duración del ciclo");
    }

    /** Valida el ciclo de parada escrito y, si es correcto, lo aplica al clúster. */
    private void aplicarMaxCiclos() {
        Validador.ResultadoEntero r = Validador.entero(campoMaxCiclos.getText(), "Parar en el ciclo", 0, Integer.MAX_VALUE);
        if (!r.valido()) {
            errorCampos.setText(r.motivo());
            return;
        }
        long actual = cluster.getReloj().getCiclo();
        if (r.valor() > 0 && r.valor() <= actual) {
            errorCampos.setText("Parar en el ciclo: " + r.valor() + " ya pasó (ciclo actual " + actual + "), usa un valor mayor o 0");
            return;
        }
        boolean cambiado = cluster.cambiarMaxCiclos(r.valor());
        if (cambiado) {
            campoMaxCiclos.confirmar();
        }
        errorCampos.setText(cambiado ? "" : "No se pudo cambiar el ciclo de parada");
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
