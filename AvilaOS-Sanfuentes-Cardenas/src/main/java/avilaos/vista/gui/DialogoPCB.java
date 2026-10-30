package avilaos.vista.gui;

import avilaos.enums.EstadoProceso;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Ficha con todos los elementos del PCB de un proceso; se actualiza en vivo con cada foto del computador. */
public class DialogoPCB extends JDialog {

    private final int idProceso;
    private final JLabel encabezado = new JLabel();
    private final JLabel id = new JLabel();
    private final JLabel nombre = new JLabel();
    private final JLabel tipo = new JLabel();
    private final JLabel computador = new JLabel();
    private final JLabel estado = new JLabel();
    private final JLabel motivo = new JLabel();
    private final JLabel prioridad = new JLabel();
    private final JLabel memoria = new JLabel();
    private final BarraColor barraDeadline = new BarraColor();
    private final BarraColor barraProgreso = new BarraColor();
    private final JLabel restante = new JLabel();
    private final JLabel registros = new JLabel();
    private final JLabel llegada = new JLabel();
    private final JLabel primera = new JLabel();
    private final JLabel fin = new JLabel();
    private final JLabel buffer = new JLabel();
    private final JLabel bloqueoSemaforo = new JLabel();

    /** Crea la ficha del proceso indicado y muestra sus datos actuales. */
    public DialogoPCB(Window duena, FilaProceso fila) {
        super(duena, "PCB de " + fila.etiqueta(), ModalityType.MODELESS);
        this.idProceso = fila.id();
        encabezado.setFont(encabezado.getFont().deriveFont(Font.BOLD, 15f));
        estado.setOpaque(true);
        estado.setForeground(Color.WHITE);
        estado.setFont(estado.getFont().deriveFont(Font.BOLD));
        estado.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));

        JPanel datos = new JPanel(new GridBagLayout());
        int f = 0;
        f = seccion(datos, f, "Identificación");
        f = fila(datos, f, "ID", id);
        f = fila(datos, f, "Nombre", nombre);
        f = fila(datos, f, "Tipo", tipo);
        f = fila(datos, f, "Computador", computador);
        f = seccion(datos, f, "Estado");
        f = fila(datos, f, "Estado", estado);
        f = fila(datos, f, "Motivo de bloqueo", motivo);
        f = seccion(datos, f, "Planificación");
        f = fila(datos, f, "Prioridad", prioridad);
        f = fila(datos, f, "Deadline", barraDeadline);
        f = seccion(datos, f, "Ejecución y memoria");
        f = fila(datos, f, "Progreso", barraProgreso);
        f = fila(datos, f, "Tiempo restante", restante);
        f = fila(datos, f, "Registros", registros);
        f = fila(datos, f, "Memoria", memoria);
        f = seccion(datos, f, "Tiempos (ciclos)");
        f = fila(datos, f, "Llegada", llegada);
        f = fila(datos, f, "Primera ejecución", primera);
        f = fila(datos, f, "Fin", fin);
        f = seccion(datos, f, "Productor-consumidor");
        f = fila(datos, f, "Buffer", buffer);
        fila(datos, f, "Bloqueado en semáforos", bloqueoSemaforo);

        JButton cerrar = new BotonSutil("Cerrar");
        cerrar.addActionListener(e -> dispose());
        JPanel abajo = new JPanel(new BorderLayout());
        abajo.add(cerrar, BorderLayout.EAST);

        JPanel contenido = new JPanel(new BorderLayout(0, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        contenido.add(encabezado, BorderLayout.NORTH);
        contenido.add(datos, BorderLayout.CENTER);
        contenido.add(abajo, BorderLayout.SOUTH);
        setContentPane(contenido);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        actualizar(fila);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(duena);
    }

    /** ID del proceso que muestra esta ficha. */
    public int getIdProceso() {
        return idProceso;
    }

    /** Muestra los datos de la copia más reciente del PCB. */
    public final void actualizar(FilaProceso p) {
        encabezado.setText(p.etiqueta() + "  " + p.nombre());
        id.setText(String.valueOf(p.id()) + "   (" + p.etiqueta() + ", único en todo el clúster)");
        nombre.setText(p.nombre());
        tipo.setText(p.tipo().getNombre());
        computador.setText("Computador " + p.computador());
        estado.setText(p.estado().getNombre().toUpperCase());
        estado.setBackground(ColoresEstado.fuerte(p.estado()));
        motivo.setText(p.motivo().isEmpty() ? "-" : p.motivo());
        prioridad.setText(p.prioridadBase() == p.prioridadEfectiva()
                ? p.prioridadBase() + "   (1 = más importante)"
                : "base " + p.prioridadBase() + ", efectiva " + p.prioridadEfectiva() + " (envejecimiento)");
        memoria.setText(p.memoria() + " unidades de RAM");
        if (p.estado() == EstadoProceso.TERMINADO) {
            barraDeadline.setDatos(1, ColoresEstado.GRIS, p.deadlineVencido() ? "venció (terminado por deadline)" : "cumplido",
                    getBackground());
        } else {
            double resto = p.deadlineInicial() > 0 ? (double) p.deadlineRestante() / p.deadlineInicial() : 0;
            barraDeadline.setDatos(resto, resto > 0.5 ? new Color(96, 125, 139) : resto > 0.25 ? ColoresEstado.NARANJA : ColoresEstado.ROJO,
                    p.deadlineRestante() + " de " + p.deadlineInicial() + " ciclos", getBackground());
        }
        barraProgreso.setDatos(p.total() > 0 ? (double) p.hechas() / p.total() : 0, ColoresEstado.fuerte(p.estado()),
                p.hechas() + " de " + p.total() + " instrucciones", getBackground());
        restante.setText(p.restantes() + " instrucciones");
        registros.setText("Contador (PC) " + p.hechas() + "    MAR " + p.mar());
        llegada.setText(String.valueOf(p.cicloLlegada()));
        primera.setText(p.cicloPrimeraEjecucion() < 0 ? "aún no se ejecuta"
                : p.cicloPrimeraEjecucion() + "   (respuesta: " + (p.cicloPrimeraEjecucion() - p.cicloLlegada()) + " ciclos)");
        fin.setText(p.cicloFin() < 0 ? "-" : String.valueOf(p.cicloFin()));
        buffer.setText(p.usaBuffer() ? p.buffer() + "   (" + p.elementosProcesados() + " de " + p.elementosObjetivo() + " elementos)" : "no usa buffer");
        bloqueoSemaforo.setText(p.usaBuffer() ? p.ciclosBloqueadoSemaforo() + " ciclos" : "-");
    }

    /** Agrega un título de sección y devuelve la siguiente fila libre. */
    private static int seccion(JPanel panel, int fila, String texto) {
        JLabel titulo = new JLabel(texto);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD));
        titulo.setBorder(BorderFactory.createEmptyBorder(fila == 0 ? 0 : 8, 0, 2, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        panel.add(titulo, c);
        return fila + 1;
    }

    /** Agrega una fila "etiqueta: valor" y devuelve la siguiente fila libre. */
    private static int fila(JPanel panel, int fila, String etiqueta, javax.swing.JComponent valor) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila;
        c.insets = new Insets(1, 10, 1, 8);
        c.anchor = GridBagConstraints.WEST;
        JLabel nombreCampo = new JLabel(etiqueta);
        nombreCampo.setForeground(new Color(108, 117, 125));
        panel.add(nombreCampo, c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(1, 0, 1, 0);
        if (valor instanceof BarraColor) {
            c.fill = GridBagConstraints.HORIZONTAL;
            valor.setPreferredSize(new java.awt.Dimension(260, 18));
        }
        panel.add(valor, c);
        return fila + 1;
    }
}
