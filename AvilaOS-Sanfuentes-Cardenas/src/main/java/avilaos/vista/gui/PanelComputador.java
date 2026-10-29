package avilaos.vista.gui;

import avilaos.enums.EstadoProceso;
import avilaos.enums.ModoCPU;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/** Columna de un computador: CPU con su proceso, RAM, política y las colas Nuevos, Listos, Bloqueados y Terminados. */
public class PanelComputador extends JPanel {

    static final int ANCHO = 500;

    private final JLabel titulo = new JLabel();
    private final JLabel proceso = new JLabel("-");
    private final JLabel registros = new JLabel("-");
    private final JLabel prioridad = new JLabel("-");
    private final BarraColor barraProgreso = new BarraColor();
    private final BarraColor barraDeadline = new BarraColor();
    private final JLabel modo = new JLabel("", SwingConstants.CENTER);
    private final JLabel accion = new JLabel("-");
    private final BarraColor barraRam = new BarraColor();
    private final JLabel politica = new JLabel();
    private final TablaProcesos tablaNuevos = new TablaProcesos(false);
    private final TablaProcesos tablaListos = new TablaProcesos(false);
    private final TablaProcesos tablaBloqueados = new TablaProcesos(true);
    private final TablaProcesos tablaTerminados = new TablaProcesos(false);
    private final JLabel cuentaNuevos = new JLabel();
    private final JLabel cuentaListos = new JLabel();
    private final JLabel cuentaBloqueados = new JLabel();
    private final JLabel cuentaTerminados = new JLabel();

