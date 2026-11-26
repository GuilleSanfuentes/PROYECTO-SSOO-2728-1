package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.hardware.Reloj;
import avilaos.so.archivos.Usuario;
import javax.swing.JComboBox;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Barra superior: ciclo del reloj, estado de la simulación y botones Iniciar, Pausar/Seguir y Stop; en el Proyecto 2, política de disco y modo. */
public class PanelControl extends JPanel implements FuenteUsuario {

    private static final String TEXTO_ADMIN = "Administrador (admin)";
    private static final String PREFIJO_USUARIO = "Usuario: ";

    private final Cluster cluster;
    private final boolean proyecto2;
    private final JComboBox<PoliticaDiscoTipo> selectorDisco = new JComboBox<>(PoliticaDiscoTipo.values());
    private final JComboBox<String> selectorModo = new JComboBox<>(new String[]{TEXTO_ADMIN});
    private final JLabel etiquetaModo = new JLabel();
    private String[] usuarios = new String[0];
    private boolean sincronizando;
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

    /** Arma la barra del Proyecto 1 sobre el clúster indicado. */
    public PanelControl(Cluster cluster) {
        this(cluster, false);
    }

    /** Arma la barra; en el Proyecto 2 cambia la configuración por los JSON y suma la política de disco y el modo de usuario. */
    public PanelControl(Cluster cluster, boolean proyecto2) {
        super(new GridLayout(2, 1));
        this.cluster = cluster;
        this.proyecto2 = proyecto2;
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
        if (proyecto2) {
            JButton cargar = boton("Cargar JSON...", this::cargarJSON);
            cargar.setToolTipText("Detiene y vuelve a armar con otros JSON (o con el disco tal como quedó y otros procesos)");
            fila1.add(cargar);
            fila1.add(boton("Guardar sistema de archivos", this::guardarSistemaArchivos));
            armarSelectores(fila1);
            fila1.remove(botonIniciar);
            fila1.add(botonIniciar, 0);
        } else {
            fila1.add(boton("Crear buffer", () -> abrir(dialogoBuffer())));
            fila1.add(boton("Guardar configuración", this::guardarConfiguracion));
            fila1.add(boton("Cargar configuración", this::cargarConfiguracion));
            fila1.add(boton("Nueva configuración", () -> abrir(dialogoConfiguracion())));
        }
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
        FiltroEntrada.soloDigitos(campoDuracion, String.valueOf(Reloj.DURACION_MAXIMA_MS).length());
        FiltroEntrada.soloDigitos(campoMaxCiclos, String.valueOf(Integer.MAX_VALUE).length() - 1);
        campoDuracion.addActionListener(e -> aplicarDuracion());
        campoMaxCiclos.addActionListener(e -> aplicarMaxCiclos());
        botonIniciar.addActionListener(e -> iniciar());
        botonStop.setToolTipText("Detiene la simulación y reinicia todo desde el archivo de configuración");
        botonPausa.addActionListener(e -> alternarPausa());
        botonStop.addActionListener(e -> detener());
        actualizarEstado();
    }

