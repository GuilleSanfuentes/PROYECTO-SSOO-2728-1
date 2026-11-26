package avilaos.vista.gui;

import avilaos.cluster.CargaSimulacion;
import avilaos.cluster.Computador;
import avilaos.config.DatosProcesos;
import avilaos.config.DatosSistemaArchivos;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Carga obligatoria de los dos JSON (sistema de archivos y procesos): Iniciar se habilita solo cuando están los dos. */
public final class DialogoCarga extends JDialog {

    /** JSON del enunciado que trae el proyecto, para cargarlos con un clic. */
    public static final String FS_ENUNCIADO = "config/sistema_archivos_enunciado.json";
    public static final String PROCESOS_ENUNCIADO = "config/procesos_enunciado.json";

    private final CargaSimulacion carga;
    private final Computador actual;
    private final JLabel estadoSistema = new JLabel();
    private final JLabel estadoProcesos = new JLabel();
    private final JTextArea observaciones = new JTextArea(8, 60);
    private final JButton iniciar = new BotonSutil("Abrir la simulación");
    private final ListaEnlazada<String> problemasSistema = new ListaEnlazada<>();
    private final ListaEnlazada<String> problemasProcesos = new ListaEnlazada<>();
    private boolean aceptado;

    /** Arma el diálogo modal sobre la carga indicada; si hay un computador en marcha ofrece conservar su disco. */
    public DialogoCarga(Window dueno, CargaSimulacion carga, Computador actual) {
        super(dueno, "ÁvilaOS - cargar los dos JSON", ModalityType.APPLICATION_MODAL);
        this.carga = carga;
        this.actual = actual;
        JLabel titulo = new JLabel("Antes de iniciar el reloj hay que cargar los dos archivos JSON");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 14f));
        JPanel filas = new JPanel(new GridBagLayout());
        JPanel botonesSistema = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        botonesSistema.add(boton("Elegir JSON...", this::elegirSistema));
        if (actual != null) {
            JButton conservar = boton("Conservar el disco actual", this::conservarDisco);
            conservar.setToolTipText("Reinicia con el sistema de archivos tal como quedó (archivos creados, borrados y renombrados)");
            botonesSistema.add(conservar);
        }
        fila(filas, 0, "1. Sistema de archivos", estadoSistema, botonesSistema);
        JPanel botonesProcesos = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        botonesProcesos.add(boton("Elegir JSON...", this::elegirProcesos));
        fila(filas, 1, "2. Procesos y configuración", estadoProcesos, botonesProcesos);

        observaciones.setEditable(false);
        observaciones.setLineWrap(true);
        observaciones.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(observaciones);
        scroll.setBorder(BorderFactory.createTitledBorder("Observaciones de la carga (entradas apartadas y avisos)"));
        scroll.setPreferredSize(new Dimension(640, 170));

        iniciar.addActionListener(e -> {
            aceptado = true;
            dispose();
        });
        JButton enunciado = boton("Usar los JSON del enunciado", this::usarEnunciado);
        enunciado.setToolTipText(FS_ENUNCIADO + " y " + PROCESOS_ENUNCIADO);
        JPanel sur = new JPanel(new BorderLayout());
        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        izquierda.add(enunciado);
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        derecha.add(iniciar);
        derecha.add(boton("Cancelar", this::dispose));
        sur.add(izquierda, BorderLayout.WEST);
        sur.add(derecha, BorderLayout.EAST);

        JPanel contenido = new JPanel(new BorderLayout(0, 10));
        contenido.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));
        contenido.add(titulo, BorderLayout.NORTH);
        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.add(filas, BorderLayout.NORTH);
        centro.add(scroll, BorderLayout.CENTER);
        contenido.add(centro, BorderLayout.CENTER);
        contenido.add(sur, BorderLayout.SOUTH);
        setContentPane(contenido);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(iniciar);
        actualizar();
        pack();
        setLocationRelativeTo(dueno);
    }

    /** Indica si se cerró con "Abrir la simulación" (los dos JSON cargados). */
    public boolean isAceptado() {
        return aceptado;
    }

    private void elegirSistema() {
        File archivo = SelectorConfiguracion.elegirJSON(this, "JSON del sistema de archivos");
        if (archivo != null) {
            leerSistema(archivo.getPath());
        }
    }

    private void elegirProcesos() {
        File archivo = SelectorConfiguracion.elegirJSON(this, "JSON de procesos");
        if (archivo != null) {
            leerProcesos(archivo.getPath());
        }
    }

    private void usarEnunciado() {
        leerSistema(FS_ENUNCIADO);
        leerProcesos(PROCESOS_ENUNCIADO);
    }

    private void conservarDisco() {
        problemasSistema.vaciar();
        if (!carga.usarEstadoActual(actual)) {
            problemasSistema.agregarFinal("No se pudo tomar el estado actual del disco");
        }
        actualizar();
    }

    private void leerSistema(String ruta) {
        DatosSistemaArchivos datos = carga.cargarSistemaArchivos(ruta);
        copiar(datos.problemas(), problemasSistema, "Sistema de archivos (" + new File(ruta).getName() + ")", datos.cargable());
        actualizar();
    }

    private void leerProcesos(String ruta) {
        DatosProcesos datos = carga.cargarProcesos(ruta);
        copiar(datos.problemas(), problemasProcesos, "Procesos (" + new File(ruta).getName() + ")", datos.cargable());
        actualizar();
    }

    /** Refresca los estados, las observaciones y el botón Iniciar. */
    private void actualizar() {
        DatosSistemaArchivos fs = carga.getSistemaArchivos();
        DatosProcesos procesos = carga.getProcesos();
        estadoSistema.setText(textoSistema(fs));
        estadoSistema.setForeground(fs == null ? ColoresEstado.ROJO : new Color(25, 135, 84));
        estadoProcesos.setText(textoProcesos(procesos));
        estadoProcesos.setForeground(procesos == null ? ColoresEstado.ROJO : new Color(25, 135, 84));
        String motivo = carga.motivoNoArmar();
        iniciar.setEnabled(motivo == null);
        iniciar.setToolTipText(motivo == null ? "Arma el sistema con los dos JSON; queda en pausa esperando Iniciar" : motivo);
        StringBuilder sb = new StringBuilder();
        Nodo<String> nodo = problemasSistema.getCabeza();
        while (nodo != null) {
            sb.append(nodo.getDato()).append('\n');
            nodo = nodo.getSiguiente();
        }
        nodo = problemasProcesos.getCabeza();
        while (nodo != null) {
            sb.append(nodo.getDato()).append('\n');
            nodo = nodo.getSiguiente();
        }
        observaciones.setText(sb.length() == 0 ? "Sin observaciones." : sb.toString());
        observaciones.setCaretPosition(0);
    }

    /** Estado del JSON del sistema de archivos en una línea. */
    public static String textoSistema(DatosSistemaArchivos fs) {
        return fs == null ? "✗ falta cargarlo"
                : "✓ " + fs.entradas().tamano() + " entradas, disco de " + fs.totalBloques() + " bloques, cabezal en "
                + fs.cabezalInicial() + (fs.problemas().estaVacia() ? "" : " (" + fs.problemas().tamano() + " observaciones)");
    }

    /** Estado del JSON de procesos en una línea. */
    public static String textoProcesos(DatosProcesos p) {
        return p == null ? "✗ falta cargarlo"
                : "✓ " + p.procesos().tamano() + " procesos, RAM " + p.ram() + ", " + p.politica().getNombre() + ", disco "
                + p.politicaDisco().getNombre() + ", swap " + p.bloquesSwap()
                + (p.problemas().estaVacia() ? "" : " (" + p.problemas().tamano() + " observaciones)");
    }

    /** Reemplaza las observaciones de un archivo por las de su última lectura. */
    private static void copiar(ListaEnlazada<String> origen, ListaEnlazada<String> destino, String titulo, boolean cargable) {
        destino.vaciar();
        if (!cargable) {
            destino.agregarFinal(titulo + ": NO se puede cargar, se conserva el anterior");
        }
        Nodo<String> nodo = origen.getCabeza();
        while (nodo != null) {
            destino.agregarFinal(titulo + ": " + nodo.getDato());
            nodo = nodo.getSiguiente();
        }
    }

    private static JButton boton(String texto, Runnable accion) {
        JButton boton = new BotonSutil(texto);
        boton.addActionListener(e -> accion.run());
        return boton;
    }

    private static void fila(JPanel panel, int numero, String etiqueta, JLabel estado, JPanel botones) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = numero * 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 0, 0, 10);
        JLabel nombre = new JLabel(etiqueta);
        nombre.setFont(nombre.getFont().deriveFont(Font.BOLD));
        panel.add(nombre, c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(botones, c);
        c.gridx = 0;
        c.gridy = numero * 2 + 1;
        c.gridwidth = 2;
        c.insets = new Insets(0, 14, 0, 0);
        panel.add(estado, c);
    }
}
