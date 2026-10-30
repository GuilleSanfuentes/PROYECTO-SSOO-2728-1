package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.hardware.Reloj;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Barra superior: ciclo del reloj, estado de la simulación y botones Iniciar, Pausar/Seguir y Stop. */
public class PanelControl extends JPanel {

    private final Cluster cluster;
    private final JLabel etiquetaCiclo = new JLabel("Ciclo: 0");
    private final JLabel etiquetaEstado = new JLabel();
    private final JButton botonIniciar = new BotonSutil("Iniciar");
    private final JButton botonPausa = new BotonSutil("Pausar");
    private final JButton botonStop = new BotonSutil("Stop");
    private final CampoEditable campoDuracion = new CampoEditable(5);
    private final CampoEditable campoMaxCiclos = new CampoEditable(5);
    private final JCheckBox casillaInactividad = new JCheckBox("Apagar por inactividad");
    private final MensajeBreve errorCampos = new MensajeBreve();
    private DialogoProceso dialogoProceso;
    private DialogoBuffer dialogoBuffer;
    private DialogoConfiguracion dialogoConfiguracion;

    /** Arma la barra sobre el clúster indicado. */
    public PanelControl(Cluster cluster) {
        super(new GridLayout(2, 1));
        this.cluster = cluster;
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        add(fila1);
        add(fila2);
        fila1.add(botonIniciar);
        fila1.add(botonPausa);
        fila1.add(botonStop);
        fila1.add(etiquetaCiclo);
        fila1.add(etiquetaEstado);
        fila1.add(boton("Crear proceso", () -> abrir(dialogoProceso())));
        fila1.add(boton("Crear buffer", () -> abrir(dialogoBuffer())));
        fila1.add(boton("Guardar configuración", this::guardarConfiguracion));
        fila1.add(boton("Cargar configuración", this::cargarConfiguracion));
        fila1.add(boton("Nueva configuración", () -> abrir(dialogoConfiguracion())));
        fila2.add(new JLabel("Ciclo (ms):"));
        fila2.add(campoDuracion);
        fila2.add(boton("Aplicar", this::aplicarDuracion));
        fila2.add(new JLabel("Parar en el ciclo:"));
        fila2.add(campoMaxCiclos);
        fila2.add(boton("Aplicar", this::aplicarMaxCiclos));
        casillaInactividad.setSelected(cluster.isApagadoInactividad());
        casillaInactividad.addActionListener(e -> cluster.setApagadoInactividad(casillaInactividad.isSelected()));
        fila2.add(casillaInactividad);
        fila2.add(errorCampos);
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

    private DialogoConfiguracion dialogoConfiguracion() {
        if (dialogoConfiguracion == null) {
            dialogoConfiguracion = new DialogoConfiguracion(SwingUtilities.getWindowAncestor(this), cluster);
        }
        return dialogoConfiguracion;
    }

    private static void abrir(java.awt.Window dialogo) {
        dialogo.setVisible(true);
        dialogo.toFront();
    }

    private static JButton boton(String texto, Runnable accion) {
        JButton boton = new BotonSutil(texto);
        boton.addActionListener(e -> accion.run());
        return boton;
    }

    /** Valida la duración escrita y, si es correcta, la aplica al reloj. */
    private void aplicarDuracion() {
        Validador.ResultadoEntero r = Validador.entero(campoDuracion.getText(), "Duración del ciclo", 0, Reloj.DURACION_MAXIMA_MS);
        if (!r.valido()) {
            errorCampos.error(r.motivo());
            return;
        }
        if (!cluster.cambiarDuracionCiclo(r.valor())) {
            errorCampos.error("No se pudo cambiar la duración del ciclo");
            return;
        }
        campoDuracion.confirmar();
        errorCampos.exito("Duración aplicada: " + r.valor() + " ms");
    }

    /** Valida el ciclo de parada escrito y, si es correcto, lo aplica al clúster. */
    private void aplicarMaxCiclos() {
        Validador.ResultadoEntero r = Validador.entero(campoMaxCiclos.getText(), "Parar en el ciclo", 0, Integer.MAX_VALUE);
        if (!r.valido()) {
            errorCampos.error(r.motivo());
            return;
        }
        long actual = cluster.getReloj().getCiclo();
        if (r.valor() > 0 && r.valor() <= actual) {
            errorCampos.error("Parar en el ciclo: " + r.valor() + " ya pasó (ciclo actual " + actual + "), usa un valor mayor o 0");
            return;
        }
        if (!cluster.cambiarMaxCiclos(r.valor())) {
            errorCampos.error("No se pudo cambiar el ciclo de parada");
            return;
        }
        campoMaxCiclos.confirmar();
        errorCampos.exito(r.valor() == 0 ? "Parada aplicada: sin límite" : "Parada aplicada: ciclo " + r.valor());
    }

    /** Pide la ruta y guarda la configuración actual del clúster. */
    private void guardarConfiguracion() {
        java.io.File archivo;
        cluster.formularioAbierto(true);
        try {
            archivo = SelectorConfiguracion.elegirParaGuardar(this);
            if (archivo == null) {
                return;
            }
            if (cluster.guardarConfiguracion(archivo.getPath())) {
                errorCampos.exito("Configuración guardada: " + archivo.getName());
            } else {
                SelectorConfiguracion.error(this, "Guardar configuración", "No se pudo guardar en " + archivo.getPath());
            }
        } finally {
            cluster.formularioAbierto(false);
        }
    }

    /** Pide el archivo y se lo pasa a la ventana para reemplazar el clúster. */
    private void cargarConfiguracion() {
        java.io.File archivo;
        cluster.formularioAbierto(true);
        try {
            archivo = SelectorConfiguracion.elegirParaCargar(this);
        } finally {
            cluster.formularioAbierto(false);
        }
        if (archivo != null && SwingUtilities.getWindowAncestor(this) instanceof VentanaPrincipal ventana) {
            ventana.cargarConfiguracion(archivo);
        }
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
