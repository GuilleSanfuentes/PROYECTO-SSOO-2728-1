package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.config.ArchivoConfiguracion;
import avilaos.config.ConfiguracionSistema;
import avilaos.hardware.ObservadorReloj;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.GeneradorIds;
import avilaos.vista.ConsolaTerminal;
import avilaos.vista.GraficoUtilizacion;
import avilaos.vista.ReporteTerminal;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import javax.swing.JComponent;
import java.io.File;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Ventana principal de ÁvilaOS: observa al clúster y delega todas las acciones en él. */
public class VentanaPrincipal extends JFrame implements ObservadorReloj {

    private static final int REFRESCO_ESTADO_MS = 200;

    private final Cluster cluster;
    private final File archivo;
    private final PanelControl control;
    private final PanelComputador[] paneles;
    private final PanelInferior inferior = new PanelInferior();
    private final PuenteEDT<FotoCluster> puente;
    private final ConsolaTerminal consola;
    private final Timer vigilante;
    private boolean resumenEmitido;
    private boolean cargando;
    private boolean finalizado;

    /** Arma la ventana sobre el clúster armado con el archivo de configuración por defecto. */
    public VentanaPrincipal(Cluster cluster, ConsolaTerminal consola) {
        this(cluster, consola, new File(ArchivoConfiguracion.RUTA_POR_DEFECTO));
    }