    /** Arma la columna vacía; los datos llegan con mostrar. */
    public PanelComputador() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(208, 213, 220)), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        setPreferredSize(new Dimension(ANCHO, 600));
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        modo.setOpaque(true);
        modo.setForeground(Color.WHITE);
        modo.setFont(modo.getFont().deriveFont(Font.BOLD));
        modo.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        barraRam.setPreferredSize(new Dimension(100, 20));

        JPanel arriba = new JPanel();
        arriba.setLayout(new BoxLayout(arriba, BoxLayout.Y_AXIS));
        arriba.add(alinear(titulo));
        arriba.add(espacio(6));
        arriba.add(alinear(tarjetaCPU()));
        arriba.add(espacio(6));
        arriba.add(alinear(tarjetaMemoria()));
        add(arriba, BorderLayout.NORTH);

        JPanel colas = new JPanel(new GridLayout(4, 1, 0, 6));
        colas.add(seccion("Nuevos", EstadoProceso.NUEVO, tablaNuevos, cuentaNuevos));
        colas.add(seccion("Listos", EstadoProceso.LISTO, tablaListos, cuentaListos));
        colas.add(seccion("Bloqueados", EstadoProceso.BLOQUEADO, tablaBloqueados, cuentaBloqueados));
        colas.add(seccion("Terminados", EstadoProceso.TERMINADO, tablaTerminados, cuentaTerminados));
        add(colas, BorderLayout.CENTER);
    }

    /** Dibuja la foto del computador. */
    public void mostrar(FotoComputador f) {
        titulo.setText(f.nombre());
        FilaProceso p = f.enCPU();
        if (p == null) {
            proceso.setText("(ninguno)");
            registros.setText("Contador (PC): -    MAR: -");
            prioridad.setText("-");
            barraProgreso.setDatos(0, ColoresEstado.VERDE, "", Color.WHITE);
            barraDeadline.setDatos(0, ColoresEstado.GRIS, "", Color.WHITE);
        } else {
            proceso.setText(p.etiqueta() + " " + p.nombre() + " (" + p.tipo().getNombre() + ")");
            registros.setText("Contador (PC): " + f.contadorCPU() + "    MAR: " + f.marCPU());
            prioridad.setText(p.prioridadTexto());
            barraProgreso.setDatos(p.total() > 0 ? (double) p.hechas() / p.total() : 0, ColoresEstado.VERDE,
                    p.hechas() + "/" + p.total(), Color.WHITE);
            double resto = p.deadlineInicial() > 0 ? (double) p.deadlineRestante() / p.deadlineInicial() : 0;
            barraDeadline.setDatos(resto, resto > 0.5 ? new Color(96, 125, 139) : resto > 0.25 ? ColoresEstado.NARANJA : ColoresEstado.ROJO,
                    p.deadlineRestante() + "/" + p.deadlineInicial(), Color.WHITE);
        }
        if (f.ociosa()) {
            modo.setText("OCIOSA");
            modo.setBackground(ColoresEstado.GRIS);
        } else if (f.modo() == ModoCPU.SISTEMA_OPERATIVO) {
            modo.setText("MODO SO");
            modo.setBackground(ColoresEstado.NARANJA);
        } else {
            modo.setText("MODO USUARIO");
            modo.setBackground(ColoresEstado.VERDE);
        }
        accion.setText(f.ultimaAccion());
        double uso = f.ramCapacidad() > 0 ? (double) f.ramUsada() / f.ramCapacidad() : 0;
        barraRam.setDatos(uso, ColoresEstado.ram(uso),
                "RAM  " + f.ramUsada() + " usada / " + f.ramCapacidad() + "  ·  " + (f.ramCapacidad() - f.ramUsada()) + " libre",
                getBackground());
        politica.setText("Política: " + f.politica().getNombre()
                + (f.politica().name().equals("ROUND_ROBIN") ? "  ·  quantum " + f.quantum() : ""));
        tablaNuevos.mostrar(f.nuevos());
        tablaListos.mostrar(f.listos());
        tablaBloqueados.mostrar(f.bloqueados());
        tablaTerminados.mostrar(f.terminados());
        cuentaNuevos.setText(String.valueOf(f.nuevos().length));
        cuentaListos.setText(String.valueOf(f.listos().length));
        cuentaBloqueados.setText(String.valueOf(f.bloqueados().length));
        cuentaTerminados.setText(String.valueOf(f.terminados().length));
    }

    private JPanel tarjetaCPU() {
        JPanel tarjeta = new JPanel(new GridBagLayout());
        tarjeta.setBorder(BorderFactory.createTitledBorder("CPU"));
        fila(tarjeta, 0, "Proceso", proceso);
        fila(tarjeta, 1, "Registros", registros);
        fila(tarjeta, 2, "Prioridad", prioridad);
        fila(tarjeta, 3, "Progreso", barraProgreso);
        fila(tarjeta, 4, "Deadline", barraDeadline);
        fila(tarjeta, 5, "Modo", modo);
        fila(tarjeta, 6, "Acción", accion);
        return tarjeta;
    }

    private JPanel tarjetaMemoria() {
        JPanel tarjeta = new JPanel(new BorderLayout(0, 4));
        tarjeta.add(barraRam, BorderLayout.NORTH);
        tarjeta.add(politica, BorderLayout.SOUTH);
        return tarjeta;
    }

    private static void fila(JPanel tarjeta, int fila, String etiqueta, JComponent valor) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila;
        c.insets = new Insets(1, 4, 1, 4);
        c.anchor = GridBagConstraints.WEST;
        JLabel nombre = new JLabel(etiqueta);
        nombre.setForeground(new Color(108, 117, 125));
        tarjeta.add(nombre, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = valor instanceof BarraColor ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        tarjeta.add(valor, c);
    }

    private static JPanel seccion(String nombre, EstadoProceso estado, TablaProcesos tabla, JLabel cuenta) {
        JLabel titulo = new JLabel(nombre);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD));
        cuenta.setForeground(new Color(108, 117, 125));
        JPanel cabecera = new JPanel(new BorderLayout(8, 0));
        cabecera.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 5, 0, 0, ColoresEstado.fuerte(estado)),
                BorderFactory.createEmptyBorder(1, 6, 1, 0)));
        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(cuenta, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(222, 226, 230)));
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.add(cabecera, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JComponent alinear(JComponent componente) {
        componente.setAlignmentX(LEFT_ALIGNMENT);
        return componente;
    }

    private static JComponent espacio(int alto) {
        JPanel espacio = new JPanel();
        espacio.setOpaque(false);
        espacio.setPreferredSize(new Dimension(1, alto));
        espacio.setMaximumSize(new Dimension(Integer.MAX_VALUE, alto));
        espacio.setAlignmentX(LEFT_ALIGNMENT);
        return espacio;
    }
}