    /** Selectores de política de disco y de modo de usuario, con el modo activo bien visible. */
    private void armarSelectores(JPanel fila) {
        selectorDisco.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> lista, Object valor, int indice,
                    boolean seleccionado, boolean enfoque) {
                return super.getListCellRendererComponent(lista, valor instanceof PoliticaDiscoTipo p ? p.getNombre() : valor,
                        indice, seleccionado, enfoque);
            }
        });
        selectorDisco.setToolTipText("Política de planificación del disco; se puede cambiar en plena ejecución");
        selectorDisco.addActionListener(e -> {
            if (!sincronizando && selectorDisco.getSelectedItem() instanceof PoliticaDiscoTipo tipo
                    && !cluster.cambiarPoliticaDisco(tipo)) {
                errorCampos.error("No se pudo cambiar la política de disco");
            }
        });
        selectorModo.setToolTipText("Administrador: todas las operaciones. Usuario: solo sus archivos y los públicos");
        selectorModo.addActionListener(e -> mostrarModo());
        etiquetaModo.setOpaque(true);
        etiquetaModo.setForeground(java.awt.Color.WHITE);
        etiquetaModo.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 8, 2, 8));
        fila.add(new JLabel("Disco:"));
        fila.add(selectorDisco);
        fila.add(new JLabel("Modo:"));
        fila.add(selectorModo);
        fila.add(etiquetaModo);
        mostrarModo();
    }

    /** Usuario activo según el selector de modo (en el Proyecto 1, siempre el administrador). */
    @Override
    public Usuario actual() {
        Object elegido = selectorModo.getSelectedItem();
        if (elegido instanceof String texto && texto.startsWith(PREFIJO_USUARIO)) {
            return Usuario.comun(texto.substring(PREFIJO_USUARIO.length()));
        }
        return Usuario.ADMIN;
    }

    /** Sincroniza los selectores con la foto: política de disco vigente y usuarios conocidos (dueños de archivos). */
    public void mostrarDisco(PoliticaDiscoTipo politica, String[] conocidos) {
        if (!proyecto2) {
            return;
        }
        sincronizando = true;
        if (politica != null && selectorDisco.getSelectedItem() != politica && !selectorDisco.isPopupVisible()) {
            selectorDisco.setSelectedItem(politica);
        }
        if (conocidos != null && !mismos(conocidos, usuarios) && !selectorModo.isPopupVisible()) {
            Object antes = selectorModo.getSelectedItem();
            usuarios = conocidos.clone();
            selectorModo.removeAllItems();
            selectorModo.addItem(TEXTO_ADMIN);
            for (String u : usuarios) {
                selectorModo.addItem(PREFIJO_USUARIO + u);
            }
            selectorModo.setSelectedItem(antes == null ? TEXTO_ADMIN : antes);
            if (selectorModo.getSelectedIndex() < 0) {
                selectorModo.setSelectedIndex(0);
            }
        }
        sincronizando = false;
        mostrarModo();
    }

    /** Etiqueta de color con el modo activo. */
    private void mostrarModo() {
        Usuario u = actual();
        etiquetaModo.setText(u.esAdmin() ? "MODO ADMINISTRADOR" : "MODO USUARIO: " + u.nombre());
        etiquetaModo.setBackground(u.esAdmin() ? ColoresEstado.AZUL : ColoresEstado.NARANJA);
    }

    private static boolean mismos(String[] a, String[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }

    /** Pide a la ventana que cargue otros JSON (Proyecto 2). */
    private void cargarJSON() {
        if (SwingUtilities.getWindowAncestor(this) instanceof VentanaPrincipal ventana) {
            ventana.cargarJSON();
        }
    }

    /** Pide la ruta y guarda el sistema de archivos actual con el formato del enunciado (Proyecto 2). */
    private void guardarSistemaArchivos() {
        cluster.formularioAbierto(true);
        try {
            java.io.File archivo = SelectorConfiguracion.elegirParaGuardarSistemaArchivos(this);
            if (archivo == null) {
                return;
            }
            avilaos.cluster.Computador pc = cluster.getComputador(0);
            if (avilaos.config.ArchivoSistemaArchivos.guardar(pc.getSistemaArchivos(), pc.getDisco(), archivo.getPath())) {
                errorCampos.exito("Sistema de archivos guardado: " + archivo.getName());
            } else {
                SelectorConfiguracion.error(this, "Guardar sistema de archivos", "No se pudo guardar en " + archivo.getPath());
            }
        } finally {
            cluster.formularioAbierto(false);
        }
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
        etiquetaEstado.setText("Estado: " + (nuevo ? "listo para iniciar" : !marcha ? "terminada" : pausado ? "en pausa" : "en marcha"));
        etiquetaEstado.setToolTipText(!marcha && !nuevo ? "Iniciar vuelve a empezar desde el archivo de configuración" : null);
        botonIniciar.setEnabled(!marcha);
        botonIniciar.setToolTipText(nuevo ? "Inicia la simulación" : "Reinicia todo desde el archivo de configuración y vuelve a correr");
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

    /** Fin de la simulación: muestra el estado final y desactiva todos los botones y campos de la barra. */
    public void bloquearTodo() {
        etiquetaEstado.setText("Estado: procesos finalizados");
        etiquetaEstado.setToolTipText(null);
        desactivar(this);
    }

    private static void desactivar(java.awt.Container contenedor) {
        for (java.awt.Component hijo : contenedor.getComponents()) {
            if (hijo instanceof javax.swing.AbstractButton || hijo instanceof javax.swing.text.JTextComponent) {
                hijo.setEnabled(false);
            } else if (hijo instanceof java.awt.Container interno) {
                desactivar(interno);
            }
        }
    }

    /** Muestra un aviso breve en la barra (verde si es confirmación, rojo si es error). */
    public void avisar(String texto, boolean esError) {
        if (esError) {
            errorCampos.error(texto);
        } else {
            errorCampos.exito(texto);
        }
    }

    /** Inicia la simulación; si ya corrió y terminó, reinicia todo y la vuelve a correr. */
    private void iniciar() {
        if (cluster.getReloj().getCiclo() == 0) {
            cluster.iniciar();
            actualizarEstado();
        } else if (SwingUtilities.getWindowAncestor(this) instanceof VentanaPrincipal ventana) {
            botonIniciar.setEnabled(false);
            ventana.reiniciar(true);
        }
    }

    /** Stop: detiene la simulación y reinicia todos los datos, listo para Iniciar o cargar otra configuración. */
    private void detener() {
        botonStop.setEnabled(false);
        botonPausa.setEnabled(false);
        if (SwingUtilities.getWindowAncestor(this) instanceof VentanaPrincipal ventana) {
            ventana.reiniciar(false);
        }
    }
}