    /** Arma la ventana sobre el clúster indicado, sin iniciar la simulación; archivo es la configuración vigente para reiniciar. */
    public VentanaPrincipal(Cluster cluster, ConsolaTerminal consola, File archivo) {
        super("ÁvilaOS  -  " + archivo.getName());
        this.cluster = cluster;
        this.archivo = archivo;
        this.consola = consola;
        this.control = new PanelControl(cluster);
        this.puente = new PuenteEDT<>(this::refrescar);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        add(control, BorderLayout.NORTH);

        paneles = new PanelComputador[cluster.cantidadComputadores()];
        PanelColumnas columnas = new PanelColumnas(paneles.length);
        for (int i = 0; i < paneles.length; i++) {
            paneles[i] = new PanelComputador(cluster, i);
            columnas.add(paneles[i]);
        }
        JScrollPane scroll = new JScrollPane(columnas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setMinimumSize(new Dimension(0, 150));

        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, inferior);
        division.setResizeWeight(0.75);
        division.setOneTouchExpandable(true);
        division.setContinuousLayout(true);
        division.setBorder(null);
        add(division, BorderLayout.CENTER);

        setMinimumSize(new Dimension(900, 700));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });
        cluster.agregarObservador(this);
        vigilante = new Timer(REFRESCO_ESTADO_MS, e -> vigilarEstado());
        vigilante.start();
    }

    /** Valida el archivo; si es correcto detiene este clúster y abre una ventana nueva con el clúster cargado, en pausa. */
    public void cargarConfiguracion(File nuevoArchivo) {
        if (cargando) {
            return;
        }
        ConfiguracionSistema nueva;
        try {
            nueva = ArchivoConfiguracion.cargarEstricto(nuevoArchivo.getPath());
        } catch (IllegalArgumentException e) {
            SelectorConfiguracion.error(this, "Cargar configuración",
                    "No se cargó \"" + nuevoArchivo.getName() + "\": " + e.getMessage() + "\n\nSigue la simulación actual.");
            return;
        }
        detenerYReemplazar(nuevoArchivo, nueva, "Configuración cargada: " + nuevoArchivo.getName() + ". Pulsa Iniciar.", false, false);
    }

    /**
     * Stop/reinicio: detiene la simulación, emite el resumen final y rearma todo desde el archivo de configuración vigente
     * (releído, por si cambió). Si iniciarDespues es true, la simulación nueva arranca sola.
     */
    public void reiniciar(boolean iniciarDespues) {
        if (cargando) {
            return;
        }
        ConfiguracionSistema config;
        String aviso;
        boolean error = false;
        try {
            config = ArchivoConfiguracion.cargarEstricto(archivo.getPath());
            aviso = iniciarDespues ? "Simulación reiniciada desde " + archivo.getName()
                    : "Simulación detenida y reiniciada desde " + archivo.getName() + ". Pulsa Iniciar o carga otra configuración.";
        } catch (IllegalArgumentException e) {
            config = cluster.configuracionInicial();
            aviso = "\"" + archivo.getName() + "\" ya no es válido (" + e.getMessage() + "); se reinició con la configuración anterior";
            error = true;
        }
        detenerYReemplazar(archivo, config, aviso, error, iniciarDespues);
    }

    /** Detiene y apaga este clúster en otro hilo y luego lo reemplaza por uno nuevo en el hilo de Swing. */
    private void detenerYReemplazar(File nuevoArchivo, ConfiguracionSistema config, String aviso, boolean esError,
            boolean iniciarDespues) {
        cargando = true;
        Thread hilo = new Thread(() -> {
            cluster.detener();
            cluster.apagar();
            SwingUtilities.invokeLater(() -> reemplazar(nuevoArchivo, config, aviso, esError, iniciarDespues));
        }, "Hilo-Reinicio-GUI");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void reemplazar(File nuevoArchivo, ConfiguracionSistema config, String aviso, boolean esError,
            boolean iniciarDespues) {
        emitirResumenFinal();
        Debug.setActivo(config.isDebugger());
        LogEventos log = new LogEventos(LogEventos.CAPACIDAD_POR_DEFECTO);
        Debug.setLog(log);
        ReporteTerminal.resumenConfiguracion(nuevoArchivo.getPath(), config);
        GeneradorIds.reiniciar();
        Cluster nuevo = new Cluster(config, log);
        nuevo.cargarProcesosIniciales();
        if (consola != null) {
            consola.reemplazarCluster(nuevo);
        }
        VentanaPrincipal ventana = new VentanaPrincipal(nuevo, consola, nuevoArchivo);
        ventana.setBounds(getBounds());
        ventana.setExtendedState(getExtendedState());
        ventana.mostrar();
        ventana.control.avisar(aviso, esError);
        vigilante.stop();
        dispose();
        if (iniciarDespues) {
            nuevo.iniciar();
        }
    }

    /** Muestra la ventana en el hilo de Swing, con el estado inicial ya dibujado. */
    public void mostrar() {
        SwingUtilities.invokeLater(() -> {
            refrescar(FotoCluster.de(cluster));
            setVisible(true);
        });
    }

    @Override
    public void alCerrarCiclo(long ciclo) {
        puente.publicar(FotoCluster.de(cluster));
    }

    private void refrescar(FotoCluster foto) {
        control.mostrarCiclo(foto.ciclo());
        control.actualizarEstado();
        for (int i = 0; i < paneles.length; i++) {
            paneles[i].mostrar(foto.computadores()[i]);
        }
        inferior.mostrar(foto);
    }

    /** Si el reloj se detuvo tras correr, dibuja el último estado y emite el resumen final una sola vez. */
    private void vigilarEstado() {
        if (finalizado) {
            return;
        }
        if (!cargando && cluster.getReloj().getCiclo() > 0 && !cluster.todosLosProcesos().estaVacia()
                && cluster.todosTerminados()) {
            finalizar();
            return;
        }
        control.actualizarEstado();
        if (!cluster.getReloj().isEjecutando() && cluster.getReloj().getCiclo() > 0 && !resumenEmitido) {
            refrescar(FotoCluster.de(cluster));
            emitirResumenFinal();
        }
    }

    /** Imprime el resumen final y guarda el gráfico de utilización de CPU. */
    private void emitirResumenFinal() {
        if (resumenEmitido || cluster.getReloj().getCiclo() == 0) {
            return;
        }
        resumenEmitido = true;
        ReporteTerminal.resumenFinal(cluster);
        GraficoUtilizacion.exportarPNG(cluster.getUtilizacion(), GraficoUtilizacion.RUTA_POR_DEFECTO);
    }

    /** Todos los procesos terminaron: apaga la simulación, cierra las demás ventanas, bloquea esta y muestra la despedida. */
    private void finalizar() {
        finalizado = true;
        vigilante.stop();
        Thread hilo = new Thread(() -> {
            cluster.detener();
            cluster.apagar();
            SwingUtilities.invokeLater(() -> {
                refrescar(FotoCluster.de(cluster));
                control.bloquearTodo();
                emitirResumenFinal();
                for (Window otra : Window.getWindows()) {
                    if (otra != this && otra.isDisplayable()) {
                        otra.dispose();
                    }
                }
                Bloqueo bloqueo = new Bloqueo();
                setGlassPane(bloqueo);
                bloqueo.setVisible(true);
                bloqueo.requestFocusInWindow();
                DialogoDespedida.mostrar(this, this::salir);
            });
        }, "Hilo-Fin-GUI");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Termina el programa: cierra todas las ventanas y finaliza el proceso. */
    private void salir() {
        for (Window ventana : Window.getWindows()) {
            ventana.dispose();
        }
        System.exit(0);
    }

    /** Capa semitransparente sobre la ventana que se traga ratón y teclado: la interfaz queda visible pero bloqueada. */
    private static final class Bloqueo extends JComponent {

        Bloqueo() {
            setOpaque(false);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            MouseAdapter tragar = new MouseAdapter() {
            };
            addMouseListener(tragar);
            addMouseMotionListener(tragar);
            addMouseWheelListener(tragar);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    e.consume();
                }

                @Override
                public void keyTyped(KeyEvent e) {
                    e.consume();
                }
            });
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(new Color(33, 37, 41, 120));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void cerrar() {
        cluster.apagar();
        emitirResumenFinal();
        dispose();
        System.exit(0);
    }
}
